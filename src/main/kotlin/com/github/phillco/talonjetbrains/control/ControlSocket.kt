package com.github.phillco.talonjetbrains.talon

import com.github.phillco.talonjetbrains.control.Command
import com.github.phillco.talonjetbrains.control.CommandResponse
import com.github.phillco.talonjetbrains.control.Response
import com.github.phillco.talonjetbrains.cursorless.cursorless
import com.github.phillco.talonjetbrains.sync.serializeEditorStateToFile
import com.github.phillco.talonjetbrains.sync.serializeOverallState
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.logger
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
                ProcessHandle.current().pid(), productInfo, null, e.message
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
