# Launch checklist – Photo Resizer: KB & Passport

## Done in this repo (v1.0.0)
- [x] Kotlin + Jetpack Compose + Material 3, `com.offerpk.photoresizer`, minSdk 24, **target/compileSdk 36**, versionCode 1 / versionName 1.0.0
- [x] Signed release **AAB + APK** built by GitHub Actions (JDK 21) and attached to the `v1.0.0` GitHub Release
- [x] Upload keystore generated and kept **outside** the repo (`/home/box/keystores/photoresizer-upload.jks`, passwords in `photoresizer-upload.txt`, backup zip in `/workspace/keybackup/`)
- [x] Repo secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
- [x] Unit tests (KB compressor within 5 % of target and never over, ad-frequency gate, units/print layout) run in CI
- [x] No media/storage read permissions (Photo Picker only); CI fails if one appears
- [x] Privacy policy on GitHub Pages: https://offerpk.github.io/photo-resizer/privacy.html
- [x] Store kit: icon 512, feature graphic 1024×500, 8 screenshots 1080×1920, listing text, Play Console answers
- [x] AdMob **test** IDs everywhere (single file: `admob.properties`)

## Before the first Play upload
- [ ] Create the Play Console app ("Photo Resizer: KB & Passport", default language English (US), App, Free).
- [ ] **Enrol in Play App Signing** and upload `photo-resizer-v1.0.0.aab`. The keystore here is the *upload* key – back up `/workspace/keybackup/photo-resizer-key-backup.zip` somewhere safe (password manager / offline drive). Losing it means an upload-key reset through Play support.
- [ ] Add a **support email** to the listing and (optionally) to PRIVACY.md.
- [ ] Fill *App content* using `store/play-console-answers.md` (ads = yes, advertising ID = yes, data safety per AdMob, content rating questionnaire, target audience 13+).
- [ ] Paste the listing from `store/listing-en.md`; upload `icon-512.png`, `feature-graphic-1024x500.png` and 4–8 screenshots from `store/screenshots/`.
- [ ] Test on at least one real device each: Android 7–9 (legacy save permission path) and Android 13+ (Photo Picker, MediaStore save).

## Switch ads from test to live (only after the app is published or in review)
1. In AdMob: add the app (link it to the Play listing), create a **Banner** and an **Interstitial** ad unit (Rewarded is optional/unused in 1.0).
2. Edit **`admob.properties`** only: replace `ADMOB_APP_ID`, `ADMOB_BANNER_ID`, `ADMOB_INTERSTITIAL_ID` (and `ADMOB_REWARDED_ID` if used).
3. Create a GDPR message (and US-states message if you want) in AdMob → *Privacy & messaging*, so the UMP SDK shows a consent form in the EEA/UK/CH.
4. Bump `versionCode`/`versionName` in `app/build.gradle.kts`, commit, tag `vX.Y.Z` → CI builds and releases the signed AAB.
5. Publish `app-ads.txt` on the developer website listed in Play (e.g. `https://offerpk.github.io/app-ads.txt` needs a user/organisation Pages site at the domain root) with the line AdMob gives you.
6. Never click your own live ads; use test devices (`RequestConfiguration.setTestDeviceIds`) when testing live builds.

## Closed testing (new personal developer accounts)
- [ ] Create a **Closed testing** track (internal testing does not count), add **≥12 testers (recruit 15–20)** via a Google Group or email list.
- [ ] Testers must opt in and stay opted in for **14 consecutive days**.
- [ ] Ship 1–2 updates during the test (bump versionCode each time), keep notes on feedback and changes.
- [ ] Apply for production access from the Dashboard; answer the questions about testing and changes.

## Production rollout
- [ ] Staged rollout 20 % → 50 % → 100 %; watch Android vitals (ANR / crash rate) and reviews.
- [ ] Reply to reviews; common requests to consider next: Urdu/Hindi/Bengali localisation, batch compress, background removal, image-to-PDF, remove-ads IAP.

## Known limitations of 1.0.0
- "White background" is a simple flood fill that works on plain, light walls; it is not AI background removal.
- No face detection / auto-crop – the user aligns the face with the guide.
- English UI only.
- Very large photos are decoded at up to 4096 px on the long edge to stay within memory on low-end phones.
