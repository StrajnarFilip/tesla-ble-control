package io.github.teslablecontrol.protocol.session

/**
 * Anti-replay window for counters in vehicle responses (port of `internal/authentication/window.go`).
 * Accepts each counter at most once, tolerating out-of-order arrival within [WINDOW_SIZE].
 */
class SlidingWindow {
    private var counter: UInt = 0u
    private var history: Long = 0
    private var used = false

    /** Returns true if [newCounter] has not been seen before (and records it). */
    fun update(newCounter: UInt): Boolean {
        if (!used) {
            used = true
            counter = newCounter
            return true
        }
        val result = slide(counter, history, newCounter) ?: return false
        counter = result.first
        history = result.second
        return true
    }

    companion object {
        const val WINDOW_SIZE = 32u

        /** `updateSlidingWindow`: new (counter, history), or null if [newCounter] may be a replay. */
        internal fun slide(counter: UInt, history: Long, newCounter: UInt): Pair<UInt, Long>? {
            if (counter == newCounter) return null
            if (newCounter < counter) {
                val age = counter - newCounter
                if (age > WINDOW_SIZE) return null
                val bit = 1L shl (age - 1u).toInt()
                if (history and bit != 0L) return null
                return counter to (history or bit)
            }
            // Go defines shifts of 64 or more as producing zero; the JVM masks the shift count instead.
            val shift = (newCounter - counter).toLong()
            var updated = if (shift >= 64) 0L else history shl shift.toInt()
            if (shift - 1 < 64) updated = updated or (1L shl (shift - 1).toInt())
            return newCounter to updated
        }
    }
}
