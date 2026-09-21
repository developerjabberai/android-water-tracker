# Purrsip (Play title: "Purrsip: Water Tracker Widget"): v1 Spec

## Platform
- Android only, minimum Android 12 (API 31)
- Kotlin. Jetpack Compose for the config app, native Glance/RemoteViews for the widget
- Local storage only

## Product principle
The home-screen widget is the product. The app exists only for configuration and a small 7-day view.

## Widget
- **Look:** one bottle for every goal, drawn from four character illustrations (`v1/design/expressions/expression-*.svg`) on a warm yellow card, with Nunito numbers. The character stays visible at rest; its outline, face and gloss are used exactly as drawn, and our own water replaces its blue body and rises with what you've drunk.
- **Daily character:** one of the four is picked at random each day. Every block of four days shows each character once in shuffled order, never the same one two days running, and the order differs per phone.
- **Goals:** all goals use the same bottle. Litre tick marks on the body tell 2 L (1 tick), 3 L (2 ticks) and 4 L (3 ticks) apart.
- **Reactions (same for every character and goal):** tap = water rises with a wave, then a happy bounce with sparkles. Unlock nudge = the character wobbles, the dotted target line draws and translucent water rises to it, then drains as a sweat drop appears. Goal reached = big hops with a shine sweep and sparkles, then a check badge.
- **Tap:** one tap logs half a glass (100 ml) by default, or a whole glass (200 ml) if the user picks that. A glass is 200 ml.
- **Fill animation:** water rises to the new level on tap, then the cat turns around briefly. Animation polish is a top priority. Approach: frame-sequence bitmap updates, since widgets can't run arbitrary animations.
- **Goal display** (goal options: 2 L, 3 L, 4 L): one bottle for all of them (see Look).
- **Pace marker:** shows how much the user should have drunk by now. It follows a front-loaded curve (rises faster in the morning and afternoon, flattens toward bedtime), running between the wake and sleep times.
- **Nudge:** the reminder is widget-only, with no notifications.
  - Fires when the user is behind pace by more than a threshold (default: half a glass)
  - Detected on unlock (ACTION_USER_PRESENT) by a minimum-priority foreground service; 10-minute cooldown between pulses; only within waking hours
  - Pulse animation on unlock, then a resting "nudge" look (a stronger blue tint on the gap) until the user logs water
  - Shown as a pulse on the widget with the gap to the pace marker highlighted
  - If the user unlocks into another app, the nudge waits on the widget. There is no fallback notification.
- **Goal reached:** one-time celebration (hops, shine sweep, sparkles), then a small check badge in the corner and the target line hides. No more nudges that day. Extra taps still log.
- **Daily reset:** midnight. An inexact alarm at 00:01 redraws the widget; unlock also rolls over a stale day, and yesterday's total is kept for the 7-day view.
- **Out of scope for MVP:** undo.

## First run
- Welcome screen with one CTA, "Add widget" (system pin-to-home dialog). The widget uses default settings until changed.
- As soon as a widget exists the app moves to the config screen. "Skip for now" is available, and launchers that cannot pin show written steps.
- Later launches go straight to the config screen.

## Config app
- Daily goal: 2 L, 3 L or 4 L
- "1 Tap on widget fills": Half glass (100 ml, default) or One glass (200 ml), where a glass is 200 ml. Shown as the same glass at two fill levels.
- Simple 7-day bar view plus today's total
- "Add widget to home screen" button, always present (no show/hide logic)

## Defaults
| Setting | Default |
|---|---|
| Goal | 2 L (choose from 2/3/4 L) |
| Glass (fixed) | 200 ml |
| Tap | 100 ml (half glass) or 200 ml (one glass) |
| Waking hours (fixed, not configurable) | 07:00 to 23:00 |
| Nudge threshold (fixed, not configurable) | 100 ml behind target |

## Reference: daily intake
Commonly cited adequate total fluid intake is about 3.7 L/day for men and 2.7 L/day for women (US National Academies), including water from food. Drinking water alone is usually 2-3 L. The app lets users set their own goal and gives no medical advice.

## Decisions log
- Artwork: the four Recraft-generated SVGs are converted to JSON by `v1/design/tools/svg_to_json.py` and drawn with android.graphics.Path. Character 4 has a stray dark mark at the bottom right that is in the original artwork and was left as drawn.
- One bottle for all goals (no two-bottle layout); litre ticks show the goal.
- Because whiskers and ears are part of each outline shape, the character no longer turns around to a plain bottle; events are reactions (bounce, wobble, hop) instead.
- Visual direction: sticker-outline bottle (bottle only, no drop or cup) with a cat face that appears only on events (tap, nudge, goal) via a turn-around animation; resting state is the plain bottle. Goal picker tiles reuse this drawing.
- Glass size setting replaced by "1 Tap on widget fills": Half glass or One glass. Daily goal picker reuses the widget bottle drawing.
- Removed wake/sleep and nudge-threshold settings to keep the config screen simple; both are fixed constants in code (Pace.kt, NudgeController.kt)
- First launch: welcome screen with Add widget as the single CTA, then config; config always keeps an add-widget button
- Goal reached: shine + sparkles once, then a check badge
- Stack: Kotlin (only option that renders widgets natively)
- Nudge: widget-only, detected on unlock via a silent foreground service (Android blocks manifest unlock receivers)
- Widget shows litres ("1.88 L") in bundled Nunito; ghost dotted level replaces the red pace line
- Interval: pace-based, not fixed hours
- Pace curve: front-loaded
- Reset: midnight
- History: 7-day view only
- No undo in MVP

## Status
Built and verified on an Android 17 (API 37) emulator: widget fill animation, ghost target level, unlock pulse, goal-reached celebration, config screen, welcome flow, midnight rollover.
Not yet verified on a physical phone: battery/OEM behaviour of the foreground service, boot restart, and the feel of the animations at real refresh rates.

## Next steps (old, superseded)
1. Install Android Studio and SDK (API 35+), create an emulator or enable USB debugging
2. Scaffold the project in `v1/`
3. Build the widget fill animation first (riskiest part), then the pace logic and nudge state, then the config app
