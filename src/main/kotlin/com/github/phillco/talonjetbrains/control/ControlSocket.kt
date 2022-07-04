package com.github.phillco.talonjetbrains.talon

import com.github.phillco.talonjetbrains.cursorless.CursorlessContainer
import com.github.phillco.talonjetbrains.cursorless.VSCodeSelection
import com.github.phillco.talonjetbrains.cursorless.VSCodeCommand
import com.github.phillco.talonjetbrains.cursorless.sendCommand
import com.github.phillco.talonjetbrains.sync.getEditor
import com.github.phillco.talonjetbrains.sync.serializeEditorStateToFile
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.command.CommandProcessor
import com.jetbrains.rd.util.use
import io.sentry.Sentry
import kotlinx.serialization.Serializable
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


@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
data class Command(
    val command: String,
    val args: List<String>? = null,
)

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
data class Response(
    val pid: Long,
    val product: String,
    val response: CommandResponse? = null,
    val receivedCommand: String?,
    // TODO(pcohen): make this type definition include
    // either an error or response object
    val error: String? = null,
)

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
data class CommandResponse(
    val result: String? = null,
    val args: List<String>? = null,
)

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
// position, range (pair of position), selection (anchor+active position)
data class VSCodeState(
    val path: String,
    val cursors: List<VSCodeSelection>,
    val contentsPath: String? = null,
)


@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
// position, range (pair of position), selection (anchor+active position)
data class CursorlessResponse(
    val oldState: VSCodeState? = null,
    val newState: VSCodeState? = null,
    val commandResult: String? = null,
    val error: String? = null,
)

fun dispatch(command: Command): CommandResponse {
    return when (command.command) {
        "ping" -> CommandResponse("pong")
        "hi" -> CommandResponse("bye")
        "serializeState" -> {
            var path: Path? = null;
            ApplicationManager.getApplication().invokeAndWait {
                path = serializeEditorStateToFile()
            }
            CommandResponse("wrote state to $path")
        }
        "slow" -> {
            Thread.sleep(5000)
            CommandResponse("finally")
        }
        "outreach" -> {
            val response = outreach(command)
            CommandResponse(response)
        }
        "cursorless" -> CommandResponse(cursorless(command))
        else -> {
            throw RuntimeException("invalid command: ${command.command}")
        }
    }
}

fun cursorless(command: Command): String? {
    val command = VSCodeCommand(
        "cursorless",
        null,
        null,
        command.args!![0]
    )

    val resultString: String? = sendCommand(command)
    val format = Json { isLenient = true }
    val state = format.decodeFromString<CursorlessResponse>(
        resultString!!
    )

    if (state.error != null) {
        throw RuntimeException(state.error)
    }

    ApplicationManager.getApplication().invokeAndWait {
        val newContents = File(state.newState!!.contentsPath!!).readText()

        val isWrite = newContents != getEditor()?.document!!.text

        // Only use the write action if the contents are changing;
        // support selection in read only files
        if (isWrite) {
            ApplicationManager.getApplication().runWriteAction {
                CommandProcessor.getInstance()
                    .executeCommand(getEditor()!!.project,
                        {
                            getEditor()?.document?.setText(newContents)
                            getEditor()?.caretModel?.caretsAndSelections =
                                state.newState.cursors.map { it.toCaretState() }
                        }, "Insert", "insertGroup"
                    )
            }
        } else {
            ApplicationManager.getApplication().runReadAction {
                CommandProcessor.getInstance()
                    .executeCommand(getEditor()!!.project,
                        {
                            getEditor()?.caretModel?.caretsAndSelections =
                                state.newState.cursors.map { it.toCaretState() }
                        }, "Insert", "insertGroup"
                    )
            }
        }

//        getEditor().
    }

    // Attempts to tell the sidecar to synchronize. Note that this doesn't seem to fully
    // fixed chaining since this doesn't actually block on Cursorless applying the changes.
    val syncResult: String? = sendCommand(VSCodeCommand("applyPrimaryEditorState"))

    return "${resultString} ${syncResult}"
}

fun outreach(command: Command): String? {
    val commandToCode = VSCodeCommand(
        "pid"
    )
    return sendCommand(commandToCode)
}


fun parseInput(inputString: String): String {
    val productInfo =
        "${ApplicationNamesInfo.getInstance().fullProductName} ${ApplicationInfo.getInstance().fullVersion}"
    try {
        println(
            "[Control Socket] Received block: |${inputString}|"
        )

        val format = Json { isLenient = true }

        val request = format.decodeFromString<Command>(
            inputString
        )


        println(
            "[Control Socket] Received command: |${request}|"
        )

        val commandResponse = dispatch(request)

        val response = Response(
            ProcessHandle.current().pid(),
            productInfo,
            commandResponse,
            Json.encodeToString(request),
        )
        println(
            "[Control Socket] Going to send response: |${response}|"
        )

        return Json.encodeToString(response) + "\n"

//        outputStream.flush()
//        println(
//            "[Control Socket] Flushed"
//        )

    } catch (e: Exception) {
//        outputStream.write("${e}\n")
        e.printStackTrace()
        Sentry.captureException(e)
        return Json.encodeToString(
            Response(
                ProcessHandle.current().pid(), productInfo, null, e.message
            )
        ) + "\n"
    }
}


val pid = ProcessHandle.current().pid()
val root = Paths.get(System.getProperty("user.home"), ".jb-state/$pid.sock")
    .absolutePathString()

val socketFile = File(root)

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

                    print("RECEIVED: ${inputString}")

                    val response = parseInput(inputString)
                    os.write(response.encodeToByteArray())

                    println(
                        "[Control Socket] Wrote response: |${response}|"
                    )
                }
            }
        }
//
//
////                println("[Control Socket] Reading: $sock")
////                val inputText: String = sock.inputStream.bufferedReader().use(BufferedReader::readText)
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
////            sock.outputStream.bufferedWriter().use { outputStream ->
////                outputStream.write("hi\n")
////                try {
////                    parseInput(imp, outputStream)
////                    outputStream.close()
////                } catch (e: Exception) {
////                    println("[Control Socket] INNER ERROR WRITING: $e")
////                    outputStream.write("${e}")
////                    e.printStackTrace()
////                }
////
////            }
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
