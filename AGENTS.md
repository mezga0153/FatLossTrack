# AGENTS.md — FatLoss Track

Guide for AI agents working on this codebase.

## Quick Reference

| Item | Value |
|---|---|
| Package | `com.fatlosstrack` |
| App version | 0.3.1 (versionCode 4) |
| Language | Kotlin 2.3.21 (AGP built-in Kotlin) |
| UI | Jetpack Compose + Material 3 (BOM 2026.09.00) |
| DI | Hilt 2.60.1 |
| DB | Room 2.8.5, version **18** |
| HTTP | Ktor 3.6.0 (OkHttp engine) |
| Health | Health Connect 1.2.0-alpha06 |
| Camera | CameraX 1.6.2 |
| Images | Coil 3.6.3 |
| Auth / Drive | Firebase BoM 34.19.0 + Google Sign-In |
| Build | Gradle 9.7.1, AGP 9.4.0, KSP 2.3.12 |
| SDK | compileSdk 37, targetSdk 36, minSdk 28 |
| JDK | 17 (`/opt/homebrew/opt/openjdk@17`) |
| Device | Pixel 10 Pro XL, Android 16 (USB adb) |

Dependency versions live in `app/gradle/libs.versions.toml`.

### Build & Deploy

```bash
./scripts/build.sh          # Build debug APK
./scripts/run-phone.sh      # Install + launch on physical device
./scripts/run-emulator.sh   # Boot emulator + install + launch
./scripts/connect-phone.sh  # Wireless ADB pairing
```

Or manually:
```bash
cd app && JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :mobile:assembleDebug
```

APK output: `app/mobile/build/outputs/apk/debug/mobile-debug.apk`

### Unit tests

JVM tests (JUnit 4) live in `app/mobile/src/test/java/`:
```bash
cd app && JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :mobile:testDebugUnitTest
```

### Release

- Bump `versionCode` / `versionName` in `app/mobile/build.gradle.kts` (one commit per Play release).
- Release builds are minified (`isMinifyEnabled = true`) and signed with `fatlosstrack-release.keystore`
  (repo root); `KEYSTORE_PASSWORD` and `KEY_PASSWORD` come from `app/local.properties`.
- Privacy policy for the Play listing: `docs/privacy-policy.html`.

---

## Architecture

**Local-first, state holders instead of ViewModels** — Room is the single source of truth.
Each main screen has a `@Stable` `*StateHolder` class (plain class, Hilt constructor-injected, **not** a
ViewModel) that wraps DAOs, preferences and services. Composables collect Flows with `collectAsState()`.

```
Compose UI ←→ *StateHolder ←→ DAOs (Room Flows) ←→ Room Database
                                                       ↑
                                          HealthConnectSyncService ← Health Connect SDK

OpenAiService → api.openai.com (direct, API key in DataStore, token usage → ai_usage)
AutoBackupManager → Documents/FatLossTrack/      DriveBackupManager → Drive appDataFolder
```

Background work (summaries, syncs) runs on the Hilt-provided `@ApplicationScope` `CoroutineScope`
(`SupervisorJob() + Dispatchers.IO`, from `di/CoroutineModule.kt`). There is no WorkManager usage.

---

## Project Structure

