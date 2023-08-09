package com.github.phillco.talonjetbrains.talon

import com.github.phillco.talonjetbrains.control.Command
import com.github.phillco.talonjetbrains.control.CommandResponse
import com.github.phillco.talonjetbrains.control.Response
import com.github.phillco.talonjetbrains.cursorless.cursorless
import com.github.phillco.talonjetbrains.listeners.addCursorlessContainerToEditor
import com.github.phillco.talonjetbrains.listeners.removeCursorlessContainerFromEditor
import com.github.phillco.talonjetbrains.sync.getEditor
import com.github.phillco.talonjetbrains.sync.serializeEditorStateToFile
import com.github.phillco.talonjetbrains.sync.serializeOverallState
import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.ide.impl.ProjectUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.playback.commands.ActionCommand
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.IdeFocusManager
import com.jetbrains.rd.util.use
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.newsclub.net.unix.AFUNIXServerSocket
import org.newsclub.net.unix.AFUNIXSocket
import org.newsclub.net.unix.AFUNIXSocketAddress
import org.newsclub.net.unix.server.SocketServer
import java.io.File
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
fun runAction(actionId: String): Pair<AnAction, ActionCallback>? {
    val editor = getEditor()
    if (editor == null) {
        log.warn("No editor found")
        return null
    }

    val actionManager = ActionManager.getInstance()
    val action = actionManager.getAction(actionId)
    if (action == null) {
        log.warn("No action found for $actionId")
        return null
    }

    val event = ActionCommand.getInputEvent(actionId)

    val result = actionManager.tryToExecute(
        action,
        event,
        editor.component,
        null /* place */,
        true /* now */
    )
    return Pair(action, result)
}

//fun stoppedDebugging() {
//    val k =
//        ExecutionManager.getInstance(getEditor()!!.project!!) as ExecutionManagerImpl
////    k.
//}

