package io.github.teslablecontrol

import android.content.Context
import io.github.teslablecontrol.core.LinkException
import io.github.teslablecontrol.core.LinkFactory
import io.github.teslablecontrol.core.LinkFailure
import io.github.teslablecontrol.protocol.testing.TcpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * The "sim" build type only, for trying every screen on an emulator. When the app's files folder holds
 * `vehicle-sim` containing `host:port`, cars are reached through the reference simulator
 * (tools/vehicle-sim) over TCP instead of Bluetooth. This build declares INTERNET for that socket;
 * debug and release builds don't, and use Bluetooth only.
 *
 *     adb reverse tcp:7001 tcp:7001
 *     adb shell "run-as io.github.teslablecontrol.sim sh -c 'echo 127.0.0.1:7001 > files/vehicle-sim'"
 */
object DebugLinks {
    fun simulator(context: Context): LinkFactory? {
        val target = runCatching { File(context.filesDir, "vehicle-sim").readText().trim() }.getOrNull() ?: return null
        val host = target.substringBeforeLast(':', "")
        val port = target.substringAfterLast(':').toIntOrNull()
        if (host.isEmpty() || port == null) return null
        return LinkFactory {
            withContext(Dispatchers.IO) {
                try {
                    TcpTransport(host, port)
                } catch (e: IOException) {
                    throw LinkException(LinkFailure.NOT_FOUND, "simulator at $target: ${e.message}", e)
                }
            }
        }
    }
}