```
fatloss_track/
├── idea.md, plan.md, ui.md, development.md, build-run.md   # Specs
├── docs/privacy-policy.html          # Play Store privacy policy
├── scripts/                          # Build/deploy shell scripts
├── backend/                          # Fastify Node.js API (exists but bypassed)
└── app/                              # Android Gradle project
    └── mobile/src/main/java/com/fatlosstrack/
        ├── FatLossTrackApp.kt        # @HiltAndroidApp, forces Locale.US
        ├── auth/AuthManager.kt       # Google Sign-In + Firebase Auth (used for Drive backup)
        ├── data/
        │   ├── CoachTone.kt                 # Tone instructions (honest/supportive/insulting/cruel), shared by all coaching prompts
        │   ├── CruelCoachNoteGenerator.kt   # Second AI call that rewrites a meal's coach_note when tone = cruel
        │   ├── DaySummaryGenerator.kt       # Day synopsis + AI daily coaching summaries
        │   ├── backup/
        │   │   ├── AutoBackupManager.kt     # DB + prefs → Documents/FatLossTrack/, 7-day rotation, restore/import
        │   │   └── DriveBackupManager.kt    # DB + prefs ↔ Google Drive appDataFolder
        │   ├── health/
        │   │   ├── HealthConnectManager.kt  # HC client (weight/steps/sleep/HR/exercise/body comp/glucose)
        │   │   └── HealthConnectSyncService.kt  # HC → Room sync, returns changed dates
        │   ├── local/
        │   │   ├── PreferencesManager.kt    # DataStore (goal, profile, API key, model, tone, theme, language, units)
        │   │   ├── AppLogger.kt             # File logger (daily rotation, 7-day retention)
        │   │   ├── CapturedPhotoStore.kt    # In-memory photo URIs between screens
        │   │   ├── PendingTextMealStore.kt  # In-memory parsed text meal (AiBar → analysis)
        │   │   ├── PendingChatStore.kt      # In-memory initial chat message + images (AiBar/camera → Chat)
        │   │   └── db/
        │   │       ├── Entities.kt          # 9 Room entities
        │   │       ├── Daos.kt              # 9 DAOs
        │   │       ├── FatLossDatabase.kt   # DB v18
        │   │       └── Converters.kt        # LocalDate↔String, Instant↔Long
        │   └── remote/
        │       ├── OpenAiService.kt         # Direct OpenAI calls (chat, streaming chat, meal parse/photo/edit)
        │       └── api/BackendApi.kt        # Backend proxy (exists but unused)
        ├── di/
        │   ├── CoroutineModule.kt           # @ApplicationScope CoroutineScope
        │   ├── DatabaseModule.kt            # Room + migrations, DAOs, HC, logger
        │   └── NetworkModule.kt             # Ktor HttpClient, BackendApi
        ├── domain/
        │   ├── ChatContextUseCase.kt        # Last-7-days data block for chat prompts
        │   ├── TdeeCalculator.kt            # TDEE / BMR / macro targets
        │   └── trend/TrendEngine.kt         # EMA + linear projection (currently unused)
        └── ui/
            ├── MainActivity.kt              # Entry point, Hilt injections, auto-backup on open
            ├── Navigation.kt                # FatLossTrackNavGraph: NavHost + bottom bar + floating AiBar + onboarding redirect
            ├── theme/Theme.kt               # 12 theme presets, AppColors via LocalAppColors
            ├── home/
            │   ├── HomeScreen.kt            # Goal progress, trend chart, N-day stats, AI period summary
            │   └── HomeStateHolder.kt       # Period stats + cached AI period summary
            ├── log/
            │   ├── LogScreen.kt             # Day timeline (loads 30-day pages on scroll) + today summary
            │   ├── LogStateHolder.kt
            │   ├── DayCard.kt               # DayCard + StatChip composables
            │   ├── DailyLogSheet.kt         # Daily log edit bottom sheet (weight/steps lock toggles)
            │   ├── MealSheets.kt            # AddMealSheet (with bookmarks) + MealEditSheet (incl. AI edit)
            │   ├── SheetHost.kt             # LogSheetState + LogSheetHost (shared by Log & Home)
            │   └── LogHelpers.kt            # Category/meal-type helpers, EditField, JSON parsers, launchSummary()
            ├── trends/
            │   ├── TrendsScreen.kt          # Charts for all TrendMetric values with time toggle
            │   ├── TrendsStateHolder.kt     # TrendMetric enum + selected metric
            │   ├── BloodGlucoseChart.kt     # Per-reading glucose chart
            │   └── OdtExporter.kt           # Export chart data as .ods via share sheet
            ├── welcome/WelcomeScreen.kt     # Onboarding steps (welcome, API key, tips) + language picker
            ├── settings/
            │   ├── SettingsScreen.kt        # Config, HC sync, theme, language, units, tone, backups, sign-in
            │   ├── SettingsStateHolder.kt
            │   ├── SetGoalScreen.kt         # Goal editor (weight loss / diabetes carb limits)
            │   ├── SetProfileScreen.kt      # Profile editor (sex, age, height, activity)
            │   ├── ModelSelectorScreen.kt   # OpenAI model picker (model list + prices)
            │   ├── AiUsageScreen.kt         # Token usage + estimated cost per feature/model
            │   ├── BookmarksScreen.kt       # Manage bookmarked meals
            │   ├── BackupManagerScreen.kt   # Local backups: list, restore, delete, import
            │   └── LogViewerScreen.kt       # Debug log viewer
            ├── camera/
            │   ├── MealCaptureScreen.kt     # CameraX multi-photo capture + gallery picker
            │   ├── AnalysisResultScreen.kt  # AI meal analysis display + edit
            │   ├── AnalysisResultStateHolder.kt
            │   ├── PhotoTakenAt.kt          # Photo timestamp (EXIF → MediaStore → mtime) to prefill meal date/time
            │   └── CameraModeSheet.kt       # Log vs Suggest picker (currently unused)
            ├── chat/
            │   ├── ChatScreen.kt            # AI chat (streaming, photos, persisted history)
            │   └── ChatStateHolder.kt
            ├── login/LoginScreen.kt         # Google sign-in screen (not routed, unused)
            ├── mock/                        # MockData, MockAiResponses (prototyping, unused)
            └── components/
                ├── AiBar.kt                 # Floating AI input pill (rainbow border)
                ├── AiBarStateHolder.kt      # Text → meal parse, else open chat
                ├── QuickLogBar.kt           # One-tap repeat chips above the AiBar + undo snackbar
                ├── QuickLogStateHolder.kt   # Bookmarks + frequent recent meals, copy/undo
                ├── TrendChart.kt            # Canvas-based weight chart
                ├── SimpleLineChart.kt       # Generic line chart with reference zones
                ├── MultiSeriesLineChart.kt  # Multi-series line chart with toggleable series (e.g. macros)
                ├── StackedBarChart.kt       # MacroBarChart composable
                ├── InfoCard.kt              # Reusable stat card
                └── TdeeState.kt             # rememberDailyTargetKcal() composable helper
```

