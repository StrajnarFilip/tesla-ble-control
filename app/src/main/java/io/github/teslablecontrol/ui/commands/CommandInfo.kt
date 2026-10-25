package io.github.teslablecontrol.ui.commands

import androidx.annotation.StringRes
import android.content.res.Resources
import io.github.teslablecontrol.R
import io.github.teslablecontrol.data.CarModel

enum class CommandGroup(@StringRes val title: Int) {
    ACCESS(R.string.group_access),
    CLIMATE(R.string.climate_title),
    CHARGING(R.string.charging_state_charging),
    BODY(R.string.group_body),
    MEDIA(R.string.controls_media),
    SECURITY(R.string.group_security),
    KEYS(R.string.keys_title),
    SOFTWARE(R.string.details_software),
    DATA(R.string.group_data),
}

/** How a command is presented: words for buttons and TalkBack, its group, and whether to confirm first. */
data class CommandInfo(
    val id: String,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val group: CommandGroup,
    /** Ask before sending (when confirmations are on). */
    val risky: Boolean = false,
    /** Shown and announced after success. */
    @StringRes val successText: Int = R.string.action_done,
    /** Only some models have this (null = all). */
    val models: Set<CarModel>? = null,
)

/** Human labels for command parameters, by parameter name. */
object ParamLabels {
    private val labels = mapOf(
        "STATE" to R.string.param_state,
        "TEMP" to R.string.climate_temperature,
        "PASSENGER_TEMP" to R.string.param_passenger_temp,
        "SEAT" to R.string.param_seat,
        "LEVEL" to R.string.param_level,
        "POSITIONS" to R.string.param_positions,
        "OVERRIDE" to R.string.param_override,
        "FAN_ONLY" to R.string.param_fan_only,
        "MODE" to R.string.param_mode,
        "PERCENT" to R.string.param_percent,
        "AMPS" to R.string.param_amps,
        "TIME" to R.string.param_time,
        "DEPART_AT" to R.string.param_depart_at,
        "OFF_PEAK_END" to R.string.param_off_peak_end,
        "PRECONDITIONING" to R.string.param_preconditioning,
        "OFF_PEAK" to R.string.param_off_peak,
        "DAYS" to R.string.param_days,
        "LATITUDE" to R.string.param_latitude,
        "LONGITUDE" to R.string.param_longitude,
        "REPEAT" to R.string.param_repeat,
        "ID" to R.string.param_id,
        "ENABLED" to R.string.param_enabled,
        "TYPE" to R.string.param_type,
        "VOLUME" to R.string.param_volume,
        "PIN" to R.string.param_pin,
        "MPH" to R.string.param_mph,
        "SETTING" to R.string.param_setting,
        "DELAY" to R.string.param_delay,
        "CATEGORY" to R.string.param_category,
        "NAME" to R.string.param_name,
        "PUBLIC_KEY" to R.string.param_public_key,
        "ROLE" to R.string.param_role,
        "FORM_FACTOR" to R.string.param_form_factor,
        "DOMAIN" to R.string.param_domain,
    )

    fun label(resources: Resources, name: String): String = labels[name]?.let(resources::getString)
        ?: name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

    fun has(name: String): Boolean = name in labels
}

