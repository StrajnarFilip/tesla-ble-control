package main

import (
	"context"
	"crypto/ecdh"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"time"

	"github.com/teslamotors/vehicle-command/internal/authentication"
	"github.com/teslamotors/vehicle-command/pkg/connector"
	"github.com/teslamotors/vehicle-command/pkg/protocol/protobuf/keys"
	universal "github.com/teslamotors/vehicle-command/pkg/protocol/protobuf/universalmessage"
	"github.com/teslamotors/vehicle-command/pkg/protocol/protobuf/vcsec"
	"github.com/teslamotors/vehicle-command/pkg/vehicle"
)

const goldenVIN = "5YJ3E1EA1PF000001"

// pipeConnector links the official client to the simulator in-process.
type pipeConnector struct {
	vehicle *Vehicle
	inbox   chan []byte
}

func (p *pipeConnector) Receive() <-chan []byte { return p.inbox }

func (p *pipeConnector) Send(_ context.Context, buffer []byte) error {
	for _, reply := range p.vehicle.HandleFrame(buffer) {
		p.inbox <- reply
	}
	return nil
}

func (p *pipeConnector) VIN() string                                   { return goldenVIN }
func (p *pipeConnector) Close()                                        {}
func (p *pipeConnector) PreferredAuthMethod() connector.AuthMethod     { return connector.AuthMethodGCM }
func (p *pipeConnector) RetryInterval() time.Duration                  { return 100 * time.Millisecond }
func (p *pipeConnector) AllowedLatency() time.Duration                 { return 4 * time.Second }

type goldenCase struct {
	ID      string            `json:"id"`
	Args    map[string]string `json:"args"`
	Kind    string            `json:"kind"`
	Payload string            `json:"payload"`
}

