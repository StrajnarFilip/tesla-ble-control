package io.github.teslablecontrol.protocol.vehicle

import com.tesla.generated.carserver.server.CarServer

/** Vehicle state categories that can be fetched over BLE (`vehicle.StateCategory`). */
enum class StateCategory(val id: String) {
    CHARGE("charge"),
    CLIMATE("climate"),
    DRIVE("drive"),
    LOCATION("location"),
    CLOSURES("closures"),
    CHARGE_SCHEDULE("charge-schedule"),
    PRECONDITIONING_SCHEDULE("precondition-schedule"),
    TIRE_PRESSURE("tire-pressure"),
    MEDIA("media"),
    MEDIA_DETAIL("media-detail"),
    SOFTWARE_UPDATE("software-update"),
    PARENTAL_CONTROLS("parental-controls");

    fun request(): CarServer.GetVehicleData {
        val builder = CarServer.GetVehicleData.newBuilder()
        when (this) {
            CHARGE -> builder.setGetChargeState(CarServer.GetChargeState.getDefaultInstance())
            CLIMATE -> builder.setGetClimateState(CarServer.GetClimateState.getDefaultInstance())
            DRIVE -> builder.setGetDriveState(CarServer.GetDriveState.getDefaultInstance())
            LOCATION -> builder.setGetLocationState(CarServer.GetLocationState.getDefaultInstance())
            CLOSURES -> builder.setGetClosuresState(CarServer.GetClosuresState.getDefaultInstance())
            CHARGE_SCHEDULE -> builder.setGetChargeScheduleState(CarServer.GetChargeScheduleState.getDefaultInstance())
            PRECONDITIONING_SCHEDULE ->
                builder.setGetPreconditioningScheduleState(CarServer.GetPreconditioningScheduleState.getDefaultInstance())
            TIRE_PRESSURE -> builder.setGetTirePressureState(CarServer.GetTirePressureState.getDefaultInstance())
            MEDIA -> builder.setGetMediaState(CarServer.GetMediaState.getDefaultInstance())
            MEDIA_DETAIL -> builder.setGetMediaDetailState(CarServer.GetMediaDetailState.getDefaultInstance())
            SOFTWARE_UPDATE -> builder.setGetSoftwareUpdateState(CarServer.GetSoftwareUpdateState.getDefaultInstance())
            PARENTAL_CONTROLS -> builder.setGetParentalControlsState(CarServer.GetParentalControlsState.getDefaultInstance())
        }
        return builder.build()
    }

    companion object {
        fun fromId(id: String): StateCategory? = entries.firstOrNull { it.id == id.lowercase() }
    }
}
