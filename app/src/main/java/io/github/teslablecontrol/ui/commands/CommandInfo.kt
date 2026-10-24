package io.github.teslablecontrol.ui.commands

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AirlineSeatReclineNormal
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EvStation
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Garage
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.Masks
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material.icons.outlined.Window
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.teslablecontrol.R
import io.github.teslablecontrol.data.CarModel

enum class CommandGroup(val title: String) {
    ACCESS("Doors and access"),
    CLIMATE("Climate"),
    CHARGING("Charging"),
    BODY("Windows, lights and more"),
    MEDIA("Media"),
    SECURITY("Security and modes"),
    KEYS("Keys"),
    SOFTWARE("Software"),
    DATA("Vehicle data"),
}

/** How a command is presented: words for buttons and TalkBack, its group, and whether to confirm first. */
data class CommandInfo(
    val id: String,
    val title: String,
    val description: String,
    val group: CommandGroup,
    val icon: ImageVector,
    /** Ask before sending (when confirmations are on). */
    val risky: Boolean = false,
    /** Shown and announced after success. */
    val successText: String = "Done",
    /** Only some models have this (null = all). */
    val models: Set<CarModel>? = null,
)

/** Human labels for command parameters, by parameter name. */
object ParamLabels {
    private val labels = mapOf(
        "STATE" to "On or off",
        "TEMP" to "Temperature",
        "PASSENGER_TEMP" to "Passenger temperature (optional)",
        "SEAT" to "Seat",
        "LEVEL" to "Level",
        "POSITIONS" to "Seats (L = driver side, R = passenger side)",
        "OVERRIDE" to "Manual override",
        "FAN_ONLY" to "Fan only (no air conditioning)",
        "MODE" to "Mode",
        "PERCENT" to "Charge limit (%)",
        "AMPS" to "Charging current (A)",
        "TIME" to "Time (HH:MM, 24-hour)",
        "DEPART_AT" to "Departure time (HH:MM)",
        "OFF_PEAK_END" to "Off-peak ends at (HH:MM)",
        "PRECONDITIONING" to "Precondition the cabin",
        "OFF_PEAK" to "Charge off-peak",
        "DAYS" to "Days (e.g. Mon,Tue or weekdays or all)",
        "LATITUDE" to "Latitude",
        "LONGITUDE" to "Longitude",
        "REPEAT" to "Repeat",
        "ID" to "Schedule ID",
        "ENABLED" to "Enabled",
        "TYPE" to "Which schedules",
        "VOLUME" to "Volume (0–10)",
        "PIN" to "4-digit PIN",
        "MPH" to "Speed limit (mph)",
        "SETTING" to "Setting",
        "DELAY" to "Start after (e.g. 2h, 30m)",
        "CATEGORY" to "What to read",
        "NAME" to "Name",
        "PUBLIC_KEY" to "Public key (PEM or hex)",
        "ROLE" to "Role",
        "FORM_FACTOR" to "Kind of key",
        "DOMAIN" to "Car system",
    )

    fun label(name: String): String = labels[name] ?: name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

    fun has(name: String): Boolean = name in labels
}

