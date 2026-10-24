package io.github.teslablecontrol.protocol.testing

import io.github.teslablecontrol.protocol.Clock
import kotlin.time.Duration

/** Manually advanced clock for deterministic tests. */
class FakeClock(
    var nanos: Long = 1_000_000_000_000L,
    var wall: Long = 1_760_000_000_000L,
) : Clock {
    override fun monotonicNanos(): Long = nanos
    override fun wallMillis(): Long = wall

    fun advance(duration: Duration) {
        nanos += duration.inWholeNanoseconds
        wall += duration.inWholeMilliseconds
    }
}
