package com.github.phillco.talonjetbrains.cursorless

import kotlinx.serialization.Serializable

@Serializable
class CursorlessStateFile {
    var line = 0
    var character = 0
}
