# Publishing PA_bot2 to the Google Play Store

## Context
PA_bot2 is functionally complete (scan → split → settle/Splitwise export), and the release
build is already R8/resource-shrunk (~6 MB AAB). But it is **not publishable as-is**: the
package id is a banned placeholder, there is no signing config, the SDK target is a preview
level Play won't accept, the AI flow needs Google OAuth verification, and none of the
legal/compliance/store-listing work exists yet. This document lists the concrete steps to get
from the current state to a live Production listing.

Two decisions already made:
- **AI access:** keep the current Google sign-in → Gemini flow and submit the OAuth consent
  screen for Google **production verification**.
- **Play account:** a new **personal** developer account — so the **12-tester / 14-day closed
  test** requirement applies before Production can be requested.

---

## Hard blockers (must be fixed in code/config first)

### A. App identity — `app/build.gradle.kts`, `res/values/strings.xml`
- `applicationId`/`namespace` are `com.example.pa_bot2`. **Google Play rejects any `com.example.*`
  id.** Choose a real, permanent id (e.g. `com.kaustubhwankhede.pabot2` or a `dev.<name>.<app>`
  form) and set both `applicationId` and `namespace`.
  - Ripple effects to handle in the same change:
    - The Google OAuth **Android client** is keyed by *package name + signing SHA‑1* — it must be
      re-registered for the new id and the release key (see D).
    - Splitwise OAuth app: confirm the registered redirect (`http://localhost:8080/splitwise-callback`,
      declared in `AndroidManifest.xml`) still matches; package change doesn't affect the URI but
      re-verify in the Splitwise dashboard.
    - `FileProvider` authority is `${applicationId}.fileprovider` (manifest) — auto-updates, no edit.
- `app_name` is the placeholder `PA_bot2` (`strings.xml`). Pick a real product/store name.

### B. Stable SDK — `app/build.gradle.kts`
- `compileSdk = 37` / `targetSdk = 37` are a **preview** API level. Play does **not** accept
  apps built against preview SDKs, and new apps must target a recent stable level. Pin both to
  the latest **stable** API (35, or 36 if stable on your toolchain). `minSdk = 31` is fine.

### C. Release signing — `app/build.gradle.kts` + new `keystore.properties`
- The release build is currently **unsigned** (`app-release-unsigned.apk`). Steps:
  1. Generate an upload keystore: `keytool -genkeypair -v -keystore upload.keystore -alias upload -keyalg RSA -keysize 2048 -validity 10000`.
  2. Add a `signingConfigs.release` that reads keystore path/passwords from a **gitignored**
     `keystore.properties` — reuse the exact pattern already used for `.env` in
     `app/build.gradle.kts` (the `Properties().apply { … }` loader). Add `keystore.properties`
     and `*.keystore` to `.gitignore`.
  3. Enroll in **Play App Signing** (recommended): you keep the upload key; Google manages the
     app signing key.
  4. Build the artifact with `./gradlew bundleRelease` → **AAB** (Play requires AAB, not APK).

### D. Google OAuth production verification (chosen AI path)
- In **Google Cloud Console** for the project behind `GOOGLE_SERVER_CLIENT_ID` (`util/AuthHandler.kt`
  requests scope `generative-language.retriever`):
  - Register the production **Android OAuth client** (new package name + the **release** SHA‑1
    from Play App Signing) and keep the **Web client** (the server client id used by Credential
    Manager).
  - Fill the **OAuth consent screen**: app name, support email, app logo, authorized domains,
    **privacy policy URL**, scope justification.
  - **Submit for verification.** The generative-language scope plus Google Sign-In triggers
    Google review; expect days–weeks and possible push-back on the scope. Until verified, the app
    is capped at 100 users — compatible with the closed-testing phase, so this can run in parallel.

### E. Security hardening before a public build
- The **Splitwise client secret** is embedded in the app (`BuildConfig` via `.env`) **and is in
  git history**. An embedded OAuth secret is extractable from any published APK. Before public
  release: **rotate** the Splitwise secret, and prefer migrating Splitwise auth to **PKCE (no
  secret)** in `util/SplitwiseAuthHandler.kt`. If the repo will be public, scrub secrets from git
  history (e.g. `git filter-repo`).
