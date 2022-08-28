package com.github.phillco.talonjetbrains.talon

import com.github.phillco.talonjetbrains.cursorless.VSCodeCommand
import com.github.phillco.talonjetbrains.cursorless.VSCodeSelection
import com.github.phillco.talonjetbrains.cursorless.sendCommand
import com.github.phillco.talonjetbrains.sync.getEditor
import com.github.phillco.talonjetbrains.sync.serial
import com.github.phillco.talonjetbrains.sync.serializeEditorStateToFile
import com.github.phillco.talonjetbrains.sync.serializeOverallState
import com.intellij.codeInsight.codeVision.CodeVisionState.NotReady.result
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
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
import org.newsclub.net.unix.AFUNIXSocketAddress
import org.newsclub.net.unix.server.AFUNIXSocketServer
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Future
import kotlin.io.path.absolutePathString

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
data class Command(
    val command: String,
    val args: List<String>? = null
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
    val error: String? = null
)

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
data class CommandResponse(
    val result: String? = null,
    val args: List<String>? = null
)

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
// position, range (pair of position), selection (anchor+active position)
data class VSCodeState(
    val path: String,
    val cursors: List<VSCodeSelection>,
    val contentsPath: String? = null
)

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
// position, range (pair of position), selection (anchor+active position)
data class CursorlessResponse(
    val oldState: VSCodeState? = null,
    val newState: VSCodeState? = null,
    val commandResult: String? = null,
    val commandException: String? = null,
    val error: String? = null
)

private val json = Json { isLenient = true }

fun dispatch(command: Command): CommandResponse {
    return when (command.command) {
        "ping" -> CommandResponse("pong")
        "hi" -> CommandResponse("bye")
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
        "outreach" -> {
            val response = outreach(command)
            CommandResponse(response)
        }
        "cursorless" -> {
            /*
            // NOTE(pcohen): this wraps the Cursorless command in a way that blocks the AWT thread.
            // Don't think this is substantially better, because it doesn't help with chaining going the other way
            // ("dollar take fine" ending up as "take fine dollar") and creates more UI jank with the lock being held.

//            var result: CommandResponse = CommandResponse("")
//            ApplicationManager.getApplication().invokeAndWait {
//                result = CommandResponse(cursorless(command))
//            }
//            result
             */
            CommandResponse(cursorless(command))
        }
        else -> {
            throw RuntimeException("invalid command: ${command.command}")
        }
    }
}

class SerialChangedError : RuntimeException("JetBrains serial changed during execution")

/**
 * Returns whether the sidecar is ready to run a next Cursorless command, by forcing a fresh
 * synchronization and double-checking that its contents match the current editor.
 */
fun testisSidecarIsReady(): Boolean {
    val format = Json { isLenient = true }

    // Attempts to tell the sidecar to synchronize. Note that this doesn't seem to fully
    // fixed chaining since this doesn't actually block on Cursorless applying the changes.
    println("** Cursorless command")
    var preCommandContents: String = ""
    ApplicationManager.getApplication().invokeAndWait {
        ApplicationManager.getApplication().runWriteAction {
            getEditor()!!.preventFreeze()
            CommandProcessor.getInstance().executeCommand(
                getEditor()!!.project, {
                println("Pre-Cursorless command contents:\n===")
                preCommandContents = getEditor()?.document!!.text
                print(preCommandContents)
                println("\n===")
            }, "Insert", "insertGroup"
            )
        }
        serializeEditorStateToFile()
    }
//    ApplicationManager.getApplication().invokeAndWait {
//        print(getEditor()?.document!!.text)
//    }
    println("** Send command")
    val preSyncResult: String? =
        sendCommand(VSCodeCommand("applyPrimaryEditorState"))
    println("** Send command: $preSyncResult")

    println("** Get state")
    val preSyncState = format.decodeFromString<VSCodeState>(
        sendCommand(VSCodeCommand("stateWithContents"))!!
    )
    val preSyncContents = File(preSyncState!!.contentsPath!!).readText()
    println("** ")
    println("Pre-sync VS Code contents:\n===")
    print(preSyncContents)
    println("\n===")
    return preCommandContents == preSyncContents
}


/**
 * Ensures that the sidecar is ready to run the next Cursorless command by running
 * `testisSidecarIsReady()` with retries.
 */
fun ensureSidecarIsReady() {
    for (i in 0..20) {
        val result = testisSidecarIsReady()
        println("testisSidecarIsReady, try $i: $result")
        if (result) {
            return
        }
        Thread.sleep(100)
    }

    val error = "Sidecar wasn't ready after N retries"
    Notifications.Bus.notify(
        Notification(
            "talon",
            "Sidecar error",
            error,
            NotificationType.ERROR
        )
    )
    throw RuntimeException("Sidecar error: $error")
}

/**
 * Runs a single Cursorless command and returns the result.
 *
 * It might raise `SerialChangedError()` if the local serial increased since we started running this command,
 * indicating a local change (like a keystroke) happened at the same time -- in which case we raise an exception
 * without making any changes; the command can then safely be rerun.
 */
