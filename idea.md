# FatLoss Track

**A trend-based, brutally honest weight-loss debugger for Android.**

FatLoss Track is *not* a calorie tracker. It answers one question better than anyone else:

> "Why am I not losing weight, even though I think I am?"

---

## Vision

Most weight-loss apps obsess over daily numbers and gamify streaks. FatLoss Track takes the opposite approach: it shows the user their real trajectory, surfaces hidden patterns that stall progress, and frames food as tradeoffs — not morality. The AI doesn't motivate; it explains.

---

## Core Principles (non-negotiable)

| Principle | Meaning |
|---|---|
| **Trends > Days** | Never panic over a single weigh-in |
| **Patterns > Numbers** | Surface *why*, not just *what* |
| **Honesty > Motivation** | No cheerleading — direct, factual feedback |
| **Uncertainty is shown, not hidden** | Always display confidence ranges |
| **AI explains tradeoffs, not rules** | Choices with consequences, not moral judgments |

*If a feature violates any of these → cut it.*

---

## Target Platform & Tech Stack

| Layer | Technology |
|---|---|
| Platform | Android (Kotlin) |
| Health data | Health Connect API |
| AI backend | OpenAI API (GPT) |
| Meal vision | OpenAI Vision API (photo → estimate) |

---

## MVP Feature Set

### 1. Data Ingestion

Fast, clean data collection from two sources.

**Health Connect (automatic sync)**
- Weight
- Steps / activity
- Sleep *(optional but powerful for pattern detection)*

**Manual input**
- Weight (always editable, overrides Health Connect)
- Meals (rough, fast — see Feature 5)
- "Off-plan day" toggle (feeds into pattern detection)

> **Design rule:** Any logging interaction must take **< 30 seconds**.

---

### 2. Smart Goal Definition

Instead of a vague "Lose 10 kg", the user sets:

- **Target weight**
- **Target weekly loss rate** (e.g. 0.5 kg/week)
- **Deadline** — auto-calculated from the above, adjustable by the user

On confirmation, the app immediately shows:

> *"This requires ~X kcal/day deficit on average."*

No magic. No optimism. Just math.

---

### 3. Trend Engine *(the backbone)*

The app **never** shows *"You gained 0.6 kg today"* as a big red panic screen.

Instead it shows:

- **7-day weighted moving average** of weight
- **Projected trajectory** to goal
- **Deviation from plan** (above/below trend)

Example messages:
> *"You are 0.9 kg above your projected trend."*
>
> *"At this rate, you'll reach your goal on May 12 — not April 2."*

This alone already beats 80% of weight-loss apps.

---

### 4. Brutally Honest Coach *(opt-in, the differentiator)*

**Onboarding question:**
> "Do you want a polite coach or an honest one?"

If the user chooses **honest**, the app does not pull punches.

Example messages:
> *"You didn't plateau. You ate more."*
>
> *"Your weekends erase your weekdays."*
>
> *"You logged food 3 out of 7 days. That's not 'tracking'."*

**Tone guidelines:**
- Direct and factual
- No emojis
- No therapy language
- Data-backed — every statement tied to real numbers

---

### 5. Meal Logging *(honest, not fake-precise)*

Two input modes:

**A. Manual quick log (one-tap categories)**
- Home-cooked
- Restaurant
- Fast food
- Alcohol included (yes/no)

**B. Photo capture**
- Snap a photo of the plate *or* receipt
- AI estimates category + rough calorie range
- User can correct with one tap

**Critical rule:** Never claim false precision.

The UI explicitly shows:
> *"Estimate range: 700–900 kcal (low confidence)"*

If you hide uncertainty, you're lying.

---

### 6. Pattern Detection *(where AI earns its keep)*

Weekly or bi-weekly AI-generated insights that surface *why-level* explanations:

> *"You lose weight 4 days, then regain it in 2."*
>
> *"Your highest-calorie days correlate with < 6 h sleep."*
>
> *"You under-eat Mon–Thu, then overeat Fri–Sat."*
>
> *"Alcohol days delay weight loss by ~48 hours."*

These are **diagnostic insights**, not generic tips.

---

### 7. Menu & Fridge Scanner *(AI as a decision tool)*

Two modes that turn the camera into a real-time food advisor:

