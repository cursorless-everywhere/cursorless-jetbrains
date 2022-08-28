package com.github.phillco.talonjetbrains.cursorless

import com.github.phillco.talonjetbrains.sync.isActiveCursorlessEditor
import com.github.phillco.talonjetbrains.sync.tempFiles
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.ui.JBColor
import com.intellij.util.io.readText
import groovy.json.JsonException
import io.methvin.watcher.DirectoryChangeEvent
import io.methvin.watcher.DirectoryWatcher
import io.sentry.Sentry
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.slf4j.helpers.NOPLogger
import java.awt.Color
import java.awt.Graphics
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentLinkedQueue
import javax.swing.JComponent

typealias HatsFormat = HashMap<String, ArrayList<CursorlessRange>>
typealias ColorsFormat = HashMap<String, HashMap<String, String>>

val DEFAULT_COLORS = mapOf(
    "light" to mapOf(
        "default" to "#757180",
        "blue" to "#089ad3",
        "green" to "#36B33F",
        "red" to "#E02D28",
        "pink" to "#e0679f",
        "yellow" to "#edb62b",
        "userColor1" to "#6a00ff",
        "userColor2" to "#ffd8b1",
    ),
    "dark" to mapOf(
        "default" to "#aaa7bb",
        "blue" to "#089ad3",
        "green" to "#36B33F",
        "red" to "#E02D28",
        "pink" to "#E06CAA",
        "yellow" to "#E5C02C",
        "userColor1" to "#6a00ff",
        "userColor2" to "#ffd8b1",
    )
)

class CursorlessContainer(val editor: Editor) : JComponent() {
    private var watcher: DirectoryWatcher
    private var watchThread: Thread
    private val parent: JComponent = editor.contentComponent

    private var started = false

    private val localOffsets = ConcurrentLinkedQueue<Pair<Int, Int>>()

    private var colors = DEFAULT_COLORS

    private val log = logger<CursorlessContainer>()

    init {
        this.parent.add(this)
        this.bounds = parent.bounds
        isVisible = true
        println("Cursorless container initialized for editor $editor!")

        this.watcher = DirectoryWatcher.builder()
            .path(Path.of(CURSORLESS_FOLDER)) // or use paths(directoriesToWatch)
            .logger(NOPLogger.NOP_LOGGER)
            .listener { event: DirectoryChangeEvent ->
                if (event.path() == Paths.get(COLORS_PATH)) {
                    println("Colors updated ($event); re rendering...")
                    this.assignColors()
                } else if (event.path() == Paths.get(HATS_PATH)) {
//                    println("Hats updated ($event); re rendering...")
                    this.assignColors()
                    localOffsets.clear()
                    this.invalidate()
                    this.repaint()
                } else {
//                    println("Other event ($event); ignoring...")
                }
            }.build()

        watchThread = Thread {
            this.watcher.watch()
        }
        watchThread.start()
        this.assignColors()
    }

