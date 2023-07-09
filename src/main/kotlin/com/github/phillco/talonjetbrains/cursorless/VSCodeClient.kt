package com.github.phillco.talonjetbrains.cursorless

import com.jetbrains.rd.util.use
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.newsclub.net.unix.AFUNIXSocket
import org.newsclub.net.unix.AFUNIXSocketAddress
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

@Serializable
data class VSCodeCommand(
    val command: String,
    val commandId: String? = null,
    val commandArgs: List<String>? = null,
    val cursorlessArgs: String? = null
)

val SOCKET_TIMEOUT_MS = 2000

fun sendCommand(command: VSCodeCommand): String? {
    println("Sending to VS Code: $command....")
    val root =
        Paths.get(System.getProperty("user.home"), ".cursorless/vscode-socket")
            .absolutePathString()

    val socketFile = File(root)
    val sock = AFUNIXSocket.newInstance()

    println("Connecting to socket file: $socketFile")
    val address = AFUNIXSocketAddress.of(socketFile)
    println("Connecting to socket address: $address")
    sock.connect(address)
    println("Connected to socket file: $socketFile")
    sock.soTimeout = SOCKET_TIMEOUT_MS

    try {


        var resp: String?



        sock.outputStream.bufferedWriter().use { outputStream ->
            val format = Json { isLenient = true }

            val encoded = format.encodeToString(command)
            println("Encoded: $encoded")
            outputStream.write(encoded)
            outputStream.flush()
            sock.inputStream.bufferedReader().use { inputStream ->

                println("Sent to VS Code")

                outputStream.flush()

                // TODO: read until we receive a new line
                resp = inputStream.readText()
                println("Received from VS Code: $resp")
                return resp
            }
        }
//        }
    } catch (e: Exception) {
        println("Error: $e")
        e.printStackTrace()
        return "Error: $e"

    }
}
