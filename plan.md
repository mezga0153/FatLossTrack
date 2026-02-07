# FatLoss Track — Technical Plan

## Project Structure

```
fatloss_track/
├── app/                        # Android app (Kotlin)
├── backend/                    # Node.js API (Fastify)
├── idea.md                     # Product vision
└── plan.md                     # This file
```

---

## Tech Stack

### Android App (`app/`)

| Component | Technology | Purpose |
|---|---|---|
| Language | Kotlin | — |
| UI | Jetpack Compose + Material 3 | Declarative UI |
| Local DB | Room | Weight, meals, goals, insights |
| Background jobs | WorkManager | Health Connect sync, trend recalc |
| Health data | Health Connect API | Weight, steps, sleep |
| Camera | CameraX | Meal photo, menu/fridge scanner |
| Charts | MPAndroidChart | Trend line, projection, deviation |
| Networking | Ktor Client (or Retrofit) | Backend API calls |
| DI | Hilt | Dependency injection |
| Backup/sync | Google Drive App Data Folder | Encrypted Room DB backup |

### Backend (`backend/`)

| Component | Technology | Purpose |
|---|---|---|
| Runtime | Node.js | — |
| Framework | Fastify | Lightweight, fast HTTP |
| AI proxy | OpenAI API (GPT + Vision) | All AI calls routed through backend |
| Auth | Firebase Auth token verification | Validate Android client tokens |
| Rate limiting | Redis (or in-memory Map for single-user) | Prevent runaway API costs |
| Config | dotenv / environment variables | API keys, rate limits |

### Sync & Backup

| What | How |
|---|---|
| Primary data store | Room (local-first, offline-capable) |
| Backup | Google Drive App Data Folder (app-private, auto-synced) |
| Encryption | AES-256 on the DB file before upload |
| Trigger | WorkManager periodic task (daily) + manual |

---

## Architecture Overview

```
┌─────────────────────────────────────┐
│           Android App               │
│                                     │
│  Compose UI ←→ ViewModels           │
│       ↕              ↕              │
│  Room DB        Repository Layer    │
│    ↕                 ↕              │
│  WorkManager    Ktor/Retrofit ──────┼──→  Backend (Fastify)
│    ↕                                │          ↕
│  Health Connect                     │     OpenAI API
│  Google Drive backup                │
└─────────────────────────────────────┘
```

All AI features go through the backend — the app never holds an OpenAI key.

---

## Backend Design (`backend/`)

