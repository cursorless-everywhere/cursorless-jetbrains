package com.github.phillco.talonjetbrains.talon

import com.github.phillco.talonjetbrains.control.Command
import com.github.phillco.talonjetbrains.control.CommandResponse
import com.github.phillco.talonjetbrains.control.Response
import com.github.phillco.talonjetbrains.cursorless.cursorless
import com.github.phillco.talonjetbrains.sync.getEditor
import com.github.phillco.talonjetbrains.sync.serializeEditorStateToFile
import com.github.phillco.talonjetbrains.sync.serializeOverallState
import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.playback.commands.ActionCommand
import com.intellij.openapi.wm.IdeFocusManager
import com.jetbrains.rd.util.use
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.newsclub.net.unix.AFUNIXSocketAddress
import org.newsclub.net.unix.server.AFUNIXSocketServer
import java.io.File
import java.net.Socket
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

// ================================================================================
// An implementation of a Unix file socket command server for JetBrains,
// to run various commands (including Cursorless commands).
//
// TODO(pcohen): replace this with implementation of the command client server,
// rather than using a new protocol.
// ================================================================================

private val json = Json { isLenient = true }
private val log = logger<ControlServer>()

val pid = ProcessHandle.current().pid()
val root = Paths.get(System.getProperty("user.home"), ".jb-state/$pid.sock")
    .absolutePathString()

val socketFile = File(root)

/** Runs the action with the given id */
fun runAction(actionId: String) {
    val editor = getEditor()
    if (editor == null) {
        log.warn("No editor found")
        return
    }

    val actionManager = ActionManager.getInstance()
    val action = actionManager.getAction(actionId)
    val event = ActionCommand.getInputEvent(actionId)

    actionManager.tryToExecute(
        action,
        event,
        editor.component,
        null /* place */,
        true /* now */
    )
}

/**
 * Dispatches the input command.
 */
fun dispatch(command: Command): CommandResponse {
    return when (command.command) {
        "ping" -> CommandResponse("pong")
        "state" -> {
            val state = serializeOverallState()
            CommandResponse(json.encodeToString(state))
        }
        "serializeState" -> {
            var path: Path? = null
            ApplicationManager.getApplication().invokeAndWait {
                path = serializeEditorStateToFile()
            }
            CommandResponse("wrote state to $path")
        }
        "slow" -> {
            Thread.sleep(5000)
            CommandResponse("finally")
        }
        "notify" -> {
            Notifications.Bus.notify(
                Notification(
                    "talon",
                    "Hello from Talon",
                    "This is a test notification",
                    NotificationType.INFORMATION
                )
            )
            CommandResponse("OK")
        }
        "cursorless" -> {
            CommandResponse(cursorless(command))
        }
        "action" -> {
            ApplicationManager.getApplication().invokeAndWait {
                command.args!!.forEach { runAction(it) }
            }
            CommandResponse("OK, ran: ${command.args}")
        }
        "find" -> {
            val searchTerm = command.args!![0]
            val direction = command.args[1]

            ApplicationManager.getApplication().invokeAndWait {
                val e: Editor = getEditor()!!
                val document =
                    e.document
                val selection =
                    e.selectionModel
                val project =
                    ProjectManager.getInstance().openProjects[0]
                val findManager =
                    FindManager.getInstance(project)
                val findModel = FindModel()
                findModel.stringToFind = searchTerm
                findModel.isCaseSensitive = false
                findModel.isRegularExpressions = true
                findModel.isForward = direction == "next"
                val result =
                    findManager.findString(
                        document.charsSequence,
                        e.caretModel.offset,
                        findModel
                    )
                if (result.isStringFound) {
                    if (direction == "next") {
                        e.caretModel
                            .moveToOffset(result.endOffset)
                    } else {
                        e.caretModel
                            .moveToOffset(result.startOffset)
                    }
                    selection.setSelection(
                        result.startOffset,
                        result.endOffset
                    )
                    e.scrollingModel
                        .scrollToCaret(ScrollType.CENTER)
                    IdeFocusManager.getGlobalInstance()
                        .requestFocus(e.contentComponent, true)
                }
            }

            CommandResponse("OK, ran: ${command.args}")
        }
        "insertAtCursors" -> {
            ApplicationManager.getApplication().invokeAndWait {
                val editor = getEditor()
                if (editor == null) {
                    log.warn("No editor found")
                    return@invokeAndWait
                }

                ApplicationManager.getApplication().runWriteAction {
                    val document = editor.document
                    val caretModel = editor.caretModel
                    val caretCount = caretModel.caretCount
                    val text = command.args!!.joinToString(" ")

                    CommandProcessor.getInstance().executeCommand(
                        editor.project,
                        {
                            for (i in 0 until caretCount) {
                                val caret = caretModel.allCarets[i]
                                if (caret.hasSelection()) {
                                    document.deleteString(
                                        caret.selectionStart,
                                        caret.selectionEnd
                                    )
                                }
                                document.insertString(caret.offset, text)
                                caret.moveToOffset(
                                    caret.offset + text.length
                                )
                            }
                        },
                        "Insert",
                        "insertGroup"
                    )
                }
            }
            CommandResponse("OK, inserted: ${command.args}")
        }
        "content" -> {
            var resp = ""
            ApplicationManager.getApplication().invokeAndWait {
                val editor = getEditor()
                if (editor == null) {
                    log.warn("No editor found")
                    return@invokeAndWait
                }
                resp = editor.document.text
            }
            CommandResponse(resp)
        }
        else -> {
            throw RuntimeException("invalid command: ${command.command}")
        }
    }
}

