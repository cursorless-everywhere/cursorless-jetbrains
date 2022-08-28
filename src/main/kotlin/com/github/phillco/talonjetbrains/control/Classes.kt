package com.github.phillco.talonjetbrains.control

import com.github.phillco.talonjetbrains.cursorless.VSCodeSelection
import kotlinx.serialization.Serializable

@Serializable
data class Command(
    val command: String,
    val args: List<String>? = null
)

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

@Serializable
data class CommandResponse(
    val result: String? = null,
    val args: List<String>? = null
)

@Serializable
// position, range (pair of position), selection (anchor+active position)
data class VSCodeState(
    val path: String,
    val cursors: List<VSCodeSelection>,
    val contentsPath: String? = null
)

@Serializable
// position, range (pair of position), selection (anchor+active position)
data class CursorlessResponse(
    val oldState: VSCodeState? = null,
    val newState: VSCodeState? = null,
    val commandResult: String? = null,
    val commandException: String? = null,
    val error: String? = null
)
