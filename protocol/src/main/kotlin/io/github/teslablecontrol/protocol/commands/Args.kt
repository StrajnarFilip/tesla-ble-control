package io.github.teslablecontrol.protocol.commands

import io.github.teslablecontrol.protocol.VehicleErrors
import io.github.teslablecontrol.protocol.crypto.KeyCodec

/** Parameter kinds; the app builds input controls from these. */
sealed interface ParamType {
    /** "on"/"off" (also true/false, yes/no, 1/0). */
    data object OnOff : ParamType
    data class Integer(val min: Int, val max: Int) : ParamType
    data class Decimal(val min: Double, val max: Double) : ParamType
    data class Choice(val options: List<String>) : ParamType
    /** Four digits. */
    data object Pin : ParamType
    data class Text(val maxLength: Int) : ParamType
    /** "HH:MM", 24-hour clock. */
    data object TimeOfDay : ParamType
    /** "HH:MM-HH:MM"; either end may be empty ("-6:00", "22:00-"). */
    data object TimeRange : ParamType
    /** Comma-separated day names, "all" or "weekdays". */
    data object Days : ParamType
    /** Go-style duration such as "2h", "10m", "1h30m", "90s". */
    data object Duration : ParamType
    data object Latitude : ParamType
    data object Longitude : ParamType
    /** PEM, hex or raw P-256 public key. */
    data object PublicKey : ParamType
    /** "21", "21.5c" or "70f"; Celsius when no unit is given. */
    data object Temperature : ParamType
    /** Numeric identifier (schedule IDs). */
    data object Identifier : ParamType
}

data class ParamSpec(
    val name: String,
    val type: ParamType,
    val required: Boolean = true,
    val default: String? = null,
)

