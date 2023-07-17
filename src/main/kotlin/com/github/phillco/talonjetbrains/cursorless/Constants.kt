package com.github.phillco.talonjetbrains.cursorless

import java.nio.file.Paths

// Default hat colors.
val DEFAULT_COLORS = mapOf(
    "light" to mapOf(
        "default" to "#757180",
        "blue" to "#089ad3",
        "green" to "#36B33F",
        "red" to "#E02D28",
        "pink" to "#e0679f",
        "yellow" to "#edb62b",
        "userColor1" to "#6a00ff",
        "userColor2" to "#ffd8b1"
    ),
    "dark" to mapOf(
        "default" to "#aaa7bb",
        "blue" to "#089ad3",
        "green" to "#36B33F",
        "red" to "#E02D28",
        "pink" to "#E06CAA",
        "yellow" to "#E5C02C",
        "userColor1" to "#6a00ff",
        "userColor2" to "#ffd8b1"
    )
)

var HATS_FILENAME = "vscode-hats.json"

var COLORS_FILENAME = "colors.json"

// TODO(pcohen): embed in this plugin
val SHAPES_DIRECTORY =
    Paths.get(System.getProperty("user.home"), "bin/vendor/cursorless-hats")

// Format of the vscode-hats.json file.
typealias HatsFormat = HashMap<String, ArrayList<CursorlessRange>>

// Format of the colors.json.
typealias ColorsFormat = HashMap<String, HashMap<String, String>>

val OVAL_SIZE = 4 // px

val SHAPE_SIZE = 8 // px