/**
 * Parses the command string, dispatches the command, and returns the response.
 */
fun parseInput(inputString: String): String {
    val productInfo =
        "${ApplicationNamesInfo.getInstance().fullProductName} ${ApplicationInfo.getInstance().fullVersion}"
    try {
        log.info(
            "[Control Socket] Received block: |$inputString|"
        )

        val format = Json { isLenient = true }

        val request = format.decodeFromString<Command>(
            inputString
        )

        log.info(
            "[Control Socket] Received command: |$request|"
        )

        val commandResponse = dispatch(request)

        val response = Response(
            ProcessHandle.current().pid(),
            productInfo,
            commandResponse,
            Json.encodeToString(request)
        )
        log.info(
            "[Control Socket] Going to send response: |$response|"
        )

        return Json.encodeToString(response) + "\n"
    } catch (e: Exception) {
        e.printStackTrace()

        return Json.encodeToString(
            Response(
                ProcessHandle.current().pid(),
                productInfo,
                null,
                e.message
            )
        ) + "\n"
    }
}

/**
 * The control server class itself.
 */
class ControlServer :
    AFUNIXSocketServer(
        AFUNIXSocketAddress(socketFile)
    ) {

    override fun onListenException(e: java.lang.Exception) {
        e.printStackTrace()
    }

    override fun doServeSocket(socket: Socket?) {
        log.info("[Control Socket] Connected: $socket")
        val sock = socket!!

        val bufferSize: Int = sock.getReceiveBufferSize()
        val buffer = ByteArray(bufferSize)

        // TODO(pcohen): build up a buffer until we get to a new line
        // since we can't guarantee that the entire message will be received in one chunk
        sock.getInputStream().use { `is` ->
            sock.getOutputStream().use { os ->
                var read: Int

                while (`is`.read(buffer).also { read = it } != -1) {
                    val inputString = String(buffer, 0, read)

                    val response = parseInput(inputString)
                    os.write(response.encodeToByteArray())

                    log.info(
                        "[Control Socket] Wrote response: |$response|"
                    )
                }
            }
        }
    }
}

fun createControlSocket() {
    log.info("[Control Socket] Creating control socket for $pid $socketFile...")

    try {
        socketFile.createNewFile()

        val server = ControlServer()
        log.info("[Control Socket] Initialized! Starting...")

        server.start()
        log.info("[Control Socket] started ${server.isReady} ${server.isRunning}")

        // NOTE(pcohen): debugging a strange bug where with certain other plugins in
        // 2021.1 we never get past .start() here (but there's no exception raised)
        Notifications.Bus.notify(
            Notification(
                "talon",
                "The control socket works!",
                "This is a test notification",
                NotificationType.INFORMATION
            )
        )
    } catch (e: Exception) {
        log.info("[Control Socket] ERROR: $e")
        e.printStackTrace()
        System.exit(1)
    }
}
