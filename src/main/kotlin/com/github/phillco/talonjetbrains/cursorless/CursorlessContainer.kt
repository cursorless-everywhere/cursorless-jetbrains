package com.github.phillco.talonjetbrains.cursorless

import com.github.phillco.talonjetbrains.sync.cursorlessRootPath
import com.github.phillco.talonjetbrains.sync.cursorlessTempFiles
import com.github.phillco.talonjetbrains.sync.isActiveCursorlessEditor
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.ui.JBColor
import com.intellij.util.io.readText
import groovy.json.JsonException
import io.methvin.watcher.DirectoryChangeEvent
import io.methvin.watcher.DirectoryWatcher
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.slf4j.helpers.NOPLogger
import java.awt.Color
import java.awt.Graphics
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentLinkedQueue
import javax.imageio.ImageIO
import javax.swing.JComponent
import kotlin.io.path.exists


/**
 * Renders the Cursorless hats within the editor.
 *
 * One is created for every editor and attached directly to its AWT component as a child component.
 */
class CursorlessContainer(val editor: Editor) : JComponent() {
    private val parent: JComponent = editor.contentComponent

    private var watcher: DirectoryWatcher
    private var watchThread: Thread
    private var started = false

    /**
     * When local changes are made (e.g., a keystroke is pushed) we record these offsets
     * temporarily, so we can adjust hats later in the document before we get them back from
     * the sidecar. This is purely a quality of life improvement.
     */
    private val localOffsets = ConcurrentLinkedQueue<Pair<Int, Int>>()

    private var colors = DEFAULT_COLORS

    private val log = logger<CursorlessContainer>()

    private val shapeImageCache = mutableMapOf<String, BufferedImage>()

    init {
        this.parent.add(this)
        this.bounds = parent.bounds

        this.assignColors()

        val rootPath = cursorlessRootPath()

        // We create a watcher for the Cursorless hats file, as well as the colors configuration
        // file.
        //
        // It's necessary to watch the hats file because `paintComponent()` is only called
        // when there are changes on the JetBrains side, but the new hats come from the sidecar
        // slightly after that, so we need to know when that happens and trigger a re-render.
        this.watcher = DirectoryWatcher.builder()
            .path(rootPath)
            .logger(NOPLogger.NOP_LOGGER)
            .listener { event: DirectoryChangeEvent ->
                if (event.path() == colorsPath()) {
                    log.debug("Colors updated ($event); re rendering...")
                    this.assignColors()
                } else if (event.path() == hatsPath()) {
                    log.debug("Hats updated ($event); re rendering...")
//                    println("Hats updated ($event); re rendering...")
                    this.assignColors()
                    localOffsets.clear()
                    this.invalidate()
                    this.repaint()
//                } else if (event.path().fileName.toString() == "root") {
//                    println("Root updated ($event); reevaluating...")
//                    forceRefreshCursorlessRoot()
                } else {
                    log.debug("Other event ($event); ignoring...")
                }
            }.build()

        watchThread = Thread {
            try {
                this.watcher.watch()
            } catch (e: UnsatisfiedLinkError) {

                println("PH: |${e}|")
                // NOTE(pcohen): On 2023.1 there seems to be a JNI link error.
                Notifications.Bus.notify(
                    Notification(
                        "talon",
                        "JNI UnsatisfiedLinkError",
                        e.message ?: "Unknown JNI error",
                        NotificationType.ERROR
                    )
                )
                throw e;
            }
        }
        watchThread.start()

        isVisible = true
        log.info("Cursorless container initialized for editor $editor!")
    }

    fun remove() {
        this.parent.remove(this)
        this.parent.invalidate()
        this.parent.repaint()
        this.watcher.close()
        this.watchThread.interrupt()
    }

    fun hatsPath(): Path {
        return Paths.get(cursorlessRootPath().toString(), HATS_FILENAME)
    }

    fun colorsPath(): Path {
        return Paths.get(cursorlessRootPath().toString(), COLORS_FILENAME)
    }

    fun shapeImage(name: String): BufferedImage {
        val imagePath = SHAPES_DIRECTORY.resolve("$name.png").toUri()
        return ImageIO.read(File(imagePath))
    }

    fun coloredShapeImage(
        fullKeyName: String,
        shapeName: String,
        colorName: String
    ): BufferedImage? {
        if (shapeImageCache.containsKey(fullKeyName)) {
            return shapeImageCache[fullKeyName]
        }

        println("generating image for $fullKeyName")
        val shape = shapeImage(shapeName)
        val color = colorForName(colorName) ?: return null

        // TODO(pcohen): don't hard code dark mode
        val coloredImage = colorImageAndPreserveAlpha(shape, color.darkVariant)
        shapeImageCache[fullKeyName] = coloredImage
        return coloredImage
    }

    private fun colorImageAndPreserveAlpha(
        img: BufferedImage,
        c: Color
    ): BufferedImage {
        val raster = img.raster
        val pixel = intArrayOf(c.red, c.green, c.blue)
        for (x in 0 until raster.width) for (y in 0 until raster.height) for (b in pixel.indices) raster.setSample(
            x, y, b,
            pixel[b]
        )
        return img
    }