- Verify the **R8/release** build doesn't break reflection-based libraries (Retrofit,
  kotlinx-serialization, Moshi, Room). The debug build never exercises R8 — test the *release*
  build's scan + Splitwise export end-to-end (add ProGuard keep rules in `proguard-rules.pro`
  only if a runtime failure shows up).

---

## Compliance & legal content (required by Play and by OAuth verification)
1. **Privacy policy** hosted at a public URL. Must disclose: Google account email (sign-in),
   camera usage, that **receipt images are uploaded to Google's Gemini API** for processing,
   Splitwise token usage, and local on-device storage of bills. Needed both in Play and on the
   OAuth consent screen.
2. **Data Safety form** (Play Console): declare data collected/shared — account email, photos
   sent to a third party (Google AI), bill/financial content stored locally; encryption in
   transit; data-deletion path.
3. **Content rating** questionnaire, **Target audience** (not children), **Ads** (none),
   and the **Financial features** declaration (handles money amounts but isn't a payments app).

---

## Play Console setup (personal account)
1. Create a **Google Play Developer account** ($25 one-time); complete **identity verification**
   (government ID / address) now required for personal accounts.
2. Create the app; complete **App access** — every core feature is gated behind Google sign-in
   (and Splitwise), so provide reviewers a **test account + step-by-step credentials**.
3. Complete the **App content** declarations from the Compliance section above.

## Store listing assets
- Icon (already have an adaptive icon), **feature graphic 1024×500**, **phone screenshots** (2–8),
  **tablet screenshots** (the UI is adaptive `ListDetailPaneScaffold`, so 7"/10" shots are worth
  providing), **short description** (≤80 chars), **full description** (≤4000), category (Finance
  or Tools), contact email.

## Release rollout (timeline-critical for a new personal account)
1. Upload the signed AAB to **Internal testing**; fix issues; review the **Pre-launch report**.
2. Run **Closed testing** with **≥12 testers opted in for ≥14 continuous days** (mandatory for new
   personal accounts before Production can be requested).
3. **Apply for Production access**; once granted, do a **staged Production rollout**.

---

## Critical files
- `app/build.gradle.kts` — applicationId + namespace, stable compile/target SDK, `signingConfigs.release`, versioning.
- `app/src/main/res/values/strings.xml` — real `app_name`.
- `keystore.properties` (new, gitignored) + `.gitignore` — signing creds, reusing the `.env` loader pattern.
- `app/src/main/java/com/example/pa_bot2/util/SplitwiseAuthHandler.kt` — rotate secret / migrate to PKCE.
- `app/src/main/java/com/example/pa_bot2/util/AuthHandler.kt` — referenced for the OAuth client/scope registered in Cloud Console.
- `AndroidManifest.xml` — re-verify deep-link host and FileProvider authority after the id change (likely no edits).
- External (not in repo): hosted **privacy policy** page.

## Verification
- `./gradlew bundleRelease` produces a **signed AAB**; confirm it's signed and reasonably sized (~6 MB).
- Build an installable APK from the AAB (`bundletool build-apks --mode=universal`) or use the Internal-testing track, install on a physical device, and **smoke-test on the signed release build**: Google sign-in → AI scan → Splitwise link/export → share image. Release behaves differently from debug (R8 + the OAuth SHA‑1 must match the registered client).
- Confirm the OAuth flow works with the **release** SHA‑1 registered in Cloud Console (a debug-key mismatch is the most common "works in debug, fails in release" trap).
- Use the Play Console **Pre-launch report** for automated multi-device testing after the first upload.

## Notes / risks
- **Biggest schedule risks:** (1) Google OAuth verification of the generative-language scope may be slow or contested; (2) the new-personal-account 12-tester/14-day closed-test gate adds ~2+ weeks minimum.
- Decide the permanent `applicationId` early — it can **never** be changed after first publish.