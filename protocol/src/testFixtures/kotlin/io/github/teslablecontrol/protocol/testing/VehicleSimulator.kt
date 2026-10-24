package io.github.teslablecontrol.protocol.testing

import io.github.teslablecontrol.protocol.toHex
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Runs the reference vehicle simulator (`tools/vehicle-sim`), which uses Tesla's own vehicle-side
 * protocol code, as a child process. The simulator exits when its stdin closes.
 */
class VehicleSimulator private constructor(private val process: Process, val port: Int, private val lines: LinkedBlockingQueue<String>) :
    AutoCloseable {

    /** Opens a new BLE-equivalent link to the vehicle. */
    fun connect(): TcpTransport = TcpTransport("127.0.0.1", port)

    /** Sends a control command (`reboot`, `sleep`) and waits for it to take effect. */
    fun control(command: String) {
        process.outputStream.write("$command\n".toByteArray())
        process.outputStream.flush()
        val reply = lines.poll(5, TimeUnit.SECONDS)
        check(reply == "OK") { "simulator did not acknowledge '$command' (got $reply)" }
    }

    override fun close() {
        runCatching { process.outputStream.close() }
        if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
    }

    class Builder {
        private val args = mutableListOf<String>()
        fun owner(publicKey: ByteArray) = apply { args += listOf("-owner", publicKey.toHex()) }
        fun driver(publicKey: ByteArray) = apply { args += listOf("-driver", publicKey.toHex()) }
        fun vin(vin: String) = apply { args += listOf("-vin", vin) }
        fun asleep(wakeDelayMillis: Long = 1500) = apply { args += listOf("-asleep", "-wake-delay", "${wakeDelayMillis}ms") }
        fun pairingDelay(millis: Long) = apply { args += listOf("-pairing-delay", "${millis}ms") }
        fun intermediateReplies() = apply { args += "-intermediate-replies" }
        fun duplicateReplies() = apply { args += "-duplicate-replies" }
        fun dropEvery(n: Int) = apply { args += listOf("-drop-every", "$n") }
        fun busy(n: Int) = apply { args += listOf("-busy", "$n") }
        fun verbose() = apply { args += "-v" }

        fun start(): VehicleSimulator {
            val binary = requireNotNull(binary) { "vehicle simulator not built (tools/vehicle-sim/build.sh)" }
            val process = ProcessBuilder(listOf(binary.path) + args)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
            val lines = LinkedBlockingQueue<String>()
            thread(isDaemon = true, name = "vehicle-sim-stdout") {
                process.inputStream.bufferedReader().forEachLine { lines.put(it.trim()) }
            }
            val first = lines.poll(10, TimeUnit.SECONDS)
            check(first != null && first.startsWith("LISTENING ")) { "simulator failed to start (got $first)" }
            return VehicleSimulator(process, first.removePrefix("LISTENING ").toInt(), lines)
        }
    }

    companion object {
        const val VIN = "5YJ3E1EA1PF000001"

        /** The simulator binary, or null when it hasn't been built (Go not installed). */
        val binary: File?
            get() = System.getProperty("vehicleSim.binary")?.let(::File)?.takeIf { it.canExecute() }

        fun builder() = Builder().vin(VIN)
    }
}
