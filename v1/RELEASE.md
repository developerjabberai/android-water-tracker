# Releasing Water Tracker to Google Play

App ID (permanent once uploaded): `com.developerjabberai.watertracker`

## One-time: create your upload key (you do this, keep it private)
Run in a terminal and choose your own passwords. **Never commit the key or its passwords, and back it up**: if you lose it you cannot update the app.

```bash
keytool -genkeypair -v -keystore ~/water-tracker-upload.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

Then create `v1/WaterTracker/keystore.properties` (it is git-ignored):

```properties
storeFile=/Users/<you>/water-tracker-upload.jks
storePassword=<your store password>
keyAlias=upload
keyPassword=<your key password>
```

In Play Console, keep **Play App Signing** on (the default). Google holds the real app-signing key; this is only your upload key.

## Build the bundle
```bash
cd v1/WaterTracker
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew bundleRelease
```
Upload `app/build/outputs/bundle/release/app-release.aab`. Bump `versionCode` in `app/build.gradle.kts` for every new upload.

## Play Console checklist
1. Create app: title "Widget: Water Tracker & Remind" (home-screen label "Water Tracker"), default language, App (not game), Free.
2. **Store listing:** text in `v1/store/listing.md`; assets in `v1/store/` (icon 512x512, feature graphic 1024x500, phone screenshots).
3. **Privacy policy URL:** host `v1/store/privacy-policy.md` (for example GitHub Pages) and paste the link.
4. **App content:** data safety (no data collected or shared; everything stays on the device), content rating questionnaire, target audience (13+ or all ages, not aimed at children), ads (none), health apps declaration (see note below).
5. **Foreground service declaration** (App content > Foreground service permissions): type `specialUse`, subtype text is in the manifest. Justification: "The home-screen widget reminds the user to drink water when they unlock their phone. Android does not deliver the unlock event to a manifest receiver, so a minimal foreground service holds a runtime receiver for ACTION_USER_PRESENT." A short screen recording of the widget reacting to an unlock helps review.
6. Upload the `.aab` to Production (organization accounts skip the 12-tester closed test). Fill release notes, submit for review.

## Notes
- The app gives no medical advice. If the Health apps declaration appears, choose the option that fits a simple hydration logger and state that it makes no medical claims.
- Notification: the foreground service shows a minimum-priority "Watching your pace" notification. Mention it in the listing so it is not a surprise.
- If the foreground service is rejected, the fallback is a periodic (about 15 minute) check instead of an unlock listener.
