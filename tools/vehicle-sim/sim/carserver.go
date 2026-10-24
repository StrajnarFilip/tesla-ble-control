package main

import (
	"google.golang.org/protobuf/proto"

	carserver "github.com/teslamotors/vehicle-command/pkg/protocol/protobuf/carserver"
)

var void = &carserver.Void{}

func ok() *carserver.Response {
	return &carserver.Response{ActionStatus: &carserver.ActionStatus{Result: carserver.OperationStatus_E_OPERATIONSTATUS_OK}}
}

func refused(reason string) *carserver.Response {
	return &carserver.Response{ActionStatus: &carserver.ActionStatus{
		Result:       carserver.OperationStatus_E_OPERATIONSTATUS_ERROR,
		ResultReason: &carserver.ResultReason{Reason: &carserver.ResultReason_PlainText{PlainText: reason}},
	}}
}

// handleCarServer applies an infotainment action to the simulated state.
func (v *Vehicle) handleCarServer(plaintext []byte) []byte {
	var action carserver.Action
	if err := proto.Unmarshal(plaintext, &action); err != nil {
		return marshal(refused("decoding error"))
	}
	response := ok()
	switch a := action.GetVehicleAction().GetVehicleActionMsg().(type) {
	case *carserver.VehicleAction_GetVehicleData:
		response.ResponseMsg = &carserver.Response_VehicleData{VehicleData: v.vehicleData(a.GetVehicleData)}
	case *carserver.VehicleAction_HvacAutoAction:
		v.climateOn = a.HvacAutoAction.GetPowerOn()
	case *carserver.VehicleAction_HvacTemperatureAdjustmentAction:
		v.driverTemp = a.HvacTemperatureAdjustmentAction.GetDriverTempCelsius()
		v.passTemp = a.HvacTemperatureAdjustmentAction.GetPassengerTempCelsius()
	case *carserver.VehicleAction_HvacSteeringWheelHeaterAction:
		v.steering = a.HvacSteeringWheelHeaterAction.GetPowerOn()
	case *carserver.VehicleAction_HvacSeatHeaterActions:
		for _, heater := range a.HvacSeatHeaterActions.GetHvacSeatHeaterAction() {
			level := int32(0)
			switch heater.GetSeatHeaterLevel().(type) {
			case *carserver.HvacSeatHeaterActions_HvacSeatHeaterAction_SEAT_HEATER_LOW:
				level = 1
			case *carserver.HvacSeatHeaterActions_HvacSeatHeaterAction_SEAT_HEATER_MED:
				level = 2
			case *carserver.HvacSeatHeaterActions_HvacSeatHeaterAction_SEAT_HEATER_HIGH:
				level = 3
			}
			switch heater.GetSeatPosition().(type) {
			case *carserver.HvacSeatHeaterActions_HvacSeatHeaterAction_CAR_SEAT_FRONT_LEFT:
				v.seatLeft = level
			case *carserver.HvacSeatHeaterActions_HvacSeatHeaterAction_CAR_SEAT_FRONT_RIGHT:
				v.seatRight = level
			}
		}
	case *carserver.VehicleAction_ChargingSetLimitAction:
		percent := a.ChargingSetLimitAction.GetPercent()
		if percent < 50 || percent > 100 {
			return marshal(refused("invalid charge limit"))
		}
		v.limit = percent
	case *carserver.VehicleAction_SetChargingAmpsAction:
		v.amps = a.SetChargingAmpsAction.GetChargingAmps()
	case *carserver.VehicleAction_ChargingStartStopAction:
		switch a.ChargingStartStopAction.GetChargingAction().(type) {
		case *carserver.ChargingStartStopAction_Stop:
			v.charging = false
		default:
			if !v.portOpen {
				return marshal(refused("cable not connected"))
			}
			v.charging = true
		}
	case *carserver.VehicleAction_ChargePortDoorOpen:
		v.portOpen = true
	case *carserver.VehicleAction_ChargePortDoorClose:
		v.portOpen = false
	case *carserver.VehicleAction_VehicleControlWindowAction:
		_, vent := a.VehicleControlWindowAction.GetAction().(*carserver.VehicleControlWindowAction_Vent)
		v.windowsOpen = vent
	case *carserver.VehicleAction_VehicleControlSetSentryModeAction:
		v.sentry = a.VehicleControlSetSentryModeAction.GetOn()
	case *carserver.VehicleAction_VehicleControlSetValetModeAction:
		on := a.VehicleControlSetValetModeAction.GetOn()
		if !on && !v.valet {
			return marshal(refused("already off"))
		}
		v.valet = on
	case *carserver.VehicleAction_MediaUpdateVolume:
		switch volume := a.MediaUpdateVolume.GetMediaVolume().(type) {
		case *carserver.MediaUpdateVolume_VolumeAbsoluteFloat:
			v.volume = volume.VolumeAbsoluteFloat
		case *carserver.MediaUpdateVolume_VolumeDelta:
			if volume.VolumeDelta > 0 {
				v.volume++
			} else {
				v.volume--
			}
		}
	case *carserver.VehicleAction_Ping:
		response.ResponseMsg = &carserver.Response_Ping{Ping: &carserver.Ping{PingId: a.Ping.GetPingId()}}
	case nil:
		return marshal(refused("missing action"))
	}
	return marshal(response)
}

