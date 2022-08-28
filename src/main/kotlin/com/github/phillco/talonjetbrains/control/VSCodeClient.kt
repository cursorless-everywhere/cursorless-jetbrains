package com.github.phillco.talonjetbrains.cursorless

import com.jetbrains.rd.util.use
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.newsclub.net.unix.AFUNIXSocket
import org.newsclub.net.unix.AFUNIXSocketAddress
import java.io.File
import java.io.IOException
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
data class VSCodeCommand(
    val command: String,
    val commandId: String? = null,
    val commandArgs: List<String>? = null,
    val cursorlessArgs: String? = null
)

val SOCKET_TIMEOUT_MS = 2000

fun sendCommand(command: VSCodeCommand): String? {
    val root = Paths.get(System.getProperty("user.home"), ".cursorless/vscode-socket").absolutePathString()

    val socketFile = File(root)
    val sock = AFUNIXSocket.newInstance()
    val address = AFUNIXSocketAddress(socketFile)
    sock.connect(address)
    var resp: String? = null
    try {
        sock.soTimeout = SOCKET_TIMEOUT_MS
        sock.inputStream.bufferedReader().use { inputStream ->
            sock.outputStream.bufferedWriter().use { outputStream ->
                val format = Json { isLenient = true }

                outputStream.write(format.encodeToString(command))
                println("Sent to VS Code")

                outputStream.flush()

                // TODO: read until we receive a new line
                resp = inputStream.readText()
                println("Received from VS Code: $resp")
                return resp
            }
        }
//        }
    } catch (e: IOException) {
        e.printStackTrace()
        return "Error: $e"
    }
}
