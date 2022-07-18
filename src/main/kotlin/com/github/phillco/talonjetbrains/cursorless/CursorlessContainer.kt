package com.github.phillco.talonjetbrains.cursorless

import com.github.phillco.talonjetbrains.sync.isActiveCursorlessEditor
import com.github.phillco.talonjetbrains.sync.tempFiles
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.ui.JBColor
import groovy.json.JsonException
import io.methvin.watcher.DirectoryChangeEvent
import io.methvin.watcher.DirectoryWatcher
import io.sentry.Sentry
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.slf4j.helpers.NOPLogger
import java.awt.Graphics
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import javax.swing.JComponent

typealias HatsFormat = HashMap<String, ArrayList<CursorlessRange>>

class CursorlessContainer(val editor: Editor) : JComponent() {
    private var watcher: DirectoryWatcher
    private var watchThread: Thread
    private val parent: JComponent

    private var started = false

    private val localOffsets = ConcurrentLinkedQueue<Pair<Int, Int>>()

    init {
        this.parent = editor.contentComponent
        this.parent.add(this)
        this.bounds = parent.bounds
        isVisible = true
        println("Cursorless container initialized for editor $editor!")

        this.watcher = DirectoryWatcher.builder()
            .path(Path.of(HATS_PATH)) // or use paths(directoriesToWatch)
            .logger(NOPLogger.NOP_LOGGER)
            .listener { event: DirectoryChangeEvent ->
                localOffsets.clear()
                this.invalidate()
                this.repaint()
            }.build()

        watchThread = Thread {
            this.watcher.watch()
        }
        watchThread.start()
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

            val ourPath =
                FileDocumentManager.getInstance()
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
            return null
        } catch (e: Exception) {
            // kotlinx.serialization.json.internal.JsonDecodingException
            return null
        }
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
                    println("adjusting $offset to ${offset + pair.second} due to local offset: $localOffsets")
                    offset += pair.second
                    affectedByLocalOffsets = true
                }
            }

            val lp = editor.offsetToLogicalPosition(offset)
            val cp = editor.visualPositionToXY(
                editor.logicalToVisualPosition(
                    lp
                )
            )

            var jColor = JBColor.WHITE
            when (colorName) {
                "red" -> jColor = JBColor.RED
                "pink" -> jColor = JBColor(
                    JBColor.getHSBColor(
                        (332 / 336.0).toFloat(),
                        .54.toFloat(),
                        .96.toFloat()
                    ),
                    JBColor.getHSBColor(
                        (332 / 336.0).toFloat(),
                        .54.toFloat(),
                        .96.toFloat()
                    )
                )
                "yellow" -> jColor = JBColor.ORANGE
                "green" -> jColor = JBColor.GREEN
                "blue" -> jColor = JBColor.BLUE
                "default" -> jColor = JBColor.GRAY
            }

            /*
            // NOTE(pcohen): these seem to break colored hats
            val alpha = if(affectedByLocalOffsets) 0.5 else 0.85
            val light = jColor!!
            val dark = jColor.darkVariant

            val jColorWithAlpha = JBColor(
                Color(light.red, light.green, light.blue, (255.0 * alpha).toInt()),
            )
             */

            g.color = jColor

            val size = 4
            g.fillOval(
                cp.x + 4, cp.y - size / 2 - 0, size, size
            )
        }
    }

    fun doPainting(g: Graphics) {
        val mapping = getHats() ?: return

//        println("Redrawing...")
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
        var HATS_PATH =
            System.getProperty("user.home") + "/.cursorless/vscode-hats.json"
    }
}
