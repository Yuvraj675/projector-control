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
The hand frames were generated with Codex (`art/prompt-*.md`): 16 portrait keyframes (rest, reach, hover, tap, D-pad directions, key presses,
swipe, air-mouse tilts) plus 12 denser in-between poses, and the landscape two-hand shot. `art/build_frames.py` then:
1. detects the flat-magenta screen quad of every frame (rotation-safe, so the UI stays glued to the glass on the tilted poses),
2. makes 3 in-betweens per neighbouring pair with **RIFE** (`rife-ncnn-vulkan`, the engine Flowframes wraps, native on Linux),
3. writes `public/assets/hand/**` and the pose graph `src/frames.json`.

The originals are not in git: they live in `projector-redesign/assets/website/hand-frames-v2/` (`raw/`, `raw2/`, `raw3/`).
To rebuild, copy them to `art/raw`, `art/raw2`, `art/raw3`, download the RIFE release (`rife-ncnn-vulkan-20221029-ubuntu.zip` from
github.com/nihui/rife-ncnn-vulkan/releases) into `art/tools/`, and run `python3 art/build_frames.py` (needs numpy, opencv-python, Pillow).

Other generated art: the room photographs (`public/assets/room-*.webp`, used as the Auto keystone backdrop and on the phone's capture screen),
the context-aware Apps and Type screens (`public/assets/ui/apps.webp`, `keyboard.webp`) and three app icons (`art/raw3/icon-*.png`).
The other five app icons are exact vector logos from simple-icons, tiled by `art/make_icons.mjs`. The Home, Remote and Connect screens are real app screenshots.
The site says so in a footnote: app screens are illustrations.

## Notes
- Download: replace `public/assets/ProjectorConnect-*.apk` and update the link and version in `index.html`.
- The old (first) version of the site is backed up in `projector-redesign/website-backup-2026-11-25/`.
- Layout: the desktop wall and hand are fitted from the real DOM and viewport (see `fitWall` in `src/main.js`), so nothing overlaps at other window sizes.
- Phone layout hides the D-pad and app tiles (the hand still demos them); the type field stays. Mobile polish is still to do.