### Routes

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/ai/chat` | Ask AI (freeform context-aware chat) |
| `POST` | `/api/ai/meal-photo` | Photo → meal estimate (Vision API) |
| `POST` | `/api/ai/menu-scan` | Menu photo → ranked options (Vision API) |
| `POST` | `/api/ai/fridge-scan` | Fridge photo → meal suggestions (Vision API) |
| `POST` | `/api/ai/insights` | Weekly pattern detection from user data |
| `GET`  | `/api/health` | Health check |

### Auth Flow

1. Android app authenticates with Firebase Auth (Google Sign-In)
2. App sends Firebase ID token in `Authorization: Bearer <token>` header
3. Backend verifies token via Firebase Admin SDK
4. Extract `uid` → use as rate-limit key

### Request Shape (all AI routes)

```json
{
  "context": {
    "goal": { "target_kg": 80, "rate_kg_per_week": 0.5, "deadline": "2026-06-01" },
    "trend": { "avg_7d": 85.2, "direction": "down", "deviation_kg": 0.9 },
    "today": { "meals_logged": 2, "estimated_kcal_range": [1200, 1500] },
    "patterns": { "days_logged_this_week": 5, "off_plan_days": 1 },
    "coach_tone": "honest"
  },
  "prompt": "Can I have pizza tonight?",
  "image_base64": null
}
```

The app assembles context locally from Room and sends it with each request. The backend doesn't store user data — it's a stateless proxy.

### Rate Limiting

| Limit | Value |
|---|---|
| Chat requests | 30/hour per user |
| Vision requests | 15/hour per user |
| Insight generation | 3/day per user |

In-memory `Map<uid, { count, resetAt }>` for single-user deploy. Swap to Redis if needed.

### Error Responses

```json
{ "error": "rate_limit", "message": "Too many requests. Resets in 12 minutes.", "retry_after_s": 720 }
{ "error": "ai_error", "message": "AI service unavailable. Try again." }
{ "error": "unauthorized", "message": "Invalid or expired token." }
```

---

## Android App Design (`app/`)

### Module / Package Structure

```
app/src/main/java/com/fatlosstrack/
├── di/                     # Hilt modules
├── data/
│   ├── local/
│   │   ├── db/             # Room database, DAOs, entities
│   │   └── prefs/          # DataStore preferences
│   ├── remote/
│   │   └── api/            # Backend API client
│   ├── health/             # Health Connect data source
│   └── repository/         # Repository implementations
├── domain/
│   ├── model/              # Domain models (Weight, Meal, Goal, Trend)
│   ├── trend/              # Trend engine (weighted moving average, projection)
│   └── usecase/            # Use cases
├── ui/
│   ├── onboarding/         # Onboard + goal setup
│   ├── dashboard/          # Main screen: trend chart + status
│   ├── logging/            # Weight + meal logging
│   ├── scanner/            # Camera (meal, menu, fridge)
│   ├── insights/           # AI pattern insights
│   ├── chat/               # Ask AI screen
│   ├── settings/           # Preferences, coach tone, backup
│   └── components/         # Shared composables
├── worker/                 # WorkManager workers
│   ├── HealthSyncWorker.kt
│   ├── TrendCalcWorker.kt
│   └── BackupWorker.kt
└── FatLossTrackApp.kt      # Application class
```

### Room Database

**Entities:**

```
WeightEntry(id, date, value_kg, source[manual|health_connect], created_at)
MealEntry(id, date, category[home|restaurant|fastfood], has_alcohol, kcal_low, kcal_high, confidence[low|medium|high], photo_uri?, note?, created_at)
Goal(id, target_kg, rate_kg_per_week, deadline, daily_deficit_kcal, created_at)
DailyLog(date, off_plan, steps?, sleep_hours?)
Insight(id, date, type[pattern|tradeoff], message, data_json)
```

### Trend Engine (local, no network needed)

```
Input:  List<WeightEntry> (last 30 days)
Output: TrendResult(
  avg_7d: Double,
  avg_14d: Double,
  projected_goal_date: LocalDate,
  deviation_from_plan_kg: Double,
  direction: UP | DOWN | FLAT,
  confidence_range: Pair<Double, Double>
)
```

Algorithm: Exponentially weighted moving average (EMA) with α = 2/(N+1), N = 7. Projection via linear regression on EMA values.

Runs locally via WorkManager after each new weight entry.

### Key Screens

| Screen | What it shows |
|---|---|
| **Dashboard** | 7-day trend chart (MPAndroidChart), projected date, deviation message, quick-log buttons |
| **Log Weight** | Number input, date picker, one tap save (< 10s) |
| **Log Meal** | Category selector, alcohol toggle, optional photo, optional note (< 30s) |
| **Scanner** | CameraX viewfinder → capture → send to backend → show results → one-tap log |
| **Insights** | List of AI-generated pattern cards, newest first |
| **Ask AI** | Chat-style UI, user types freely, context sent automatically |
| **Onboarding** | Weight, goal, rate, coach tone (honest/polite), Health Connect permission |

### Health Connect Integration

```
Permissions needed:
- android.permission.health.READ_WEIGHT
- android.permission.health.READ_STEPS
- android.permission.health.READ_SLEEP

