# ProjectorConnect website

A static site with an Android download, a setup walkthrough, and a live perspective correction demo. It uses HTML, CSS, and browser JavaScript modules without runtime dependencies.

From the repository root, run `python3 -m http.server 8080 --bind 127.0.0.1 --directory website` and open <http://127.0.0.1:8080>. Use any static host to serve this directory as-is.

## The room scene

- `assets/room.webp` is the locked camera plate. `assets/film.webp` is the separately composited picture used in the opening and demo.
- `room.js` positions the projection and renders the beam, lens bloom, and dust. The lens and wall coordinates are normalized against the room plate. Update them together if you change the camera image.
- `perspective.js` maps the projected image to four corner points. `correction.js` provides touch, mouse, and keyboard controls for the demo.
- The opening uses a CSS scroll timeline on supported desktops. Phones and reduced-motion users get a static composition and the full interactive demo. The beam canvas pauses outside the opening or in an inactive tab.
- The app previews come from actual ProjectorConnect screenshots. The room, film, and phone insert images are original generated artwork. The preview is fitted to the phone glass using the same perspective transform as the wall. The Barlow Condensed license is in `assets/fonts/`.

When shipping a new app version, replace the APK in `assets` and update its download link and version label in `index.html`.
