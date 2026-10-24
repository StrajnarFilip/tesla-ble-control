package io.github.teslablecontrol.protocol

/** Diagnostic sink for protocol events. Messages never contain key material. */
interface ProtocolLog {
    enum class Level { DEBUG, INFO, WARN, ERROR }

    fun isEnabled(level: Level): Boolean = true
    fun log(level: Level, message: String)

    companion object {
        val NONE: ProtocolLog = object : ProtocolLog {
            override fun isEnabled(level: Level) = false
            override fun log(level: Level, message: String) = Unit
        }
    }
}

internal inline fun ProtocolLog.debug(message: () -> String) {
    if (isEnabled(ProtocolLog.Level.DEBUG)) log(ProtocolLog.Level.DEBUG, message())
}

internal inline fun ProtocolLog.info(message: () -> String) {
    if (isEnabled(ProtocolLog.Level.INFO)) log(ProtocolLog.Level.INFO, message())
}

internal inline fun ProtocolLog.warn(message: () -> String) {
    if (isEnabled(ProtocolLog.Level.WARN)) log(ProtocolLog.Level.WARN, message())
}
