package io.github.teslablecontrol.ui.format

import android.content.Context
import android.icu.text.DisplayContext
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import io.github.teslablecontrol.R
import io.github.teslablecontrol.core.CommandOutcome
import io.github.teslablecontrol.core.ControllerState
import io.github.teslablecontrol.core.FailureKind
import io.github.teslablecontrol.core.LinkStatus
import io.github.teslablecontrol.core.Stage
import io.github.teslablecontrol.data.CarModel
import io.github.teslablecontrol.data.UnitSystem
import java.util.Locale
import kotlin.math.roundToInt

/** The UI language's locale, read so that composables update when the language changes. */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.ROOT

/** One decimal place, written the way the UI language writes numbers. */
@Composable
fun oneDecimal(value: Float): String = String.format(currentLocale(), "%.1f", value)

/** Temperature and distance units, resolved from the setting and the phone's region. */
data class Units(val fahrenheit: Boolean, val miles: Boolean, val psi: Boolean) {
    companion object {
        private val FAHRENHEIT_REGIONS = setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")
        private val MILES_REGIONS = setOf("US", "GB", "LR", "MM")

        fun resolve(system: UnitSystem, locale: Locale = Locale.getDefault()): Units = when (system) {
            UnitSystem.METRIC -> Units(fahrenheit = false, miles = false, psi = false)
            UnitSystem.IMPERIAL -> Units(fahrenheit = true, miles = true, psi = true)
            UnitSystem.AUTO -> {
                val region = locale.country.uppercase()
                Units(fahrenheit = region in FAHRENHEIT_REGIONS, miles = region in MILES_REGIONS, psi = region in FAHRENHEIT_REGIONS)
            }
        }
    }
}

/** A number and its unit, so the number can be shown larger than the unit. */
data class Reading(val number: String, val unit: String)

fun Units.temperatureReading(celsius: Float?): Reading = when {
    celsius == null -> Reading("—", "")
    fahrenheit -> Reading("${(celsius * 9f / 5f + 32f).roundToInt()}", "°F")
    else -> Reading(String.format(Locale.getDefault(), "%.1f", celsius), "°C")
}

fun Units.distanceReading(miles: Float?): Reading = when {
    miles == null -> Reading("—", "")
    this.miles -> Reading("${miles.roundToInt()}", "mi")
    else -> Reading("${(miles * 1.609344f).roundToInt()}", "km")
}

fun percentReading(value: Int?): Reading = if (value == null) Reading("—", "") else Reading("$value", "%")

fun Units.temperature(celsius: Float?): String = when {
    celsius == null -> "—"
    fahrenheit -> "${(celsius * 9f / 5f + 32f).roundToInt()} °F"
    else -> String.format(Locale.getDefault(), "%.1f °C", celsius)
}

/** Temperature as a bare number in the display unit, for steppers. */
fun Units.temperatureValue(celsius: Float): Float = if (fahrenheit) celsius * 9f / 5f + 32f else celsius

fun Units.celsiusFrom(display: Float): Float = if (fahrenheit) (display - 32f) * 5f / 9f else display

fun Units.distanceFromMiles(miles: Float?): String = when {
    miles == null -> "—"
    this.miles -> "${miles.roundToInt()} mi"
    else -> "${(miles * 1.609344f).roundToInt()} km"
}

fun Units.pressureFromBar(bar: Float?): String = when {
    bar == null -> "—"
    psi -> "${(bar * 14.5038f).roundToInt()} psi"
    else -> String.format(Locale.getDefault(), "%.1f bar", bar)
}

fun Units.speedFromMph(mph: Double): String = if (miles) "${mph.roundToInt()} mph" else "${(mph * 1.609344).roundToInt()} km/h"

