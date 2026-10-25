package io.github.teslablecontrol.ui.commands

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

object CommandInfos {
    private val list = listOf(
        // Access
        CommandInfo("lock", "Lock", "Locks all doors.", CommandGroup.ACCESS, successText = "Car locked"),
        CommandInfo("unlock", "Unlock", "Unlocks all doors.", CommandGroup.ACCESS, risky = true, successText = "Car unlocked"),
        CommandInfo("wake", "Wake car", "Wakes the car so it can answer climate, charging and media commands.", CommandGroup.ACCESS, successText = "Car is waking up"),
        CommandInfo("drive", "Remote start", "Allows driving without a key for a short time.", CommandGroup.ACCESS, risky = true, successText = "Remote start enabled"),
        CommandInfo("autosecure-modelx", "Close doors and lock", "Closes the falcon-wing doors and locks.", CommandGroup.ACCESS, successText = "Closing and locking", models = setOf(CarModel.MODEL_X)),
        CommandInfo("frunk-open", "Open frunk", "Opens the front trunk. It must be closed by hand.", CommandGroup.ACCESS, risky = true, successText = "Frunk opened"),
        CommandInfo("trunk-open", "Open trunk", "Opens the rear trunk.", CommandGroup.ACCESS, risky = true, successText = "Trunk opened"),
        CommandInfo("trunk-close", "Close trunk", "Closes the rear trunk (powered liftgates only).", CommandGroup.ACCESS, successText = "Trunk closing"),
        CommandInfo("trunk-move", "Open or close trunk", "Toggles the rear trunk.", CommandGroup.ACCESS, risky = true, successText = "Trunk moving"),
        CommandInfo("tonneau-open", "Open tonneau", "Opens the bed cover.", CommandGroup.ACCESS, risky = true, successText = "Tonneau opening", models = setOf(CarModel.CYBERTRUCK)),
        CommandInfo("tonneau-close", "Close tonneau", "Closes the bed cover.", CommandGroup.ACCESS, successText = "Tonneau closing", models = setOf(CarModel.CYBERTRUCK)),
        CommandInfo("tonneau-stop", "Stop tonneau", "Stops the bed cover where it is.", CommandGroup.ACCESS, successText = "Tonneau stopped", models = setOf(CarModel.CYBERTRUCK)),
        CommandInfo("body-controller-state", "Read locks and doors", "Reads locks, doors and sleep state without waking the car.", CommandGroup.DATA, successText = "Status updated"),

        // Keys
        CommandInfo("list-keys", "List keys", "Shows every key that can unlock the car.", CommandGroup.KEYS, successText = "Keys loaded"),
        CommandInfo("add-key", "Add a key", "Gives another phone or card access. Needs an owner key.", CommandGroup.KEYS, risky = true, successText = "Key added"),
        CommandInfo("add-key-request", "Request pairing", "Asks the car to add a key; approve it with a key card in the car.", CommandGroup.KEYS, successText = "Request sent. Tap your key card in the car."),
        CommandInfo("remove-key", "Remove a key", "Takes away a key's access. Needs an owner key.", CommandGroup.KEYS, risky = true, successText = "Key removed"),
        CommandInfo("session-info", "Check a key", "Asks the car whether a key is enrolled.", CommandGroup.KEYS, successText = "The key is enrolled"),

        // Climate
        CommandInfo("climate-on", "Climate on", "Starts heating or cooling the cabin.", CommandGroup.CLIMATE, successText = "Climate on"),
        CommandInfo("climate-off", "Climate off", "Stops climate control.", CommandGroup.CLIMATE, successText = "Climate off"),
        CommandInfo("climate-set-temp", "Set temperature", "Sets the cabin temperature.", CommandGroup.CLIMATE, successText = "Temperature set"),
        CommandInfo("seat-heater", "Seat heater", "Sets one seat's heater level.", CommandGroup.CLIMATE, successText = "Seat heater set"),
        CommandInfo("seat-cooler", "Seat cooling", "Sets a front seat's ventilation level (where fitted).", CommandGroup.CLIMATE, successText = "Seat cooling set"),
        CommandInfo("steering-wheel-heater", "Steering wheel heater", "Turns the steering wheel heater on or off.", CommandGroup.CLIMATE, successText = "Steering wheel heater set"),
        CommandInfo("auto-seat-and-climate", "Automatic seat climate", "Lets the car manage front seat heating with climate.", CommandGroup.CLIMATE, successText = "Automatic seat climate set"),
        CommandInfo("preconditioning-max", "Defrost", "Maximum preconditioning to clear ice and fog.", CommandGroup.CLIMATE, successText = "Defrost set"),
        CommandInfo("bioweapon-mode", "Bioweapon Defense Mode", "HEPA filtration at maximum (cars with a HEPA filter).", CommandGroup.CLIMATE, successText = "Bioweapon Defense Mode set"),
        CommandInfo("cabin-overheat-protection", "Cabin overheat protection", "Keeps the parked cabin from getting too hot.", CommandGroup.CLIMATE, successText = "Overheat protection set"),
        CommandInfo("cabin-overheat-protection-temp", "Overheat protection temperature", "Sets when overheat protection starts.", CommandGroup.CLIMATE, successText = "Activation temperature set"),
        CommandInfo("climate-keeper", "Keep climate", "Keeps climate on when you leave: Keep, Dog or Camp mode.", CommandGroup.CLIMATE, successText = "Climate keeper set"),

        // Charging
        CommandInfo("charging-start", "Start charging", "Starts charging if a cable is connected.", CommandGroup.CHARGING, successText = "Charging started"),
        CommandInfo("charging-stop", "Stop charging", "Stops charging.", CommandGroup.CHARGING, successText = "Charging stopped"),
        CommandInfo("charge-max-range", "Charge to maximum", "Starts a charge to 100% for a long trip.", CommandGroup.CHARGING, successText = "Charging to maximum"),
        CommandInfo("charge-standard-range", "Charge to standard", "Starts a charge to the everyday limit.", CommandGroup.CHARGING, successText = "Charging to standard"),
        CommandInfo("charging-set-limit", "Set charge limit", "Sets how full the battery charges.", CommandGroup.CHARGING, successText = "Charge limit set"),
        CommandInfo("charging-set-amps", "Set charging current", "Sets the charging current in amps.", CommandGroup.CHARGING, successText = "Charging current set"),
        CommandInfo("charge-port-open", "Open charge port", "Opens the charge port door.", CommandGroup.CHARGING, successText = "Charge port opened"),
        CommandInfo("charge-port-close", "Close charge port", "Closes the charge port door.", CommandGroup.CHARGING, successText = "Charge port closed"),
        CommandInfo("charging-schedule", "Scheduled charging", "Starts charging at a set time every day.", CommandGroup.CHARGING, successText = "Charging scheduled"),
        CommandInfo("charging-schedule-cancel", "Cancel scheduled charging", "Turns off the daily charging start time.", CommandGroup.CHARGING, successText = "Scheduled charging off"),
        CommandInfo("scheduled-departure", "Scheduled departure", "Charges and preconditions to be ready at a departure time.", CommandGroup.CHARGING, successText = "Departure scheduled"),
        CommandInfo("scheduled-departure-clear", "Cancel scheduled departure", "Turns off scheduled departure.", CommandGroup.CHARGING, successText = "Scheduled departure off"),
        CommandInfo("charging-schedule-add", "Add charge schedule", "Adds a charging window for chosen days and place.", CommandGroup.CHARGING, successText = "Charge schedule added"),
        CommandInfo("charging-schedule-remove", "Remove charge schedules", "Removes charge schedules by place or ID.", CommandGroup.CHARGING, risky = true, successText = "Charge schedules removed"),
        CommandInfo("precondition-schedule-add", "Add precondition schedule", "Warms or cools the cabin by a time on chosen days.", CommandGroup.CLIMATE, successText = "Precondition schedule added"),
        CommandInfo("precondition-schedule-remove", "Remove precondition schedules", "Removes precondition schedules by place or ID.", CommandGroup.CLIMATE, risky = true, successText = "Precondition schedules removed"),
        CommandInfo("low-power-mode", "Low power mode", "Reduces the energy the car uses while parked.", CommandGroup.CHARGING, successText = "Low power mode set"),
        CommandInfo("keep-accessory-power", "Keep accessory power", "Keeps USB and 12 V outlets powered after you leave.", CommandGroup.CHARGING, successText = "Accessory power set"),

        // Body and other
        CommandInfo("windows-vent", "Vent windows", "Lowers all windows slightly.", CommandGroup.BODY, risky = true, successText = "Windows vented"),
        CommandInfo("windows-close", "Close windows", "Closes all windows.", CommandGroup.BODY, successText = "Windows closing"),
        CommandInfo("sunroof", "Sunroof position", "Sets the sunroof opening (0 closed – 100 open; older Model S/X).", CommandGroup.BODY, successText = "Sunroof moving", models = setOf(CarModel.MODEL_S, CarModel.MODEL_X)),
        CommandInfo("homelink", "HomeLink", "Triggers your garage door opener at the car's location.", CommandGroup.BODY, risky = true, successText = "HomeLink triggered"),
        CommandInfo("honk", "Honk", "Sounds the horn once.", CommandGroup.BODY, successText = "Honked"),
        CommandInfo("flash-lights", "Flash lights", "Flashes the headlights.", CommandGroup.BODY, successText = "Lights flashed"),
        CommandInfo("ping", "Test connection", "Sends a harmless message to check the secure connection.", CommandGroup.BODY, successText = "The car answered"),
        CommandInfo("set-vehicle-name", "Rename car", "Changes the name shown on the car's screen.", CommandGroup.BODY, successText = "Car renamed"),

        // Media
        CommandInfo("media-toggle-playback", "Play or pause", "Toggles media playback.", CommandGroup.MEDIA, successText = "Playback toggled"),
        CommandInfo("media-next-track", "Next track", "Skips to the next track.", CommandGroup.MEDIA, successText = "Next track"),
        CommandInfo("media-previous-track", "Previous track", "Goes back one track.", CommandGroup.MEDIA, successText = "Previous track"),
        CommandInfo("media-next-favorite", "Next favourite", "Goes to the next favourite station or playlist.", CommandGroup.MEDIA, successText = "Next favourite"),
        CommandInfo("media-previous-favorite", "Previous favourite", "Goes back to the previous favourite.", CommandGroup.MEDIA, successText = "Previous favourite"),
        CommandInfo("media-volume-up", "Volume up", "Raises the volume one step.", CommandGroup.MEDIA, successText = "Volume up"),
        CommandInfo("media-volume-down", "Volume down", "Lowers the volume one step.", CommandGroup.MEDIA, successText = "Volume down"),
        CommandInfo("media-set-volume", "Set volume", "Sets the volume from 0 to 10.", CommandGroup.MEDIA, successText = "Volume set"),

        // Security
        CommandInfo("sentry-mode", "Sentry Mode", "Watches the car's surroundings with the cameras while parked.", CommandGroup.SECURITY, successText = "Sentry Mode set"),
        CommandInfo("valet-mode-on", "Valet Mode on", "Limits speed and locks the glovebox and personal data.", CommandGroup.SECURITY, successText = "Valet Mode on"),
        CommandInfo("valet-mode-off", "Valet Mode off", "Leaves Valet Mode.", CommandGroup.SECURITY, successText = "Valet Mode off"),
        CommandInfo("valet-pin-reset", "Reset valet PIN", "Clears the Valet Mode PIN.", CommandGroup.SECURITY, risky = true, successText = "Valet PIN cleared"),
        CommandInfo("speed-limit-activate", "Speed Limit Mode on", "Turns on Speed Limit Mode with its PIN.", CommandGroup.SECURITY, successText = "Speed Limit Mode on"),
        CommandInfo("speed-limit-deactivate", "Speed Limit Mode off", "Turns off Speed Limit Mode with its PIN.", CommandGroup.SECURITY, successText = "Speed Limit Mode off"),
        CommandInfo("speed-limit-set", "Set speed limit", "Sets the Speed Limit Mode maximum (mph).", CommandGroup.SECURITY, successText = "Speed limit set"),
        CommandInfo("speed-limit-clear-pin", "Clear speed limit PIN", "Clears the Speed Limit Mode PIN using the current PIN.", CommandGroup.SECURITY, risky = true, successText = "Speed limit PIN cleared"),
        CommandInfo("speed-limit-clear-pin-admin", "Clear speed limit PIN (owner)", "Clears the Speed Limit Mode PIN without it. Needs an owner key.", CommandGroup.SECURITY, risky = true, successText = "Speed limit PIN cleared"),
        CommandInfo("pin-to-drive-clear", "Turn off PIN to Drive", "Turns off PIN to Drive and clears its PIN. Needs an owner key.", CommandGroup.SECURITY, risky = true, successText = "PIN to Drive off"),
        CommandInfo("guest-mode-on", "Guest mode on", "Restricts the car for a guest driver.", CommandGroup.SECURITY, successText = "Guest mode on"),
        CommandInfo("guest-mode-off", "Guest mode off", "Leaves guest mode.", CommandGroup.SECURITY, successText = "Guest mode off"),
        CommandInfo("erase-guest-data", "Erase guest data", "Deletes data created in guest mode.", CommandGroup.SECURITY, risky = true, successText = "Guest data erased"),
        CommandInfo("parental-controls-on", "Parental controls on", "Turns on parental controls with a PIN.", CommandGroup.SECURITY, successText = "Parental controls on"),
        CommandInfo("parental-controls-off", "Parental controls off", "Turns off parental controls with the PIN.", CommandGroup.SECURITY, successText = "Parental controls off"),
        CommandInfo("parental-controls-set-speed-limit", "Parental speed limit", "Sets the parental controls speed limit (mph).", CommandGroup.SECURITY, successText = "Parental speed limit set"),
        CommandInfo("parental-controls-enable-setting", "Parental control setting", "Turns one parental controls rule on or off.", CommandGroup.SECURITY, successText = "Parental setting changed"),
        CommandInfo("parental-controls-clear-pin-admin", "Clear parental PIN (owner)", "Clears the parental controls PIN. Needs an owner key.", CommandGroup.SECURITY, risky = true, successText = "Parental PIN cleared"),

        // Software
        CommandInfo("software-update-start", "Install software update", "Installs a downloaded update after a delay. The car can't be driven while it installs.", CommandGroup.SOFTWARE, risky = true, successText = "Update scheduled"),
        CommandInfo("software-update-cancel", "Cancel software update", "Cancels a scheduled installation.", CommandGroup.SOFTWARE, successText = "Update cancelled"),

        // Data
        CommandInfo("state", "Read vehicle data", "Reads one category of data, such as charge or climate.", CommandGroup.DATA, successText = "Data read"),
    )

    private val byId = list.associateBy { it.id }

    val all: List<CommandInfo> get() = list

    fun get(id: String): CommandInfo = byId[id] ?: CommandInfo(id, id, "", CommandGroup.DATA)

    fun has(id: String): Boolean = id in byId

    /** Commands the official library offers that can't be sent over Bluetooth, with the reason. */
    val unavailableOverBluetooth: List<Pair<String, String>> = listOf(
        "Turn on PIN to Drive" to "Tesla's library only allows this over the internet. You can turn PIN to Drive off here.",
        "Rename a key" to "Bluetooth can't send a key name. Rename it on the car's touchscreen: Controls, then Locks, then Keys, then the pencil.",
        "Account and product information" to "These come from Tesla's servers, not the car.",
        "Nearby charging sites" to "The car doesn't return this list over Bluetooth.",
    )
}
