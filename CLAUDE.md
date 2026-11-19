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
  core/     pure Kotlin maths (no Android): Geometry (Pt, Mat3, homography), Keystone (panel model, Offsets),
            Solver (largest rectangle), Pose (phone gravity + camera intrinsics -> levelled view), Pipeline (per-capture solve,
            outlier-rejecting combine)
  vision/   pure Kotlin image processing: GrayImage, Mask ops, MarkerDetector (finds the 4 pattern discs)
  link/     ADB: AdbConnection (dadb), Projector (read/apply keystone, helper, pattern app), AdbScanner + Subnet
  data/     Learning (self-improvement from accept/reject), Codec (JSON for profile + history)
app/src/main/assets/projector/   ksall.jar (helper run ON the projector) and pattern.apk (test pattern) — built, committed
projector/helper/KsAll.java      source of the helper (calls Hisilicon AdvancedPictureSetting through app_process)
pattern/                         Gradle module: the full-screen four-marker test pattern app installed on the projector
tools/build-assets.sh            rebuilds ksall.jar and pattern.apk into app assets
```

## Key facts about the projector (verified on the real device)
- Keystone = homography moving the four frame corners; offsets are pixels measured **inward**: TL=(X1,Y1) TR=(1280-X2,Y2)
  BL=(X3,720-Y3) BR=(1280-X4,720-Y4). Panel is 1280x720.
- Values are set through `AdvancedPictureSetting.setXnKeystoneOffset(int)` which is **relative** (adds to the current value);
  `getXnKeystonePosition()` reads the absolute value. Values never go below 0. The helper `KsAll get|delta` wraps this.
- Needs `/system/framework/HitvShare.jar`, `HitvManager.jar` and the `hitvservice` service, so only this firmware family works.
- Screenshots (`screencap`) include the keystone warp, which is how the model was verified.
- The firmware draws a mute indicator in the bottom-right of the frame; the pattern keeps its BR marker clear of it.

## Method
1. Show pattern -> 2. phone photographs it (gravity + intrinsics recorded) -> 3. markers detected -> 4. photo levelled with a
pure-rotation homography (exact for any plane) -> 5. panel->view homography from the 4 markers -> 6. largest rectangle of the
target aspect whose pre-image fits the panel (corner offsets capped) -> 7. average over captures, apply, verify read-back.
Unknown camera yaw is assumed 0 (phone parallel to the wall) unless learned.

## Testing
- `./gradlew testDebugUnitTest` runs the JVM tests (geometry, solver, pose with a simulated camera, detector on rendered
  photos, link layer against a simulated projector, learning on a simulated wall).
- Optional, skipped when not configured:
  - `KS_CAPTURES=<dir>` real photos + sidecar JSON: cross-checks the Kotlin pipeline against the Python prototype.
  - `KS_PROJECTOR=<ip>` live read-only test against a real projector; `KS_SCAN=<ip>/<prefix>` live subnet scan.
- Assets: `tools/build-assets.sh` (needs JDK 17, Android SDK platform 34 + build-tools 34.0.0).
- Build: `./gradlew assembleDebug` (JDK 17, `local.properties` with `sdk.dir`).

## Learning
`data/Learning.kt`: accepted result -> fit aspect, yaw, roll-bias (EMA over sessions); rejected -> nudge one parameter in the
direction that improves the predicted picture (step halves on reversal). Params persist in `profile.json`, sessions in history.
