# AutoKeystone

An Android app that fixes the keystone (trapezium) of a Hisilicon-based Android projector using only your phone.

> Status: work in progress — the measurement, solver, ADB and learning layers are done and tested; the phone UI is being built.
> See [CLAUDE.md](CLAUDE.md) for the engineering notes.

## Idea

Some budget projectors (Newlink / Zhiying firmware on a Hisilicon Hi3751 board, e.g. "HY300-style") advertise an
*auto keystone* feature but ship without the camera / tilt sensor it needs. This app replaces the missing sensors with
your phone:

1. It finds the projector on your Wi-Fi (ADB over the network) and connects with one tap.
2. It shows a test pattern (four markers) on the projector.
3. You take a few photos of the wall, guided by a live level indicator.
4. It measures the marker positions, removes the phone's own perspective using the phone's gravity sensor and camera
   intrinsics, solves for the four corner offsets that make the picture a clean rectangle, and applies them.
5. You say whether it looks right. The app learns from your answer.

The app is independent of any computer: the ADB client is built in.

## How it works (short)

* **Model.** The projector's keystone is a four-corner warp. The app reads and sets the corner offsets through the projector's
  own Hisilicon TV service, using a tiny helper it pushes over ADB.
* **Measurement.** A test pattern with four white discs is shown on the projector. The phone photographs it; the discs are
  found in the photo and the phone's gravity sensor plus camera intrinsics turn the photo into a square-on view.
* **Solving.** From the four discs the app knows how the panel maps into that view, so it can pick the largest rectangle that
  fits and compute the corner offsets that produce it. Several photos are averaged to cancel hand shake.
* **Learning.** When you accept a result the app learns your preferred shape (aspect ratio, camera angle, sensor bias); when
  you reject it you say what looks wrong and it adjusts and tries again.

## Building

```bash
./tools/build-assets.sh        # only needed when projector/ or pattern/ change
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # JVM tests
```

Requires JDK 17 and the Android SDK (platform 34, build-tools 34.0.0).

## Compatibility

Only projectors with the Hisilicon TV API (`/system/framework/HitvShare.jar` and the `hitvservice` service) are supported,
e.g. Newlink/Zhiying "HY300-style" units on a Hi3751 board. The app detects other projectors and says so.