func (v *Vehicle) vehicleData(get *carserver.GetVehicleData) *carserver.VehicleData {
	data := &carserver.VehicleData{}
	if get.GetGetChargeState() != nil {
		state := &carserver.ChargeState_ChargingState{Type: &carserver.ChargeState_ChargingState_Stopped{Stopped: void}}
		if v.charging {
			state = &carserver.ChargeState_ChargingState{Type: &carserver.ChargeState_ChargingState_Charging{Charging: void}}
		}
		data.ChargeState = &carserver.ChargeState{
			ChargingState:                   state,
			OptionalBatteryLevel:            &carserver.ChargeState_BatteryLevel{BatteryLevel: 78},
			OptionalBatteryRange:            &carserver.ChargeState_BatteryRange{BatteryRange: 249.5},
			OptionalEstBatteryRange:         &carserver.ChargeState_EstBatteryRange{EstBatteryRange: 231.0},
			OptionalChargeLimitSoc:          &carserver.ChargeState_ChargeLimitSoc{ChargeLimitSoc: v.limit},
			OptionalChargeLimitSocMin:       &carserver.ChargeState_ChargeLimitSocMin{ChargeLimitSocMin: 50},
			OptionalChargeLimitSocMax:       &carserver.ChargeState_ChargeLimitSocMax{ChargeLimitSocMax: 100},
			OptionalChargingAmps:            &carserver.ChargeState_ChargingAmps{ChargingAmps: v.amps},
			OptionalChargeCurrentRequestMax: &carserver.ChargeState_ChargeCurrentRequestMax{ChargeCurrentRequestMax: 32},
			OptionalChargePortDoorOpen:      &carserver.ChargeState_ChargePortDoorOpen{ChargePortDoorOpen: v.portOpen},
			OptionalMinutesToFullCharge:     &carserver.ChargeState_MinutesToFullCharge{MinutesToFullCharge: 95},
		}
	}
	if get.GetGetClimateState() != nil {
		data.ClimateState = &carserver.ClimateState{
			OptionalInsideTempCelsius:    &carserver.ClimateState_InsideTempCelsius{InsideTempCelsius: 19.5},
			OptionalOutsideTempCelsius:   &carserver.ClimateState_OutsideTempCelsius{OutsideTempCelsius: 8},
			OptionalDriverTempSetting:    &carserver.ClimateState_DriverTempSetting{DriverTempSetting: v.driverTemp},
			OptionalPassengerTempSetting: &carserver.ClimateState_PassengerTempSetting{PassengerTempSetting: v.passTemp},
			OptionalIsClimateOn:          &carserver.ClimateState_IsClimateOn{IsClimateOn: v.climateOn},
			OptionalMinAvailTempCelsius:  &carserver.ClimateState_MinAvailTempCelsius{MinAvailTempCelsius: 15},
			OptionalMaxAvailTempCelsius:  &carserver.ClimateState_MaxAvailTempCelsius{MaxAvailTempCelsius: 28},
			OptionalSeatHeaterLeft:       &carserver.ClimateState_SeatHeaterLeft{SeatHeaterLeft: v.seatLeft},
			OptionalSeatHeaterRight:      &carserver.ClimateState_SeatHeaterRight{SeatHeaterRight: v.seatRight},
			OptionalSteeringWheelHeater:  &carserver.ClimateState_SteeringWheelHeater{SteeringWheelHeater: v.steering},
		}
	}
	if get.GetGetClosuresState() != nil {
		sentry := &carserver.ClosuresState_SentryModeState{Type: &carserver.ClosuresState_SentryModeState_Off{Off: void}}
		if v.sentry {
			sentry = &carserver.ClosuresState_SentryModeState{Type: &carserver.ClosuresState_SentryModeState_Armed{Armed: void}}
		}
		data.ClosuresState = &carserver.ClosuresState{
			OptionalLocked:              &carserver.ClosuresState_Locked{Locked: v.locked},
			OptionalDoorOpenDriverFront: &carserver.ClosuresState_DoorOpenDriverFront{DoorOpenDriverFront: false},
			OptionalDoorOpenTrunkFront:  &carserver.ClosuresState_DoorOpenTrunkFront{DoorOpenTrunkFront: v.frunkOpen},
			OptionalDoorOpenTrunkRear:   &carserver.ClosuresState_DoorOpenTrunkRear{DoorOpenTrunkRear: v.trunkOpen},
			OptionalWindowOpenDriverFront: &carserver.ClosuresState_WindowOpenDriverFront{
				WindowOpenDriverFront: v.windowsOpen,
			},
			OptionalValetMode:           &carserver.ClosuresState_ValetMode{ValetMode: v.valet},
			OptionalSentryModeAvailable: &carserver.ClosuresState_SentryModeAvailable{SentryModeAvailable: true},
			SentryModeState:             sentry,
		}
	}
	if get.GetGetDriveState() != nil {
		data.DriveState = &carserver.DriveState{
			ShiftState: &carserver.ShiftState{Type: &carserver.ShiftState_P{P: void}},
			OptionalOdometerInHundredthsOfAMile: &carserver.DriveState_OdometerInHundredthsOfAMile{
				OdometerInHundredthsOfAMile: 1234567,
			},
		}
	}
	if get.GetGetLocationState() != nil {
		data.LocationState = &carserver.LocationState{
			OptionalLatitude:  &carserver.LocationState_Latitude{Latitude: 46.0569},
			OptionalLongitude: &carserver.LocationState_Longitude{Longitude: 14.5058},
			OptionalHeading:   &carserver.LocationState_Heading{Heading: 270},
		}
	}
	if get.GetGetTirePressureState() != nil {
		data.TirePressureState = &carserver.TirePressureState{
			OptionalTpmsPressureFl: &carserver.TirePressureState_TpmsPressureFl{TpmsPressureFl: 2.9},
			OptionalTpmsPressureFr: &carserver.TirePressureState_TpmsPressureFr{TpmsPressureFr: 2.9},
			OptionalTpmsPressureRl: &carserver.TirePressureState_TpmsPressureRl{TpmsPressureRl: 2.8},
			OptionalTpmsPressureRr: &carserver.TirePressureState_TpmsPressureRr{TpmsPressureRr: 2.85},
		}
	}
	if get.GetGetMediaState() != nil {
		data.MediaState = &carserver.MediaState{
			OptionalNowPlayingTitle:  &carserver.MediaState_NowPlayingTitle{NowPlayingTitle: "Simulated Song"},
			OptionalNowPlayingArtist: &carserver.MediaState_NowPlayingArtist{NowPlayingArtist: "Test Artist"},
			OptionalAudioVolume:      &carserver.MediaState_AudioVolume{AudioVolume: v.volume},
			OptionalAudioVolumeMax:   &carserver.MediaState_AudioVolumeMax{AudioVolumeMax: 10.333},
		}
	}
	if get.GetGetMediaDetailState() != nil {
		data.MediaDetailState = &carserver.MediaDetailState{}
	}
	if get.GetGetSoftwareUpdateState() != nil {
		data.SoftwareUpdateState = &carserver.SoftwareUpdateState{
			Status: &carserver.SoftwareUpdateState_SoftwareUpdateStatus{
				Type: &carserver.SoftwareUpdateState_SoftwareUpdateStatus_Available{Available: void},
			},
			OptionalVersion: &carserver.SoftwareUpdateState_Version{Version: "2026.38.4"},
		}
	}
	if get.GetGetParentalControlsState() != nil {
		data.ParentalControlsState = &carserver.ParentalControlsState{
			OptionalParentalControlsActive: &carserver.ParentalControlsState_ParentalControlsActive{ParentalControlsActive: false},
		}
	}
	if get.GetGetChargeScheduleState() != nil {
		data.ChargeScheduleState = &carserver.ChargeScheduleState{}
	}
	if get.GetGetPreconditioningScheduleState() != nil {
		data.PreconditioningScheduleState = &carserver.PreconditioningScheduleState{}
	}
	return data
}
