package com.github.phillco.talonjetbrains.cursorless

import com.github.phillco.talonjetbrains.sync.isActiveCursorlessEditor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
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
import java.util.function.Consumer
import javax.swing.JComponent

class CursorlessContainer(private val editor: Editor) : JComponent() {
    private var watcher: DirectoryWatcher
    private var watchThread: Thread
    private val parent: JComponent

    private var started = false

//    val log = Logger

    init {
        this.parent = editor.contentComponent
        this.parent.add(this)
        this.bounds = parent.bounds
        isVisible = true
//        log.info("hi")
        println("cursorless container initialized!")

        this.watcher = DirectoryWatcher.builder()
            .path(Path.of(HATS_PATH)) // or use paths(directoriesToWatch)
            .logger(NOPLogger.NOP_LOGGER)
            .listener { event: DirectoryChangeEvent ->
                println("PHIL: " + event)
                this.invalidate()
                this.repaint()
            } // .fileHashing(false) // defaults to true
            // .logger(logger) // defaults to LoggerFactory.getLogger(DirectoryWatcher.class)
            // .watchService(watchService) // defaults based on OS to either JVM WatchService or the JNA macOS WatchService
            .build()
//
// //        this.watchThread = FileWatcher()
// //        this.watchThread.start()
// //        GlobalScope.launch {
// //            val dispatcher = this.coroutineContext
// //            CoroutineScope(dispatcher).launch {
// //                watch()
// //            }
// //        }

        watchThread = Thread {
            this.watcher.watch()
        }
        watchThread.start()
    }

    fun startWatching() {
        if (started) {
            return
        }

        started = true
    }
//
//    suspend fun watch() = coroutineScope {
//        val currentDirectory = File("/Users/phillco/.jb-state/")
//
//        print("starting to watch!")
//
//        val watchChannel = currentDirectory.asWatchChannel()
//
//        launch {
//            watchChannel.consumeEach { event ->
//                // do something with event
//                print("file changed! ${event}")
//            }
//        }
//        print("done")
//
//        // once you no longer need this channel, make sure you close it
// //        watchChannel.close()
//    }

    fun getHats(): HashMap<String, HashMap<String, ArrayList<CursorlessRange>>>? {
        try {
            val format = Json { isLenient = true }

            return format.decodeFromString<HashMap<String, HashMap<String, ArrayList<CursorlessRange>>>>(
                File(HATS_PATH).readText()
            )
        } catch (e: JsonException) {
            return null
        }
    }

    fun doPainting(g: Graphics) {
//        val hats = Json.
//            File(
//                HATS_PATH)
//        )

        val map = getHats() ?: return

//        println("Redrawing...")
        map.keys.stream().filter { filePath: String ->
            true
//            filePath == FileDocumentManager.getInstance().getFile(
//                editor.document
//            )!!.path
        }.forEach { filePath: String ->
            map[filePath]!!.keys.forEach(
                Consumer { color: String ->
                    map[filePath]!![color]!!.forEach(
                        Consumer { range: CursorlessRange ->
                            val cp = editor.visualPositionToXY(
                                editor.logicalToVisualPosition(
                                    LogicalPosition(
                                        range.start!!.line,
                                        range.start!!.character
                                    )
                                )
                            )
                            var jColor = JBColor.WHITE
                            when (color) {
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
                            g.color = jColor
                            val size = 4
                            g.fillOval(
                                cp.x + 4,
                                cp.y - size / 2 - 0,
                                size,
                                size
                            )
                        }
                    )
                }
            )
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        if (!File(System.getProperty("user.home") + "/.enable-cursorless-jetbrains").exists()) {
            println("~/.enable-cursorless-jetbrains-old doesn't exist; not withdrawing...")
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

//        g.setColor(JBColor.BLUE);
//        g.drawRect(100, 100, 500, 500);
//
//        g.setColor(JBColor.RED);
//        Point p = editor.visualPositionToXY(editor.getCaretModel().getVisualPosition());
//        g.drawOval(p.x, p.y, 20 - 10, 20 - 10);
//
//        Point p2 = editor.visualPositionToXY(editor.logicalToVisualPosition(new LogicalPosition(9, 1)));
//        g.setColor(JBColor.GREEN);
//        g.drawOval(p2.x, p2.y, 20 - 10, 20 - 10);
//        val objectMapper = ObjectMapper()nhl
//        val typeRef: TypeReference<HashMap<String, HashMap<String, ArrayList<CursorlessRange>>>> =
//            object :
//                TypeReference<HashMap<String?, HashMap<String?, ArrayList<CursorlessRange?>?>?>?>() {}

//        val mapper = jacksonObjectMapper()
//        mapper.registerKotlinModule()

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
