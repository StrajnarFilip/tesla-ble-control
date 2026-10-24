package io.github.teslablecontrol.protocol

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Time source for the protocol. Vehicle clocks are tracked against the monotonic clock; wall time is
 * only used to age cached sessions across process restarts.
 */
interface Clock {
    fun monotonicNanos(): Long
    fun wallMillis(): Long

    companion object {
        val SYSTEM: Clock = object : Clock {
            override fun monotonicNanos(): Long = System.nanoTime()
            override fun wallMillis(): Long = System.currentTimeMillis()
        }
    }
}

/** A point in monotonic time by which an operation must finish. */
class Deadline private constructor(private val clock: Clock, private val atNanos: Long) {
    fun remaining(): Duration = (atNanos - clock.monotonicNanos()).coerceAtLeast(0).nanoseconds

    val isExpired: Boolean get() = clock.monotonicNanos() >= atNanos

    companion object {
        fun after(timeout: Duration, clock: Clock = Clock.SYSTEM): Deadline =
            Deadline(clock, clock.monotonicNanos() + timeout.inWholeNanoseconds)
    }
}
