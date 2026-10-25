# Tesla BLE Control

An Android app that controls a Tesla directly over Bluetooth, using Tesla's published
[vehicle-command](https://github.com/teslamotors/vehicle-command) protocol. It needs no Tesla
account and no internet connection: the app doesn't declare the `INTERNET`, `ACCESS_NETWORK_STATE`
or `ACCESS_WIFI_STATE` permissions.

- **Every command Tesla's library can send over Bluetooth (85)**: locks, trunks, frunk, windows,
  climate, seats, charging, schedules, media, Sentry, Valet, speed limits, guest mode, parental
  controls, keys, software updates and the car's state. The common ones have their own screens;
  all of them are under *All commands* (the search icon).
- **Guided setup** with pictures: VIN, permissions, finding the car, and pairing with the key card.
- **Background connection** (optional) and a **Quick Settings tile** for locking and unlocking.
- **Languages:** English, Slovenian, Croatian, French, German, Norwegian Bokmål, Danish, Italian
  and Spanish. Follows your phone’s language; Android 13 and newer also offer a per-app language
  choice in the phone’s app settings.
- A grey theme with large text, labelled controls and TalkBack support; light and dark.

## Install

Download the APK from the [latest release](https://github.com/StrajnarFilip/tesla-ble-control/releases/latest)
and open it on your phone (Android 8 or newer); Android asks once to allow installs from your
browser or file manager. With a computer, `adb install tesla-ble-control-0.1.0.apk` works too.

Releases are signed with a private key. To check an APK, `apksigner verify --print-certs` should
show this SHA-256 certificate digest:

```
f2a7f7809a30dff5a9baebffbfefcdbc1e87eed29849b19b0e705518c57d2dc9
```

Updates install over the previous version and keep the pairing. Uninstalling the app deletes its
key, and the phone then has to be paired with the car again.

## Pairing

The setup guide walks through it. The key part is approving the new key with your key card:

1. Sit in the car with the key card. Take your phone off the wireless charger.
2. Tap **Send pairing request**.
3. Right away, place the key card on the card reader and hold it still for a few seconds:
   - **Model 3 built from about 2024:** on the wireless phone charger.
   - **Earlier Model 3, and Model Y:** on the centre console, just behind the cup holders.
   - **Model S, Model X and Cybertruck:** try both spots.

The car's screen may show nothing during pairing; the app notices the approval by itself. If it
doesn't finish, see *Settings → Troubleshooting*. Tip: in the Tesla app, touch and hold the VIN on
the car's main screen to copy it, then tap **Paste VIN** in the guide.

## Limits

- Bluetooth range is a few metres, and a car accepts at most three Bluetooth connections (key fobs
  and phone keys share them).
- Some things can't be done over Bluetooth with Tesla's published commands; the app says so where
  they would appear. The steering wheel heater only switches on or off (Level 1, Level 2 and Auto
  are chosen in the car). Turning PIN to Drive on, renaming keys and anything from Tesla's servers
  need the internet.
- Model S and Model X built before 2021 don't support this protocol.

## Build it yourself

You need:

- **JDK 17 or newer** (17, 21 and 25 work).
- **The Android SDK** with platform 37 and build tools 36.0.0. Android Studio installs them when you
  open the project. Without Android Studio, use the
  [command-line tools](https://developer.android.com/studio#command-line-tools-only):
  ```
  sdkmanager --licenses
  sdkmanager "platforms;android-37.0" "build-tools;36.0.0" "platform-tools"
  ```
  Then tell the build where the SDK is, either with the `ANDROID_HOME` environment variable or a
  `local.properties` file in the project folder containing `sdk.dir=/path/to/Android/Sdk`.

Everything else (Gradle, Kotlin, the protobuf compiler and the libraries) is downloaded by the build.

```
git clone https://github.com/StrajnarFilip/tesla-ble-control.git
cd tesla-ble-control
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

On Windows, use `gradlew.bat assembleDebug`.

A debug build is signed with your computer's own debug key, so it can't install over the released
APK (or the other way round): uninstall the other one first, then pair again.

### Tests

```
./gradlew test lint
```

This runs the protocol, controller and app tests and Android lint. The integration tests against
the vehicle simulator also need [Go](https://go.dev/dl/) 1.23 or newer and `bash`; without Go they
are skipped. The first run with Go downloads Tesla's vehicle-command module to build the simulator.

### Release builds

`./gradlew assembleRelease` builds an unsigned APK unless signing is configured. Maintainers keep
the release key outside the repository and point the build at it from `~/.gradle/gradle.properties`:

```
teslaBle.releaseStoreFile=/path/to/tesla-ble-control-release.p12
teslaBle.releaseStorePassword=…
teslaBle.releaseKeyAlias=tesla-ble-control
teslaBle.releaseKeyPassword=…
```

## How it is built

| Module | What it is |
|---|---|
| `protocol/` | A Kotlin port of Tesla's Go client: session handshake, AES-GCM signing, replay protection, BLE framing, dispatcher, and builders for all 85 commands. Pure JVM. |
| `core/` | Orchestration: connect, secure, wake the car when needed, refresh state, session cache. Pure JVM. |
| `app/` | Android: Bluetooth GATT transport, Keystore-protected key, Compose UI, background service, Quick Settings tile. |
| `tools/vehicle-sim/` | Test-only Go simulator built on Tesla's own vehicle-side code. |
| `tools/icons/` | Generates the action icons in `app/src/main/res/drawable/ic_action_*.xml`. |

The port is checked in three ways:
- **Tesla's worked examples:** every example in Tesla's `protocol.md` is reproduced.
- **Golden comparison:** the command payloads must match what Tesla's own Go client sends, for every command (`commands.json`).
- **Simulator tests:** integration tests run against the simulator, which uses Tesla's vehicle-side verifier, including dropped and duplicated messages, busy replies, sleep and reboots.

### Trying the app on an emulator

The **sim** build type (`./gradlew :app:assembleSim`, app id `io.github.teslablecontrol.sim`)
talks to the simulator over TCP instead of Bluetooth. It is the only build with the `INTERNET`
permission, which the local socket needs.

```
sleep infinity | tools/vehicle-sim/build/bin/vehicle-sim -listen 127.0.0.1:7001 -pairing-delay 6s -asleep -v &
adb install -r app/build/outputs/apk/sim/app-sim.apk
adb reverse tcp:7001 tcp:7001
adb shell "run-as io.github.teslablecontrol.sim sh -c 'mkdir -p files && echo 127.0.0.1:7001 > files/vehicle-sim'"
```

Then run the setup guide with VIN `5YJ3E1EA1PF000001`; the simulator approves pairing after six
seconds.

## License

Copyright 2026 Filip Strajnar. Licensed under the [Apache License, Version 2.0](LICENSE).

The protocol code is derived from Tesla's vehicle-command (also Apache 2.0), and the app bundles the
Inter typeface (SIL Open Font License 1.1); see [NOTICE](NOTICE).
