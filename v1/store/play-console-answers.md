# Google Play Console: answers to copy in

App ID `com.developerjabberai.watertracker` · Title "Widget: Water Tracker & Remind" (app name on the phone: "Water Tracker") · version 1.0.8 (code 9) · signed AAB at
`v1/WaterTracker/app/build/outputs/bundle/release/app-release.aab`

## 1. Create the app
- App or game: **App** · Free or paid: **Free** · Declarations: accept the developer program policies and US export laws.

## 2. Store listing (Main store listing)
- Text: `v1/store/listing.md`
- App icon: `v1/store/icon-512.png` (512×512)
- Feature graphic: `v1/store/feature-graphic-1024x500.png`
- Phone screenshots (upload all five, in order): `v1/store/screenshots/1-home.png` … `5-settings.png`
- Category: **Health & Fitness** · Email: your support email · Website (optional): https://developerjabberai.github.io/android-water-tracker/

## 3. App content
| Form | Answer |
|---|---|
| **Privacy policy** | https://developerjabberai.github.io/android-water-tracker/privacy-policy.html |
| **Ads** | No, the app contains no ads |
| **App access** | All functionality is available without any special access or login |
| **Content rating (IARC)** | Category: *Utility, Productivity, Communication or Other*. Answer **No** to every question (violence, sexual content, language, controlled substances, gambling, user-generated content, location sharing, purchases). Expected result: Everyone |
| **Target audience** | **13 and over** (not designed for children, so no Families programme) |
| **News app** | No |
| **COVID-19 contact tracing / status app** | No |
| **Data safety** | *Does the app collect or share any of the required user data types?* **No.** The app requests no internet permission and stores everything on-device. Encrypted in transit: not applicable. Data deletion: not applicable (nothing collected). Note Android Auto Backup of a small settings file goes to the user's own Google account, not to the developer, so it is not "collection" |
| **Government app** | No |
| **Financial features** | None |
| **Health apps declaration** | Water Tracker logs water intake only. Pick the closest category (Nutrition & weight management or "other health and fitness"), state that it makes **no medical claims**, has no health-data integration (no Health Connect) and is not a medical device |
| **Advertising ID** | The app does not use it (answer "No") |

## 4. Foreground service declaration (App content > Foreground service permissions)
- Type: **Special use** (`FOREGROUND_SERVICE_SPECIAL_USE`, subtype text is in the manifest)
- Where it is used: the background reminder service (`UnlockService`)
- Justification (paste): *The home-screen widget reminds the user to drink water when they unlock their phone. Android does not deliver the unlock event (ACTION_USER_PRESENT) to a manifest-declared receiver, so a minimal foreground service keeps a runtime receiver registered for it. The service shows one minimum-priority notification, does no network or heavy work, and stores nothing about unlocks.*
- Demo video: `v1/store/video/foreground-service-demo.mp4` (about 30 seconds, captioned). Upload it to YouTube as **Unlisted** and paste the link in the declaration. It shows the widget, the quiet "Tap your widget to log water" notification, locking and unlocking the phone, and the widget's reminder.
- Fallback if this is rejected: replace the unlock listener with a periodic (about 15 minute) check, which needs no foreground service.

## 5. Permissions summary (for your own reference)
| Permission | Why |
|---|---|
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | The unlock listener above |
| `RECEIVE_BOOT_COMPLETED` | Restart that listener after a reboot |
| `POST_NOTIFICATIONS` | Lets Android show the service's quiet "Tap your widget to log water" notification (asked once, after the welcome, with a short in-app explanation first; optional) |
No location, contacts, storage, camera, microphone or internet permissions.

## 6. Release
1. **Internal testing** first: create a release, upload the `.aab`, add your own Google account as a tester, install the Play-signed build on your phone and check the widget, the reminder and the creeper.
2. Then **Production**: create the release, use the "What's new" text from `listing.md`, roll out to 100%, submit for review.
3. Keep **Play App Signing** on (default). Your `.jks` is only the upload key: keep it and its passwords safe and backed up.
4. Bump `versionCode` in `v1/WaterTracker/app/build.gradle.kts` for every upload. Play rejects a version code it has already seen (this is why the bundle is now code 2).

## 7. Before you press submit
- [ ] Replace `<your-support-email>` in `v1/store/listing.md` (the email field in Play Console is separate and required)
- [ ] Name: "Water Tracker" is a generic phrase, so it can't be trademarked and there is nothing to register or search. Anyone can use it, and it is very crowded in the store. Your icon, screenshots and characters are what set the app apart
- [ ] Confirm your Recraft plan allows commercial use of the four character illustrations
- [ ] Turn on GitHub Pages so the privacy policy URL above works (Settings > Pages > deploy from `main` / `docs`)