---

## Database (Room, v18)

### Entities

| Table | PK | Key Fields |
|---|---|---|
| `weight_entries` | `id` (auto) | `date`, `valueKg`, `source` (MANUAL/HEALTH_CONNECT) |
| `meal_entries` | `id` (auto) | `date`, `description`, `itemsJson?`, `totalKcal`, `totalProteinG`, `totalCarbsG`, `totalFatG`, `coachNote?`, `category` (HOME/RESTAURANT/FAST_FOOD), `mealType?` (BREAKFAST/BRUNCH/LUNCH/DINNER/SNACK), `hasAlcohol`, `photoUri?`, `note?`, `loggedAt?` (user-set time; `displayTime` falls back to `createdAt`) |
| `goals` | `id` (auto) | `targetKg`, `rateKgPerWeek`, `deadline`, `dailyDeficitKcal?` |
| `daily_logs` | `date` (PK) | `weightKg?`, `steps?`, `sleepHours?`, `restingHr?`, `exercisesJson?`, `notes?`, `offPlan`, `daySummary?`, `synopsis?`, `weightLocked`, `stepsLocked`, `bodyFatPct?`, `bodyWaterKg?`, `leanBodyMassKg?`, `boneMassKg?`, `bloodSugarMmol?` |
| `insights` | `id` (auto) | `date`, `type` (PATTERN/TRADEOFF), `message`, `dataJson?` |
| `chat_messages` | `id` (auto) | `role` (user/assistant), `content`, `imageUris?` (comma-separated) |
| `ai_usage` | `id` (auto) | `feature`, `model`, `promptTokens`, `completionTokens` |
| `bookmarked_meals` | `id` (auto) | `name`, meal fields (description, items, kcal, macros, category, mealType), `sortOrder` |
| `blood_glucose_entries` | `id` (auto) | `timestamp`, `valueMmolL`, `source` ("HC"/"MANUAL") |