fun cursorlessSingle(command: Command): String? {
    val format = Json { isLenient = true }
    val startingSerial = serial
    ensureSidecarIsReady()

    print("running with serial: $startingSerial")
    val vcCommand = VSCodeCommand(
        "cursorless", null, null, command.args!![0]
    )

    val resultString: String? = sendCommand(vcCommand)
    val response = format.decodeFromString<CursorlessResponse>(
        resultString!!
    )

    if (response.error != null) {
        throw RuntimeException(response.error)
    }

    if (response.commandException != null) {
        Notifications.Bus.notify(
            Notification(
                "talon",
                "Cursorless error",
                response.commandException,
                NotificationType.ERROR
            )
        )
        return "Cursorless error: ${response.commandException}"
    }

    ApplicationManager.getApplication().invokeAndWait {
        val newContents = File(response.newState!!.contentsPath!!).readText()

        val isWrite = newContents != getEditor()?.document!!.text

        // Only use the write action if the contents are changing;
        // support selection in read only files
        if (isWrite) {
            ApplicationManager.getApplication().runWriteAction {
                CommandProcessor.getInstance().executeCommand(
                    getEditor()!!.project, {
                    println("New contents:\n===")
                    println(newContents)
                    println("\n===")
                    getEditor()?.document?.setText(newContents)
                    getEditor()?.caretModel?.caretsAndSelections =
                        response.newState.cursors.map { it.toCaretState() }
                }, "Insert", "insertGroup"
                )
            }
        } else {
            ApplicationManager.getApplication().runReadAction {
                CommandProcessor.getInstance().executeCommand(
                    getEditor()!!.project, {
                    getEditor()?.caretModel?.caretsAndSelections =
                        response.newState.cursors.map { it.toCaretState() }
                }, "Insert", "insertGroup"
                )
            }
        }

    }

    // Attempts to tell the sidecar to synchronize. Note that this doesn't seem to fully
    // fixed chaining since this doesn't actually block on Cursorless applying the changes.
    val postSyncResult: String? =
        sendCommand(VSCodeCommand("applyPrimaryEditorState"))

    return "$resultString $postSyncResult"
}

/**
 * Runs `cursorlessSingle` but automatically retry on `SerialChangedError()`s.
 */
fun cursorless(command: Command): String? {
    for (i in 0..20) {
        try {
            println("cursorless try $i")
            return cursorlessSingle(command)
        } catch (e: Exception) {
            println("cursorless hit $e, try $i")
            Thread.sleep(100)
        }
    }
    throw RuntimeException("")
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
            "[Control Socket] Received block: |$inputString|"
        )

        val format = Json { isLenient = true }

        val request = format.decodeFromString<Command>(
            inputString
        )

        println(
            "[Control Socket] Received command: |$request|"
        )

        val commandResponse = dispatch(request)

        val response = Response(
            ProcessHandle.current().pid(),
            productInfo,
            commandResponse,
            Json.encodeToString(request)
        )
        println(
            "[Control Socket] Going to send response: |$response|"
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
    AFUNIXSocketServer(
        AFUNIXSocketAddress(socketFile)
    ) {

//    constructor() : super() {
//        println("[Control Socket] Constructor")
//
//    }

//    override fun newServerSocket(): AFUNIXServerSocket {
//        println("[Control Socket] Starting")
//
//        val server = AFUNIXServerSocket.newInstance()
//        server.bind(AFUNIXSocketAddress(socketFile))
//        println("[Control Socket] Bound")
//
//        return server
//    }

    override fun onServerStarting() {
        println()
        println("Creating server: " + javaClass.name)
        println("with the following configuration:")
        println("- maxConcurrentConnections: $maxConcurrentConnections")
    }

    override fun onServerBound(address: SocketAddress) {
        println("Created server -- bound to $address")
    }

    override fun onServerBusy(busySince: Long) {
        println("Server is busy")
    }

    override fun onServerReady(activeCount: Int) {
        println(
            "Active connections: " + activeCount +
                "; waiting for the next connection..."
        )
    }

    override fun onServerStopped(theServerSocket: ServerSocket) {
        println("Close server $theServerSocket")
    }

    override fun onSubmitted(socket: Socket, submit: Future<*>?) {
        println("Accepted: $socket")
    }

    override fun onListenException(e: java.lang.Exception) {
        e.printStackTrace()
    }

    override fun doServeSocket(socket: Socket?) {
        println("[Control Socket] Connected: $socket")
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

val server = ControlServer()

fun createControlSocket() {
    println("[Control Socket] Creating control socket for $pid...")

    try {
        server.start()
    } catch (e: Exception) {
        println("[Control Socket] ERROR: $e")
        e.printStackTrace()
        System.exit(1)
    }

    println("[Control Socket] started ${server.isReady} ${server.isRunning}")
}
