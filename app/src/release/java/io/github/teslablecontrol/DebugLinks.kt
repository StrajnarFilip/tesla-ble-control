package io.github.teslablecontrol

import android.content.Context
import io.github.teslablecontrol.core.LinkFactory

/** This build always uses Bluetooth; only the "sim" build type can reach the simulator. */
object DebugLinks {
    @Suppress("UNUSED_PARAMETER")
    fun simulator(context: Context): LinkFactory? = null
}