/** The app's own pictures for commands that have one; the rest use [CommandInfo.icon]. */
private val PICTURES: Map<String, Int> = mapOf(
    "lock" to R.drawable.ic_action_locked, "unlock" to R.drawable.ic_action_unlocked,
    "wake" to R.drawable.ic_action_wake, "drive" to R.drawable.ic_action_remote_start,
    "autosecure-modelx" to R.drawable.ic_action_locked,
    "frunk-open" to R.drawable.ic_action_frunk_open, "trunk-open" to R.drawable.ic_action_trunk_open,
    "trunk-close" to R.drawable.ic_action_trunk_closed, "trunk-move" to R.drawable.ic_action_trunk_open,
    "tonneau-open" to R.drawable.ic_action_tonneau_open, "tonneau-close" to R.drawable.ic_action_tonneau_closed,
    "tonneau-stop" to R.drawable.ic_action_stop,
    "climate-on" to R.drawable.ic_action_climate, "climate-off" to R.drawable.ic_action_climate,
    "seat-heater" to R.drawable.ic_action_seat_heat_2, "seat-cooler" to R.drawable.ic_action_seat_cool_2,
    "auto-seat-and-climate" to R.drawable.ic_action_seat_heat_1,
    "steering-wheel-heater" to R.drawable.ic_action_steering_heat, "preconditioning-max" to R.drawable.ic_action_defrost,
    "cabin-overheat-protection" to R.drawable.ic_action_cop_on, "cabin-overheat-protection-temp" to R.drawable.ic_action_temp_medium,
    "climate-keeper" to R.drawable.ic_action_keeper_on,
    "charging-start" to R.drawable.ic_action_charge_start, "charging-stop" to R.drawable.ic_action_charge_stop,
    "charge-max-range" to R.drawable.ic_action_charge_max, "charge-standard-range" to R.drawable.ic_action_charge_standard,
    "charge-port-open" to R.drawable.ic_action_port_open, "charge-port-close" to R.drawable.ic_action_port_closed,
    "low-power-mode" to R.drawable.ic_action_leaf, "keep-accessory-power" to R.drawable.ic_action_outlet,
    "windows-vent" to R.drawable.ic_action_windows_vented, "windows-close" to R.drawable.ic_action_windows_closed,
    "sunroof" to R.drawable.ic_action_sunroof_open, "homelink" to R.drawable.ic_action_homelink,
    "honk" to R.drawable.ic_action_honk, "flash-lights" to R.drawable.ic_action_flash, "ping" to R.drawable.ic_action_ping,
    "media-toggle-playback" to R.drawable.ic_action_play_pause, "media-next-track" to R.drawable.ic_action_next,
    "media-previous-track" to R.drawable.ic_action_previous, "media-next-favorite" to R.drawable.ic_action_favorite_next,
    "media-previous-favorite" to R.drawable.ic_action_favorite_previous,
    "sentry-mode" to R.drawable.ic_action_sentry,
    "speed-limit-activate" to R.drawable.ic_action_speed_limit, "speed-limit-deactivate" to R.drawable.ic_action_speed_limit,
    "speed-limit-set" to R.drawable.ic_action_speed_limit, "speed-limit-clear-pin" to R.drawable.ic_action_speed_limit,
    "speed-limit-clear-pin-admin" to R.drawable.ic_action_speed_limit,
    "guest-mode-on" to R.drawable.ic_action_guest, "guest-mode-off" to R.drawable.ic_action_guest_off,
    "erase-guest-data" to R.drawable.ic_action_erase,
    "parental-controls-on" to R.drawable.ic_action_parental, "parental-controls-off" to R.drawable.ic_action_parental,
    "parental-controls-set-speed-limit" to R.drawable.ic_action_parental, "parental-controls-enable-setting" to R.drawable.ic_action_parental,
    "parental-controls-clear-pin-admin" to R.drawable.ic_action_parental,
    "software-update-start" to R.drawable.ic_action_update_install, "software-update-cancel" to R.drawable.ic_action_update_cancel,
)

/** The app's picture for this command, if it has one. */
val CommandInfo.picture: Int? get() = PICTURES[id]

