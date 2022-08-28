# talon-jetbrains

> **Warning**
> This plugin is under development and isn't yet suited for the general public.

<!-- Plugin description -->
This is a new plugin for supporting JetBrains editors with Talon, using newer technologies. It also
implements Cursorless support [using the VS Code sidecar](https://github.com/phillco/cursorless-everywhere).
<!-- Plugin description end -->

# Interesting code locations

- **[listeners](https://github.com/phillco/talon-jetbrains/tree/main/src/main/kotlin/com/github/phillco/talonjetbrains/listeners)**: this is where we wire up listeners for the various change events in the IDE so we can react to them
- **[StateWriter](https://github.com/phillco/talon-jetbrains/blob/main/src/main/kotlin/com/github/phillco/talonjetbrains/sync/StateWriter.kt)**: this is where we write out statefiles for the editor in response to listener changes. These state files can be watched by Talon, or the [Cursorless sidecar](https://githubcom/phillco/cursorless-sidecar) to reload the sidecar.


### Cursorless:

- **[CursorlessContainer](https://github.com/phillco/talon-jetbrains/blob/main/src/main/kotlin/com/github/phillco/talonjetbrains/cursorless/CursorlessContainer.kt)**: this is what renders the hats inside of the editor when using the [Cursorless sidecar](https://github.com/phillco/cursorless-sidecar.
- **[VSCodeClient](https://github.com/phillco/talon-jetbrains/blob/main/src/main/kotlin/com/github/phillco/talonjetbrains/cursorless/VSCodeClient.kt)**: allows sending messages to the embedded Unix main socket server inside of the )[Cursorless sidecar](https://github.com/phillco/cursorless-sidecar. This is used to control the sidecar without having to focus it.
- **[CursorlessClient](https://github.com/phillco/talon-jetbrains/blob/main/src/main/kotlin/com/github/phillco/talonjetbrains/cursorless/CursorlessClient.kt)**: Uses the `VSCodeClient` to actually run Cursorless commands in JetBrains.

### Interfacing from Talon

- **[ControlSocket](https://github.com/phillco/talon-jetbrains/blob/main/src/main/kotlin/com/github/phillco/talonjetbrains/control/ControlSocket.kt)**: this exposes a [Unix domain socket](https://en.wikipedia.org/wiki/Unix_domain_socket) for Talon to control instances of JetBrains, rather than the embedded HTTP server of the old plugin. (Note: this will probably be replaced within implementation of the more common command client/server) 
