# ProjectorConnect website

An immersive one-page site for the app. A photographed hand holds a phone whose screen is the real app; scrolling moves a
Three.js camera through the chapters (Remote, Type, Apps, Auto keystone, Set up) while the phone drives a projected "Android TV" on a
far wall. Built with Vite, Three.js, GSAP (ScrollTrigger), Lenis and Motion.

```sh
npm ci
npm run dev      # http://127.0.0.1:5173
npm run build    # static site in dist/
npm run preview
```

## How it works
- **Hand flipbook** (`src/hand.js`): photographed poses of a hand on pure black, with the phone screen flat magenta. A shader keys the
  magenta out and maps the live app screenshot onto the glass through a per-frame homography, so the UI stays glued to the phone.
  `goTo(pose)` walks a graph of poses (rest, reach, hover, tap, D-pad directions, key presses, swipe, air-mouse tilt) and plays the
  keyframes plus optical-flow in-betweens at about 18 fps, blending neighbours. A second rig holds the landscape two-hand shot used by Auto keystone.
- **Wall** (`src/wall.js`): a canvas-drawn Android TV (home, search, apps, calibration pattern) shown through a homography shader, so its four
  corners can be keystoned. The Auto keystone chapter takes a "photo" (hand presses the shutter, the phone screen shows a real room photograph)
  and the wall snaps from a trapezoid to a rectangle.
- **Scene** (`src/stage.js`, `particles.js`, `title.js`): projector beam, dust that reacts to the pointer, signal particles from the phone to the wall,
  bloom/grain/aberration post, and a big in-scene "AUTO KEYSTONE" title behind the hands.
- **Interactions** (`src/main.js`): a working D-pad (clicks and arrow keys), a type-to-wall field, app tiles, the capture button, a liquid-glass nav.
  Demo loops pause for a few seconds after the visitor interacts.

## Art pipeline (`art/`)
The hand frames were generated with Codex (`art/prompt-*.md`), then processed by `art/build_frames.py`, which finds each frame's magenta screen
quad, builds in-between frames with screen-aware optical flow and writes `public/assets/hand/**` and `src/frames.json`.
The original PNGs are not in git: they live in `projector-redesign/assets/website/hand-frames-v2/` (`raw/`, `raw2/`). To rebuild, copy them to `art/raw` and `art/raw2`
and run `python3 art/build_frames.py` (needs numpy, opencv-python, Pillow).

The room-photo capture screens (`public/assets/ui/capture-*.webp`) are the app's real capture screen with a generated room photograph behind the UI.

## Notes
- Download: replace `public/assets/ProjectorConnect-*.apk` and update the link and version in `index.html`.
- The old (first) version of the site is backed up in `projector-redesign/website-backup-2026-11-25/`.
- Phone layout hides the D-pad and app tiles (the hand still demos them); the type field stays.
