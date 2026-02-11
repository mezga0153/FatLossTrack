# AGENTS.md — FatLoss Track

Guide for AI agents working on this codebase.

## Quick Reference

| Item | Value |
|---|---|
| Package | `com.fatlosstrack` |
| Language | Kotlin 2.3.0 |
| UI | Jetpack Compose + Material 3 (BOM 2026.01.01) |
| DI | Hilt 2.57.2 |
| DB | Room 2.8.4, version **8** |
| HTTP | Ktor 3.0.3 (OkHttp engine) |
| Health | Health Connect 1.2.0-alpha02 |
| Camera | CameraX 1.4.1 |
| Build | Gradle 8.11.1, AGP 8.9.1, KSP 2.3.5 |
| SDK | compileSdk/targetSdk 36, minSdk 28 |
| JDK | 17 (`/opt/homebrew/opt/openjdk@17`) |
| Device | Pixel 10 Pro XL, Android 16 (USB adb) |

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

---

## Architecture

**Local-first MVVM without ViewModels** — DAOs are injected directly into composables via Hilt, state collected with `collectAsState()`. Room is the single source of truth.

```
Compose UI ←→ DAOs (Room Flows) ←→ Room Database
                                       ↑
                              HealthConnectSyncService
                                       ↑
                              Health Connect SDK

OpenAiService → api.openai.com (direct, API key in DataStore)
```

No ViewModels exist yet. All state hoisting is done at the composable level.

---

## Project Structure

```
fatloss_track/
├── idea.md, plan.md, ui.md, development.md, build-run.md   # Specs
├── scripts/                          # Build/deploy shell scripts
├── backend/                          # Fastify Node.js API (defined but bypassed)
└── app/                              # Android Gradle project
    └── mobile/src/main/java/com/fatlosstrack/
        ├── FatLossTrackApp.kt        # @HiltAndroidApp, forces Locale.US
        ├── auth/AuthManager.kt       # Google Sign-In + Firebase Auth
        ├── data/
        │   ├── DaySummaryGenerator.kt       # AI daily coaching summaries
        │   ├── health/
        │   │   ├── HealthConnectManager.kt  # HC client (read weight/steps/sleep/HR/exercise)
        │   │   └── HealthConnectSyncService.kt  # HC → Room sync, returns changed dates
        │   ├── local/
        │   │   ├── PreferencesManager.kt    # DataStore (goal, API key, model, tone)
        │   │   ├── AppLogger.kt             # File logger (daily rotation, 7-day retention)
        │   │   ├── CapturedPhotoStore.kt    # In-memory photo URIs between screens
        │   │   ├── PendingTextMealStore.kt  # In-memory text meal data
        │   │   └── db/
        │   │       ├── Entities.kt          # 6 Room entities (includes chat_messages)
        │   │       ├── Daos.kt              # 6 DAOs
        │   │       ├── FatLossDatabase.kt   # DB v8
        │   │       └── Converters.kt        # LocalDate↔String, Instant↔Long
        │   └── remote/
        │       ├── OpenAiService.kt         # Direct OpenAI calls (chat, meal parse, photo)
        │       └── api/BackendApi.kt        # Backend proxy (exists but unused)
        ├── di/
        │   ├── DatabaseModule.kt            # Room, DAOs, HC, logger
        │   └── NetworkModule.kt             # Ktor HttpClient, BackendApi
        ├── domain/
        │   ├── TdeeCalculator.kt            # TDEE / BMR / macro targets
        │   └── trend/TrendEngine.kt         # EMA + linear projection
        └── ui/
            ├── MainActivity.kt              # Entry point, Hilt injections
            ├── Navigation.kt                # NavHost + bottom bar + floating AiBar
            ├── theme/Theme.kt               # Dark Material 3 palette
            ├── home/HomeScreen.kt           # Goal progress, trend chart, N-day stats, AI summary
            ├── log/
            │   ├── LogScreen.kt             # Day timeline + today summary (~160 lines)
            │   ├── DayCard.kt               # DayCard + StatChip composables
            │   ├── DailyLogSheet.kt         # Daily log edit bottom sheet
            │   ├── MealSheets.kt            # AddMealSheet + MealEditSheet
            │   ├── SheetHost.kt             # LogSheetState + LogSheetHost (shared by Log & Home)
            │   └── LogHelpers.kt            # Category/meal-type helpers, EditField, JSON parsers, summary scope
            ├── trends/TrendsScreen.kt       # Weight/cal/sleep/steps charts with time toggle
            ├── login/LoginScreen.kt         # Google sign-in gate
            ├── settings/
            │   ├── SettingsScreen.kt        # Config, HC sync, sign out
            │   ├── SetGoalScreen.kt         # Goal editor
            │   ├── SetProfileScreen.kt      # Profile editor (sex, age, height, activity)
            │   └── LogViewerScreen.kt       # Debug log viewer
            ├── camera/
            │   ├── MealCaptureScreen.kt     # CameraX multi-photo capture
            │   ├── AnalysisResultScreen.kt  # AI meal analysis display + edit
            │   └── CameraModeSheet.kt       # Log vs Suggest mode picker
            ├── chat/ChatScreen.kt           # AI freeform chat
            └── components/
                ├── AiBar.kt                 # Floating AI input pill
                ├── TrendChart.kt            # Canvas-based weight chart
                ├── SimpleLineChart.kt       # Generic line chart
                ├── StackedBarChart.kt       # MacroBarChart composable
                ├── InfoCard.kt              # Reusable stat card
                └── TdeeState.kt             # rememberDailyTargetKcal() composable helper
```

