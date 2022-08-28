# talon-jetbrains

> **Warning**
> This plugin is under development and isn't yet suited for the general public -- it should be used only by people working on the Cursorless Everywhere project for now.

<!-- Plugin description -->
This is a new plugin for supporting JetBrains editors with Talon, using newer technologies. It also
implements Cursorless support [using the VS Code sidecar](https://github.com/phillco/cursorless-everywhere).
<!-- Plugin description end -->

# Interesting code locations

- **[listeners/](https://github.com/phillco/talon-jetbrains/tree/main/src/main/kotlin/com/github/phillco/talonjetbrains/listeners)**: this is where we wire up listeners for the various change events in the IDE (opening/closing projects/files, switching tabs, document insertions/deletions, cursor changes, scrolling, etc.) so we can react to them
- **[StateWriter](https://github.com/phillco/talon-jetbrains/blob/main/src/main/kotlin/com/github/phillco/talonjetbrains/sync/StateWriter.kt)**: this is where we write out statefiles for the editor in response to listener changes. These state files can be watched by Talon, or the [Cursorless sidecar](https://githubcom/phillco/cursorless-sidecar) to reload the sidecar.


### Cursorless:

- **[CursorlessContainer](https://github.com/phillco/talon-jetbrains/blob/main/src/main/kotlin/com/github/phillco/talonjetbrains/cursorless/CursorlessContainer.kt)**: this is what renders the hats inside of the editor when using the [Cursorless sidecar](https://github.com/phillco/cursorless-sidecar).
- **[VSCodeClient](https://github.com/phillco/talon-jetbrains/blob/main/src/main/kotlin/com/github/phillco/talonjetbrains/cursorless/VSCodeClient.kt)**: allows sending messages to the embedded Unix main socket server inside of the [Cursorless sidecar](https://github.com/phillco/cursorless-sidecar). This is used to control the sidecar/VS Code instance without having to focus it, as the traditional command client/server has to do.
- **[CursorlessClient](https://github.com/phillco/talon-jetbrains/blob/main/src/main/kotlin/com/github/phillco/talonjetbrains/cursorless/CursorlessClient.kt)**: Uses the `VSCodeClient` to actually run Cursorless commands in JetBrains.

### Interfacing from Talon

- **[ControlSocket](https://github.com/phillco/talon-jetbrains/blob/main/src/main/kotlin/com/github/phillco/talonjetbrains/control/ControlSocket.kt)**: this exposes a [Unix domain socket](https://en.wikipedia.org/wiki/Unix_domain_socket) for Talon to control instances of JetBrains, rather than the embedded HTTP server of the old plugin. (Note: this will probably be replaced with an implementation of the more common command client/server) 