object CommandInfos {
    private val list = listOf(
        // Access
        CommandInfo("lock", R.string.action_lock, R.string.command_lock_description, CommandGroup.ACCESS, successText = R.string.command_lock_success),
        CommandInfo("unlock", R.string.action_unlock, R.string.command_unlock_description, CommandGroup.ACCESS, risky = true, successText = R.string.command_unlock_success),
        CommandInfo("wake", R.string.action_wake, R.string.command_wake_description, CommandGroup.ACCESS, successText = R.string.command_wake_success),
        CommandInfo("drive", R.string.controls_remote_start, R.string.command_drive_description, CommandGroup.ACCESS, risky = true, successText = R.string.command_drive_success),
        CommandInfo("autosecure-modelx", R.string.controls_autosecure, R.string.command_autosecure_modelx_description, CommandGroup.ACCESS, successText = R.string.command_autosecure_modelx_success, models = setOf(CarModel.MODEL_X)),
        CommandInfo("frunk-open", R.string.command_frunk_open_title, R.string.command_frunk_open_description, CommandGroup.ACCESS, risky = true, successText = R.string.command_frunk_open_success),
        CommandInfo("trunk-open", R.string.command_trunk_open_title, R.string.command_trunk_open_description, CommandGroup.ACCESS, risky = true, successText = R.string.command_trunk_open_success),
        CommandInfo("trunk-close", R.string.command_trunk_close_title, R.string.command_trunk_close_description, CommandGroup.ACCESS, successText = R.string.command_trunk_close_success),
        CommandInfo("trunk-move", R.string.command_trunk_move_title, R.string.command_trunk_move_description, CommandGroup.ACCESS, risky = true, successText = R.string.command_trunk_move_success),
        CommandInfo("tonneau-open", R.string.command_tonneau_open_title, R.string.command_tonneau_open_description, CommandGroup.ACCESS, risky = true, successText = R.string.command_tonneau_open_success, models = setOf(CarModel.CYBERTRUCK)),
        CommandInfo("tonneau-close", R.string.command_tonneau_close_title, R.string.command_tonneau_close_description, CommandGroup.ACCESS, successText = R.string.command_tonneau_close_success, models = setOf(CarModel.CYBERTRUCK)),
        CommandInfo("tonneau-stop", R.string.controls_tonneau_stop, R.string.command_tonneau_stop_description, CommandGroup.ACCESS, successText = R.string.command_tonneau_stop_success, models = setOf(CarModel.CYBERTRUCK)),
        CommandInfo("body-controller-state", R.string.command_body_controller_state_title, R.string.command_body_controller_state_description, CommandGroup.DATA, successText = R.string.command_body_controller_state_success),

        // Keys
        CommandInfo("list-keys", R.string.command_list_keys_title, R.string.command_list_keys_description, CommandGroup.KEYS, successText = R.string.command_list_keys_success),
        CommandInfo("add-key", R.string.keys_add, R.string.command_add_key_description, CommandGroup.KEYS, risky = true, successText = R.string.command_add_key_success),
        CommandInfo("add-key-request", R.string.command_add_key_request_title, R.string.command_add_key_request_description, CommandGroup.KEYS, successText = R.string.command_add_key_request_success),
        CommandInfo("remove-key", R.string.command_remove_key_title, R.string.command_remove_key_description, CommandGroup.KEYS, risky = true, successText = R.string.command_remove_key_success),
        CommandInfo("session-info", R.string.command_session_info_title, R.string.command_session_info_description, CommandGroup.KEYS, successText = R.string.command_session_info_success),

        // Climate
        CommandInfo("climate-on", R.string.command_climate_on_success, R.string.command_climate_on_description, CommandGroup.CLIMATE, successText = R.string.command_climate_on_success),
        CommandInfo("climate-off", R.string.command_climate_off_success, R.string.command_climate_off_description, CommandGroup.CLIMATE, successText = R.string.command_climate_off_success),
        CommandInfo("climate-set-temp", R.string.command_climate_set_temp_title, R.string.command_climate_set_temp_description, CommandGroup.CLIMATE, successText = R.string.command_climate_set_temp_success),
        CommandInfo("seat-heater", R.string.command_seat_heater_title, R.string.command_seat_heater_description, CommandGroup.CLIMATE, successText = R.string.command_seat_heater_success),
        CommandInfo("seat-cooler", R.string.climate_seat_cooling, R.string.command_seat_cooler_description, CommandGroup.CLIMATE, successText = R.string.command_seat_cooler_success),
        CommandInfo("steering-wheel-heater", R.string.climate_steering_wheel, R.string.command_steering_wheel_heater_description, CommandGroup.CLIMATE, successText = R.string.command_steering_wheel_heater_success),
        CommandInfo("auto-seat-and-climate", R.string.climate_auto_seat, R.string.command_auto_seat_and_climate_description, CommandGroup.CLIMATE, successText = R.string.command_auto_seat_and_climate_success),
        CommandInfo("preconditioning-max", R.string.command_preconditioning_max_title, R.string.command_preconditioning_max_description, CommandGroup.CLIMATE, successText = R.string.command_preconditioning_max_success),
        CommandInfo("bioweapon-mode", R.string.climate_bioweapon, R.string.command_bioweapon_mode_description, CommandGroup.CLIMATE, successText = R.string.command_bioweapon_mode_success),
        CommandInfo("cabin-overheat-protection", R.string.climate_cop, R.string.command_cabin_overheat_protection_description, CommandGroup.CLIMATE, successText = R.string.command_cabin_overheat_protection_success),
        CommandInfo("cabin-overheat-protection-temp", R.string.command_cabin_overheat_protection_temp_title, R.string.command_cabin_overheat_protection_temp_description, CommandGroup.CLIMATE, successText = R.string.command_cabin_overheat_protection_temp_success),
        CommandInfo("climate-keeper", R.string.command_climate_keeper_title, R.string.command_climate_keeper_description, CommandGroup.CLIMATE, successText = R.string.command_climate_keeper_success),

        // Charging
        CommandInfo("charging-start", R.string.charging_start, R.string.command_charging_start_description, CommandGroup.CHARGING, successText = R.string.command_charging_start_success),
        CommandInfo("charging-stop", R.string.charging_stop, R.string.command_charging_stop_description, CommandGroup.CHARGING, successText = R.string.command_charging_stop_success),
        CommandInfo("charge-max-range", R.string.command_charge_max_range_title, R.string.command_charge_max_range_description, CommandGroup.CHARGING, successText = R.string.command_charge_max_range_success),
        CommandInfo("charge-standard-range", R.string.command_charge_standard_range_title, R.string.command_charge_standard_range_description, CommandGroup.CHARGING, successText = R.string.command_charge_standard_range_success),
        CommandInfo("charging-set-limit", R.string.command_charging_set_limit_title, R.string.command_charging_set_limit_description, CommandGroup.CHARGING, successText = R.string.command_charging_set_limit_success),
        CommandInfo("charging-set-amps", R.string.command_charging_set_amps_title, R.string.command_charging_set_amps_description, CommandGroup.CHARGING, successText = R.string.command_charging_set_amps_success),
        CommandInfo("charge-port-open", R.string.command_charge_port_open_title, R.string.command_charge_port_open_description, CommandGroup.CHARGING, successText = R.string.command_charge_port_open_success),
        CommandInfo("charge-port-close", R.string.command_charge_port_close_title, R.string.command_charge_port_close_description, CommandGroup.CHARGING, successText = R.string.command_charge_port_close_success),
        CommandInfo("charging-schedule", R.string.charging_scheduled, R.string.command_charging_schedule_description, CommandGroup.CHARGING, successText = R.string.command_charging_schedule_success),
        CommandInfo("charging-schedule-cancel", R.string.command_charging_schedule_cancel_title, R.string.command_charging_schedule_cancel_description, CommandGroup.CHARGING, successText = R.string.command_charging_schedule_cancel_success),
        CommandInfo("scheduled-departure", R.string.charging_departure, R.string.command_scheduled_departure_description, CommandGroup.CHARGING, successText = R.string.command_scheduled_departure_success),
        CommandInfo("scheduled-departure-clear", R.string.command_scheduled_departure_clear_title, R.string.command_scheduled_departure_clear_description, CommandGroup.CHARGING, successText = R.string.command_scheduled_departure_clear_success),
        CommandInfo("charging-schedule-add", R.string.charging_add_schedule, R.string.command_charging_schedule_add_description, CommandGroup.CHARGING, successText = R.string.command_charging_schedule_add_success),
        CommandInfo("charging-schedule-remove", R.string.command_charging_schedule_remove_title, R.string.command_charging_schedule_remove_description, CommandGroup.CHARGING, risky = true, successText = R.string.command_charging_schedule_remove_success),
        CommandInfo("precondition-schedule-add", R.string.command_precondition_schedule_add_title, R.string.command_precondition_schedule_add_description, CommandGroup.CLIMATE, successText = R.string.command_precondition_schedule_add_success),
        CommandInfo("precondition-schedule-remove", R.string.command_precondition_schedule_remove_title, R.string.command_precondition_schedule_remove_description, CommandGroup.CLIMATE, risky = true, successText = R.string.command_precondition_schedule_remove_success),
        CommandInfo("low-power-mode", R.string.charging_low_power, R.string.command_low_power_mode_description, CommandGroup.CHARGING, successText = R.string.command_low_power_mode_success),
        CommandInfo("keep-accessory-power", R.string.command_keep_accessory_power_title, R.string.command_keep_accessory_power_description, CommandGroup.CHARGING, successText = R.string.command_keep_accessory_power_success),

        // Body and other
        CommandInfo("windows-vent", R.string.command_windows_vent_title, R.string.command_windows_vent_description, CommandGroup.BODY, risky = true, successText = R.string.command_windows_vent_success),
        CommandInfo("windows-close", R.string.command_windows_close_title, R.string.command_windows_close_description, CommandGroup.BODY, successText = R.string.command_windows_close_success),
        CommandInfo("sunroof", R.string.command_sunroof_title, R.string.command_sunroof_description, CommandGroup.BODY, successText = R.string.command_sunroof_success, models = setOf(CarModel.MODEL_S, CarModel.MODEL_X)),
        CommandInfo("homelink", R.string.controls_homelink, R.string.command_homelink_description, CommandGroup.BODY, risky = true, successText = R.string.command_homelink_success),
        CommandInfo("honk", R.string.home_honk, R.string.command_honk_description, CommandGroup.BODY, successText = R.string.command_honk_success),
        CommandInfo("flash-lights", R.string.home_flash, R.string.command_flash_lights_description, CommandGroup.BODY, successText = R.string.command_flash_lights_success),
        CommandInfo("ping", R.string.controls_ping_short, R.string.command_ping_description, CommandGroup.BODY, successText = R.string.command_ping_success),
        CommandInfo("set-vehicle-name", R.string.controls_rename, R.string.command_set_vehicle_name_description, CommandGroup.BODY, successText = R.string.command_set_vehicle_name_success),

        // Media
        CommandInfo("media-toggle-playback", R.string.media_play_pause, R.string.command_media_toggle_playback_description, CommandGroup.MEDIA, successText = R.string.command_media_toggle_playback_success),
        CommandInfo("media-next-track", R.string.command_media_next_track_success, R.string.command_media_next_track_description, CommandGroup.MEDIA, successText = R.string.command_media_next_track_success),
        CommandInfo("media-previous-track", R.string.command_media_previous_track_success, R.string.command_media_previous_track_description, CommandGroup.MEDIA, successText = R.string.command_media_previous_track_success),
        CommandInfo("media-next-favorite", R.string.media_next_favourite, R.string.command_media_next_favorite_description, CommandGroup.MEDIA, successText = R.string.media_next_favourite),
        CommandInfo("media-previous-favorite", R.string.media_previous_favourite, R.string.command_media_previous_favorite_description, CommandGroup.MEDIA, successText = R.string.media_previous_favourite),
        CommandInfo("media-volume-up", R.string.command_media_volume_up_success, R.string.command_media_volume_up_description, CommandGroup.MEDIA, successText = R.string.command_media_volume_up_success),
        CommandInfo("media-volume-down", R.string.command_media_volume_down_success, R.string.command_media_volume_down_description, CommandGroup.MEDIA, successText = R.string.command_media_volume_down_success),
        CommandInfo("media-set-volume", R.string.command_media_set_volume_title, R.string.command_media_set_volume_description, CommandGroup.MEDIA, successText = R.string.command_media_set_volume_success),

        // Security
        CommandInfo("sentry-mode", R.string.security_sentry, R.string.command_sentry_mode_description, CommandGroup.SECURITY, successText = R.string.command_sentry_mode_success),
        CommandInfo("valet-mode-on", R.string.command_valet_mode_on_success, R.string.command_valet_mode_on_description, CommandGroup.SECURITY, successText = R.string.command_valet_mode_on_success),
        CommandInfo("valet-mode-off", R.string.command_valet_mode_off_success, R.string.command_valet_mode_off_description, CommandGroup.SECURITY, successText = R.string.command_valet_mode_off_success),
        CommandInfo("valet-pin-reset", R.string.command_valet_pin_reset_title, R.string.command_valet_pin_reset_description, CommandGroup.SECURITY, risky = true, successText = R.string.command_valet_pin_reset_success),
        CommandInfo("speed-limit-activate", R.string.command_speed_limit_activate_success, R.string.command_speed_limit_activate_description, CommandGroup.SECURITY, successText = R.string.command_speed_limit_activate_success),
        CommandInfo("speed-limit-deactivate", R.string.command_speed_limit_deactivate_success, R.string.command_speed_limit_deactivate_description, CommandGroup.SECURITY, successText = R.string.command_speed_limit_deactivate_success),
        CommandInfo("speed-limit-set", R.string.command_speed_limit_set_title, R.string.command_speed_limit_set_description, CommandGroup.SECURITY, successText = R.string.command_speed_limit_set_success),
        CommandInfo("speed-limit-clear-pin", R.string.command_speed_limit_clear_pin_title, R.string.command_speed_limit_clear_pin_description, CommandGroup.SECURITY, risky = true, successText = R.string.command_speed_limit_clear_pin_success),
        CommandInfo("speed-limit-clear-pin-admin", R.string.command_speed_limit_clear_pin_admin_title, R.string.command_speed_limit_clear_pin_admin_description, CommandGroup.SECURITY, risky = true, successText = R.string.command_speed_limit_clear_pin_success),
        CommandInfo("pin-to-drive-clear", R.string.security_pin_to_drive_off, R.string.command_pin_to_drive_clear_description, CommandGroup.SECURITY, risky = true, successText = R.string.command_pin_to_drive_clear_success),
        CommandInfo("guest-mode-on", R.string.command_guest_mode_on_success, R.string.command_guest_mode_on_description, CommandGroup.SECURITY, successText = R.string.command_guest_mode_on_success),
        CommandInfo("guest-mode-off", R.string.command_guest_mode_off_success, R.string.command_guest_mode_off_description, CommandGroup.SECURITY, successText = R.string.command_guest_mode_off_success),
        CommandInfo("erase-guest-data", R.string.security_erase_guest, R.string.command_erase_guest_data_description, CommandGroup.SECURITY, risky = true, successText = R.string.command_erase_guest_data_success),
        CommandInfo("parental-controls-on", R.string.command_parental_controls_on_success, R.string.command_parental_controls_on_description, CommandGroup.SECURITY, successText = R.string.command_parental_controls_on_success),
        CommandInfo("parental-controls-off", R.string.command_parental_controls_off_success, R.string.command_parental_controls_off_description, CommandGroup.SECURITY, successText = R.string.command_parental_controls_off_success),
        CommandInfo("parental-controls-set-speed-limit", R.string.security_parental_speed, R.string.command_parental_controls_set_speed_limit_description, CommandGroup.SECURITY, successText = R.string.command_parental_controls_set_speed_limit_success),
        CommandInfo("parental-controls-enable-setting", R.string.command_parental_controls_enable_setting_title, R.string.command_parental_controls_enable_setting_description, CommandGroup.SECURITY, successText = R.string.command_parental_controls_enable_setting_success),
        CommandInfo("parental-controls-clear-pin-admin", R.string.command_parental_controls_clear_pin_admin_title, R.string.command_parental_controls_clear_pin_admin_description, CommandGroup.SECURITY, risky = true, successText = R.string.command_parental_controls_clear_pin_admin_success),

        // Software
        CommandInfo("software-update-start", R.string.command_software_update_start_title, R.string.command_software_update_start_description, CommandGroup.SOFTWARE, risky = true, successText = R.string.command_software_update_start_success),
        CommandInfo("software-update-cancel", R.string.command_software_update_cancel_title, R.string.command_software_update_cancel_description, CommandGroup.SOFTWARE, successText = R.string.command_software_update_cancel_success),

        // Data
        CommandInfo("state", R.string.command_state_title, R.string.command_state_description, CommandGroup.DATA, successText = R.string.command_state_success),
    )

    private val byId = list.associateBy { it.id }

    val all: List<CommandInfo> get() = list

    fun get(id: String): CommandInfo = byId[id] ?: CommandInfo(id, R.string.commands_unknown, R.string.commands_unknown, CommandGroup.DATA)

    fun has(id: String): Boolean = id in byId

    /** Commands the official library offers that can't be sent over Bluetooth, with the reason. */
    val unavailableOverBluetooth: List<Pair<Int, Int>> = listOf(
        R.string.label_turn_on_pin_to_drive to R.string.unavailable_pin_to_drive_reason,
        R.string.label_rename_a_key to R.string.unavailable_rename_key_reason,
        R.string.label_account_and_product_information to R.string.unavailable_account_reason,
        R.string.label_nearby_charging_sites to R.string.unavailable_charging_sites_reason,
    )
}