Most goal/profile settings live in DataStore (`PreferencesManager`), not in the `goals` table.

### Migrations

- **4→5**: `daily_logs.daySummary`
- **5→6**: `meal_entries.totalProteinG`
- **6→7**: `CREATE TABLE chat_messages`
- **7→8**: `meal_entries.totalCarbsG`, `meal_entries.totalFatG`
- **8→9**: `CREATE TABLE ai_usage`
- **9→10**: `chat_messages.imageUris`
- **10→11**: `CREATE TABLE bookmarked_meals`
- **11→12**: `daily_logs.synopsis`
- **12→13**: `daily_logs.weightLocked`, `daily_logs.stepsLocked`
- **13→14**: `daily_logs.bodyFatPct`, `bodyWaterKg`, `leanBodyMassKg`, `boneMassKg`
- **14→15**: `bookmarked_meals.sortOrder`
- **15→16**: `daily_logs.bloodSugarMmol`
- **16→17**: `meal_entries.loggedAt`
- **17→18**: `CREATE TABLE blood_glucose_entries`
- Uses `addMigrations()` — **never** use `fallbackToDestructiveMigration()` (it wipes user data)
- `exportSchema = false`, so there are no schema JSON files to update

### Important: Adding new columns

1. Add field to entity in `Entities.kt`
2. Bump version in `FatLossDatabase.kt`
3. Add `MIGRATION_N_N+1` in `DatabaseModule.kt` with `ALTER TABLE ... ADD COLUMN ...`
4. Register migration with `.addMigrations()`

---

## Navigation

### Bottom tabs: `Home` | `Trends` | `Log` | `Chat` | `Settings`

### Routes

| Route | Screen | Notes |
|---|---|---|
| `home` | HomeScreen | Start destination |
| `trends` | TrendsScreen | Stat chips on Home/Log jump here with a preselected metric |
| `log` | LogScreen | |
| `chat` | ChatScreen | |
| `settings` | SettingsScreen | |
| `set_goal` | SetGoalScreen | From settings |
| `set_profile` | SetProfileScreen | From settings |
| `model_selector` | ModelSelectorScreen | From settings |
| `ai_usage` | AiUsageScreen | From settings |
| `bookmarks` | BookmarksScreen | From settings |
| `backup_manager` | BackupManagerScreen | From settings |
| `log_viewer` | LogViewerScreen | From settings (debug) |
| `onboarding/welcome?first={first}` | WelcomeScreen | Auto-opened when no goal is set; `first=false` from settings |
| `set_profile_onboarding` → `set_goal_onboarding` | SetProfile/SetGoalScreen | Onboarding chain |
| `onboarding/apikey` | WelcomeScreen | Also shown once per session if goal set but API key blank |
| `onboarding/tips` | WelcomeScreen | Last onboarding step |
| `capture/{mode}?targetDate={date}` | MealCaptureScreen | mode = `log`, `suggest` or `chat` |
| `analysis/{mode}/{count}?targetDate={date}` | AnalysisResultScreen | Photo analysis; mode = `CaptureMode` name |
| `analysis/text` | AnalysisResultScreen | Text meal analysis |

Onboarding chain: welcome → profile → goal → apikey → tips.

The **AiBar** floats above the bottom nav on main screens. It handles:

- Text input → `parseTextMeal()`; if it's a meal → `PendingTextMealStore` → `analysis/text`, otherwise → `PendingChatStore` → Chat tab
- Camera button → `capture/log`

