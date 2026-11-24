import { setupChapters } from './chapters.js';
import { setupCorrection } from './correction.js';
import { setupFeatureTour } from './feature-tour.js';
import { setupRoom } from './room.js';

const motion = matchMedia('(prefers-reduced-motion: reduce)');
const room = document.querySelector('.room');
const opening = room.closest('.opening');
// Load spatial rendering only when the Room chapter is in view.
const worldEntry = new IntersectionObserver(([entry], observer) => {
  if (!entry.isIntersecting) return;
  observer.disconnect();
  if (motion.matches) {
    setupRoom(room, motion);
    return;
  }
  import('./world.js').then(({ mountWorld }) => mountWorld(opening, motion)).then((mounted) => {
    if (!mounted) setupRoom(room, motion);
  }).catch(() => setupRoom(room, motion));
});
worldEntry.observe(opening);
setupCorrection(document.querySelector('.demonstration'), motion);
setupChapters(motion, setupFeatureTour(motion));