    /**
     * Assigns our colors by taking the default colors and overriding them with
     * the values (if any) in `COLORS_PATH`.
     */
    fun assignColors() {
        val colors = ColorsFormat()

        DEFAULT_COLORS.forEach { (colorScheme, defaults) ->
            run {
                colors[colorScheme] = HashMap()
                colors[colorScheme]!!.putAll(defaults)
            }
        }

        if (Files.exists(Path.of(COLORS_FILENAME))) {
            val format = Json { isLenient = true }

            // TODO(pcohen): anywhere where we parse JSON, show appropriate errors to the user
            // if the parse fails
            val map = format.decodeFromString<ColorsFormat>(
                Path.of(COLORS_FILENAME).readText()
            )

            map.forEach { colorScheme, colorMap ->
                colorMap.forEach { name, hex ->
                    colors[colorScheme]?.set(name, hex)
                }
            }
        }

        this.colors = colors
    }

    /**
     * Records a "local offset" (a change to the document that's created before
     * VS Code has had time to generate new hats from that edit).
     *
     * This is just to make hats a little look bit less janky as the user performs edits. It's pretty fragile
     * because we don't handle overlapping requests well (the hats file isn't associated with local serial,
     * so we will load old hats if the user is making a large series of changes)
     */
    fun addLocalOffset(startOffset: Int, sizeDelta: Int) {
        localOffsets += Pair(startOffset, sizeDelta)
        log.info("localOffsets = $localOffsets")
        this.invalidate()
        this.repaint()
    }

    fun startWatchingIfNeeded() {
        if (started) {
            return
        }

        started = true
    }

    fun editorPath(): String? {
        val file = FileDocumentManager.getInstance().getFile(editor.document)
        return file?.path
    }

    /**
     * Returns the list of hat decorations for this editor, if there is a valid one.
     */
    fun getHats(): HatsFormat? {
        try {
            val format = Json { isLenient = true }

            val map =
                format.decodeFromString<HashMap<String, HashMap<String, ArrayList<CursorlessRange>>>>(
                    File(hatsPath().toString()).readText()
                )

            val editorPath = editorPath()

            if (!cursorlessTempFiles.containsKey(editorPath)) {
                return null
            }

            val editorTemporaryPath =
                cursorlessTempFiles[editorPath]!!.toAbsolutePath().toString()

            // Don't render stale hats for other files. This usually happens when switching between files.
            if (!map.containsKey(editorTemporaryPath)) {
                return null
            }

            return map[editorTemporaryPath]!!
        } catch (e: JsonException) {
            log.info(e)
            e.printStackTrace()

            return null
        } catch (e: Exception) {
            // kotlinx.serialization.json.internal.JsonDecodingException

            log.info(e)
            e.printStackTrace()

            return null
        }
    }

    fun colorForName(colorName: String): JBColor? {
        val lightColor = this.colors["light"]?.get(colorName)
        val darkColor = this.colors["dark"]?.get(colorName)

        if (lightColor == null || darkColor == null) {
            println("Missing color for $colorName")
            return null
        }

        return JBColor(
            Color.decode(lightColor),
            Color.decode(darkColor)
        )
    }

    fun renderForColor(
        g: Graphics,
        mapping: HatsFormat,
        fullKeyName: String,
        colorName: String,
        shapeName: String?
    ) {
        mapping[fullKeyName]!!.forEach { range: CursorlessRange ->
            var offset = range.startOffset(editor)

            localOffsets.forEach { pair ->
                if (offset >= pair.first) {
                    log.warn("adjusting $offset to ${offset + pair.second} due to local offset: $localOffsets")
                    offset += pair.second
                }
            }

            val logicalPosition = range.start?.toLogicalPosition()!!

            val coordinates = editor.visualPositionToXY(
                editor.logicalToVisualPosition(logicalPosition)
            )

            val color = this.colorForName(colorName) ?: return
            g.color = color

            if (shapeName != null) {
                val size = SHAPE_SIZE
                val image = coloredShapeImage(fullKeyName, shapeName, colorName)
                g.drawImage(
                    image,
                    coordinates.x,
                    coordinates.y - SHAPE_SIZE / 2 + 1,
                    size, size,
                    null
                )
            } else {
                val size = OVAL_SIZE
                g.fillOval(
                    coordinates.x + OVAL_SIZE,
                    coordinates.y - size / 2,
                    size,
                    size
                )
            }
        }
    }

    fun doPainting(g: Graphics) {
        val mapping = getHats() ?: return

//        println("Redrawing for ${editorPath()}...")
        mapping.keys.forEach { fullName ->
            run {
                var shape: String? = null
                val color: String

                if (fullName.indexOf("-") > 0) {
                    val parts = fullName.split("-")
                    shape = parts[1]
                    color = parts[0]
                } else {
                    color = fullName
                }

                renderForColor(g, mapping, fullName, color, shape)
            }
        }
    }

    fun isLibraryFile(): Boolean {
        val path = editorPath()
        // TODO(pcohen): hack for now; detect if the module is marked as a library
        // /Users/phillco/Library/Java/JavaVirtualMachines/corretto-11.0.14.1/Contents/Home/lib/src.zip!/java.desktop/javax/swing/JComponent.java
        return (path != null) && "node_modules/" in path
    }

    fun isReadOnly(): Boolean {
        val path = editorPath()
        return !editor.document.isWritable || path == null || !Files.isWritable(
            Path.of(path)
        ) || isLibraryFile()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        if (isReadOnly()) {
            // NOTE(pcohen): work round bad performance in read only library
            return
        }

        startWatchingIfNeeded()

        if (!hatsPath().exists()) {
            log.info("Hatsfile ${hatsPath()} doesn't exist; not withdrawing...")
            return
        }

        if (!isActiveCursorlessEditor()) {
            return
        }

        try {
            doPainting(g)
        } catch (e: NullPointerException) {
            e.printStackTrace()
        } catch (e: JsonException) {
            e.printStackTrace()
        }
    }
}