/** "5 minutes ago", worded to sit in the middle of a sentence such as "Updated 5 minutes ago". */
fun relativeTime(millis: Long?, now: Long = System.currentTimeMillis()): String? = millis?.let {
    val formatter = RelativeDateTimeFormatter.getInstance(
        ULocale.forLocale(Locale.getDefault()), null, RelativeDateTimeFormatter.Style.LONG, DisplayContext.CAPITALIZATION_FOR_MIDDLE_OF_SENTENCE,
    )
    val minutes = ((now - it) / DateUtils.MINUTE_IN_MILLIS).coerceAtLeast(0)
    val (amount, unit) = when {
        minutes < 60 -> minutes to RelativeDateTimeFormatter.RelativeUnit.MINUTES
        minutes < 24 * 60 -> minutes / 60 to RelativeDateTimeFormatter.RelativeUnit.HOURS
        else -> minutes / (24 * 60) to RelativeDateTimeFormatter.RelativeUnit.DAYS
    }
    formatter.format(amount.toDouble(), RelativeDateTimeFormatter.Direction.LAST, unit)
}

/** The model's name; only "Other" is a word that needs translating. */
@Composable
fun carModelName(model: CarModel): String =
    if (model == CarModel.OTHER) androidx.compose.ui.res.stringResource(R.string.model_other) else model.label

fun minutesToClock(minutes: Int): String = String.format(Locale.ROOT, "%02d:%02d", (minutes / 60) % 24, minutes % 60)

/** "1 h 20 min" or "45 min". */
fun durationText(minutes: Int): String = when {
    minutes >= 60 -> if (minutes % 60 == 0) "${minutes / 60} h" else "${minutes / 60} h ${minutes % 60} min"
    else -> "$minutes min"
}

/** The car's day bitmask (bit 0 = Sunday) as text: "Every day", "Weekdays" or "Mon, Wed". */
fun daysText(
    mask: Int,
    locale: Locale = Locale.getDefault(),
    everyDay: String = "Every day",
    weekdays: String = "Weekdays",
    noDays: String = "No days",
): String {
    if (mask and 127 == 127) return everyDay
    if (mask and 127 == 62) return weekdays
    val names = java.text.DateFormatSymbols(locale).shortWeekdays.drop(1)
    return names.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.joinToString(", ").ifEmpty { noDays }
}

@Composable
fun scheduleDaysText(mask: Int): String = daysText(
    mask,
    currentLocale(),
    androidx.compose.ui.res.stringResource(R.string.commands_days_all),
    androidx.compose.ui.res.stringResource(R.string.commands_days_weekdays),
    androidx.compose.ui.res.stringResource(R.string.label_no_days),
)

fun stageText(context: Context, stage: Stage): String = context.getString(
    when (stage) {
        Stage.CONNECTING -> R.string.status_connecting
        Stage.SECURING -> R.string.status_securing
        Stage.WAKING -> R.string.status_waking
        Stage.SENDING -> R.string.status_sending
        Stage.REFRESHING -> R.string.status_refreshing
    },
)

/** One line for notifications and the tile: what is happening, else "Locked and awake" or the connection. */
fun statusLine(context: Context, state: ControllerState): String {
    state.activity?.let { return stageText(context, it.stage) }
    if (state.link != LinkStatus.CONNECTED) return bannerTitle(context, state)
    val lock = state.snapshot.isLocked?.let { context.getString(if (it) R.string.status_locked else R.string.status_unlocked) }
    val sleep = state.snapshot.isAsleep?.let { context.getString(if (it) R.string.status_asleep else R.string.status_awake) }
    return when {
        lock != null && sleep != null -> context.getString(R.string.status_lock_and_sleep, lock, sleep.lowercase())
        else -> lock ?: sleep ?: context.getString(R.string.status_connected)
    }
}

/** The status banner's headline: what is happening right now, else the lock state, else the link. */
fun bannerTitle(context: Context, state: ControllerState): String {
    state.activity?.let { return stageText(context, it.stage) }
    return context.getString(
        when {
            state.link == LinkStatus.CONNECTED && state.snapshot.isLocked == true -> R.string.status_locked
            state.link == LinkStatus.CONNECTED && state.snapshot.isLocked == false -> R.string.status_unlocked
            state.link == LinkStatus.CONNECTED -> R.string.status_connected
            state.link == LinkStatus.CONNECTING -> R.string.status_connecting
            else -> R.string.status_not_connected
        },
    )
}

