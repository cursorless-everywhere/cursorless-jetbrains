package com.github.phillco.talonjetbrains.talon

import com.github.phillco.talonjetbrains.sync.testSocket
import com.jetbrains.rd.util.use
import io.sentry.Sentry
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.newsclub.net.unix.AFUNIXServerSocket
import org.newsclub.net.unix.AFUNIXSocketAddress
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
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

        outputStream.write(Json.encodeToString(response) + "\n")
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

fun createControlSocket() {
    println("[Control Socket] Creating control socket...")

    val pid = ProcessHandle.current().pid()
    val root = Paths.get(System.getProperty("user.home"), ".jb-state/$pid.sock")
        .absolutePathString()

    val socketFile = File(root)
    val server = AFUNIXServerSocket.newInstance()
    server.bind(AFUNIXSocketAddress.of(socketFile))
    while (!Thread.interrupted()) {
        println("[Control Socket] Waiting for connection at ${root}...")
        try {
            server.accept().use { sock ->
                println("[Control Socket] Connected: $sock")


//                println("[Control Socket] Reading: $sock")
//                val inputText: String = sock.inputStream.bufferedReader().use(BufferedReader::readText)
                sock.inputStream.bufferedReader().use { inputStream ->
                    sock.outputStream.bufferedWriter().use { outputStream ->
                        try {
                            println("[Control Socket] Reading: $sock")
                            val imp = inputStream.readText()
                            println("[Control Socket] Read: $imp")


                            parseInput(imp, outputStream)
                        } catch (e: Exception) {
                            outputStream.write("${e}")
                        }
                    }
                }
            }
        } catch (e: IOException) {
            println(e)
            if (server.isClosed) {
                throw e
            } else {
                e.printStackTrace()
            }
        }
    }
}
