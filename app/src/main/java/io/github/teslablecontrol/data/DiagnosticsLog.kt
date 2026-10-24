package io.github.teslablecontrol.data

import android.util.Log
import io.github.teslablecontrol.protocol.ProtocolLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** In-memory log of connection and protocol events, shown under Settings → Diagnostics. */
class DiagnosticsLog(private val capacity: Int = 500) : ProtocolLog {
    data class Entry(val timeMillis: Long, val level: ProtocolLog.Level, val message: String)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    /** When true, every message (TX/RX summaries) is recorded; otherwise only info and above. */
    @Volatile
    var detailed: Boolean = false

    override fun isEnabled(level: ProtocolLog.Level): Boolean = detailed || level >= ProtocolLog.Level.INFO

    override fun log(level: ProtocolLog.Level, message: String) {
        if (!isEnabled(level)) return
        val entry = Entry(System.currentTimeMillis(), level, message)
        _entries.update { (it + entry).takeLast(capacity) }
        when (level) {
            ProtocolLog.Level.DEBUG -> Log.d(TAG, message)
            ProtocolLog.Level.INFO -> Log.i(TAG, message)
            ProtocolLog.Level.WARN -> Log.w(TAG, message)
            ProtocolLog.Level.ERROR -> Log.e(TAG, message)
        }
    }

    fun info(message: String) = log(ProtocolLog.Level.INFO, message)
    fun warn(message: String) = log(ProtocolLog.Level.WARN, message)

    fun clear() = _entries.update { emptyList() }

    companion object {
        private const val TAG = "TeslaBle"
    }
}
