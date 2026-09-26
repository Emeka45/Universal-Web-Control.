# Universal Web Control Foundation Implementation Plan

> **For agentic workers:** Use the host's available task-by-task implementation workflow. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first working Android foundation for Universal Web Control: a native mobile dashboard with a secure service boundary ready for Cloudflare integrations, plus a repeatable GitHub Actions build.

**Architecture:** Native Kotlin + Jetpack Compose provides the Android UI and app lifecycle. A small service layer keeps Cloudflare-specific operations outside the UI so future Workers, Pages, DNS, Domains, Analytics, AI, Security, and account integrations can be added without rewriting screens. The first release intentionally uses safe local service metadata rather than embedding credentials or pretending that unconfigured API operations succeeded.

**Tech Stack:** Kotlin, Android Gradle Plugin, Jetpack Compose, Material 3, AndroidX lifecycle, Gradle, GitHub Actions.

## Global Constraints

- Product name: Universal Web Control.
- Repository is `Emeka45/Universal-Web-Control.`.
- The app must present itself as an independent third-party tool and must not imply Cloudflare endorsement.
- Cloudflare may be referenced only as the supported service in descriptive UI/copy.
- No API tokens, OAuth secrets, or account credentials are committed to the repository.
- Mobile-first UI must remain usable on small Android screens.
- Initial foundation must build as an Android APK through GitHub Actions.
- U branding is required.
- Service modules must have clear boundaries so real API integration can be added incrementally.

---

### Task 1: Android project and build foundation

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/drawable/ic_universal_web_control.xml`
- Create: `.gitignore`
- Test: GitHub Actions Android build workflow

**Interfaces:**
- Produces an Android application module with application id `com.coeric.universalwebcontrol`.
- Produces a debug APK artifact from `assembleDebug`.

- [ ] **Step 1: Add the focused build configuration**
  Configure a single Android application module with compile/target SDK 36, min SDK 26, Java/Kotlin JVM target 17, Compose enabled, and no network credentials.

- [ ] **Step 2: Verify the project configuration**
  Run the GitHub Actions Gradle build after the initial project is committed.
  Expected: Gradle configuration succeeds and the debug APK is produced.

- [ ] **Step 3: Implement the minimum Android shell**
  Add the manifest, application theme, strings, and U vector icon.
  Expected: the app can launch to the main activity.

- [ ] **Step 4: Verify packaging**
  Run `assembleDebug`.
  Expected: `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 5: Commit the passing deliverable**
  Commit as `feat: create universal web control android foundation`.

---

### Task 2: Dashboard and service boundary

**Files:**
- Create: `app/src/main/java/com/coeric/universalwebcontrol/MainActivity.kt`
- Create: `app/src/main/java/com/coeric/universalwebcontrol/model/ServiceModule.kt`
- Create: `app/src/main/java/com/coeric/universalwebcontrol/data/ControlService.kt`
- Create: `app/src/main/java/com/coeric/universalwebcontrol/data/LocalControlService.kt`
- Create: `app/src/main/java/com/coeric/universalwebcontrol/ui/UniversalWebControlApp.kt`
- Create: `app/src/main/java/com/coeric/universalwebcontrol/ui/theme/Theme.kt`
- Create: `app/src/test/java/com/coeric/universalwebcontrol/data/LocalControlServiceTest.kt`

**Interfaces:**
- `ControlService.modules(): List<ServiceModule>` returns the supported service catalogue.
- `ServiceModule` contains stable id, display name, description, and availability state.
- `LocalControlService` implements the interface without network access.

- [ ] **Step 1: Add focused tests**
  Test that the local catalogue exposes Workers, Pages, DNS, Domains, Analytics, AI, Security, and Account modules, and that none claims a remote connection before authentication.

- [ ] **Step 2: Verify the expected initial failure**
  Run the unit test.
  Expected: compilation fails because the service boundary does not yet exist.

- [ ] **Step 3: Implement the minimum service layer and dashboard**
  Build a responsive Compose dashboard with app branding, a status panel, service cards, and a clear connection state. Tapping a service shows that its integration is not yet authenticated rather than silently failing.

- [ ] **Step 4: Verify the focused pass**
  Run the unit test.
  Expected: all service catalogue assertions pass.

- [ ] **Step 5: Run the affected integration check**
  Run `assembleDebug`.
  Expected: the dashboard compiles and packages.

- [ ] **Step 6: Commit the passing deliverable**
  Commit as `feat: add service catalogue dashboard`.

---

### Task 3: CI, documentation, and release-safe guardrails

**Files:**
- Create: `.github/workflows/android.yml`
- Create: `README.md`
- Create: `progress.md`

**Interfaces:**
- CI consumes the repository source and produces a downloadable debug APK.
- README documents the independent-tool positioning and future service integration boundary.

- [ ] **Step 1: Add CI**
  Configure GitHub Actions with JDK 17 and Gradle caching, then run `assembleDebug`.

- [ ] **Step 2: Add artifact verification**
  CI must upload `app-debug.apk` when the build succeeds.

- [ ] **Step 3: Add documentation**
  Document project purpose, supported service categories, security rule that credentials are never committed, and the next integration boundary.

- [ ] **Step 4: Run the full verification**
  Run the CI workflow and confirm a successful APK artifact.

- [ ] **Step 5: Commit the passing deliverable**
  Commit as `ci: add android build workflow and project docs`.

## Unresolved Product Decisions

- The production authentication mechanism (OAuth flow, token-based connection, or another supported mechanism) is intentionally deferred until the service boundary is built; no credentials are stored in this foundation.
- The first real Cloudflare API module to integrate after the foundation is not fixed by this plan.