// runGolden drives the official pkg/vehicle client through every command and records the
// plaintext payload the vehicle receives for each. The Kotlin port must produce the same messages.
func runGolden() int {
	sim, err := NewVehicle(Options{VIN: goldenVIN, PairingApprovalDelay: -1})
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 1
	}
	clientKey, _ := authentication.NewECDHPrivateKey(rand.Reader)
	sim.Enroll(clientKey.PublicBytes(), keys.Role_ROLE_OWNER, vcsec.KeyFormFactor_KEY_FORM_FACTOR_ANDROID_DEVICE)
	otherKey, _ := authentication.NewECDHPrivateKey(rand.Reader)
	otherPublic, err := ecdh.P256().NewPublicKey(otherKey.PublicBytes())
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 1
	}
	otherHex := hex.EncodeToString(otherKey.PublicBytes())

	conn := &pipeConnector{vehicle: sim, inbox: make(chan []byte, 64)}
	car, err := vehicle.NewVehicle(conn, clientKey, nil)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 1
	}
	setup, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := car.Connect(setup); err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 1
	}
	if err := car.StartSession(setup, nil); err != nil {
		fmt.Fprintln(os.Stderr, "handshake:", err)
		return 1
	}
	sim.TakeRecorded()

	var cases []goldenCase
	failed := false
	run := func(id string, args map[string]string, call func(ctx context.Context) error) {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if err := call(ctx); err != nil {
			fmt.Fprintf(os.Stderr, "note: %s returned %s (payload still recorded)\n", id, err)
		}
		records := sim.TakeRecorded()
		if len(records) == 0 {
			fmt.Fprintf(os.Stderr, "error: %s sent nothing\n", id)
			failed = true
			return
		}
		if args == nil {
			args = map[string]string{}
		}
		cases = append(cases, goldenCase{ID: id, Args: args, Kind: records[0].Kind, Payload: hex.EncodeToString(records[0].Payload)})
	}
	type a = map[string]string

	// Access (VCSEC)
	run("lock", nil, car.Lock)
	run("unlock", nil, car.Unlock)
	run("wake", nil, car.Wakeup)
	run("drive", nil, car.RemoteDrive)
	run("autosecure-modelx", nil, car.AutoSecureVehicle)
	run("trunk-open", nil, car.OpenTrunk)
	run("trunk-close", nil, car.CloseTrunk)
	run("trunk-move", nil, car.ActuateTrunk)
	run("frunk-open", nil, car.OpenFrunk)
	run("tonneau-open", nil, car.OpenTonneau)
	run("tonneau-close", nil, car.CloseTonneau)
	run("tonneau-stop", nil, car.StopTonneau)
	run("body-controller-state", nil, func(ctx context.Context) error { _, err := car.BodyControllerState(ctx); return err })
	run("list-keys", nil, func(ctx context.Context) error { _, err := car.KeySummary(ctx); return err })
	run("add-key", a{"PUBLIC_KEY": otherHex, "ROLE": "driver", "FORM_FACTOR": "android_device"}, func(ctx context.Context) error {
		return car.AddKeyWithRole(ctx, otherPublic, keys.Role_ROLE_DRIVER, vcsec.KeyFormFactor_KEY_FORM_FACTOR_ANDROID_DEVICE)
	})
	run("remove-key", a{"PUBLIC_KEY": otherHex}, func(ctx context.Context) error { return car.RemoveKey(ctx, otherPublic) })
	run("add-key-request", a{"PUBLIC_KEY": otherHex, "ROLE": "owner", "FORM_FACTOR": "android_device"}, func(ctx context.Context) error {
		return car.SendAddKeyRequestWithRole(ctx, otherPublic, keys.Role_ROLE_OWNER, vcsec.KeyFormFactor_KEY_FORM_FACTOR_ANDROID_DEVICE)
	})
	run("session-info", a{"PUBLIC_KEY": otherHex, "DOMAIN": "vcsec"}, func(ctx context.Context) error {
		_, err := car.SessionInfo(ctx, otherPublic, universal.Domain_DOMAIN_VEHICLE_SECURITY)
		return err
	})

	// Climate
	run("climate-on", nil, car.ClimateOn)
	run("climate-off", nil, car.ClimateOff)
	run("climate-set-temp", a{"TEMP": "21.5"}, func(ctx context.Context) error { return car.ChangeClimateTemp(ctx, 21.5, 21.5) })
	run("climate-set-temp", a{"TEMP": "22c", "PASSENGER_TEMP": "19.5"}, func(ctx context.Context) error { return car.ChangeClimateTemp(ctx, 22, 19.5) })
	run("climate-set-temp", a{"TEMP": "70f"}, func(ctx context.Context) error {
		var degrees float32 = 70
		degrees = (degrees - 32.0) * 5.0 / 9.0
		return car.ChangeClimateTemp(ctx, degrees, degrees)
	})
	run("seat-heater", a{"SEAT": "front-left", "LEVEL": "high"}, func(ctx context.Context) error {
		return car.SetSeatHeater(ctx, map[vehicle.SeatPosition]vehicle.Level{vehicle.SeatFrontLeft: vehicle.LevelHigh})
	})
	run("seat-heater", a{"SEAT": "2nd-row-left-back", "LEVEL": "medium"}, func(ctx context.Context) error {
		return car.SetSeatHeater(ctx, map[vehicle.SeatPosition]vehicle.Level{vehicle.SeatSecondRowLeftBack: vehicle.LevelMed})
	})
	run("seat-heater", a{"SEAT": "3rd-row-right", "LEVEL": "off"}, func(ctx context.Context) error {
		return car.SetSeatHeater(ctx, map[vehicle.SeatPosition]vehicle.Level{vehicle.SeatThirdRowRight: vehicle.LevelOff})
	})
	run("seat-cooler", a{"SEAT": "front-right", "LEVEL": "low"}, func(ctx context.Context) error {
		return car.SetSeatCooler(ctx, vehicle.LevelLow, vehicle.SeatFrontRight)
	})
	run("steering-wheel-heater", a{"STATE": "on"}, func(ctx context.Context) error { return car.SetSteeringWheelHeater(ctx, true) })
	run("auto-seat-and-climate", a{"POSITIONS": "LR", "STATE": "on"}, func(ctx context.Context) error {
		return car.AutoSeatAndClimate(ctx, []vehicle.SeatPosition{vehicle.SeatFrontLeft, vehicle.SeatFrontRight}, true)
	})
	run("auto-seat-and-climate", a{"POSITIONS": "L", "STATE": "off"}, func(ctx context.Context) error {
		return car.AutoSeatAndClimate(ctx, []vehicle.SeatPosition{vehicle.SeatFrontLeft}, false)
	})
	run("preconditioning-max", a{"STATE": "on"}, func(ctx context.Context) error { return car.SetPreconditioningMax(ctx, true, false) })
	run("preconditioning-max", a{"STATE": "on", "OVERRIDE": "on"}, func(ctx context.Context) error { return car.SetPreconditioningMax(ctx, true, true) })
	run("bioweapon-mode", a{"STATE": "on"}, func(ctx context.Context) error { return car.SetBioweaponDefenseMode(ctx, true, false) })
	run("cabin-overheat-protection", a{"STATE": "on", "FAN_ONLY": "on"}, func(ctx context.Context) error {
		return car.SetCabinOverheatProtection(ctx, true, true)
	})
	run("cabin-overheat-protection-temp", a{"LEVEL": "medium"}, func(ctx context.Context) error {
		return car.SetCabinOverheatProtectionTemperature(ctx, vehicle.LevelMed)
	})
	run("climate-keeper", a{"MODE": "dog"}, func(ctx context.Context) error { return car.SetClimateKeeperMode(ctx, vehicle.ClimateKeeperModeDog, false) })

	// Charging
	run("charge-port-open", nil, car.OpenChargePort)
	run("charging-start", nil, car.ChargeStart)
	run("charging-stop", nil, car.ChargeStop)
	run("charge-max-range", nil, car.ChargeMaxRange)
	run("charge-standard-range", nil, car.ChargeStandardRange)
	run("charging-set-limit", a{"PERCENT": "80"}, func(ctx context.Context) error { return car.ChangeChargeLimit(ctx, 80) })
	run("charging-set-amps", a{"AMPS": "16"}, func(ctx context.Context) error { return car.SetChargingAmps(ctx, 16) })
	run("charge-port-close", nil, car.CloseChargePort)
	run("charging-schedule", a{"TIME": "02:30"}, func(ctx context.Context) error { return car.ScheduleCharging(ctx, true, 150*time.Minute) })
	run("charging-schedule-cancel", nil, func(ctx context.Context) error { return car.ScheduleCharging(ctx, false, 0) })
	run("scheduled-departure", a{"DEPART_AT": "07:30", "OFF_PEAK_END": "06:00", "PRECONDITIONING": "all", "OFF_PEAK": "weekdays"},
		func(ctx context.Context) error {
			return car.ScheduleDeparture(ctx, 7*time.Hour+30*time.Minute, 6*time.Hour, vehicle.ChargingPolicyAllDays, vehicle.ChargingPolicyWeekdays)
		})
	run("scheduled-departure-clear", nil, car.ClearScheduledDeparture)
	run("charging-schedule-add", a{"DAYS": "weekdays", "TIME": "22:00-6:00", "LATITUDE": "46.05", "LONGITUDE": "14.5", "ID": "1700000000"},
		func(ctx context.Context) error {
			return car.AddChargeSchedule(ctx, &vehicle.ChargeSchedule{
				Id: 1700000000, DaysOfWeek: 62, StartEnabled: true, StartTime: 22 * 60, EndEnabled: true, EndTime: 6 * 60,
				Enabled: true, Latitude: 46.05, Longitude: 14.5,
			})
		})
	run("charging-schedule-remove", a{"TYPE": "id", "ID": "1700000000"}, func(ctx context.Context) error { return car.RemoveChargeSchedule(ctx, 1700000000) })
	run("charging-schedule-remove", a{"TYPE": "home"}, func(ctx context.Context) error { return car.BatchRemoveChargeSchedules(ctx, true, false, false) })
	run("precondition-schedule-add", a{"DAYS": "all", "TIME": "07:30", "LATITUDE": "46.05", "LONGITUDE": "14.5", "REPEAT": "once", "ID": "1700000001"},
		func(ctx context.Context) error {
			return car.AddPreconditionSchedule(ctx, &vehicle.PreconditionSchedule{
				Id: 1700000001, DaysOfWeek: 127, PreconditionTime: 7*60 + 30, OneTime: true, Enabled: true, Latitude: 46.05, Longitude: 14.5,
			})
		})
	run("precondition-schedule-remove", a{"TYPE": "work"}, func(ctx context.Context) error { return car.BatchRemovePreconditionSchedules(ctx, false, true, false) })
	run("precondition-schedule-remove", a{"TYPE": "id", "ID": "5"}, func(ctx context.Context) error { return car.RemovePreconditionSchedule(ctx, 5) })
	run("low-power-mode", a{"STATE": "on"}, func(ctx context.Context) error { return car.SetLowPowerMode(ctx, true) })
	run("keep-accessory-power", a{"STATE": "off"}, func(ctx context.Context) error { return car.SetKeepAccessoryPowerMode(ctx, false) })

	// Body and other
	run("windows-vent", nil, car.VentWindows)
	run("windows-close", nil, car.CloseWindows)
	run("sunroof", a{"LEVEL": "15"}, func(ctx context.Context) error { return car.ChangeSunroofState(ctx, 15) })
	run("homelink", a{"LATITUDE": "46.05", "LONGITUDE": "14.5"}, func(ctx context.Context) error { return car.TriggerHomelink(ctx, 46.05, 14.5) })
	run("honk", nil, car.HonkHorn)
	run("flash-lights", nil, car.FlashLights)
	run("ping", nil, car.Ping)
	run("set-vehicle-name", a{"NAME": "Blue Bird"}, func(ctx context.Context) error { return car.SetVehicleName(ctx, "Blue Bird") })

	// Media
	run("media-toggle-playback", nil, car.ToggleMediaPlayback)
	run("media-next-track", nil, car.MediaNextTrack)
	run("media-previous-track", nil, car.MediaPreviousTrack)
	run("media-next-favorite", nil, car.MediaNextFavorite)
	run("media-previous-favorite", nil, car.MediaPreviousFavorite)
	run("media-volume-up", nil, car.VolumeUp)
	run("media-volume-down", nil, car.VolumeDown)
	run("media-set-volume", a{"VOLUME": "5.5"}, func(ctx context.Context) error { return car.SetVolume(ctx, 5.5) })

	// Security
	run("sentry-mode", a{"STATE": "on"}, func(ctx context.Context) error { return car.SetSentryMode(ctx, true) })
	run("valet-mode-on", a{"PIN": "1234"}, func(ctx context.Context) error { return car.EnableValetMode(ctx, "1234") })
	run("valet-mode-off", nil, car.DisableValetMode)
	run("valet-pin-reset", nil, car.ResetValetPin)
	run("speed-limit-activate", a{"PIN": "1234"}, func(ctx context.Context) error { return car.ActivateSpeedLimit(ctx, "1234") })
	run("speed-limit-deactivate", a{"PIN": "1234"}, func(ctx context.Context) error { return car.DeactivateSpeedLimit(ctx, "1234") })
	run("speed-limit-set", a{"MPH": "65"}, func(ctx context.Context) error { return car.SpeedLimitSetLimitMPH(ctx, 65) })
	run("speed-limit-clear-pin", a{"PIN": "1234"}, func(ctx context.Context) error { return car.ClearSpeedLimitPIN(ctx, "1234") })
	run("speed-limit-clear-pin-admin", nil, car.ClearSpeedLimitPINAdminAction)
	run("pin-to-drive-clear", nil, car.ClearPINToDrive)
	run("guest-mode-on", nil, func(ctx context.Context) error { return car.SetGuestMode(ctx, true) })
	run("guest-mode-off", nil, func(ctx context.Context) error { return car.SetGuestMode(ctx, false) })
	run("erase-guest-data", nil, car.EraseGuestData)
	run("parental-controls-on", a{"PIN": "1234"}, func(ctx context.Context) error { return car.ParentalControlsActivate(ctx, "1234") })
	run("parental-controls-off", a{"PIN": "1234"}, func(ctx context.Context) error { return car.ParentalControlsDeactivate(ctx, "1234") })
	run("parental-controls-set-speed-limit", a{"MPH": "60"}, func(ctx context.Context) error { return car.ParentalControlsSetSpeedLimit(ctx, 60) })
	run("parental-controls-enable-setting", a{"SETTING": "curfew", "STATE": "on"}, func(ctx context.Context) error {
		return car.ParentalControlsEnableSetting(ctx, vehicle.ParentalControlsSettingCurfew, true)
	})
	run("parental-controls-clear-pin-admin", nil, car.ParentalControlsClearPIN)

	// Software
	run("software-update-start", a{"DELAY": "2h"}, func(ctx context.Context) error { return car.ScheduleSoftwareUpdate(ctx, 2*time.Hour) })
	run("software-update-cancel", nil, car.CancelSoftwareUpdate)

	// State
	categories := []struct {
		id       string
		category vehicle.StateCategory
	}{
		{"charge", vehicle.StateCategoryCharge},
		{"climate", vehicle.StateCategoryClimate},
		{"drive", vehicle.StateCategoryDrive},
		{"location", vehicle.StateCategoryLocation},
		{"closures", vehicle.StateCategoryClosures},
		{"charge-schedule", vehicle.StateCategoryChargeSchedule},
		{"precondition-schedule", vehicle.StateCategoryPreconditioningSchedule},
		{"tire-pressure", vehicle.StateCategoryTirePressure},
		{"media", vehicle.StateCategoryMedia},
		{"media-detail", vehicle.StateCategoryMediaDetail},
		{"software-update", vehicle.StateCategorySoftwareUpdate},
		{"parental-controls", vehicle.StateCategoryParentalControls},
	}
	for _, c := range categories {
		category := c.category
		run("state", a{"CATEGORY": c.id}, func(ctx context.Context) error { _, err := car.GetState(ctx, category); return err })
	}

	car.Disconnect()
	encoder := json.NewEncoder(os.Stdout)
	encoder.SetIndent("", "  ")
	if err := encoder.Encode(cases); err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 1
	}
	if failed {
		return 1
	}
	return 0
}
