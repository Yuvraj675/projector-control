# CLAUDE.md — ProjectorConnect (formerly AutoKeystone)

Engineering notes for working on this repository.

## Rules for this repo
- Commit after every step with a descriptive message.
- Commits use the user's own git identity (the configured `user.name` / `user.email`).
- Never add `Co-Authored-By` lines or any other attribution trailers to commits.
- Never commit personal photos (captures of the user's room). They are gitignored; keep them out of tests.
- Keep `README.md` and this file current whenever behaviour, layout or commands change.

## What it is
An Android app (Kotlin, Jetpack Compose, CameraX) called **ProjectorConnect**: a phone remote for an Android projector (mouse/keyboard/remote keys, apps, APK install, links, power and status) that also contains **Auto keystone**, which fixes the keystone of a Hisilicon "bigfish"/Hi3751 projector
(Newlink/Zhiying firmware). The projector advertises auto keystone but has no camera or tilt sensor, so the phone does the
sensing and the projector is driven over ADB (network, port 5555) by a built-in ADB client (`dadb`), with no computer.

## Layout
```
app/src/main/kotlin/dev/keystone/auto/
  MainActivity.kt, AppViewModel.kt, UiState.kt   single activity; AppViewModel runs the whole flow, UiState holds the
                                                 sealed Screen / ConnectState / Session / VerifyState / DoneState types
  core/      pure Kotlin maths (no Android): AirMouse (gyro -> pointer, PadMotion), Geometry (Pt, Mat3, homography), Keystone (Panel model, Offsets),
             Solver (largest rectangle), Pose (gravity + intrinsics -> levelled view), Pipeline (per-capture solve, combine)
  vision/    pure Kotlin image processing: GrayImage, Mask ops, MarkerDetector (finds the 4 pattern discs)
  link/      ADB over TCP: AdbConnection (dadb; run, openStream for a live stdin, push), Projector (keystone, helper, pattern app,
             firmware Auto switch), RemoteInput (+InputProtocol/RemoteKey: virtual mouse/keyboard via the projector's uinput),
             ProjectorTools (apps, links, power, status, APK/bundle install), AdbScanner + Subnet
  data/      Learning (accept/reject -> aspect, yaw, roll bias), Codec (JSON for profile + history)
  platform/  Android glue: Store (settings, profile, history, original keystone), Network (Wi-Fi subnet), AppLog, GyroPointer,
             CameraRig/CameraMeta (CameraX + Camera2 intrinsics), GravityTracker, CaptureProcessor (photo -> measurement)
  ui/        Compose: theme/Theme, Components (older), Kit (Tile, RoundKey, Segmented, ToastHost), Icons (own line-icon set, no icon
             library), Illustrations, Root (screen switch + toast),
             screens/ Home, Remote (touchpad/air/D-pad), Keyboard (type, speak, links), Apps (+Install), Device (projector panel),
             Connect, Guide, Capture (+ CaptureOverlay), FlowScreens (Working, Verify, Feedback, ResultPhoto, Done),
             SettingsScreens (Settings, History, Log)
app/src/main/assets/projector/   ksall.jar (helper run ON the projector) and pattern.apk (test pattern) - built, committed
projector/helper/KsAll.java      source of the helper (calls Hisilicon AdvancedPictureSetting through app_process)
pattern/                         Gradle module: the full-screen four-marker pattern app installed on the projector
tools/build-assets.sh            rebuilds ksall.jar and pattern.apk into app assets
docs/screenshots/                README images, produced from the UI snapshot tests
```

## The remote (how the phone becomes a mouse)
The projector has `/system/bin/uinput` (Android 12 evdev format) and the shell user is in group `uhid`, so no root is needed.
`RemoteInput` runs `uinput -` over one ADB shell stream and writes JSON lines: `register` (name, vid/pid, `configuration`
lists of UI_SET_EVBIT/KEYBIT/RELBIT) then `inject` with flat `[type,code,value,...]` events ending in a SYN `[0,0,0]`.
Verified on the real projector: the mouse registers as a CURSOR device and moves Android's pointer, the keyboard delivers
KEYCODE_* (Home=172, Volume, letters with Shift) and both vanish when stdin closes. Registration takes ~1.6 s. Keys above
248 (580 = recent apps) are declared explicitly. Typing uses a US map (`InputProtocol.keyFor`); other characters are skipped
and reported. All writes go through one thread; pointer deltas are merged every 12 ms. `AppViewModel` starts it on entering
Remote/Keyboard and closes it 45 s after leaving (`REMOTE_IDLE_MS`). Sharing text to the app (`ACTION_SEND`) opens a link.
Installs push each APK to `/data/local/tmp/pc-install-*`, run `pm install` / `pm install-multiple` (bundles are unzipped on the
phone), and always remove the folder. The air mouse maths (`AirMouse`) is unit-tested but the feel is untested on hardware.

## The keystone flow (AppViewModel)
Connect (scan / remembered host / manual IP -> connect, push helper, install pattern app, remember original keystone) -> Home
(tile: Auto keystone) -> Guide -> Capture (N photos, each analysed immediately) -> Working (solve, sanity-check, apply) -> Verify
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
- Version 0.3.0 adds: connection watchdog (`AppViewModel.startWatchdog`, `link/Reconnect.kt`), Auto-keystone switch-off on connect, saved keystones (`data/SavedKeystones.kt`), sleep timer on the projector (`ProjectorTools.scheduleSleep`), volume-key passthrough (`MainActivity.onKeyDown`), memory guard, diagnostics (`link/Diagnostics.kt`), screenshot (`Shell.pull`), send-file-and-play (`ProjectorTools.sendAndOpen`), pinned apps, remote-only mode (`ProjectorInfo.supported == false` still connects, no keystone), recovery card. 101 JVM tests; none of the new features have been run on a phone or the real projector yet.
- Version 0.2.0 (renamed from AutoKeystone; the application id `dev.keystone.auto` and the Gradle module names are unchanged on
  purpose, so the new APK installs over the old one and keeps the learned profile, history and ADB key). 83 JVM tests pass; the
  live tests (`KS_PROJECTOR=<ip>`: `LiveToolsTest`, the read-only `readsTheRealProjector`) pass against the real projector.
  **Do not run `readsTheRealProjector` when firmware Auto keystone is on**: its no-op `applyKeystone` now switches Auto off.
- **Not yet run on a phone**: camera, sensors (gyro air mouse, gravity), speech recogniser, Wi-Fi scan, dadb on Android, the
  file picker/install path, and the touch feel. If something fails ask for Settings -> Activity log and the screen.
- Firmware Auto keystone (see above) was found on in a real session and blocks every setter; Projector -> Keystone shows it and
  can switch it off. Why it switched on is unknown.
- Ideas not built: live mirroring (only a screenshot exists), non-ASCII typing (needs an IME or clipboard route on the projector),
  a launcher widget/quick-settings tile for the remote, CEC/HDMI control, multi-projector switcher, quick keystone re-check after moving.
- No git remote is configured yet; the user will add one.
- Fixed design decisions: BR marker offset from the corner (mute indicator), corner offsets capped at 320/180 px in the solver,
  hard limits 400/220 in `Panel`, outlier rejection only for gross deviations, photos stay on the phone.
- The wider project (the user's projector, what was changed on it, backups, research prototype, laptop setup) is described in
  `../PROJECT_STATE.md`, which lives outside this repo and may not exist elsewhere. Read it first if it is there.