On Home and Log, `QuickLogBar` sits above it: chips for bookmarked meals and meals repeated in the last
30 days (or eaten yesterday, via `MealDao.getFrequentMeals()`). Tap logs a copy to today at the current
time with no AI call and an undo snackbar; long-press opens `AddMealSheet` prefilled. The chips hide while
the keyboard is up or the AiBar shows a response.

Camera modes:
- `log` → `analysis/LogMeal/{count}`
- `suggest` → photos + suggestion prompt handed to Chat via `PendingChatStore`
- `chat` (from Chat's camera button) → photos stay in `CapturedPhotoStore`, pops back to Chat

Chrome (bottom bar + AiBar) hides on `capture*`, `analysis*`, `onboarding*`, `set_goal`, `set_profile`,
the onboarding profile/goal routes, `log_viewer`, `backup_manager`, `ai_usage`, `model_selector`, `bookmarks`.
The AiBar also hides on Chat (it has its own input).

---

## AI Integration

The app calls **OpenAI directly** (`OpenAiService`). The API key is user-provided and stored in DataStore.
The backend proxy exists but is bypassed.

- **Model:** user-selectable in `ModelSelectorScreen`; default `gpt-5.6-terra`.
- **Language:** prompts get a language suffix so replies match the app language.
- **Units:** prompts enforce metric units.
- **Usage tracking:** every call records prompt/completion tokens to `ai_usage` with a `feature` tag
  (`chat`, `meal_text`, `meal_photo`, `meal_suggest`, `meal_edit`, `day_summary`, `period_summary`, `coach_note`).
  `AiUsageScreen` turns these into estimated cost.

### AI Features

| Feature | Entry Point | Description |
|---|---|---|
| Chat | `OpenAiService.streamChatWithHistory()` | Streamed (SSE), history in `chat_messages`, optional photos, context from `ChatContextUseCase` |
| One-shot chat | `OpenAiService.chat()` | Used by summaries, coach notes, AiBar fallback |
| Text meal parse | `OpenAiService.parseTextMeal()` | "2 eggs and toast" → JSON with kcal, macros, items, `is_meal`, `day_offset` |
| Photo meal log | `OpenAiService.analyzeMeal(mode="log")` | Vision API: photos → nutrition breakdown; date/time prefilled from photo metadata |
| Photo suggest | Chat with photos | `capture/suggest` hands fridge photos + a prompt to Chat |
| AI meal edit | `OpenAiService.editMealWithAi()` | Natural-language corrections from analysis screen, meal edit sheet and chat |
| Cruel coach note | `CruelCoachNoteGenerator.applyTo()` | When tone = `cruel`, rewrites the meal's `coach_note` from a dossier of the user's data |
| Day summary | `DaySummaryGenerator.generateForDate()` | 1-2 sentence coaching note per day, stored in `daily_logs.daySummary` |
| Period summary | `HomeStateHolder` | N-day trend summary; module-level cache keyed by data fingerprint, 30-min cooldown |

Coach tones (`CoachTone.instruction()`): `honest` (default), `supportive`, `insulting`, `cruel`.

### Day summaries trigger on:
- Health Connect sync (for changed dates)
- Manual daily log edit
- Meal add/edit/delete (Log/Home sheets, analysis screen, AiBar, quick-log chips and their undo, chat edits)
- Coach tone change (today + yesterday)

`launchForDate()` writes the `"⏳"` placeholder immediately (shows loading indicator), then generates
on the `@ApplicationScope`. A deterministic `synopsis` is always stored (no API key needed); the AI call
is skipped if a hash of the day's input data hasn't changed since the last generation.

---

## Health Connect

### Permissions (all READ)
`WEIGHT`, `STEPS`, `SLEEP`, `HEART_RATE`, `RESTING_HEART_RATE`, `EXERCISE`, `ACTIVE_CALORIES_BURNED`,
`BODY_FAT`, `BODY_WATER_MASS`, `LEAN_BODY_MASS`, `BONE_MASS`, `BLOOD_GLUCOSE`

The first seven are the core set sync needs; body composition and blood glucose are optional
(their getters return null when not granted).

### Sync behavior

- Auto-syncs last 7 days on app open (after `cleanupDuplicateWeights()`)
- Manual sync from Settings; `launchSyncFromStart()` re-syncs from the start date
- All `readRecords` calls paginate past the 1000-record limit
- **Merge rule**: HC values overwrite existing fields when HC has a value; existing values are kept when HC has none.
  Weight and steps are protected by `weightLocked` / `stepsLocked`, which `DailyLogSheet` sets when the user
  types a manual value (and can toggle).
- Blood glucose: HC readings in the synced range are deleted and re-inserted into `blood_glucose_entries`
  (manual readings untouched); `daily_logs.bloodSugarMmol` is the average of the day's readings
- Sleep: uses stages (excludes AWAKE types), time window 18:00-14:00
- Resting HR: prefers `RestingHeartRateRecord`, fallback = median of bottom quartile of raw HR
- Exercises: mapped to 24+ exercise types with name/duration/kcal

---

## Theme & Colors

`Theme.kt` defines 12 `ThemePreset`s (purple, blue, teal, green, orange, pink × dark/light), stored in the
`theme_preset` preference. Default is `PURPLE_DARK`. `buildAppColors(accentHue, mode)` generates an `AppColors`
palette from the preset's hue, provided via `LocalAppColors`.

Semantic colors: `surface`, `surfaceVariant`, `cardSurface`, `onSurface`, `onSurfaceVariant`, `primary`,
`primaryContainer`, `secondary`, `tertiary`, `accent`, `trendDown`, `trendUp`, `trendFlat`, `confidenceBand`,
`aiBarBg`, `error`, `outline`.

Top-level names like `Primary`, `Surface`, `CardSurface` are `@Composable` getters over `LocalAppColors.current`,
so they only work inside composition. Don't hardcode hex colors — they won't follow the theme.

---

## Common Patterns

### Injecting dependencies into screens

`MainActivity` (Hilt `@Inject`) → `FatLossTrackNavGraph` in `Navigation.kt` → screens.
Main screens take one `state: *StateHolder` parameter. Smaller screens (goal, profile, welcome,
settings sub-screens) still take `PreferencesManager` / DAOs directly. There are no ViewModels.

### Shared components between screens

The `ui/log/` package contains `internal` composables shared between `LogScreen` and `HomeScreen`:
- `DayCard.kt` — `DayCard`, `StatChip`
- `MealSheets.kt` — `AddMealSheet`, `MealEditSheet`
- `DailyLogSheet.kt` — `DailyLogEditSheet`
- `SheetHost.kt` — `LogSheetState`, `rememberLogSheetState()`, `LogSheetHost()` (deduplicates 3 ModalBottomSheets)
- `LogHelpers.kt` — `EditField`, `categoryIcon`, `categoryColor`, `mealTypeLabel`, `launchSummary()`, JSON parsers

`TdeeState.kt` in `ui/components/` provides `rememberDailyTargetKcal()` — used by `LogScreen`, `HomeScreen`, and `TrendsScreen`.

### Fire-and-forget summary generation

```kotlin
daySummaryGenerator.launchForDate(date, reason = "mealAdded")
daySummaryGenerator.launchForDates(dates, reason = "hcSync")
```

Or use the shared helper:
```kotlin
launchSummary(date, dailyLogDao, daySummaryGenerator)  // from LogHelpers.kt
```

Don't create ad-hoc `CoroutineScope(...)`s — inject `@ApplicationScope CoroutineScope` instead.

### Passing data between screens

Larger payloads go through in-memory `object` stores instead of nav args:
`CapturedPhotoStore` (photo URIs + comment), `PendingTextMealStore` (parsed meal JSON + date),
`PendingChatStore` (initial chat message + images).

Each is backed by a `MutableStateFlow` holding one immutable value. Consume with `take()`, which reads
and clears atomically. `ChatScreen` collects `CapturedPhotoStore.captured` to attach photos from its
camera button; the analysis screen reads `captured.value` without clearing so it can re-analyze, and
clears in `cleanup()`.

### Localization

- UI strings live in `res/values/strings.xml` with translations in `values-sl` and `values-hu`.
  Add every new string to all three.
- Language is switched with `AppCompatDelegate.setApplicationLocales()` (Settings / Welcome).
- `Locale.US` is still forced as the default in `FatLossTrackApp.onCreate()` so number formatting/parsing
  doesn't use decimal commas.

---

## Gotchas & Lessons Learned

1. **Never use `fallbackToDestructiveMigration()`** — it wipes all user data on schema changes
2. **Android 16 Health Connect** crashes on 0×0 adaptive icons — always provide raster launcher icons
3. **Sleep calculation**: Use stages (filter out AWAKE types), not session duration
4. **Resting HR**: Use `RestingHeartRateRecord`, not raw `HeartRateRecord` samples
5. **HC merge**: HC overwrites synced fields; only `weightLocked` / `stepsLocked` protect manual weight/steps. If you add a manually-editable HC field, give it a lock or it will be overwritten on the next sync
6. **Locale**: Slovenian locale uses decimal commas which break `toDoubleOrNull()`. Fixed globally with `Locale.US`.
7. **Form seeding race**: `collectAsState` initial values can race `LaunchedEffect`. Goal/profile forms seed once with `LaunchedEffect(Unit)` reading each pref via `.first()`
8. **Text meal weekday resolution**: AI prompt needs current date context to resolve "last Friday" etc.
9. **Summary generation blocks UI**: Always use `launchForDate()` (placeholder + app scope), never generate on the main thread
10. **`DayCard` and helpers in `ui/log/`** are `internal`, not `private` — shared with `HomeScreen`
11. **NEVER run `adb uninstall`** — it permanently deletes ALL user data (Room DB, DataStore preferences, API keys, meal history, goals). To reinstall, ALWAYS use `adb install -r` (replace) which preserves app data. If the app crashes after install, do a clean build (`rm -rf app/mobile/build && ./gradlew :mobile:assembleDebug --no-build-cache`) and `adb install -r` again. Never uninstall.
12. **Gradle build cache can produce corrupt APKs** — if `mergeProjectDexDebug` says `FROM-CACHE` and the app crashes with `ClassNotFoundException`, clear the cache: `rm -rf ~/.gradle/caches/build-cache-* app/mobile/build app/build app/.gradle` and rebuild with `--no-build-cache`.
13. **Model ids are duplicated**: when adding/changing an OpenAI model, update `ModelSelectorScreen` (list + prices), `AiUsageScreen` (pricing), and the default in `PreferencesManager` + the `collectAsState(initial = ...)` in `SettingsScreen` / `ModelSelectorScreen`
14. **Reasoning models** can spend the whole completion budget on reasoning tokens and return empty content — `OpenAiService` guards against this; keep that in mind when lowering token limits
15. **Coach notes for the cruel tone** must come from `CruelCoachNoteGenerator`, not the meal-parse prompt — the parse prompt knows nothing about the user and the model can dodge the roast with `{"is_meal": false}`
16. **Unit tests only cover `TdeeCalculator` and `TrendEngine`** — they pin current behaviour, so update them when you change that math on purpose. Everything else still needs verifying by building and running on device

---

## Backend (Node.js)

Exists at `backend/` but is **currently bypassed** — the Android app calls OpenAI directly. Fastify 5 + `openai` SDK:

- Firebase ID token auth middleware, enforced on the AI routes
- AI endpoints with rate limiting
- Health check at `/api/health`
- Tests: `cd backend && npm test` (`node --test`)

If re-enabling the backend: update `BackendApi.kt` and switch `OpenAiService` to proxy through it.
