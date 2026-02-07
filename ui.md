# FatLoss Track — UI & UX Specification (MVP)

**Purpose:**  
Build a trend-first, anxiety-resistant weight-loss app that explains *why progress stalls*, instead of reacting to daily noise.

This document defines **non-negotiable UI principles, screen structure, and interaction rules**.  
If a UI decision contradicts this doc, it is wrong.

---

## Core UI Principles (Non-Negotiable)

1. **Trends dominate. Days are secondary.**
2. **No daily success/failure framing.**
3. **No false precision. Always show ranges & uncertainty.**
4. **Neutral, analytical tone. No cheerleading.**
5. **Data before opinions. AI explains, it doesn’t motivate.**
6. **Fast input (<30 seconds) or it doesn’t ship.**

---

## Global Layout

### Bottom Navigation (fixed)

[ Home ] [ Trends ] [ Log ] [ Settings ]

- No additional tabs.
- Meals, exercise, sleep are **inputs**, not destinations.

### Global AI Bar (Persistent)

- Floating pill above system navigation (Pixel-style)
- Available on **all screens**
- Actions:
  - Text input
  - Voice input
  - Camera upload (meal / menu / fridge)

**Purpose:** real-time decision support & explanations  
**Not:** browsing historical data

---

## Home Screen (Status Overview)

**Primary question answered:**  
> “Am I on track *this week*?”

### 1. Primary Trend Card (Top, dominant)

- 7-day weighted moving average (line)
- Goal projection line
- Confidence band (historical variance)
- One sentence summary, e.g.:
  - “You are 0.6 kg above your projected trend.”
  - “At this rate, goal is reached on May 18.”

**Tap → Trends (Weight view)**

❌ No daily weight emphasis  
❌ No green/red success colors

---

### 2. Global Status Cards (2–3 max)

Each card = **one factual sentence**, data-backed.

Examples:

- **Consistency**
  - “You logged data on 4 of the last 7 days.”
- **Energy Balance**
  - “Estimated intake ~180 kcal/day above plan.”
- **Sleep Impact**
  - “<6h sleep correlates with +0.4 kg fluctuations.”

**Tap → Trends (pre-filtered view)**

If it can’t be said in one sentence, it doesn’t belong here.

---

### 3. Today & Yesterday Cards

Only place where day-level data appears on Home.

Each card shows:

- Weight (if logged)
- Meals (count + category icons)
- Activity summary
- Alcohol indicator (yes/no)

Actions:

- **Edit Day** (primary)
- Tap card → Day Detail (Log tab)

Neutral colors. No praise. No warnings.

---

## Log Tab (Day-Level Data)

**Purpose:** factual record of what happened  
**Not:** performance evaluation

### Default View

- Scrollable list of day cards:
  - Today
  - Yesterday
  - Older days
- Collapsible by **week**
- Weeks collapsible into **months**

### Day Card Contents

- Same layout as Home day cards
- Minimal, factual

---

### Day Detail View

Editable sections:

- Weight (source shown: Health Connect / Manual)
- Meals
- Activity
- Sleep
- Off-plan toggle
- Optional notes

Editing UX:

- Bottom-sheet modal
- Large tap targets
- Camera vs manual clearly separated
- Always optimized for speed

---

## Trends Tab (Analytical Core)

**Read-only. No editing. No input.**

### 1. Weight Trend (Default)

- 7-day average
- Goal projection
- Confidence band
- Time range toggle: 30 / 60 / 90 days

Daily dots hidden by default.

---

### 2. Deviation & Diagnosis Cards

Examples:

- “Weekends erase ~70% of weekday deficit.”
- “Alcohol days delay weight loss ~48 hours.”

Tap → explanation modal (AI-generated, cached)

---

### 3. Pattern Library

Filters:

- Food timing
- Sleep
- Alcohol
- Activity

AI explains **patterns**, never gives rules.

---

## AI Interaction Rules

- AI always has access to:
  - Current trend state
  - Goal & timeline
  - Logged meals & activity
  - Coach tone preference
- AI must:
  - Reference real user data
  - Show uncertainty
  - Say “not enough data” when applicable
- AI must NOT:
  - Give medical advice
  - Moralize food
  - Invent precision

---

## Visual Style Guidelines

- Dark theme first
- Muted, neutral palette
- No streaks, badges, fireworks, or celebrations
- Charts resemble **instrument panels**, not fitness posters

---

## Forbidden UI Patterns

❌ Daily weight as primary metric  
❌ Red/green “good/bad” framing  
❌ Exact calorie claims  
❌ Streaks or gamification  
❌ Motivational quotes  
❌ “Great job!” style feedback  

---

## Design North Star

If the user opens the app and:

- Panics about today’s weight → **FAIL**
- Understands their weekly trajectory → **SUCCESS**
