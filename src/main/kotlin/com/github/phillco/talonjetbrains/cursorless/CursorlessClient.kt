package com.github.phillco.talonjetbrains.cursorless

import com.github.phillco.talonjetbrains.control.Command
import com.github.phillco.talonjetbrains.control.CursorlessResponse
import com.github.phillco.talonjetbrains.control.VSCodeState
import com.github.phillco.talonjetbrains.sync.getEditor
import com.github.phillco.talonjetbrains.sync.serial
import com.github.phillco.talonjetbrains.sync.serializeEditorStateToFile
import com.github.phillco.talonjetbrains.talon.ControlServer
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.logger
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

private val log = logger<ControlServer>()

class SerialChangedError :
    RuntimeException("JetBrains serial changed during execution")

/**
 * Returns whether the sidecar is ready to run a next Cursorless command, by forcing a fresh
 * synchronization and double-checking that its contents match the current editor.
 */
fun testisSidecarIsReady(): Boolean {
    val format = Json { isLenient = true }

    var preCommandContents: String = ""
    ApplicationManager.getApplication().invokeAndWait {
        ApplicationManager.getApplication().runWriteAction {
            CommandProcessor.getInstance().executeCommand(
                getEditor()!!.project,
                {
                    log.info("Pre-Cursorless command contents:\n===")
                    preCommandContents = getEditor()?.document!!.text
                    log.info(preCommandContents)
                    log.info("\n===")
                },
                "Insert",
                "insertGroup"
            )
        }
        serializeEditorStateToFile()
    }
    sendCommand(VSCodeCommand("applyPrimaryEditorState"))
    val preSyncState = format.decodeFromString<VSCodeState>(
        sendCommand(VSCodeCommand("stateWithContents"))!!
    )
    val preSyncContents = File(preSyncState!!.contentsPath!!).readText()
    return preCommandContents == preSyncContents
}

/**
 * Ensures that the sidecar is ready to run the next Cursorless command by running
 * `testisSidecarIsReady()` with retries.
 */
fun ensureSidecarIsReady() {
    for (i in 0..3) {
        val result = testisSidecarIsReady()
        log.info("testisSidecarIsReady, try $i: $result")
        if (result) {
            return
        }
        Thread.sleep(15)
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

    log.info("running with serial: $startingSerial")
    val vcCommand = VSCodeCommand(
        "cursorless",
        null,
        null,
        command.args!![0]
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

        log.info("pre-command serial: $startingSerial")
        log.info("post-command serial: $serial")

        if (startingSerial != serial) {
            Notifications.Bus.notify(
                Notification(
                    "talon",
                    "Sidecar error",
                    "Serial differed: $serial vs $startingSerial; retrying",
                    NotificationType.INFORMATION
                )
            )
            throw SerialChangedError()
        }

        val isWrite = newContents != getEditor()?.document!!.text

        // Only use the write action if the contents are changing;
        // support selection in read only files
        if (isWrite) {
            ApplicationManager.getApplication().runWriteAction {
                CommandProcessor.getInstance().executeCommand(
                    getEditor()!!.project,
                    {
                        log.info("New contents:\n===")
                        log.info(newContents)
                        log.info("\n===")
                        getEditor()?.document?.setText(newContents)
                        getEditor()?.caretModel?.caretsAndSelections =
                            response.newState.cursors.map { it.toCaretState() }
                    },
                    "Insert",
                    "insertGroup"
                )
            }
        } else {
            ApplicationManager.getApplication().runReadAction {
                CommandProcessor.getInstance().executeCommand(
                    getEditor()!!.project,
                    {
                        getEditor()?.caretModel?.caretsAndSelections =
                            response.newState.cursors.map { it.toCaretState() }
                    },
                    "Insert",
                    "insertGroup"
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
    for (i in 0..5) {
        try {
            log.info("cursorless try $i")
            return cursorlessSingle(command)
        } catch (e: Exception) {
            log.info("cursorless hit $e, try $i")
            Thread.sleep(30)
        }
    }
    throw RuntimeException("")
}
