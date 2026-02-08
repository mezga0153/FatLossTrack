# Build & Run

Convenience scripts in `scripts/`. All assume macOS with Homebrew JDK 17 and Android SDK installed (see [development.md](development.md)).

## Scripts

### `./scripts/build.sh`
Builds the debug APK. Output at `app/mobile/build/outputs/apk/debug/mobile-debug.apk`.

### `./scripts/run-emulator.sh`
Boots the `FatLossTrack` AVD if not already running, builds the app, installs it, and launches it on the emulator.

### `./scripts/connect-phone.sh`
Interactive script to pair and connect a physical Android device over Wi-Fi (wireless ADB). Walk-through:
1. Asks whether you've already paired (skip if yes)
2. Prompts for the pairing address and code from **Settings → Developer options → Wireless debugging → Pair device with pairing code**
3. Prompts for the debug address from the **Wireless debugging** main screen (different port)

Only needs to be run once per device. The pairing persists across reboots.

### `./scripts/run-phone.sh`
Builds the app, installs it on the connected phone, and launches it. Requires a device connected via `connect-phone.sh` or USB. Automatically picks the first non-emulator device.

## Typical workflows

**Emulator:**
```bash
./scripts/run-emulator.sh
```

**Physical device (first time):**
```bash
./scripts/connect-phone.sh   # pair + connect
./scripts/run-phone.sh       # build + install + launch
```

**Physical device (subsequent):**
```bash
./scripts/run-phone.sh
```