---

## Database (Room, v8)

### Entities

| Table | PK | Key Fields |
|---|---|---|
| `weight_entries` | `id` (auto) | `date`, `valueKg`, `source` (MANUAL/HEALTH_CONNECT) |
| `meal_entries` | `id` (auto) | `date`, `description`, `itemsJson?`, `totalKcal`, `totalProteinG`, `totalCarbsG`, `totalFatG`, `coachNote?`, `category` (HOME/RESTAURANT/FAST_FOOD), `mealType?` (BREAKFAST/BRUNCH/LUNCH/DINNER/SNACK) |
| `goals` | `id` (auto) | `targetKg`, `rateKgPerWeek`, `deadline`, `dailyDeficitKcal?` |
| `daily_logs` | `date` (PK) | `weightKg?`, `steps?`, `sleepHours?`, `restingHr?`, `exercisesJson?`, `notes?`, `offPlan`, `daySummary?` |
| `insights` | `id` (auto) | `date`, `type` (PATTERN/TRADEOFF), `message`, `dataJson?` |

### Migrations

- **4→5**: `ALTER TABLE daily_logs ADD COLUMN daySummary TEXT DEFAULT NULL`
- **5→6**: `ALTER TABLE meal_entries ADD COLUMN totalProteinG INTEGER NOT NULL DEFAULT 0`
- **6→7**: `CREATE TABLE chat_messages ...`
- **7→8**: `ALTER TABLE meal_entries ADD COLUMN totalCarbsG INTEGER NOT NULL DEFAULT 0` + `ALTER TABLE meal_entries ADD COLUMN totalFatG INTEGER NOT NULL DEFAULT 0`
- Uses `addMigrations()` — **never** use `fallbackToDestructiveMigration()` (it wipes user data)

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
| `trends` | TrendsScreen | |
| `log` | LogScreen | |
| `settings` | SettingsScreen | |
| `chat` | ChatScreen | |
| `set_goal` | SetGoalScreen | From settings |
| `set_profile` | SetProfileScreen | From settings |
| `log_viewer` | LogViewerScreen | From settings (debug) |
| `capture/{mode}?targetDate={date}` | MealCaptureScreen | mode = "log" or "suggest" |
| `analysis/{mode}/{count}?targetDate={date}` | AnalysisResultScreen | Photo analysis |
| `analysis/text` | AnalysisResultScreen | Text meal analysis |

The **AiBar** floats above the bottom nav on main screens except Chat/camera/analysis. It handles:
- Text meal parsing → `analysis/text`
- Camera → `CameraModeSheet` → `capture/{mode}`
- Freeform chat → inline response card

Chrome (bottom bar + AiBar) hides on camera, analysis, set_goal, set_profile, log_viewer screens; AiBar also hides on Chat.

---

## AI Integration

The app calls **OpenAI directly** (`OpenAiService`). The API key is user-provided and stored in DataStore. The backend proxy exists but is bypassed.

### AI Features

