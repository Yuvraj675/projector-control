# CLAUDE.md — AutoKeystone

Engineering notes for working on this repository.

## Rules for this repo
- Commit after every step with a descriptive message.
- Commits use the user's own git identity (the configured `user.name` / `user.email`).
- Never add `Co-Authored-By` lines or any other attribution trailers to commits.
- Never commit personal photos (captures of the user's room). They are gitignored; keep them out of tests.
- Keep `README.md` and this file current whenever behaviour, layout or commands change.

## What it is
An Android app (Kotlin, Jetpack Compose, CameraX) that fixes the keystone of a Hisilicon "bigfish"/Hi3751 projector
(Newlink/Zhiying firmware). The projector advertises auto keystone but has no camera or tilt sensor, so the phone does the
sensing and the projector is driven over ADB (network, port 5555) by a built-in ADB client (`dadb`), with no computer.

## Layout
```
app/src/main/kotlin/dev/keystone/auto/
  MainActivity.kt, AppViewModel.kt, UiState.kt   single activity; AppViewModel runs the whole flow, UiState holds the
                                                 sealed Screen / ConnectState / Session / VerifyState / DoneState types
  core/      pure Kotlin maths (no Android): Geometry (Pt, Mat3, homography), Keystone (Panel model, Offsets),
             Solver (largest rectangle), Pose (gravity + intrinsics -> levelled view), Pipeline (per-capture solve, combine)
  vision/    pure Kotlin image processing: GrayImage, Mask ops, MarkerDetector (finds the 4 pattern discs)
  link/      ADB over TCP: AdbConnection (dadb), Projector (read/apply keystone, helper, pattern app), AdbScanner + Subnet
  data/      Learning (accept/reject -> aspect, yaw, roll bias), Codec (JSON for profile + history)
  platform/  Android glue: Store (settings, profile, history, original keystone), Network (Wi-Fi subnet), AppLog,
             CameraRig/CameraMeta (CameraX + Camera2 intrinsics), GravityTracker, CaptureProcessor (photo -> measurement)
  ui/        Compose: theme/Theme, Components, Illustrations (code-drawn diagrams), Root (screen switch),
             screens/ Connect, Guide, Capture (+ CaptureOverlay), FlowScreens (Working, Verify, Feedback, ResultPhoto, Done),
             SettingsScreens (Settings, History, Log)
app/src/main/assets/projector/   ksall.jar (helper run ON the projector) and pattern.apk (test pattern) - built, committed
projector/helper/KsAll.java      source of the helper (calls Hisilicon AdvancedPictureSetting through app_process)
pattern/                         Gradle module: the full-screen four-marker pattern app installed on the projector
tools/build-assets.sh            rebuilds ksall.jar and pattern.apk into app assets
docs/screenshots/                README images, produced from the UI snapshot tests
```

## The flow (AppViewModel)
Connect (scan / remembered host / manual IP -> connect, push helper, install pattern app, remember original keystone) ->
Guide -> Capture (N photos, each analysed immediately) -> Working (solve, sanity-check, apply) -> Verify
(Looks correct -> optional result photo -> learn -> Done; Not quite -> Feedback chip -> adjust -> re-solve from the same
photos -> Verify again; Undo any time). Photos retaken after a rejection are measured against the keystone that is on the
projector at that moment (`Session.keystoneAtStart` is updated). All projector calls go through `proj {}` which reconnects once.

## Key facts about the projector (verified on the real device)
- Keystone = homography moving the four frame corners; offsets are pixels measured **inward**: TL=(X1,Y1) TR=(1280-X2,Y2)
  BL=(X3,720-Y3) BR=(1280-X4,720-Y4). Panel is 1280x720.
- Values are set through `AdvancedPictureSetting.setXnKeystoneOffset(int)` which is **relative** (adds to the current value);
  `getXnKeystonePosition()` reads the absolute value. Values never go below 0. The helper `KsAll get|delta` wraps this.
- **Firmware Auto mode** (`getAutoKeystone()==1`, prop `persist.newlink.autokeystone_onoff=1`) breaks everything: logcat shows
  `ZYKeystoneService AutoKeystone Not Mix-Auto Now!` and `adjustKeystoneOffset Illegal Value of N, Out of Range[0, -1]`, every
  corner setter is refused and the getters read all zeros. It got switched on during a real session (cause unknown). The helper now
  calls `setAutoKeystone(0)` before `delta`/`manual` and prints `AUTO=<0|1>`; `Projector.applyKeystone` uses that. Seen in the
  phone log as "did not accept the new keystone (... got all 0)".
- Needs `/system/framework/HitvShare.jar`, `HitvManager.jar` and the `hitvservice` service, so only this firmware family works.
- Screenshots (`screencap`) include the keystone warp, which is how the model was verified.
- The firmware draws a mute indicator in the bottom-right of the frame; the pattern keeps its BR marker clear of it.

## Method
1. Show pattern -> 2. phone photographs it (gravity + intrinsics recorded) -> 3. markers detected -> 4. photo levelled with a
pure-rotation homography (exact for any plane) -> 5. panel->view homography from the 4 markers -> 6. largest rectangle of the
target aspect whose pre-image fits the panel (corner offsets capped) -> 7. average over captures, apply, verify read-back.
Unknown camera yaw is assumed 0 (phone parallel to the wall) unless learned.

## Testing
- `./gradlew testDebugUnitTest` runs everything on the JVM: geometry, solver, pose with a simulated camera, detector on
  rendered photos, link layer against a simulated projector, learning on a simulated wall, and **UI snapshot tests**.
- Optional, skipped when not configured:
  - `KS_CAPTURES=<dir>` real photos + sidecar JSON: cross-checks the Kotlin pipeline against the Python prototype.
  - `KS_PROJECTOR=<ip>` live read-only test against a real projector (also the generated-key-pair connection);
    `KS_SCAN=<ip>/<prefix>` live subnet scan.
- **UI snapshots**: `ui/UiSnapshotTest` and `ui/CaptureSnapshotTest` (Robolectric, native graphics) draw the activity's decor view
  into PNGs under `app/build/ui-snapshots/`. Look at them after any UI change; they catch layout problems and crashes
  without a device. `captureToImage()` does not work under Robolectric, hence the manual draw. Qualifier order matters
  (`w411dp-h891dp-port-xxhdpi`). `CaptureOverlay` is a pure composable precisely so it can be rendered without a camera.
- Not testable here: the real camera, sensors, Wi-Fi scanning on a phone and the dadb behaviour on Android. These need a phone.
- Assets: `tools/build-assets.sh` (needs JDK 17, Android SDK platform 34 + build-tools 34.0.0).
- Build: `./gradlew assembleDebug` (JDK 17, `local.properties` with `sdk.dir`).

## Learning
`data/Learning.kt`: accepted result -> fit aspect, yaw, roll-bias (EMA over sessions); rejected -> nudge one parameter in the
direction that improves the predicted picture (step halves on reversal). Params persist in `profile.json`, sessions in history.

## Status, open items and wider context
- The app is complete and tested on the JVM (53 tests, live tests against the real projector pass). It has **not yet been run on a
  phone**: camera, sensors, Wi-Fi scan and dadb on Android are untested. If something fails on a device, ask for
  Settings -> Activity log. Most likely trouble spots: CameraX binding with three use cases (a fallback exists), ADB key
  generation on Android (falls back to no key, fine for this no-auth projector), Android 17 local-network permission.
- No git remote is configured yet; the user will add one.
- Fixed design decisions: BR marker offset from the corner (mute indicator), corner offsets capped at 320/180 px in the solver,
  hard limits 400/220 in `Panel`, outlier rejection only for gross deviations, photos stay on the phone.
- The wider project (the user's projector, what was changed on it, backups, research prototype, laptop setup) is described in
  `../PROJECT_STATE.md`, which lives outside this repo and may not exist elsewhere. Read it first if it is there.

