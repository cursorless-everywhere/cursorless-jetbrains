package com.github.phillco.talonjetbrains.talon

import com.github.phillco.talonjetbrains.sync.testSocket
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
import java.io.BufferedWriter
import java.io.File
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
    val response: CommandResponse? = null,
    val receivedCommand: String?,
    val error: String? = null,
)

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
data class CommandResponse(
    val result: String? = null,
    val args: List<String>? = null,
)

fun dispatch(command: Command): CommandResponse {
    return when (command.command) {
        "ping" -> CommandResponse("pong")
        "hi" -> CommandResponse("bye")
        "slow" -> {
            Thread.sleep(5000)
            CommandResponse("finally")
        }
        "outreach" -> CommandResponse(testSocket())
        else -> {
            throw RuntimeException("invalid command: ${command.command}")
        }
    }
}


fun parseInput(inputString: String, outputStream: BufferedWriter) {
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
            commandResponse,
            Json.encodeToString(request),
        )
        println(
            "[Control Socket] Going to send response: |${response}|"
        )

        outputStream.write(Json.encodeToString(response) + "\n")
        println(
            "[Control Socket] Wrote response: |${response}|"
        )
//        outputStream.flush()
//        println(
//            "[Control Socket] Flushed"
//        )

    } catch (e: Exception) {
//        outputStream.write("${e}\n")
        outputStream.write(
            Json.encodeToString(
                Response(
                    ProcessHandle.current().pid(), null, e.message
                )
            ) + "\n"
        )
        e.printStackTrace()
        Sentry.captureException(e)
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


//                println("[Control Socket] Reading: $sock")
//                val inputText: String = sock.inputStream.bufferedReader().use(BufferedReader::readText)

        try {
            var imp: String = "null"
            sock.use { sock ->
                sock.inputStream.bufferedReader().use { inputStream ->
                    try {
                        println("[Control Socket] Reading")
                        imp = inputStream.readText()
                        println("[Control Socket] Read: $imp")
                    } catch (e: Exception) {
                        println("[Control Socket] INNER ERROR READING: $e")
                        e.printStackTrace()
                    }
                }

                Thread.sleep(300)

                sock.outputStream.bufferedWriter().use { outputStream ->
                    outputStream.write("hi\n")
                    try {
                        parseInput(imp, outputStream)
                        outputStream.close()
                    } catch (e: Exception) {
                        println("[Control Socket] INNER ERROR WRITING: $e")
                        outputStream.write("${e}")
                        e.printStackTrace()
                    }

                }
            }
        } catch (e: Exception) {
            println("[Control Socket] ERROR: $e")
            e.printStackTrace()
        }

        println("[Control Socket] Done Serving")
        sock.close()
    }

}

fun createControlSocket() {
    println("[Control Socket] Creating control socket...")

    val server = ControlServer()
    server.start()
}
