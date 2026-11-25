# ProjectorControl website

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

Frames also get a baked silhouette alpha (so the dark phone bezel is solid over any backdrop). Steps with a Codex-made midpoint in `raw4/` (the largest thumb
moves) only need one RIFE in-between; the builder uses whichever `raw4` midpoints exist (see `MIDS` in `build_frames.py`). `raw4/hv-*.png` are
"thumb hovering just above the button" poses: a D-pad click is hover, press, hold, lift, return. Magenta spill on edge pixels is removed when frames are built,
and the shader keys on chroma (so the thumb's shadow on the screen darkens the UI instead of showing as a purple band).

The originals are not in git: they live in `projector-redesign/assets/website/hand-frames-v2/` (`raw/`, `raw2/`, `raw3/`, `raw4/`).
To rebuild, copy them to `art/raw`, `art/raw2`, `art/raw3`, `art/raw4`, download the RIFE release (`rife-ncnn-vulkan-20221029-ubuntu.zip` from
github.com/nihui/rife-ncnn-vulkan/releases) into `art/tools/`, and run `python3 art/build_frames.py` (needs numpy, opencv-python, Pillow).

Other generated art: the room photographs (`public/assets/room-*.webp`, used as the Auto keystone backdrop and on the phone's capture screen),
the context-aware Apps and Type screens (`public/assets/ui/apps.webp`, `keyboard.webp`) and three app icons (`art/raw3/icon-*.png`).
The other five app icons are exact vector logos from simple-icons, tiled by `art/make_icons.mjs`. The Home and Connect screens are edits of real app screenshots with the device name and IP replaced by generic values; the Type screen shows an on-screen keyboard.
The site says so in a footnote: app screens are illustrations.

## Hosting, SEO and security
- Set `SITE_URL=https://your.domain npm run build` to emit absolute canonical, Open Graph and sitemap URLs (without it the canonical link is simply omitted).
- The build injects a strict Content-Security-Policy meta tag (no inline or third-party script, no external requests at all). `public/_headers` carries the same policy plus
  HSTS, `nosniff`, `X-Frame-Options`, a locked-down Permissions-Policy and immutable caching for Netlify/Cloudflare Pages. Mirror it on any other host.
- The download section shows the APK's SHA-256, computed at build time from the file in `public/assets`, so the page can never drift from the real file.
- `public/.well-known/security.txt` points reports to the repo's private security advisories.
- Audited: `npm audit` clean, no secrets in the current tree or the full git history, user input only ever reaches a canvas (never the DOM).
- Lighthouse (production build, headless software WebGL): Accessibility 100, Best Practices 100, SEO 100 with `SITE_URL` set. Performance is limited by software rendering in CI; adaptive quality steps resolution, bloom and particles down on slow devices.
- Accessibility: one persistent `h1` and a screen-reader outline of every chapter (the visual chapters are shown one at a time), skip link, live announcements of the current chapter, keyboard-operable controls, reduced-motion support.

## Notes
- Loader: a keystoned picture (trapezoid with the four calibration dots, drawn in SVG by `renderLoader` in `main.js`) that is straightened as assets load, a lamp that brightens with the percentage, and a status line; on completion the straight frame zooms through the camera. The stylesheet is a blocking `<link>` in `index.html` (it used to be imported from JS, which showed unstyled HTML in dev), plus a two-line critical style for the background.
- Download: the page reads `website/release.json` (written by the release workflow, see the root README) for the APK link, version, size and SHA-256. Without it the button links to the Releases page.
- The old (first) version of the site is backed up in `projector-redesign/website-backup-2026-11-25/`.
- Layout: the desktop wall and hand are fitted from the real DOM and viewport (see `fitWall` in `src/main.js`), so nothing overlaps at other window sizes.
- Phone layout hides the D-pad and app tiles (the hand still demos them); the type field stays. Mobile polish is still to do.

## Cinema details and polish (2026-11-25)
- `src/cinema.js` + the "cinema layer" block in `style.css`: film grain with a slow projector flicker, vignette, a warm pool of light that trails the pointer, reel-change cue marks (the two circles, top right) on every chapter change, a 24 fps timecode in the HUD that follows the scroll, and **Lights down** (the bulb button or the `L` key; remembered for the session). The loader is a film leader (crosshair, rings, sweep).
- `src/accordion.js`: Set up steps and FAQ answers open and close with an animated height and content rise. It replaces `name=` exclusivity with `data-group` so the open FAQ item closes with an animation; reduced motion and no-JS keep the native behaviour.
- Hand matte (`art/build_frames.py`): the generated frames sit on a dark smudge of cast shadow (value ~37), which used to survive as soft alpha and showed as a black, jagged halo and a hairy edge on the phone. The matte is now a tight silhouette (threshold above the smudge, the near-black bezel kept by its distance to the screen, the knit cuff by a lower threshold in its corner), smoothed, 1 px eroded, with edge colours inpainted from inside. Re-run `python3 art/build_frames.py` after changing it (RIFE frames are cached).
- D-pad: `public/assets/ui/dpad.webp` has the pad moved and scaled so the thumb of each pose (`dpad-hover`, `up-1`, `left-1`, `right-1`, `tap`) lands on its button; `PAD` in `main.js` and `tipOf` in `stage.js` use the same positions. Change one, change all three.
- The nav lens has no blur in any engine (the CSS fallback used to blur the labels); the Auto keystone status and actions are centred with left/right + text-align, not a translate (GSAP baked a stale pixel offset into it).
- Hand matte, second pass: a brightness-only threshold cut the shadowed underside of the palm off with a hard edge (the raw frames have a pure black background, value ~1, but the shaded skin is only 12-45). `matte()` now keeps any warm pixel (R-B >= 6) down to value 12, and the sleeve region down to 8, so the whole heel of the hand and the cuff survive. The left and bottom edges of the hand plane are not feathered in the shader (the arm leaves the photo there).
- Swipe poses `sw-a` and `sw-b` (the Apps chapter) were regenerated with Codex (`art/prompt-J.md`, output in `art/raw5/`, listed in `build_frames.py`): the first versions had a long, swollen, smeared thumb. The cached RIFE in-betweens around them were deleted and rebuilt.

## Viewport sizes
- Never use `vh` / `dvh` / `svh` in CSS. `src/viewport.js` publishes `--vh` (1% of a stable viewport height); write `calc(var(--vh, 1vh) * N)`. On touch devices the height ignores the small jumps of the collapsing address bar (it keeps the smaller value, so nothing is cut off); on desktop it is exactly `innerHeight`. JS reads the size from `vp.w` / `vp.h`, not from `window.inner*`.
- Desktop hand placement (`keyframes` in `src/main.js`) is a fraction of a 16:10 window; wider windows keep the hand the same distance from the left edge so its cropped edge never shows (checked from 1024x600 to 3440x1440).

## Hand frame polish
After `art/build_frames.py`, run `python3 art/polish_frames.py` once (needs numpy, Pillow, scipy). It sharpens the softer RIFE in-betweens to match their keyframes, evens out
exposure between poses (gain clamped to +-8%), and writes the half-size phone sets `public/assets/hand/portrait-m` and `landscape-m` that `src/hand.js` loads on small or touch screens.
Playback (`hand.js`): linear blend between frames (a smoothstep per pair made the picture dwell and then jump), a deep texture cache (80 desktop, 56 phone) and idle prefetch of every
frame one hop from the current pose, so a move never waits for a decode. Known limit: the biggest thumb moves (tilts, key presses) have few in-betweens and still step visibly; they need more generated midpoints.

## Phone layout (portrait, aspect < 17:20)
References looked at: Awwwards "mobile" collections (scroll-driven product stories keep one pinned object and swap short copy above it) and Framer's phone-scroll components (device pinned to the lower half, progress shown as dots). The site follows that pattern: copy on top, a small projected wall, then the phone as the hero, bleeding off the bottom edge.
- `portraitFrames()` in `main.js` fits copy / wall / phone from the real viewport; the phone gets at least half of the height.
- Auto keystone chapter: `.copy-wide` is a flex column pinned between the 3D title (`--ks-top`) and the hands, buttons in the flow, so nothing can overlap. `fitKeystone()` (re-run when `--ks-top` changes, on resize and on entering the chapter) drops the steps one notch at a time (`k2`: third step, `k3`: all steps) only if the column still does not fit. Phones show the short step text (`.m`), portrait tablets the long one (`.d`).
- Short phones (<= 680px high) drop the one-line lede on chapters 2-4; widths under 360px shrink the brand and hide the GitHub button; portrait tablets (>= 600px) get larger type.
- The fixed "screens are illustrations" footnote is hidden on phones (the footer says it).
- Checked at 320x568, 360x640, 390x664, 390x844, 412x915, 430x932, 600x960, 768x1024, 820x1180. Landscape phones keep the desktop layout (tested at 844x390: cramped but nothing breaks).

## Never let anything be wider than the screen
Mobile browsers treat any element wider than the viewport as page width and zoom the whole page out (tiny text, "zoomed" top-left corner). The film grain was a fixed layer at `inset: -50%` (twice the screen) and caused exactly that on phones while desktop browsers just clipped it. It is now screen-sized (the grain pattern is shifted with `background-position`), and `html`/`body` use `overflow-x: clip`. Check with `document.documentElement.scrollWidth === innerWidth` at several scroll positions, ideally in a mobile-emulated context (`isMobile`, `hasTouch`).

## Mobile address bar (the "stretch" bug)
`vp.h` (src/viewport.js) is the stable height. Everything that is laid out against it must be tied to it, not to the live viewport: `#gl` and `.copy-stack` use `calc(var(--vh) * 100)` anchored at the top, so when Chrome's address bar slides away the canvas keeps its size and proportions (it used to be `height: 100%`, which stretched the picture). While the live viewport is taller than the stable height, `html.has-gap` fades the canvas into the page colour over its last 3rem. The resize handler only rebuilds the scroll timeline when the stable size changes, and `ScrollTrigger.config({ ignoreMobileResize: true })` is set, so the bar sliding in and out no longer makes the page jump. Test it by growing a mobile-emulated viewport by ~90px.

Phone Auto keystone chapter: the explanation is a dark frosted-glass card (`backdrop-filter`) directly above the buttons. Set up: the hand is pinned to the bottom-left; `.setup::before` is translucent glass for the first screenful and solid further down.

## Background flicker when opening dropdowns on phones
Causes removed: (1) `Stage.resize()` is a no-op unless the stable size or pixel ratio changed (assigning `canvas.width` clears the canvas even to the same value, and phones fire `resize` for the address bar and layout shifts); (2) no `backdrop-filter` over the live canvas on phones (the Set up backdrop is a plain translucent gradient there; a blur re-rendered every frame while the accordion changed the section's height); (3) the bottom fade is a separate `.gl-fade` strip instead of a `mask-image` on the WebGL canvas; (4) `overflow-anchor: none` so growing accordions cannot make the browser nudge the scroll position. Test: count writes to `#gl`'s `width` (should stay constant over 20 `resize` events and over opening dropdowns).
