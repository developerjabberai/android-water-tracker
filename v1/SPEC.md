# Water Tracker: v1 Spec

## Platform
- Android only, minimum Android 12 (API 31)
- Kotlin. Jetpack Compose for the config app, native Glance/RemoteViews for the widget
- Local storage only

## Product principle
The home-screen widget is the product. The app exists only for configuration and a small 7-day view.

## Widget
- **Tap:** one tap logs half a glass (default glass 250 ml, so a tap is 125 ml). Users are educated that one tap = half a glass.
- **Fill animation:** water rises to the new level on tap. Animation polish is a top priority. Approach: frame-sequence bitmap updates, since widgets can't run arbitrary animations.
- **Goal display** (goal options: 2 L, 3 L, 4 L):
  - 2 L: two 1 L bottles, filling one after another
  - 3 L or 4 L: one big bottle
- **Pace marker:** shows how much the user should have drunk by now. It follows a front-loaded curve (rises faster in the morning and afternoon, flattens toward bedtime), running between the wake and sleep times.
- **Nudge:** the reminder is widget-only, with no notifications.
  - Fires when the user is behind pace by more than a threshold (default: half a glass)
  - Deferred until the next screen-on/unlock, so it only shows when the phone is in use
  - Shown as a pulse on the widget with the gap to the pace marker highlighted
  - If the user unlocks into another app, the nudge waits on the widget. There is no fallback notification.
- **Goal reached:** one-time celebration animation, then "Goal reached". No more nudges that day. Extra taps still log.
- **Daily reset:** midnight.
- **Out of scope for MVP:** undo.

## Config app
- Daily goal: 2 L, 3 L or 4 L
- Glass size (default 250 ml)
- Wake and sleep times (defaults 07:00 and 23:00)
- Nudge sensitivity (default: half a glass behind pace)
- Simple 7-day bar view plus today's total
- Widget-setup guide (how to add the widget to the home screen)

## Defaults
| Setting | Default |
|---|---|
| Goal | 2 L (choose from 2/3/4 L) |
| Glass | 250 ml |
| Tap | 125 ml |
| Wake / sleep | 07:00 / 23:00 |
| Nudge threshold | 125 ml behind pace |

## Reference: daily intake
Commonly cited adequate total fluid intake is about 3.7 L/day for men and 2.7 L/day for women (US National Academies), including water from food. Drinking water alone is usually 2-3 L. The app lets users set their own goal and gives no medical advice.

## Decisions log
- Stack: Kotlin (only option that renders widgets natively)
- Nudge: widget-only, deferred until next unlock
- Interval: pace-based, not fixed hours
- Pace curve: front-loaded
- Reset: midnight
- History: 7-day view only
- No undo in MVP

## Next steps
1. Install Android Studio and SDK (API 35+), create an emulator or enable USB debugging
2. Scaffold the project in `v1/`
3. Build the widget fill animation first (riskiest part), then the pace logic and nudge state, then the config app
