# 🏋️ FatLoss Track

> Because your bathroom scale shouldn't be the only thing judging you.

An Android app that tracks your weight loss journey with AI coaching, Health Connect integration, and enough charts to make a data scientist weep with joy.

## What It Does

- **📸 Snap your meals** — Point your camera at food and AI tells you how many calories you just pretended not to eat
- **🤖 AI coaching** — A brutally honest (or gentle, your choice) AI coach that summarizes your day and tells you if that midnight snack was worth it
- **📊 Trend charts** — Watch your weight line go down (hopefully) with EMA smoothing and goal projections
- **❤️ Health Connect** — Automatically pulls weight, steps, sleep, heart rate, and exercises from your wearables
- **🗣️ Natural language logging** — Type "2 eggs and toast for breakfast" and the AI figures out the rest
- **📱 Fridge scan** — Show AI what's in your fridge, get meal suggestions that aren't "just eat the cheese"

## Screenshots

*Coming soon — we're too busy losing weight to take screenshots.*

## Tech Stack

| What | With What |
|---|---|
| Language | Kotlin 2.1 |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| Database | Room |
| AI | OpenAI (direct API) |
| Health | Health Connect SDK |
| Camera | CameraX |
| HTTP | Ktor |
| Theme | Dark mode only, because we have taste |

## Getting Started

### Prerequisites

- Android Studio with SDK 36
- JDK 17
- An OpenAI API key (and the willingness to spend $0.02 per meal photo)
- A physical device or emulator running Android 9+

### Build & Run

```bash
# Build
./scripts/build.sh

# Run on your phone
./scripts/run-phone.sh

# Run on emulator
./scripts/run-emulator.sh
```

### First Launch

1. Sign in with Google
2. Go to **Settings** → set your OpenAI API key
3. Go to **Settings** → **Edit Goal** to set your target weight
4. Grant Health Connect permissions when prompted
5. Start logging meals and watch the magic happen

## Architecture

Local-first MVVM (minus the VM part — we inject DAOs straight into composables because life is too short for boilerplate). Room is the single source of truth. Health Connect syncs in. OpenAI lives in the cloud.

For the full technical deep-dive, see [AGENTS.md](AGENTS.md).

## The AI Features

| Feature | What Happens |
|---|---|
| Photo meal log | 📸 → 🤖 → "That's a burger, ~650 kcal. You know what you did." |
| Text meal log | "pizza and beer" → 🤖 → structured nutrition data |
| Fridge scan | 📸🥦🥚🧀 → 🤖 → "Make a veggie omelette, skip the cheese (just kidding, add the cheese)" |
| Daily summary | 🤖 → "Great step count at 12k! Watch dinner portions though." |
| Period summary | 🤖 → "Down 0.8kg this week. Keep it up or the chart goes red." |
| Chat | Ask anything weight-loss related. No judgment. Okay, some judgment. |

## Project Structure

```
fatloss_track/
├── app/                    # Android app (Kotlin + Compose)
│   └── mobile/             # The one and only module
├── backend/                # Node.js API (exists, currently napping)
├── scripts/                # Build & deploy helpers
├── AGENTS.md               # AI agent onboarding guide
└── idea.md, plan.md, ...   # The docs that started it all
```

## Contributing

This is a personal project, but if you want to contribute:

1. Read [AGENTS.md](AGENTS.md) first
2. Don't use `fallbackToDestructiveMigration()` (we learned this the hard way)
3. Keep `Locale.US` — Slovenian decimal commas have caused enough pain
4. Test on a real device — the emulator lies about Health Connect

## License

MIT — Use it, fork it, lose weight with it. Just don't blame us if the AI calls your portion sizes "ambitious."
