# Development Setup (macOS)

## Prerequisites

You need Homebrew. Everything else installs from it.

### 1. JDK 17 (Android requires 17+, you have 11)

```bash
brew install openjdk@17
```

Then add to your shell profile (`~/.zshrc`):

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
export PATH="$JAVA_HOME/bin:$PATH"
```

> **Why not `/usr/libexec/java_home`?** That utility only finds JDKs in `/Library/Java/JavaVirtualMachines/`. Homebrew installs to `/opt/homebrew/opt/`, so you need the explicit path.

Reload: `source ~/.zshrc`

Verify: `java --version` → should show 17.x

### 2. Android SDK

```bash
brew install --cask android-studio
```

Open Android Studio once → SDK Manager → install:
- Android SDK Platform 35 (API 35)
- Android SDK Build-Tools 35
- Android SDK Command-Line Tools
- Android Emulator
- An emulator system image (e.g. API 35 x86_64)

Then add to `~/.zshrc`:

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

Reload: `source ~/.zshrc`

Verify: `adb --version`

### 3. Gradle (needed to bootstrap the wrapper)

```bash
brew install gradle
```

Then generate the wrapper inside the app project:

```bash
cd app
gradle wrapper --gradle-version 8.11.1
```

This creates `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar`. After this, you only use `./gradlew` — the system Gradle is no longer needed.

Verify: `./gradlew --version`

### 4. Node.js (you already have v24 — good)

Nothing to do. If you ever need to manage versions:

```bash
brew install nvm
```

### 5. Redis (optional — only if you want rate limiting beyond in-memory)

```bash
brew install redis
brew services start redis
```

Not needed for single-user MVP. Skip it.

---

## Project Setup

### Backend

```bash
cd backend
cp .env.example .env
# Edit .env — add your OPENAI_API_KEY and FIREBASE_PROJECT_ID
npm install
npm run dev
```

Backend runs at `http://localhost:3000`. Health check: `curl http://localhost:3000/api/health`

### Android App

```bash
cd app
./gradlew installDebug
```

First run downloads Gradle 8.11.1 + all dependencies (~5-10 min).

To run on emulator:
1. Open Android Studio → AVD Manager → create an emulator (API 35)
2. Start it
3. `./gradlew installDebug` installs and launches

Or just open the `app/` folder in Android Studio and hit Run.

> **Note:** The debug build points to `http://10.0.2.2:3000` which is the emulator's alias for `localhost`. Make sure the backend is running.

---

## Day-to-Day

| Task | Command |
|---|---|
| Run backend | `cd backend && npm run dev` |
| Build APK | `cd app && ./gradlew assembleDebug` |
| Install on device/emulator | `cd app && ./gradlew installDebug` |
| Run all checks | `cd app && ./gradlew check` |
| Clean build | `cd app && ./gradlew clean` |

---

## Firebase Setup (needed for auth)

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create project → enable Authentication → enable Google Sign-In
3. Add Android app with package name `com.fatlosstrack`
4. Download `google-services.json` → place in `app/mobile/`
5. Copy project ID to `backend/.env` → `FIREBASE_PROJECT_ID=...`
6. For backend token verification: create a service account key → download JSON → set `GOOGLE_APPLICATION_CREDENTIALS` path in `.env`

---

## Troubleshooting

**`JAVA_HOME` wrong version** — Run `java --version`. Must be 17+. If it shows 11, the shell profile isn't sourced.

**Gradle wrapper missing or broken** — Run `gradle wrapper --gradle-version 8.11.1` from the `app/` directory to regenerate it. Then use `./gradlew`.

**Emulator can't reach backend** — Backend must be on `0.0.0.0:3000`, not `127.0.0.1`. The app uses `10.0.2.2:3000` (Android emulator loopback alias).
