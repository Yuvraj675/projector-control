# Contributing to ProjectorControl

Thanks for helping. Bug reports, field reports from other projector models, fixes and features are all welcome.

## Reporting a problem

Open an issue and include:

- the phone (model, Android version) and the projector (model, firmware if you know it),
- what you did, what you expected and what happened,
- **Settings, Activity log** from the app and the **diagnostics report** (Projector, Help). The report never contains your IP address.

Do not attach photos of your room or anything else you would not post publicly.

## Ideas and new device support

Open an issue first for anything bigger than a small fix, so the approach can be agreed before you spend time on it. Support for another
projector family is very welcome: describe how its keystone, input and ADB behave (`adb shell` output helps).

## Development setup

- JDK 17, the Android SDK (platform 34, build-tools 34.0.0), and `sdk.dir=...` in `local.properties`.
- App: `./gradlew assembleDebug`. Tests: `./gradlew testDebugUnitTest` (all JVM tests, including the Robolectric UI snapshots in `app/build/ui-snapshots/`; look at them after a UI change).
- Projector-side files (`app/src/main/assets/projector/`) are rebuilt with `./tools/build-assets.sh` and only need it when `projector/` or `pattern/` change.
- Website: `cd website && npm ci && npm run dev`. See [website/README.md](website/README.md).
- The code map and the facts about the projector firmware are in [CLAUDE.md](CLAUDE.md).

## Pull requests

1. Fork, create a branch, keep the change focused (one fix or feature per pull request).
2. Add or update tests for behaviour changes. Pure Kotlin logic (`core/`, `vision/`, `link/`, `data/`) should stay testable on the JVM without Android.
3. Run `./gradlew testDebugUnitTest` and, for website changes, `cd website && npm run build`. Both must pass.
4. Update `README.md` (and `CLAUDE.md` if the layout or a projector fact changes) when behaviour, commands or layout change.
5. Write commit messages that say what changed and why, in the imperative ("Fix the reconnect backoff").
6. Open the pull request and describe what you tested and on which devices.

### Ground rules

- **Never write the firmware's Auto keystone setting.** It breaks every keystone setter on the supported firmware; the app only reads it. See CLAUDE.md.
- Keep keystone values range-checked before they are sent to a projector.
- No personal data in the repository: no photos of rooms, real IP addresses or device identifiers, keystores or passwords. `*.jpg`, `*.jks` and `local.properties` are git-ignored on purpose.
- Keep the website free of third-party requests (it ships a strict Content-Security-Policy) and keep it accessible: reduced motion, keyboard use, readable contrast.
- By contributing you agree that your work is released under the [MIT License](LICENSE).

## Releasing (maintainers)

Releases are published by a manually triggered GitHub Action, `.github/workflows/release.yml`:

1. Raise `versionCode` and `versionName` in `app/build.gradle` and push to the default branch.
2. GitHub, **Actions, Release, Run workflow**. Optional release notes; otherwise they are generated from the commits.
3. The workflow runs the unit tests, builds and signs the APK and creates the release `v<versionName>` with `ProjectorControl-<version>.apk`, `ProjectorControl.apk`
   (fixed name, so `releases/latest/download/ProjectorControl.apk` always points at the newest build) and `SHA256SUMS.txt`. It then commits `website/release.json`
   (version, size, SHA-256) so the website links and describes the new build. Rebuild and redeploy the site afterwards.

The workflow needs four repository secrets: `KEYSTORE_BASE64` (the release keystore, base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.
The signing key must never change between releases (phones refuse updates signed with a different key) and must never be committed. Without the secrets
(local builds, forks) `app/build.gradle` signs with the debug key.