object CommandInfos {
    private val list = listOf(
        // Access
        CommandInfo("lock", "Lock", "Locks all doors.", CommandGroup.ACCESS, Icons.Outlined.Lock, successText = "Car locked"),
        CommandInfo("unlock", "Unlock", "Unlocks all doors.", CommandGroup.ACCESS, Icons.Outlined.LockOpen, risky = true, successText = "Car unlocked"),
        CommandInfo("wake", "Wake car", "Wakes the car so it can answer climate, charging and media commands.", CommandGroup.ACCESS, Icons.Outlined.WbSunny, successText = "Car is waking up"),
        CommandInfo("drive", "Remote start", "Allows driving without a key for a short time.", CommandGroup.ACCESS, Icons.Outlined.DirectionsCar, risky = true, successText = "Remote start enabled"),
        CommandInfo("autosecure-modelx", "Close doors and lock", "Closes the falcon-wing doors and locks.", CommandGroup.ACCESS, Icons.Outlined.Shield, successText = "Closing and locking", models = setOf(CarModel.MODEL_X)),
        CommandInfo("frunk-open", "Open frunk", "Opens the front trunk. It must be closed by hand.", CommandGroup.ACCESS, Icons.Outlined.Luggage, risky = true, successText = "Frunk opened"),
        CommandInfo("trunk-open", "Open trunk", "Opens the rear trunk.", CommandGroup.ACCESS, Icons.Outlined.Luggage, risky = true, successText = "Trunk opened"),
        CommandInfo("trunk-close", "Close trunk", "Closes the rear trunk (powered liftgates only).", CommandGroup.ACCESS, Icons.Outlined.Luggage, successText = "Trunk closing"),
        CommandInfo("trunk-move", "Open or close trunk", "Toggles the rear trunk.", CommandGroup.ACCESS, Icons.Outlined.Luggage, risky = true, successText = "Trunk moving"),
        CommandInfo("tonneau-open", "Open tonneau", "Opens the bed cover.", CommandGroup.ACCESS, Icons.Outlined.Garage, risky = true, successText = "Tonneau opening", models = setOf(CarModel.CYBERTRUCK)),
        CommandInfo("tonneau-close", "Close tonneau", "Closes the bed cover.", CommandGroup.ACCESS, Icons.Outlined.Garage, successText = "Tonneau closing", models = setOf(CarModel.CYBERTRUCK)),
        CommandInfo("tonneau-stop", "Stop tonneau", "Stops the bed cover where it is.", CommandGroup.ACCESS, Icons.Outlined.Garage, successText = "Tonneau stopped", models = setOf(CarModel.CYBERTRUCK)),
        CommandInfo("body-controller-state", "Read locks and doors", "Reads locks, doors and sleep state without waking the car.", CommandGroup.DATA, Icons.Outlined.Info, successText = "Status updated"),

        // Keys
        CommandInfo("list-keys", "List keys", "Shows every key that can unlock the car.", CommandGroup.KEYS, Icons.Outlined.Key, successText = "Keys loaded"),
        CommandInfo("add-key", "Add a key", "Gives another phone or card access. Needs an owner key.", CommandGroup.KEYS, Icons.Outlined.VpnKey, risky = true, successText = "Key added"),
        CommandInfo("add-key-request", "Request pairing", "Asks the car to add a key; approve it with a key card in the car.", CommandGroup.KEYS, Icons.Outlined.VpnKey, successText = "Request sent. Tap your key card in the car."),
        CommandInfo("remove-key", "Remove a key", "Takes away a key's access. Needs an owner key.", CommandGroup.KEYS, Icons.Outlined.Delete, risky = true, successText = "Key removed"),
        CommandInfo("session-info", "Check a key", "Asks the car whether a key is enrolled.", CommandGroup.KEYS, Icons.Outlined.Info, successText = "The key is enrolled"),

        // Climate
        CommandInfo("climate-on", "Climate on", "Starts heating or cooling the cabin.", CommandGroup.CLIMATE, Icons.Outlined.AcUnit, successText = "Climate on"),
        CommandInfo("climate-off", "Climate off", "Stops climate control.", CommandGroup.CLIMATE, Icons.Outlined.AcUnit, successText = "Climate off"),
        CommandInfo("climate-set-temp", "Set temperature", "Sets the cabin temperature.", CommandGroup.CLIMATE, Icons.Outlined.Thermostat, successText = "Temperature set"),
        CommandInfo("seat-heater", "Seat heater", "Sets one seat's heater level.", CommandGroup.CLIMATE, Icons.Outlined.AirlineSeatReclineNormal, successText = "Seat heater set"),
        CommandInfo("seat-cooler", "Seat cooling", "Sets a front seat's ventilation level (where fitted).", CommandGroup.CLIMATE, Icons.Outlined.AirlineSeatReclineNormal, successText = "Seat cooling set"),
        CommandInfo("steering-wheel-heater", "Steering wheel heater", "Turns the steering wheel heater on or off.", CommandGroup.CLIMATE, Icons.Outlined.Whatshot, successText = "Steering wheel heater set"),
        CommandInfo("auto-seat-and-climate", "Automatic seat climate", "Lets the car manage front seat heating with climate.", CommandGroup.CLIMATE, Icons.Outlined.AirlineSeatReclineNormal, successText = "Automatic seat climate set"),
        CommandInfo("preconditioning-max", "Defrost", "Maximum preconditioning to clear ice and fog.", CommandGroup.CLIMATE, Icons.Outlined.AcUnit, successText = "Defrost set"),
        CommandInfo("bioweapon-mode", "Bioweapon Defense Mode", "HEPA filtration at maximum (cars with a HEPA filter).", CommandGroup.CLIMATE, Icons.Outlined.Masks, successText = "Bioweapon Defense Mode set"),
        CommandInfo("cabin-overheat-protection", "Cabin overheat protection", "Keeps the parked cabin from getting too hot.", CommandGroup.CLIMATE, Icons.Outlined.WbSunny, successText = "Overheat protection set"),
        CommandInfo("cabin-overheat-protection-temp", "Overheat protection temperature", "Sets when overheat protection starts.", CommandGroup.CLIMATE, Icons.Outlined.Thermostat, successText = "Activation temperature set"),
        CommandInfo("climate-keeper", "Keep climate", "Keeps climate on when you leave: Keep, Dog or Camp mode.", CommandGroup.CLIMATE, Icons.Outlined.Pets, successText = "Climate keeper set"),

        // Charging
        CommandInfo("charging-start", "Start charging", "Starts charging if a cable is connected.", CommandGroup.CHARGING, Icons.Outlined.Bolt, successText = "Charging started"),
        CommandInfo("charging-stop", "Stop charging", "Stops charging.", CommandGroup.CHARGING, Icons.Outlined.Bolt, successText = "Charging stopped"),
        CommandInfo("charge-max-range", "Charge to maximum", "Starts a charge to 100% for a long trip.", CommandGroup.CHARGING, Icons.Outlined.BatteryChargingFull, successText = "Charging to maximum"),
        CommandInfo("charge-standard-range", "Charge to standard", "Starts a charge to the everyday limit.", CommandGroup.CHARGING, Icons.Outlined.BatteryChargingFull, successText = "Charging to standard"),
        CommandInfo("charging-set-limit", "Set charge limit", "Sets how full the battery charges.", CommandGroup.CHARGING, Icons.Outlined.BatteryChargingFull, successText = "Charge limit set"),
        CommandInfo("charging-set-amps", "Set charging current", "Sets the charging current in amps.", CommandGroup.CHARGING, Icons.Outlined.EvStation, successText = "Charging current set"),
        CommandInfo("charge-port-open", "Open charge port", "Opens the charge port door.", CommandGroup.CHARGING, Icons.Outlined.EvStation, successText = "Charge port opened"),
        CommandInfo("charge-port-close", "Close charge port", "Closes the charge port door.", CommandGroup.CHARGING, Icons.Outlined.EvStation, successText = "Charge port closed"),
        CommandInfo("charging-schedule", "Scheduled charging", "Starts charging at a set time every day.", CommandGroup.CHARGING, Icons.Outlined.Schedule, successText = "Charging scheduled"),
        CommandInfo("charging-schedule-cancel", "Cancel scheduled charging", "Turns off the daily charging start time.", CommandGroup.CHARGING, Icons.Outlined.Schedule, successText = "Scheduled charging off"),
        CommandInfo("scheduled-departure", "Scheduled departure", "Charges and preconditions to be ready at a departure time.", CommandGroup.CHARGING, Icons.Outlined.Event, successText = "Departure scheduled"),
        CommandInfo("scheduled-departure-clear", "Cancel scheduled departure", "Turns off scheduled departure.", CommandGroup.CHARGING, Icons.Outlined.Event, successText = "Scheduled departure off"),
        CommandInfo("charging-schedule-add", "Add charge schedule", "Adds a charging window for chosen days and place.", CommandGroup.CHARGING, Icons.Outlined.Schedule, successText = "Charge schedule added"),
        CommandInfo("charging-schedule-remove", "Remove charge schedules", "Removes charge schedules by place or ID.", CommandGroup.CHARGING, Icons.Outlined.Delete, risky = true, successText = "Charge schedules removed"),
        CommandInfo("precondition-schedule-add", "Add precondition schedule", "Warms or cools the cabin by a time on chosen days.", CommandGroup.CLIMATE, Icons.Outlined.Schedule, successText = "Precondition schedule added"),
        CommandInfo("precondition-schedule-remove", "Remove precondition schedules", "Removes precondition schedules by place or ID.", CommandGroup.CLIMATE, Icons.Outlined.Delete, risky = true, successText = "Precondition schedules removed"),
        CommandInfo("low-power-mode", "Low power mode", "Reduces the energy the car uses while parked.", CommandGroup.CHARGING, Icons.Outlined.Power, successText = "Low power mode set"),
        CommandInfo("keep-accessory-power", "Keep accessory power", "Keeps USB and 12 V outlets powered after you leave.", CommandGroup.CHARGING, Icons.Outlined.Power, successText = "Accessory power set"),

        // Body and other
        CommandInfo("windows-vent", "Vent windows", "Lowers all windows slightly.", CommandGroup.BODY, Icons.Outlined.Window, risky = true, successText = "Windows vented"),
        CommandInfo("windows-close", "Close windows", "Closes all windows.", CommandGroup.BODY, Icons.Outlined.Window, successText = "Windows closing"),
        CommandInfo("sunroof", "Sunroof position", "Sets the sunroof opening (0 closed – 100 open; older Model S/X).", CommandGroup.BODY, Icons.Outlined.WbSunny, successText = "Sunroof moving", models = setOf(CarModel.MODEL_S, CarModel.MODEL_X)),
        CommandInfo("homelink", "HomeLink", "Triggers your garage door opener at the car's location.", CommandGroup.BODY, Icons.Outlined.Garage, risky = true, successText = "HomeLink triggered"),
        CommandInfo("honk", "Honk", "Sounds the horn once.", CommandGroup.BODY, Icons.Outlined.Campaign, successText = "Honked"),
        CommandInfo("flash-lights", "Flash lights", "Flashes the headlights.", CommandGroup.BODY, Icons.Outlined.Lightbulb, successText = "Lights flashed"),
        CommandInfo("ping", "Test connection", "Sends a harmless message to check the secure connection.", CommandGroup.BODY, Icons.Outlined.Sensors, successText = "The car answered"),
        CommandInfo("set-vehicle-name", "Rename car", "Changes the name shown on the car's screen.", CommandGroup.BODY, Icons.Outlined.Edit, successText = "Car renamed"),

        // Media
        CommandInfo("media-toggle-playback", "Play or pause", "Toggles media playback.", CommandGroup.MEDIA, Icons.Outlined.PlayArrow, successText = "Playback toggled"),
        CommandInfo("media-next-track", "Next track", "Skips to the next track.", CommandGroup.MEDIA, Icons.Outlined.SkipNext, successText = "Next track"),
        CommandInfo("media-previous-track", "Previous track", "Goes back one track.", CommandGroup.MEDIA, Icons.Outlined.SkipPrevious, successText = "Previous track"),
        CommandInfo("media-next-favorite", "Next favourite", "Goes to the next favourite station or playlist.", CommandGroup.MEDIA, Icons.Outlined.MusicNote, successText = "Next favourite"),
        CommandInfo("media-previous-favorite", "Previous favourite", "Goes back to the previous favourite.", CommandGroup.MEDIA, Icons.Outlined.MusicNote, successText = "Previous favourite"),
        CommandInfo("media-volume-up", "Volume up", "Raises the volume one step.", CommandGroup.MEDIA, Icons.AutoMirrored.Outlined.VolumeUp, successText = "Volume up"),
        CommandInfo("media-volume-down", "Volume down", "Lowers the volume one step.", CommandGroup.MEDIA, Icons.AutoMirrored.Outlined.VolumeDown, successText = "Volume down"),
        CommandInfo("media-set-volume", "Set volume", "Sets the volume from 0 to 10.", CommandGroup.MEDIA, Icons.AutoMirrored.Outlined.VolumeUp, successText = "Volume set"),

        // Security
        CommandInfo("sentry-mode", "Sentry Mode", "Watches the car's surroundings with the cameras while parked.", CommandGroup.SECURITY, Icons.Outlined.Security, successText = "Sentry Mode set"),
        CommandInfo("valet-mode-on", "Valet Mode on", "Limits speed and locks the glovebox and personal data.", CommandGroup.SECURITY, Icons.Outlined.Person, successText = "Valet Mode on"),
        CommandInfo("valet-mode-off", "Valet Mode off", "Leaves Valet Mode.", CommandGroup.SECURITY, Icons.Outlined.Person, successText = "Valet Mode off"),
        CommandInfo("valet-pin-reset", "Reset valet PIN", "Clears the Valet Mode PIN.", CommandGroup.SECURITY, Icons.Outlined.Person, risky = true, successText = "Valet PIN cleared"),
        CommandInfo("speed-limit-activate", "Speed Limit Mode on", "Turns on Speed Limit Mode with its PIN.", CommandGroup.SECURITY, Icons.Outlined.Speed, successText = "Speed Limit Mode on"),
        CommandInfo("speed-limit-deactivate", "Speed Limit Mode off", "Turns off Speed Limit Mode with its PIN.", CommandGroup.SECURITY, Icons.Outlined.Speed, successText = "Speed Limit Mode off"),
        CommandInfo("speed-limit-set", "Set speed limit", "Sets the Speed Limit Mode maximum (mph).", CommandGroup.SECURITY, Icons.Outlined.Speed, successText = "Speed limit set"),
        CommandInfo("speed-limit-clear-pin", "Clear speed limit PIN", "Clears the Speed Limit Mode PIN using the current PIN.", CommandGroup.SECURITY, Icons.Outlined.Speed, risky = true, successText = "Speed limit PIN cleared"),
        CommandInfo("speed-limit-clear-pin-admin", "Clear speed limit PIN (owner)", "Clears the Speed Limit Mode PIN without it. Needs an owner key.", CommandGroup.SECURITY, Icons.Outlined.Speed, risky = true, successText = "Speed limit PIN cleared"),
        CommandInfo("pin-to-drive-clear", "Turn off PIN to Drive", "Turns off PIN to Drive and clears its PIN. Needs an owner key.", CommandGroup.SECURITY, Icons.Outlined.Key, risky = true, successText = "PIN to Drive off"),
        CommandInfo("guest-mode-on", "Guest mode on", "Restricts the car for a guest driver.", CommandGroup.SECURITY, Icons.Outlined.Person, successText = "Guest mode on"),
        CommandInfo("guest-mode-off", "Guest mode off", "Leaves guest mode.", CommandGroup.SECURITY, Icons.Outlined.Person, successText = "Guest mode off"),
        CommandInfo("erase-guest-data", "Erase guest data", "Deletes data created in guest mode.", CommandGroup.SECURITY, Icons.Outlined.Delete, risky = true, successText = "Guest data erased"),
        CommandInfo("parental-controls-on", "Parental controls on", "Turns on parental controls with a PIN.", CommandGroup.SECURITY, Icons.Outlined.Shield, successText = "Parental controls on"),
        CommandInfo("parental-controls-off", "Parental controls off", "Turns off parental controls with the PIN.", CommandGroup.SECURITY, Icons.Outlined.Shield, successText = "Parental controls off"),
        CommandInfo("parental-controls-set-speed-limit", "Parental speed limit", "Sets the parental controls speed limit (mph).", CommandGroup.SECURITY, Icons.Outlined.Shield, successText = "Parental speed limit set"),
        CommandInfo("parental-controls-enable-setting", "Parental control setting", "Turns one parental controls rule on or off.", CommandGroup.SECURITY, Icons.Outlined.Shield, successText = "Parental setting changed"),
        CommandInfo("parental-controls-clear-pin-admin", "Clear parental PIN (owner)", "Clears the parental controls PIN. Needs an owner key.", CommandGroup.SECURITY, Icons.Outlined.Shield, risky = true, successText = "Parental PIN cleared"),

        // Software
        CommandInfo("software-update-start", "Install software update", "Installs a downloaded update after a delay. The car can't be driven while it installs.", CommandGroup.SOFTWARE, Icons.Outlined.SystemUpdate, risky = true, successText = "Update scheduled"),
        CommandInfo("software-update-cancel", "Cancel software update", "Cancels a scheduled installation.", CommandGroup.SOFTWARE, Icons.Outlined.SystemUpdate, successText = "Update cancelled"),

        // Data
        CommandInfo("state", "Read vehicle data", "Reads one category of data, such as charge or climate.", CommandGroup.DATA, Icons.Outlined.Info, successText = "Data read"),
    )

    private val byId = list.associateBy { it.id }

    val all: List<CommandInfo> get() = list

    fun get(id: String): CommandInfo = byId[id] ?: CommandInfo(id, id, "", CommandGroup.DATA, Icons.Outlined.Info)

    fun has(id: String): Boolean = id in byId

    /** Commands the official library offers that can't be sent over Bluetooth, with the reason. */
    val unavailableOverBluetooth: List<Pair<String, String>> = listOf(
        "Turn on PIN to Drive" to "Tesla's library only allows this over the internet. You can turn PIN to Drive off here.",
        "Rename a key" to "Key names are stored in your Tesla account, which needs the internet.",
        "Account and product information" to "These come from Tesla's servers, not the car.",
        "Nearby charging sites" to "The car doesn't return this list over Bluetooth.",
    )
}