| Feature | Entry Point | Description |
|---|---|---|
| Freeform chat | `OpenAiService.chat()` | Weight-loss Q&A |
| Text meal parse | `OpenAiService.parseTextMeal()` | "2 eggs and toast" → structured JSON with kcal, items, day_offset |
| Photo meal log | `OpenAiService.analyzeMeal(mode=LogMeal)` | Vision API: photos → nutrition breakdown |
| Photo suggest | `OpenAiService.analyzeMeal(mode=SuggestMeal)` | Vision API: fridge photos → meal ideas |
| Day summary | `DaySummaryGenerator.generateForDate()` | 1-2 sentence coaching note per day, stored in `daily_logs.daySummary` |
| Period summary | HomeScreen (inline) | N-day trend summary, cached in module-level var |

### Day summaries trigger on:
- Health Connect sync (for changed dates)
- Manual daily log edit
- Meal add/edit/delete
- Camera meal logged

Day summaries write `"⏳"` placeholder immediately (shows loading indicator), then generate async via `CoroutineScope(SupervisorJob() + Dispatchers.IO)`.

---

## Health Connect

### Permissions (all READ)
`WEIGHT`, `STEPS`, `SLEEP`, `HEART_RATE`, `RESTING_HEART_RATE`, `EXERCISE`, `ACTIVE_CALORIES_BURNED`

### Sync behavior
- Auto-syncs last 7 days on app open
- Manual sync from Settings
- **Merge rule**: HC fills `null` fields only — manual entries always win
- Sleep: uses stages (excludes AWAKE types), time window 18:00-14:00
- Resting HR: prefers `RestingHeartRateRecord`, fallback = median of bottom quartile of raw HR
- Exercises: mapped to 24+ exercise types with name/duration/kcal

---

## Theme & Colors

Dark Material 3 theme defined in `Theme.kt`:

| Name | Hex | Usage |
|---|---|---|
| `Surface` | `#0D0D1A` | App background |
| `CardSurface` | `#16162A` | Card backgrounds |
| `Primary` | `#6C9CFF` | CTAs, trend lines, highlights |
| `Secondary` | `#59D8A0` | Positive trends, green accents |
| `Tertiary` | `#FF6B6B` | Negative trends, errors |
| `Accent` | `#CDA0FF` | AI bar glow, violet highlights |
| `OnSurface` | `#E8E8F0` | Primary text |
| `OnSurfaceVariant` | `#8B8BA3` | Secondary text |

---

## Common Patterns

### Injecting dependencies into screens

Dependencies flow: `MainActivity` (Hilt `@Inject`) → `Navigation.kt` → individual screens as parameters. There are no ViewModels.

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
CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
    daySummaryGenerator?.generateForDate(date)
}
```

Or use the shared helper:
```kotlin
launchSummary(date, dailyLogDao, daySummaryGenerator)  // from LogHelpers.kt
```

### Locale

`Locale.US` is forced in `FatLossTrackApp.onCreate()` to prevent decimal comma issues (Slovenian locale). All number formatting uses US locale implicitly.

---

## Gotchas & Lessons Learned

1. **Never use `fallbackToDestructiveMigration()`** — it wipes all user data on schema changes
2. **Android 16 Health Connect** crashes on 0×0 adaptive icons — always provide raster launcher icons
3. **Sleep calculation**: Use stages (filter out AWAKE types), not session duration
4. **Resting HR**: Use `RestingHeartRateRecord`, not raw `HeartRateRecord` samples
5. **HC merge**: Always check `existing.field ?: newValue` to preserve manual entries
6. **Locale**: Slovenian locale uses decimal commas which break `toDoubleOrNull()`. Fixed globally.
7. **Goal form race condition**: `collectAsState` initial values can race `LaunchedEffect` — gate on non-null
8. **Text meal weekday resolution**: AI prompt needs current date context to resolve "last Friday" etc.
9. **Summary generation blocks UI**: Always do async with placeholder, never on main thread
10. **`DayCard` and helpers in `ui/log/`** are `internal`, not `private` — shared with `HomeScreen`

---

## Backend (Node.js)

Exists at `backend/` but is **currently bypassed** — the Android app calls OpenAI directly. The backend provides:
- Firebase token auth middleware
- 5 AI endpoints with rate limiting
- Health check at `/api/health`

If re-enabling the backend: update `BackendApi.kt` and switch `OpenAiService` to proxy through it.
