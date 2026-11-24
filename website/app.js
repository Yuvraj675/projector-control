import { setupChapters } from './chapters.js';
import { setupCorrection } from './correction.js';
import { setupFeatureTour } from './feature-tour.js';
import { setupRoom } from './room.js';

const motion = matchMedia('(prefers-reduced-motion: reduce)');
setupRoom(document.querySelector('.room'), motion);
setupCorrection(document.querySelector('.demonstration'), motion);
setupChapters(motion, setupFeatureTour(motion));