Sync strategy:
- WorkManager periodic (every 6 hours)
- Also on app open
- Dedup by date, manual entry always wins
```

### Google Drive Backup

```
1. Room DB → export to temp file
2. Encrypt with AES-256 (key derived from user passphrase or device key)
3. Upload to Drive App Data Folder (hidden from user's Drive UI)
4. On restore: download → decrypt → replace Room DB → restart
5. Trigger: daily via WorkManager + manual from settings
```

---

## Build Order (MVP phases)

### Phase 1 — Skeleton + Trend (no AI yet)

**Backend:**
- [ ] Fastify project scaffold, health route, env config
- [ ] Firebase Auth token verification middleware
- [ ] OpenAI proxy route (chat) with rate limiting

**App:**
- [ ] Project scaffold (Compose, Hilt, Room, Navigation)
- [ ] Onboarding flow (goal setup, coach tone)
- [ ] Room DB with WeightEntry + Goal entities
- [ ] Manual weight logging screen
- [ ] Trend engine (EMA + projection)
- [ ] Dashboard with MPAndroidChart trend line
- [ ] Health Connect weight sync (WorkManager)

**Result:** App that logs weight, shows trend, projects goal date. No AI.

### Phase 2 — Meal Logging + AI Chat

**Backend:**
- [ ] `/api/ai/chat` route with context-aware system prompt
- [ ] `/api/ai/meal-photo` route (Vision API)

**App:**
- [ ] Meal logging screen (categories + alcohol toggle)
- [ ] Camera capture for meal photos (CameraX)
- [ ] Backend API client (Ktor)
- [ ] Ask AI chat screen
- [ ] Tradeoff messages on meal log ("this costs X days")

**Result:** Full logging + freeform AI chat.

### Phase 3 — Pattern Detection + Scanners

**Backend:**
- [ ] `/api/ai/insights` route
- [ ] `/api/ai/menu-scan` route
- [ ] `/api/ai/fridge-scan` route

**App:**
- [ ] Weekly insight generation (WorkManager trigger)
- [ ] Insights screen (pattern cards)
- [ ] Menu scanner flow
- [ ] Fridge scanner flow
- [ ] Google Drive encrypted backup

**Result:** Full MVP feature set.

---

## System Prompts (backend, per route)

### Chat (`/api/ai/chat`)

```
You are a weight-loss analyst. You are {tone} — direct and factual, never motivational.
You have the user's data: goal, trend, meals, patterns.
Frame food as tradeoffs, not rules. Show uncertainty. Never give medical advice.
If data is insufficient, say so.
```

### Meal Photo (`/api/ai/meal-photo`)

```
Analyze this meal photo. Estimate:
1. What the meal likely is
2. Calorie range (low–high), be honest about confidence
3. Category: home-cooked, restaurant, or fast food
Return JSON: { "description": "...", "kcal_low": N, "kcal_high": N, "confidence": "low|medium|high", "category": "..." }
```

### Menu/Fridge Scan

```
Given the user's current trend ({deviation} from plan, {days_to_goal} days remaining):
Analyze this {menu|fridge} photo.
Rank options by how well they fit the user's current trajectory.
Show calorie ranges, not exact numbers. Frame as tradeoffs.
```

---

## Environment & Deploy

### Backend

```bash
# Development
cd backend && npm run dev     # Fastify with --watch

# Production (single user)
# Any VPS, Railway, Fly.io, or Render
# Env vars: OPENAI_API_KEY, FIREBASE_PROJECT_ID
```

### App

```bash
# Development
cd app && ./gradlew installDebug

# Release
# Standard Android signing + Play Store flow
```

---

## Open Decisions

| Decision | Options | Leaning |
|---|---|---|
| HTTP client | Ktor Client vs Retrofit | Ktor (Kotlin-native, coroutines) |
| Image transfer | Base64 in JSON vs multipart | Base64 (simpler, images are small) |
| Backup encryption key | User passphrase vs device keystore | Device keystore (less friction) |
| Rate limit store | In-memory Map vs Redis | In-memory (single user MVP) |
| Coach tone implementation | System prompt swap vs separate models | System prompt swap |
