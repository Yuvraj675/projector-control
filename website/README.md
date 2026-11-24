# ProjectorConnect website

The site follows three chapters: the room and its projected picture, a movie-night walkthrough using the phone, and device setup.

From this directory, run:

```sh
npm ci
npm run dev
```

Open the local URL printed by Vite. Run `npm run build` to produce a static site in `dist/`, or `npm run preview` to serve that build.

## The scenes

- `public/assets/room.webp` is the locked camera plate. `film.webp` is the projected picture in the opening and the perspective demo.
- `room.js` positions the picture and renders the lens bloom, beam, and dust. The lens and wall coordinates are normalized against the room plate. The canvas pauses outside the opening and in inactive tabs.
- `perspective.js` maps an image to four corners. `correction.js` provides touch, mouse, and keyboard controls for the demo.
- `chapters.js` handles hash navigation, history, focus, and the spring-driven chapter indicator.
- `feature-tour.js` uses GSAP ScrollTrigger to bring the phone closer and advance the movie-night moments as visitors scroll. Motion blends screenshot changes on the photographed phone display. Visitors can select any moment directly. The compact layout skips pinning, and reduced motion keeps the direct controls with instant transitions.
- The app previews use real ProjectorConnect screenshots. The room, film, and phone insert images are original generated artwork. The Barlow Condensed license is in `public/assets/fonts/`.

When shipping a new app version, replace the APK in `public/assets` and update its download link and version label in `index.html`.
