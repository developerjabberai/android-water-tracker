# Water Tracker: v1 Spec

## Platform
- Android only, minimum Android 12 (API 31)
- Kotlin. Jetpack Compose for the config app, native Glance/RemoteViews for the widget
- Local storage only

## Product principle
The home-screen widget is the product. The app exists only for configuration and a small 7-day view.

## Widget
- **Tap:** one tap logs half a glass (default glass 200 ml, so a tap is 100 ml). Users are educated that one tap = half a glass.
- **Fill animation:** water rises to the new level on tap. Animation polish is a top priority. Approach: frame-sequence bitmap updates, since widgets can't run arbitrary animations.
- **Goal display** (goal options: 2 L, 3 L, 4 L):
  - 2 L: two 1 L bottles, filling one after another
  - 3 L or 4 L: one big bottle
- **Pace marker:** shows how much the user should have drunk by now. It follows a front-loaded curve (rises faster in the morning and afternoon, flattens toward bedtime), running between the wake and sleep times.
- **Nudge:** the reminder is widget-only, with no notifications.
  - Fires when the user is behind pace by more than a threshold (default: half a glass)
  - Detected on unlock (ACTION_USER_PRESENT) by a minimum-priority foreground service; 10-minute cooldown between pulses; only within waking hours
  - Pulse animation on unlock, then a resting "nudge" look (stronger gap shading and a soft glow) until the user logs water
  - Shown as a pulse on the widget with the gap to the pace marker highlighted
  - If the user unlocks into another app, the nudge waits on the widget. There is no fallback notification.
- **Goal reached:** one-time celebration (shine sweeps across the full bottle(s) plus sparkles), then a small check badge in the corner and the target line hides. No more nudges that day. Extra taps still log.
- **Daily reset:** midnight. An inexact alarm at 00:01 redraws the widget; unlock also rolls over a stale day, and yesterday's total is kept for the 7-day view.
- **Out of scope for MVP:** undo.

## First run
- Welcome screen with one CTA, "Add widget" (system pin-to-home dialog). The widget uses default settings until changed.
- As soon as a widget exists the app moves to the config screen. "Skip for now" is available, and launchers that cannot pin show written steps.
- Later launches go straight to the config screen.

## Config app
- Daily goal: 2 L, 3 L or 4 L
- Glass size: 200, 300 or 400 ml (default 200), shown as a visual picker with what one tap adds
- Simple 7-day bar view plus today's total
- "Add widget to home screen" button, always present (no show/hide logic)

## Defaults
| Setting | Default |
|---|---|
| Goal | 2 L (choose from 2/3/4 L) |
| Glass | 200 ml |
| Tap | 100 ml |
| Waking hours (fixed, not configurable) | 07:00 to 23:00 |
| Nudge threshold (fixed, not configurable) | 100 ml behind target |

## Reference: daily intake
Commonly cited adequate total fluid intake is about 3.7 L/day for men and 2.7 L/day for women (US National Academies), including water from food. Drinking water alone is usually 2-3 L. The app lets users set their own goal and gives no medical advice.

## Decisions log
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
