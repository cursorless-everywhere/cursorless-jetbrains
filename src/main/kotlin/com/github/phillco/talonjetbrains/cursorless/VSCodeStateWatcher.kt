package com.github.phillco.talonjetbrains.cursorless

import com.intellij.openapi.editor.Editor
import io.methvin.watcher.DirectoryChangeEvent
import io.methvin.watcher.DirectoryWatcher
import org.slf4j.helpers.NOPLogger
import java.nio.file.Path

class VSCodeStateWatcher() {
    private var watcher: DirectoryWatcher
    private var watchThread: Thread

    private var started = false

    init {
        println("cursorless container initialized!")

        this.watcher = DirectoryWatcher.builder()
            .path(Path.of(VS_CODE_STATE)) // or use paths(directoriesToWatch)
            .logger( NOPLogger.NOP_LOGGER)
            .listener { event: DirectoryChangeEvent ->
                println("PHIL: VS Code state changed:" + event)
            }
            .build()

        watchThread = Thread {
            this.watcher.watch()
        }
        watchThread.start()
    }

    companion object {
        var VS_CODE_STATE = System.getProperty("user.home") + "/.cursorless/vscode-state.json"
    }
}