/**
 * Dispatches the input command.
 *
 * // TODO(pcohen): we need JSON input
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
            println("cursorless command: ${command.args}")
            CommandResponse(cursorless(command))
        }

        "action" -> {
            var result = ""
            ApplicationManager.getApplication().invokeAndWait {
                command.args!!.forEach {
                    val r = runAction(it)
                    if (r != null) {
                        val (action, callback) = r
                        if (callback.isRejected) {
                            result += "${action} -> REJECTED\n"
                        } else {
                            result += "${action} -> OK\n"
                        }
//                        result += "${action} -> done=${callback.isDone} processed=${callback.isProcessed} rejected=${callback.isRejected}\n"
                    }
//                    ExecutionManager.getInstance(getEditor()!!.project!!).getRunningProcesses()[0].ter
                }
            }
            CommandResponse("OK, ran: ${result}")
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

        "openFile" -> {
            val filePath = command.args!![0]
            val file = VfsUtil.findFile(Paths.get(filePath.strip()), true)

            // TODO(pcohen): focus it if it's already open
            ApplicationManager.getApplication().invokeAndWait {
                FileEditorManager.getInstance(
                    getEditor()!!.project!!
                ).openFile(file!!, true)

                if (command.args.size > 1) {
                    val line = command.args[1].toInt()
                    val column =
                        if (command.args.size > 2) command.args[2].toInt() else 0
                    val e: Editor = getEditor()!!
                    e.caretModel.removeSecondaryCarets()
                    e.caretModel.moveToLogicalPosition(
                        LogicalPosition(
                            (line - 1).coerceAtLeast(0),
                            (column - 1).coerceAtLeast(0),
                        )
                    )
                    e.scrollingModel.scrollToCaret(ScrollType.CENTER)
                }
            }
            CommandResponse("OK, opened: ${filePath}")
        }

        "goto" -> {
            var line = command.args!![0].toInt()
            var column =
                if (command.args.size > 1) command.args[1].toInt() else 0

            // Both count from 0, so adjust.
            line = (line - 1).coerceAtLeast(0)
            column = (column - 1).coerceAtLeast(0)

            val pos = LogicalPosition(line, column)
            ApplicationManager.getApplication().invokeAndWait {
                val e: Editor = getEditor()!!
                e.caretModel.removeSecondaryCarets()
                e.caretModel.moveToLogicalPosition(pos)
                e.scrollingModel.scrollToCaret(ScrollType.CENTER)
                e.selectionModel.removeSelection()
                IdeFocusManager.getGlobalInstance()
                    .requestFocus(e.contentComponent, true)
            }
            return CommandResponse("OK, moved to: ${pos}")
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

        "removeCursorlessContainer" -> {
            ApplicationManager.getApplication().invokeAndWait {
                val editor = getEditor()
                removeCursorlessContainerFromEditor(editor!!)
            }
            CommandResponse("OK, removed")
        }

        "addCursorlessContainer" -> {
            ApplicationManager.getApplication().invokeAndWait {
                val editor = getEditor()
                addCursorlessContainerToEditor(editor!!)
            }
            CommandResponse("OK, added")
        }

        "rebindCursorlessContainer" -> {
            ApplicationManager.getApplication().invokeAndWait {
                val editor = getEditor()

                removeCursorlessContainerFromEditor(editor!!)
                addCursorlessContainerToEditor(editor)
            }
            CommandResponse("OK, rebinded")
        }

        "openProject" -> {
            val projectPath = command.args!![0]

            // See if it's already open.
            // TODO(pcohen): probably need to do path normalization
            val openProjects = ProjectManager.getInstance().openProjects
            for (project in openProjects) {
                if (project.basePath == projectPath) {
                    println("Already open, focusing: ${project}")
                    // NOTE(pcohen): would be nice to be able to not steal focus
                    ApplicationManager.getApplication().invokeAndWait {
                        ProjectUtil.focusProjectWindow(project, true)
                    }

                    return CommandResponse("OK, already open: ${project}")
                }
            }

            val project =
                ProjectManager.getInstance().loadAndOpenProject(projectPath)
            CommandResponse("OK, opened: ${project}")
        }

        "navigateHistory" -> {
            val direction = command.args!![0]
            val navType = command.args[1]
            val navTypeObj = when (navType) {
                "file" -> NavigationType.FILE
                "function" -> NavigationType.FUNCTION
                "language" -> NavigationType.LANGUAGE
                else -> throw RuntimeException("invalid nav type: ${navType}")
            }
            if (direction != "forward" && direction != "back") {
                throw RuntimeException("invalid direction: ${direction}")
            }
            navigate(direction == "forward", navTypeObj)
            CommandResponse("OK, navigated ${direction} by ${navType}")
        }

        "navigateFileBack" -> navigate(false, NavigationType.FILE)
        "navigateFileForward" -> navigate(true, NavigationType.FILE)
        "navigateFunctionBack" -> navigate(false, NavigationType.FUNCTION)
        "navigateFunctionForward" -> navigate(true, NavigationType.FUNCTION)

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

        println("dispatching command: $request")

        val commandResponse = dispatch(request)

        println("command response ${request} : $commandResponse")

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


class ControlServer :
    SocketServer<AFUNIXSocketAddress, AFUNIXSocket, AFUNIXServerSocket>(
        AFUNIXSocketAddress.of(socketFile)
    ) {

//    constructor() : super() {
//
//    }

    override fun newServerSocket(): AFUNIXServerSocket {
        val server = AFUNIXServerSocket.newInstance()
        server.bind(AFUNIXSocketAddress.of(socketFile))
        return server
    }

    override fun doServeSocket(sock: AFUNIXSocket) {
        println("[Control Socket] Connected: $sock")

        val bufferSize: Int = sock.getReceiveBufferSize()
        val buffer = ByteArray(bufferSize)

        // TODO(pcohen): build up a buffer until we get to a new line
        // since we can't guarantee that the entire message will be received in one chunk
        sock.getInputStream().use { `is` ->
            sock.getOutputStream().use { os ->
                var read: Int

                while (`is`.read(buffer).also { read = it } != -1) {
                    val inputString = String(buffer, 0, read)

                    print("RECEIVED: $inputString")

                    val response = parseInput(inputString)
                    os.write(response.encodeToByteArray())

                    println(
                        "[Control Socket] Wrote response: |$response|"
                    )
                }
            }
        }
//
//
// //                println("[Control Socket] Reading: $sock")
// //                val inputText: String = sock.inputStream.bufferedReader().use(BufferedReader::readText)
//
//        try {
//            var imp: String = "null"
//            sock.inputStream.reader().use { inputStream ->
//                try {
//                    println("[Control Socket] Reading")
//                    imp = inputStream.readText()
//                    println("[Control Socket] Read: $imp")
//                } catch (e: Exception) {
//                    println("[Control Socket] INNER ERROR READING: $e")
//                    e.printStackTrace()
//                }
//            }
//
//
//            println("[Control Socket] Time to write")
//
//            sock.outputStream.writer().use { writer ->
//                writer.write("hi\n")
//                writer.flush()
//            }
//
//            println("[Control Socket] Done writing")
//
//
// //            sock.outputStream.bufferedWriter().use { outputStream ->
// //                outputStream.write("hi\n")
// //                try {
// //                    parseInput(imp, outputStream)
// //                    outputStream.close()
// //                } catch (e: Exception) {
// //                    println("[Control Socket] INNER ERROR WRITING: $e")
// //                    outputStream.write("${e}")
// //                    e.printStackTrace()
// //                }
// //
// //            }
//        } catch (e: Exception) {
//            println("[Control Socket] ERROR: $e")
//            e.printStackTrace()
//        }
//
//        println("[Control Socket] Done Serving")
//        sock.close()
    }
}

fun createControlSocket() {
    println("[Control Socket] Creating control socket...")

    val server = ControlServer()
    server.start()
}