/** The banner's second line, a plain sentence about the car and the link. */
fun bannerDetail(context: Context, state: ControllerState): String = context.getString(
    when {
        state.link == LinkStatus.CONNECTING -> R.string.banner_detail_connecting
        state.link != LinkStatus.CONNECTED -> R.string.banner_detail_disconnected
        state.snapshot.isAsleep == true -> R.string.banner_detail_asleep
        state.snapshot.isAsleep == false -> R.string.banner_detail_awake
        else -> R.string.banner_detail_connected
    },
)

/** Every title and detail the banner can show, so it can keep room for the longest. */
fun bannerVariants(context: Context): Pair<List<String>, List<String>> =
    (Stage.entries.map { stageText(context, it) } + listOf(
        R.string.status_locked, R.string.status_unlocked, R.string.status_connected, R.string.status_connecting,
        R.string.status_not_connected, R.string.status_not_paired, R.string.home_key_unusable_title,
    ).map(context::getString)) to listOf(
        R.string.banner_detail_connecting, R.string.banner_detail_disconnected, R.string.banner_detail_asleep,
        R.string.banner_detail_awake, R.string.banner_detail_connected, R.string.banner_detail_pair,
    ).map(context::getString)

/** A fix the UI can offer next to a failure message. */
enum class FailureAction { PAIR, WAKE, BLUETOOTH, PERMISSION, LOCATION, RETRY, HELP }

fun failureMessage(context: Context, failure: CommandOutcome.Failure): String {
    if (failure.mayHaveSucceeded) return context.getString(R.string.result_sent_unconfirmed)
    return when (failure.kind) {
        FailureKind.NOT_PAIRED -> context.getString(R.string.failure_not_paired)
        FailureKind.BLUETOOTH_OFF -> context.getString(R.string.failure_bluetooth_off)
        FailureKind.PERMISSION_MISSING -> context.getString(R.string.failure_permission)
        FailureKind.LOCATION_OFF -> context.getString(R.string.failure_location_off)
        FailureKind.OUT_OF_RANGE -> context.getString(R.string.failure_out_of_range)
        FailureKind.TOO_MANY_CONNECTIONS -> context.getString(R.string.failure_too_many)
        FailureKind.CONNECTION_FAILED -> context.getString(R.string.failure_connection_failed)
        FailureKind.ASLEEP -> context.getString(R.string.failure_asleep)
        FailureKind.TIMEOUT -> context.getString(R.string.failure_timeout)
        FailureKind.BUSY -> context.getString(R.string.failure_busy)
        FailureKind.REFUSED -> context.getString(R.string.failure_refused, failure.message)
        FailureKind.KEYCHAIN -> context.getString(R.string.failure_keychain, keychainReason(failure.message))
        FailureKind.NOT_ALLOWED -> context.getString(R.string.failure_not_allowed)
        FailureKind.MOBILE_ACCESS_DISABLED -> context.getString(R.string.failure_mobile_access)
        FailureKind.INVALID_INPUT -> context.getString(R.string.failure_invalid, failure.message)
        FailureKind.CONNECTION_LOST -> context.getString(R.string.failure_connection_lost)
        FailureKind.PROTOCOL -> context.getString(R.string.failure_protocol, failure.message)
    }
}

fun failureAction(failure: CommandOutcome.Failure): FailureAction? = when (failure.kind) {
    FailureKind.NOT_PAIRED -> FailureAction.PAIR
    FailureKind.ASLEEP -> FailureAction.WAKE
    FailureKind.BLUETOOTH_OFF -> FailureAction.BLUETOOTH
    FailureKind.PERMISSION_MISSING -> FailureAction.PERMISSION
    FailureKind.LOCATION_OFF -> FailureAction.LOCATION
    FailureKind.OUT_OF_RANGE, FailureKind.TOO_MANY_CONNECTIONS, FailureKind.MOBILE_ACCESS_DISABLED -> FailureAction.HELP
    FailureKind.TIMEOUT, FailureKind.BUSY, FailureKind.CONNECTION_FAILED, FailureKind.CONNECTION_LOST ->
        if (failure.mayHaveSucceeded) null else FailureAction.RETRY
    else -> null
}

/** "WHITELISTOPERATION_INFORMATION_NO_PERMISSION_TO_ADD" → "no permission to add". */
private fun keychainReason(message: String): String =
    message.substringAfter("WHITELISTOPERATION_INFORMATION_", message).replace('_', ' ').lowercase()