    fun assignColors() {
        val colors = ColorsFormat()

        DEFAULT_COLORS.forEach { (colorScheme, defaults) ->
            run {
                colors[colorScheme] = HashMap()
                colors[colorScheme]!!.putAll(defaults)
            }
        }

        if (Files.exists(Path.of(COLORS_PATH))) {
            val format = Json { isLenient = true }

            // TODO(pcohen): anywhere where we parse JSON, show appropriate errors to the user
            // if the parse fails
            val map = format.decodeFromString<ColorsFormat>(
                Path.of(COLORS_PATH).readText()
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
        println("localOffsets = $localOffsets")
        this.invalidate()
        this.repaint()
    }

    fun startWatching() {
        if (started) {
            return
        }

        started = true
    }

    fun getHats(): HatsFormat? {
        try {
            val format = Json { isLenient = true }

            val map =
                format.decodeFromString<HashMap<String, HashMap<String, ArrayList<CursorlessRange>>>>(
                    File(HATS_PATH).readText()
                )

            val ourPath = FileDocumentManager.getInstance()
                .getFile(editor.document)!!.path

            if (!tempFiles.containsKey(ourPath)) {
                return null
            }

            val ourTemporaryPath =
                tempFiles[ourPath]!!.toAbsolutePath().toString()
            if (!map.containsKey(ourTemporaryPath)) {
                return null
            }

            return map[ourTemporaryPath]!!
        } catch (e: JsonException) {
            println(e)
            e.printStackTrace()
            Sentry.captureException(e)
            return null
        } catch (e: Exception) {
            println(e)
            e.printStackTrace()
            Sentry.captureException(e)

            // kotlinx.serialization.json.internal.JsonDecodingException
            return null
        }
    }

    fun colorForName(colorName: String): JBColor {
        val lightColor = this.colors["light"]?.get(colorName)
        val darkColor = this.colors["dark"]?.get(colorName)

        if (lightColor == null || darkColor == null) {
            throw RuntimeException("Missing color for $colorName")
        }

        return JBColor(
            Color.decode(lightColor), Color.decode(darkColor)
        )
    }

    fun renderForColor(g: Graphics, mapping: HatsFormat, colorName: String) {
        mapping[colorName]!!.forEach { range: CursorlessRange ->
            // NOTE(pcohen): use offsets so we handle tabs properly
            var offset = range.startOffset!!

            // NOTE(pcohen): this was an attempt to make hats on subsequent lines a bit less janky
            // When local edits are performed (use the line number, and then try to figure out the character offset)
//                            val startOfLineOffset = editor.logicalPositionToOffset(
//                                LogicalPosition(range.start!!.line, 0)
//                            )
//                            var offset = startOfLineOffset + range.start!!.character

            var affectedByLocalOffsets = false
            localOffsets.forEach { pair ->
                if (offset >= pair.first) {
                    log.warn("adjusting $offset to ${offset + pair.second} due to local offset: $localOffsets")
                    offset += pair.second
                    affectedByLocalOffsets = true
                }
            }

            val lp = editor.offsetToLogicalPosition(offset)
            val cp = editor.visualPositionToXY(editor.logicalToVisualPosition(lp))

            /*
            // NOTE(pcohen): these seem to break colored hats
            val alpha = if(affectedByLocalOffsets) 0.5 else 0.85
            val light = jColor!!
            val dark = jColor.darkVariant

            val jColorWithAlpha = JBColor(
                Color(light.red, light.green, light.blue, (255.0 * alpha).toInt()),
            )
             */

            g.color = this.colorForName(colorName)

            val size = 4
            g.fillOval(
                cp.x + 4, cp.y - size / 2 - 0, size, size
            )
        }
    }

    fun doPainting(g: Graphics) {
        val mapping = getHats() ?: return

//        println("Redrawing for ${editor.document}...")
        mapping.keys.forEach { color -> renderForColor(g, mapping, color) }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        if (!File(System.getProperty("user.home") + "/.enable-cursorless-jetbrains").exists()) {
            println("~/.enable-cursorless-jetbrains doesn't exist; not withdrawing...")
            return
        }

        startWatching()

        if (!File(HATS_PATH).exists()) {
            println("Hatsfile doesn't exist; not withdrawing...")
            return
        }

        if (!isActiveCursorlessEditor()) {
            return
        }

        try {
            doPainting(g)
        } catch (e: NullPointerException) {
            Sentry.captureException(e)
            e.printStackTrace()
        } catch (e: JsonException) {
            Sentry.captureException(e)
            e.printStackTrace()
        }
    }

    companion object {
        var CURSORLESS_FOLDER =
            System.getProperty("user.home") + "/.cursorless/"
        var HATS_PATH =
            System.getProperty("user.home") + "/.cursorless/vscode-hats.json"

        var COLORS_PATH =
            System.getProperty("user.home") + "/.cursorless/colors.json"
    }
}