/** Typed access to string parameter values, with the official CLI's parsing rules. */
class Args(
    private val values: Map<String, String>,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    fun raw(name: String): String? = values[name]?.trim()?.takeIf { it.isNotEmpty() }

    fun has(name: String): Boolean = raw(name) != null

    fun string(name: String): String = raw(name) ?: throw missing(name)

    fun nowEpochSeconds(): Long = nowEpochSeconds.invoke()

    fun onOff(name: String): Boolean = parseOnOff(name, string(name))

    fun onOff(name: String, default: Boolean): Boolean = raw(name)?.let { parseOnOff(name, it) } ?: default

    private fun parseOnOff(name: String, value: String): Boolean = when (value.lowercase()) {
        "on", "true", "yes", "1", "enable", "enabled" -> true
        "off", "false", "no", "0", "disable", "disabled" -> false
        else -> throw invalid(name, "must be 'on' or 'off'")
    }

    fun int(name: String, range: IntRange? = null): Int {
        val value = string(name).toIntOrNull() ?: throw invalid(name, "must be a whole number")
        if (range != null && value !in range) throw invalid(name, "must be between ${range.first} and ${range.last}")
        return value
    }

    fun long(name: String): Long = string(name).toLongOrNull() ?: throw invalid(name, "must be a whole number")

    fun double(name: String, min: Double? = null, max: Double? = null): Double {
        val value = string(name).toDoubleOrNull()?.takeIf { it.isFinite() } ?: throw invalid(name, "must be a number")
        if (min != null && value < min) throw invalid(name, "must be at least $min")
        if (max != null && value > max) throw invalid(name, "must be at most $max")
        return value
    }

    fun float(name: String, min: Double? = null, max: Double? = null): Float = double(name, min, max).toFloat()

    fun <T> choice(name: String, options: Map<String, T>): T =
        options[string(name).lowercase()] ?: throw invalid(name, "must be one of ${options.keys.joinToString()}")

    fun pin(name: String): String {
        val pin = string(name)
        if (pin.length != 4 || !pin.all { it in '0'..'9' }) throw invalid(name, "must be four digits")
        return pin
    }

    /** "HH:MM" as minutes after midnight (`MinutesAfterMidnight`). */
    fun minutesAfterMidnight(name: String): Int = parseTime(name, string(name))

    private fun parseTime(name: String, text: String): Int {
        val parts = text.split(":")
        if (parts.size != 2) throw invalid(name, "expected HH:MM")
        val hours = parts[0].trim().toIntOrNull() ?: throw invalid(name, "expected HH:MM")
        val minutes = parts[1].trim().toIntOrNull() ?: throw invalid(name, "expected HH:MM")
        if (hours !in 0..23 || minutes !in 0..59) throw invalid(name, "hours or minutes outside valid range")
        return hours * 60 + minutes
    }

    /** "start-end" where either end may be empty; returns minutes after midnight or null. */
    fun timeRange(name: String): Pair<Int?, Int?> {
        val parts = string(name).split("-")
        if (parts.size != 2) throw invalid(name, "invalid time range")
        val start = parts[0].trim().takeIf { it.isNotEmpty() }?.let { parseTime(name, it) }
        val end = parts[1].trim().takeIf { it.isNotEmpty() }?.let { parseTime(name, it) }
        return start to end
    }

    /** Day names to the vehicle's bitmask (`GetDays`). */
    fun days(name: String): Int {
        var mask = 0
        for (day in string(name).split(",")) {
            mask = mask or (DAY_BITS[day.trim().uppercase()] ?: throw invalid(name, "unrecognized day name: $day"))
        }
        return mask
    }

    /** Latitude or longitude in degrees (`GetDegree`: both must be within ±180). */
    fun degrees(name: String): Float = float(name, -180.0, 180.0)

    /** Go-style duration in whole seconds. */
    fun durationSeconds(name: String): Int {
        val nanos = parseGoDuration(string(name)) ?: throw invalid(
            name, "valid times are <n><unit> where <unit> is s, m or h, e.g. 2h or 10m",
        )
        return (nanos / 1_000_000_000L).toInt()
    }

    fun publicKey(name: String): ByteArray = try {
        KeyCodec.parsePublicKey(string(name))
    } catch (e: IllegalArgumentException) {
        throw invalid(name, e.message ?: "invalid public key")
    }

    /** Degrees Celsius from "21", "21c" or "70f" (Fahrenheit converted like the CLI does). */
    fun temperatureCelsius(name: String): Float {
        val text = string(name).trim()
        val unit = text.lastOrNull()?.lowercaseChar()
        val number = if (unit == 'c' || unit == 'f') text.dropLast(1).trim() else text
        val degrees = number.toFloatOrNull() ?: throw invalid(name, "format as 22C or 72F")
        return if (unit == 'f') (degrees - 32f) * 5f / 9f else degrees
    }

    private fun missing(name: String) = VehicleErrors.invalidArgument("missing $name")

    private fun invalid(name: String, reason: String) = VehicleErrors.invalidArgument("$name $reason")

    companion object {
        /** `dayNamesBitMask` from the official CLI. */
        val DAY_BITS: Map<String, Int> = mapOf(
            "SUN" to 1, "SUNDAY" to 1,
            "MON" to 2, "MONDAY" to 2,
            "TUES" to 4, "TUESDAY" to 4, "TUE" to 4,
            "WED" to 8, "WEDNESDAY" to 8,
            "THURS" to 16, "THURSDAY" to 16, "THU" to 16,
            "FRI" to 32, "FRIDAY" to 32,
            "SAT" to 64, "SATURDAY" to 64,
            "ALL" to 127,
            "WEEKDAYS" to 62,
        )

        private val DURATION_UNITS = mapOf(
            "ns" to 1.0, "us" to 1e3, "µs" to 1e3, "ms" to 1e6, "s" to 1e9, "m" to 60e9, "h" to 3600e9,
        )
        private val DURATION_PART = Regex("([0-9]*\\.?[0-9]+)(ns|us|µs|ms|s|m|h)")

        /** A subset of Go's `time.ParseDuration`: one or more `<number><unit>` parts. */
        fun parseGoDuration(text: String): Long? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            var total = 0.0
            var position = 0
            for (match in DURATION_PART.findAll(trimmed)) {
                if (match.range.first != position) return null
                total += match.groupValues[1].toDouble() * DURATION_UNITS.getValue(match.groupValues[2])
                position = match.range.last + 1
            }
            if (position != trimmed.length || position == 0) return null
            return total.toLong()
        }
    }
}
