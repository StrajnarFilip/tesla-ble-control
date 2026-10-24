package io.github.teslablecontrol.ui.commands

/** The catalog's spelling of a switch value. */
fun onOff(on: Boolean): String = if (on) "on" else "off"

/** Commands whose only argument is a 4-digit PIN the user types. */
val PIN_COMMANDS: Set<String> = setOf(
    "valet-mode-on", "speed-limit-activate", "speed-limit-deactivate", "speed-limit-clear-pin",
    "parental-controls-on", "parental-controls-off",
)
