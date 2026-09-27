# Play Console – App content answers

Re-check Google's current AdMob disclosure guide before submitting, because it is the authority:
https://developers.google.com/admob/android/privacy/play-data-disclosure

## Basics
| Question | Answer |
|---|---|
| App or game | App |
| Free or paid | Free |
| Category | Tools |
| Contains ads | **Yes** (Google AdMob: banner on Home, interstitial after an export) |
| In-app purchases | No (v1.0.0) |
| Privacy policy URL | https://offerpk.github.io/photo-resizer/privacy.html |
| App access | All functionality is available without special access or login |
| Government app | **No** |
| Financial features | None |
| Health | None / not a health app |
| News app | No |
| COVID-19 contact tracing/status | No |

## Advertising ID
- **Does your app use advertising ID?** **Yes.**
- Purpose: **Advertising or marketing** (and Analytics / Fraud prevention as used by the Google Mobile Ads SDK).
- The `com.google.android.gms.permission.AD_ID` permission is merged from the Google Mobile Ads SDK (visible in the release manifest).

## Target audience and content
- **Target age groups:** 13–15, 16–17, 18 and over (**13+**). Do **not** select any group under 13.
- **Appeals to children?** No. The store listing and graphics are not designed for children.
- The ads SDK request is not tagged as child-directed (`setTagForUnderAgeOfConsent(false)`).

## Content rating (IARC questionnaire)
- Category: **Utility, Productivity, Communication, or Other**.
- Violence / sexuality / language / controlled substances / gambling: **No** to all.
- User-generated content shared with others / user-to-user communication: **No** (the share sheet just hands a file to another app the user picks).
- Shares user location: **No**. Digital purchases: **No**.
- Expected rating: **Everyone / PEGI 3 / USK 0** (ads do not change this).

## Data safety
The app itself collects nothing and processes photos only on the device. The declarations below come from the **Google Mobile Ads SDK** (AdMob), per Google's disclosure guide.

**Does your app collect or share any of the required user data types?** Yes (via the AdMob SDK).
**Is all of the user data collected by your app encrypted in transit?** Yes.
**Do you provide a way for users to request that their data is deleted?** No – the developer has no server and stores no user data. (Explain in the free-text: "The app has no account or server. Ad-related data is processed by Google; users can reset their advertising ID in Android settings.")

| Data type | Collected | Shared | Optional? | Purposes |
|---|---|---|---|---|
| **Location → Approximate location** (derived from IP address) | Yes | Yes | Not optional | Advertising or marketing, Analytics, Fraud prevention, security & compliance |
| **App activity → App interactions** (ad impressions/clicks, app launches) | Yes | Yes | Not optional | Advertising or marketing, Analytics, Fraud prevention, security & compliance |
| **App info and performance → Diagnostics** (crash logs / performance of the SDK) | Yes | Yes | Not optional | Analytics, Fraud prevention, security & compliance |
| **Device or other IDs** (advertising ID, app set ID) | Yes | Yes | Not optional | Advertising or marketing, Analytics, Fraud prevention, security & compliance |

**Not collected:** Photos and videos (processed on-device only, never transmitted), Files and docs, Personal info (name, email, etc.), Financial info, Health, Messages, Contacts, Calendar, Audio, Web browsing, Precise location.

Processing is not ephemeral for ad data (Google processes it); photos are never collected, so "Photos" is **not** declared.

## Permissions declarations
- **Photo and video permissions:** none requested (system Photo Picker only). The release APK is checked in CI to contain no `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE`.
- `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion=28` – only used on Android 7–9 to save into Pictures. No declaration form needed.
- `FOREGROUND_SERVICE` / `WAKE_LOCK` – merged from AndroidX WorkManager (a dependency of the Ads SDK). The app declares no foreground service type, so no foreground-service declaration should be needed; if Play asks, answer that the app itself does not start foreground services.
- `ACCESS_ADSERVICES_*` – merged from the Ads SDK (Privacy Sandbox APIs).

## Ads declarations
- Ad formats: adaptive banner (Home screen, bottom), interstitial (only after a successful save/share, never on the first export of a session, at most once per 90 s, at the natural break when leaving the Result screen).
- No ads on app launch/exit, no ads in editors, no rewarded ads in v1.0.0.
- Consent: Google UMP SDK runs before any ad request; "Privacy options" is offered in About & privacy when required.
