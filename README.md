<div align="center">

# ProjectorConnect

**Your phone as the remote for any Android projector or TV box, over Wi-Fi, with no computer. Mouse, keyboard, apps, APK installs, file sending, and camera-based Auto keystone for Hisilicon projectors.**

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)
![Version](https://img.shields.io/badge/version-0.4.1-blue)
![Tests](https://img.shields.io/badge/JVM%20tests-101%20passing-brightgreen)
![Projector](https://img.shields.io/badge/projector-Android%20TV%20%2F%20box%20with%20ADB-lightgrey)

[Features](#features) · [Quick start](#quick-start) · [Set up your projector](#set-up-your-projector) · [Using the app](#using-the-app) · [Auto keystone](#auto-keystone) · [Troubleshooting](#troubleshooting) · [How it works](#how-it-works) · [Build from source](#build-from-source)

<p align="center">
  <img src="docs/screenshots/home.png" width="19%" alt="Home"/>
  <img src="docs/screenshots/remote.png" width="19%" alt="Touchpad remote"/>
  <img src="docs/screenshots/dpad.png" width="19%" alt="D-pad"/>
  <img src="docs/screenshots/keyboard.png" width="19%" alt="Type and speak"/>
  <img src="docs/screenshots/apps.png" width="19%" alt="Apps"/>
</p>

</div>

---

Cheap Android projectors ship with a clumsy remote (or none), hidden settings, and an "auto keystone" that does nothing. ProjectorConnect turns your phone into the missing remote and control panel. It talks to the projector over **ADB on your Wi-Fi**, so nothing needs to be installed on the projector and no computer is involved.

> **Independent project.** Not affiliated with any projector maker. It works with the projector's standard Android debugging interface. Names of projectors, firmwares and apps are used only to describe compatibility.

## Features

- **Remote**: *touchpad* (swipe to move, tap to click, two fingers to scroll, two-finger tap for Back, press and hold to drag), *air mouse* (point the phone like a Magic Remote), and a *D-pad*. Back, Home, recents, menu, volume, mute and play/pause are always there. Your phone's **volume buttons** control the projector too.
- **Type & speak**: what you type (or dictate) appears on the projector as you type. Type your phone's clipboard, paste a link to open it, or search YouTube.
- **Apps**: list, open, stop and uninstall apps. **Pin favourites to Home** for one tap launching. A memory guard clears the background first when the projector is low on RAM.
- **Install apps from the phone**: pick an `.apk` (or an `.apkm` / `.apks` / `.xapk` bundle). Failures are explained in plain words ("built for 64-bit, this projector is 32-bit").
- **Send to projector**: the **Send file** tile on Home (any file; videos, photos and songs then play), or share a video, photo or song to the app and it is copied to the projector and played there. Share a YouTube, web, magnet or Stremio link and it opens.
- **Projector screen**: a live view of the projector's screen on your phone that you can touch (tap, drag, two-finger pinch) like a touch screen. The picture is straightened on the phone, so touches land where they look even with keystone on. Landscape mode fills the phone's screen; **Smooth** (default) keeps the load on the projector low, **Sharp** looks better but makes its video encoder work harder while the picture moves. Also copy a **diagnostics report** (link speed, memory, temperature) for bug reports. The report never contains your IP address.
- **Power**: wake, screen off, restart, free up memory, and a **sleep timer** (30/60/90 min) that runs on the projector, so it still works if you close the app.
- **Stays connected**: it pings the projector, reconnects with backoff, and rescans your Wi-Fi if the projector's IP address changed.
- **Auto keystone** (Hisilicon projectors): straightens the picture using the phone camera. Save named setups ("Bedroom ceiling", "Living room table") and switch in one tap.
- **Safe**: the original keystone is saved the first time it meets a projector, every value is range-checked, and the Projector screen can restore the original home screen if you use a custom launcher.
- **Works on other projectors**: if the projector is not Hisilicon, you still get everything except keystone.

## Quick start

1. **Turn on network debugging on the projector** ([guide below](#set-up-your-projector)).
2. **Install ProjectorConnect on your Android phone** (Android 8.0+). Download the latest `ProjectorConnect-x.y.z.apk` from the Releases page, open it, and allow *install unknown apps* if asked. Or [build it yourself](#build-from-source).
3. Put the phone and projector on the **same Wi-Fi**.
4. Open the app and tap **Find my projector**. When the projector asks *Allow debugging?*, choose **Always allow**.

That's it. Next time it reconnects by itself.

## Set up your projector

ProjectorConnect needs one thing from the projector: **ADB debugging over the network**. How you turn it on depends on whether the projector lets you reach Android's Settings.

### Option A: the Android Settings are available

1. On the projector open **Settings**, then **Device Preferences** (or **System**, or **About**).
2. Open **About** and find **Build number** (some say **Android TV OS build**).
3. Select it **7 times** with the OK button. A message says you are now a developer.
4. Go back to **Settings → Device Preferences / System → Developer options**.
5. Switch on **USB debugging** (also called **ADB debugging**). If you also see **ADB over network** or **Wireless debugging**, switch that on too.
6. Check the projector is on the same Wi-Fi as your phone, then continue with [Quick start](#quick-start) step 4.

### Option B: the Settings are hidden (common on cheap projectors)

Many projectors remove the Settings icon from the home screen. A tiny launcher app brings it back:

**[Project Settings](https://github.com/5ham5h33r/Project-Settings)** by 5ham5h33r. It adds a visible **Settings** icon that opens the hidden Android TV settings screen. It needs no root, no ADB and no special permissions.

Install it on the projector, any of these ways:

- **Downloader app** (easiest): open *Downloader by AFTVnews* on the projector and enter the code `6940650` (or `aftv.news/6940650`). Allow Downloader to *install unknown apps* if asked, then choose **Install** again.
- **Download the APK** from the [Project Settings releases page](https://github.com/5ham5h33r/Project-Settings/releases) onto a USB stick, a file-sharing app such as LocalSend, or the projector's browser, then open it with the projector's file manager.

Then open the new **Settings** icon on the projector and follow **Option A** above.

### If the app cannot find the projector

Some firmwares only offer *USB debugging* and leave the network port closed. In that case, do this **once** from a computer that is on the same Wi-Fi (this is the only time a computer is needed):

```bash
adb connect <projector-ip>:5555        # works if the port is already open
# if that is refused, connect the projector by USB once and run:
adb tcpip 5555
```

The projector's IP address is shown in its Wi-Fi settings. You can type it into ProjectorConnect with **Enter IP address**.

> **Good to know.** ADB over the network has no password on most projector firmwares. Anyone on your Wi-Fi can connect, so use it on a network you trust and switch network debugging off if you don't need it.

## Using the app

| I want to... | Do this |
|---|---|
| Use the phone as a mouse | **Remote → Touchpad**. Swipe, tap to click, two fingers to scroll, two-finger tap for Back |
| Point instead of swipe | **Remote → Air**. Hold the big button, aim, tap to click |
| Move around menus | **Remote → D-pad** |
| Type a search or password | **Type & speak**. Turn off *Type as I go* to compose first and send once |
| Open a link on the big screen | Share it to ProjectorConnect from any app, or paste it in **Type & speak** |
| Play a video from my phone | Home → Send file, or share the video to ProjectorConnect |
| Use the projector like a touch screen | Home → Projector screen |
| Install an app | Home → **Install app** and pick the APK. 32-bit projectors need *armeabi-v7a* builds |
| Launch YouTube in one tap | **Apps**, press and hold YouTube, **Pin to Home** |
| Stop the screen at bedtime | **Projector → Sleep timer** |
| Check why it is slow | **Projector** shows memory and storage. **Free up memory** stops background apps |
| Fix a crooked picture | Home → **Auto keystone** (Hisilicon projectors) |

## Auto keystone

The picture on a tilted projector is a trapezium. These projectors advertise *auto keystone* but have no camera or tilt sensor, so "Auto" only resets the shape. ProjectorConnect uses your **phone's camera, gravity sensor and calibrated lens** instead, and applies the result through the projector's own four-corner keystone engine.

**Works on:** projectors with the Hisilicon TV API (`HitvShare.jar`, `HitvManager.jar`, `hitvservice`), for example Newlink / Zhiying firmware on a Hi3751 board (the "HY300" style units). The app checks and tells you if yours is not one.

1. Dim the room and stand square to the wall, as far back as the projector.
2. Tap **Auto keystone** and follow the three-page guide, then tap *I'm ready*.
3. Hold the phone level. The projector shows four circles. A live view tells you when all four are visible and the phone is steady, then the photo is taken by itself. Four photos by default.
4. The app works out and applies the correction, then shows the pattern again.
5. Say whether it looks right.
   - *Looks correct*: optionally take one photo of the result. The app learns what "right" means for your room.
   - *Not quite*: say what is off (right edge short, slopes up to the left, too wide...). It adjusts and asks again. **Undo** restores the previous keystone at any time.

The first time it meets a projector it saves the original keystone (*Settings → Restore the original keystone*). Moved the projector? Save each place under **Projector → Saved keystones**.

## Troubleshooting

| Problem | What to try |
|---|---|
| *No projector found* | Same Wi-Fi? Network debugging on? Try **Enter IP address**. Some routers isolate Wi-Fi clients (look for "AP isolation") |
| *Refused the connection* | USB / network debugging is off, or the projector restarted. Turn it on again |
| Projector asks *Allow debugging?* every time | Choose **Always allow**. Keep the app's data (it stores the key it identifies itself with) |
| Phone scan finds nothing on Android 17+ | Grant the app the *local network* permission in the phone's app settings |
| An app won't install: *built for 64-bit* | The projector is 32-bit. Get the **armeabi-v7a** (arm32) build of the app |
| Netflix TV app fails to install or run | Many projector firmwares are not certified. Use the phone's casting instead |
| Keystone says it was not accepted | The firmware's own Auto keystone is on and refuses every change. The app never changes that setting itself: turn it off in the projector's own keystone settings |
| Mouse feels too fast or slow | **Settings → Remote** sets touchpad and air-mouse speed |
| Link keeps dropping | Check the projector's Wi-Fi signal. The **diagnostics report** under *Projector → Help* shows the response time |

Still stuck? Open **Settings → Activity log** and include it, plus the diagnostics report, in an issue.

## Compatibility

| | Needs |
|---|---|
| Remote, keyboard, apps, install, files, power | Any Android projector or TV box (Android 8+) with ADB over the network. The virtual mouse uses the stock `uinput` tool |
| Auto keystone | The Hisilicon TV API above, plus a phone with a back camera and gravity sensor |
| Air mouse | A phone with a gyroscope |
| Best keystone accuracy | A phone that reports camera calibration (most recent ones). Others fall back to focal length and sensor size |

Developed and tested on a 1 GB, 32-bit Hisilicon Hi3751 projector (Android 12) and a recent Android phone.

## Privacy and safety

- Photos are analysed on the phone, kept only until analysed, and never uploaded. APK and media files go straight from the phone to the projector over your Wi-Fi.
- The ADB key pair and everything the app learns live in the app's private storage.
- Every keystone value is range-checked before it is sent. Results that look wrong (tiny area, non-convex) are refused.
- Only English letters, digits and common symbols can be typed (US keyboard layout).

## How it works

<details>
<summary><b>The remote</b></summary>

The projector ships Android's `uinput` tool and lets the ADB shell user create virtual input devices. The app starts one `uinput -` process over the ADB connection and streams JSON to it: a virtual **mouse** (relative movement, wheel, buttons), a virtual **keyboard**, and the remote keys (Home, Back, volume, media, D-pad). Android sees real hardware, so it draws its normal pointer and every app behaves as with a mouse. Nothing is installed on the projector, and the devices disappear when you leave the remote (about 45 seconds later) or disconnect. Pointer movement is merged and sent about 80 times a second.
</details>

<details>
<summary><b>Auto keystone</b></summary>

- **The keystone is a four-corner warp.** The app reads and sets the corner offsets through the projector's Hisilicon TV service, using a small helper (`ksall.jar`) pushed over ADB. Values are pixels pulled in from each corner of the 1280x720 panel.
- **Measurement.** A small app (`pattern.apk`) draws four white circles. The phone photographs them, and the circles are found with a morphological opening plus a convexity check.
- **Removing the phone's own perspective.** Gravity and the camera intrinsics turn the photo into the view of a level camera with a pure rotation, which is exact for any plane.
- **Solving.** The four circles give the panel-to-view mapping exactly. The app picks the largest rectangle of the target shape whose pre-image still fits the panel, and computes the corner offsets that produce it. Several photos are solved independently and averaged after rejecting gross outliers.
- **Applying.** The setters are *relative* steps, so it reads the current values, sends the differences and verifies by reading back.
- **Learning.** An accepted picture is ground truth: the app fits the aspect ratio, camera angle and roll-sensor bias that would have produced it. A rejection nudges the parameter behind the complaint, with a smaller step each time you reverse.
</details>

<details>
<summary><b>Staying connected</b></summary>

A watchdog pings the projector every few seconds. If it stops answering, the app retries with a growing delay and, every few failures, scans the subnet for a projector with the same identity in case its IP address changed, then reconnects and restarts the remote.
</details>

## Build from source

```bash
./tools/build-assets.sh        # only when projector/ or pattern/ change (rebuilds the two projector-side files)
./gradlew assembleRelease      # app/build/outputs/apk/release/ (debug-signed, installs over any build)
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # all JVM tests, including UI snapshots
```

Requires JDK 17 and the Android SDK (platform 34, build-tools 34.0.0). Put `sdk.dir=...` in `local.properties`.

**Tests.** Geometry, solver, pose (with a simulated camera), the marker detector on rendered photos, learning on a simulated wall, the ADB layer (against a simulated projector), remote input, installs and every projector tool run as ordinary JVM tests. UI snapshot tests render each screen with Robolectric to `app/build/ui-snapshots/`. Optional live tests run against real hardware: `KS_PROJECTOR=<ip>` for a read-only projector check and `KS_SCAN=<ip>/<prefix>` for the subnet scan. `KS_CAPTURES=<dir>` cross-checks the pipeline against real photos (never committed: they show a room).

The code map, projector facts and repository conventions are in [CLAUDE.md](CLAUDE.md).

## Known limitations

- The touchpad and air-mouse feel, and several 0.3.0 features (reconnect, sleep timer, send file, screenshot), have been verified in code and against a simulated projector, but not yet broadly on hardware.
- Non-English text cannot be typed, because the projector only receives key presses.
- App names are guessed from package names for apps the app does not know.
- The phone is assumed to face the wall (small deviations are absorbed by averaging and learning). Moving the projector or your seat may need a new run.

## Contributing

Issues and field reports are welcome, especially from **other projector models**: say what you have, what worked and attach the diagnostics report. Pull requests are welcome too; run `./gradlew testDebugUnitTest` first and read [CLAUDE.md](CLAUDE.md) for the layout.

## Credits

- [Project Settings](https://github.com/5ham5h33r/Project-Settings) by 5ham5h33r, the launcher that brings back hidden Settings on locked-down projectors.
- [dadb](https://github.com/mobile-dev-inc/dadb) for the ADB protocol on Android.