**A. Restaurant Menu Scanner**
- Snap one or more photos of a physical menu
- AI parses the items, estimates calorie ranges, and **ranks them by fit** against the user's current trend and remaining budget
- Output example:

> *"Best options for staying on track tonight:*
> *1. Grilled salmon salad (~550–700 kcal) — keeps you on plan*
> *2. Chicken pasta (~800–1000 kcal) — costs ~1 lighter day*
> *3. Burger + fries (~1100–1400 kcal) — pushes goal by ~3 days"*

- User taps their choice → auto-logged as that meal

**B. Fridge / Pantry Scanner**
- Take a photo of what's in the fridge or on the counter
- AI identifies visible ingredients and suggests 2–3 simple meals ranked by goal alignment
- Output example:

> *"With eggs, spinach, and feta I'd suggest:*
> *1. Spinach & feta omelette (~350–450 kcal) — great fit*
> *2. Egg-fried rice (if you have leftover rice) (~500–650 kcal) — solid*
> *No recipe novel — just ingredients, rough steps, and calorie range."*

**Design rules:**
- Always show estimate ranges, never exact numbers
- Rankings are relative to the user's current weekly trend, not generic "healthy" labels
- Suggestions update if the user had a heavy or light day — context-aware, not static

---

### 8. Tradeoff Explanations *(not rules)*

Instead of:
> ~~"Don't eat pizza."~~

The app shows:
> *"If you eat this meal, you'll need either:*
> *– 2 lighter days, or*
> *– accept a 3–4 day delay in progress."*

This reframes food as **choices with consequences**, not morality.

---

### 9. Ask AI *(freeform, context-aware chat)*

A persistent text field where the user can type **anything** and get an honest, personalized answer.

The AI has full context:
- Current goal and timeline
- Weight trend and trajectory
- Logged meals and patterns
- Sleep and activity data (if connected)
- Coach tone preference (polite or honest)

**Example prompts and responses:**

> **User:** *"Can I have pizza tonight?"*
> **AI:** *"You've been 200 kcal/day over trend this week. Pizza (~900–1200 kcal) would push your goal date from March 8 to March 14. If you want it, skip the sides and you're looking at ~3 days instead of 6."*

> **User:** *"Why did I gain weight this week?"*
> **AI:** *"You didn't — your 7-day average dropped 0.2 kg. Tuesday's spike was water retention after Sunday's high-sodium restaurant meal. You're still on trend."*

> **User:** *"What should I eat before my run tomorrow?"*
> **AI:** *"Based on your logged dinners this week, you're averaging ~1600 kcal/day — slightly under. A banana + toast (~250–350 kcal) before the run won't hurt your trend and will actually help performance."*

**Design rules:**
- AI never gives medical advice — hard guardrail
- Responses are grounded in the user's actual data, not generic tips
- Tone matches the coach preference set during onboarding
- If there's not enough data to answer, AI says so: *"I only have 3 days of data — ask me again next week for a real answer."*

---

## User Flow (MVP)

```
Onboard → Set goal → Connect Health Connect → Daily logging loop
                                                     │
                                          ┌──────────┼──────────┐
                                          ▼          ▼          ▼
                                      Weigh-in   Log meal   View trend
                                          │          │          │
                                          └──────────┼──────────┘
                                                     ▼
                                             Weekly AI insight
                                             (pattern + tradeoff)
```

---

## What Makes This Different

| Typical app | FatLoss Track |
|---|---|
| Celebrates streaks | Shows real trajectory |
| Precise calorie counts | Honest estimate ranges |
| "Great job!" after logging | "Your weekends erase your weekdays." |
| Daily weight = success/failure | 7-day trend = signal |
| Generic tips | Personalized pattern analysis |
| Rules ("don't eat X") | Tradeoffs ("X costs you Y days") |

---

## Future Considerations (post-MVP)

- **Wearable deep-integration** — resting heart rate, HRV as stress/recovery signals
- **Social accountability** — optional shared dashboards with a partner or coach
- **Adaptive goals** — AI suggests adjusting the rate based on observed metabolism
- **Export / share** — generate a PDF summary for a doctor or nutritionist
- **Widget** — home-screen trend graph for at-a-glance progress
