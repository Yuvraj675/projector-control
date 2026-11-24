import { setupCorrection } from './correction.js';
import { setupRoom } from './room.js';
import { fitProjection } from './perspective.js';

const motion = matchMedia('(prefers-reduced-motion: reduce)');
setupRoom(document.querySelector('.room'), motion);
setupCorrection(document.querySelector('.demonstration'), motion);

const previews = {
  remote: ['ProjectorConnect directional pad and playback buttons', 'Use the D-pad, touchpad, or air mouse to move around.', 'dpad'],
  keyboard: ['ProjectorConnect screen for typing, dictation, and opening links', 'Speak a search or paste a link from your phone.'],
  apps: ['ProjectorConnect list of installed apps and projector controls', 'Send files, install apps, and pin favorites for later.'],
  'fine-tune': ['ProjectorConnect corner handles on a projected picture', 'Use auto keystone or nudge a corner on compatible projectors.'],
};

const featureList = document.querySelector('.feature-list');
const proof = document.querySelector('.feature-proof');
const image = document.querySelector('#feature-image');
const caption = document.querySelector('#feature-caption');
const frame = proof.querySelector('.feature-frame');
const phone = proof.querySelector('.phone');
// The four display corners in the insert shot, clockwise from the top left.
const display = [[.278, .098], [.712, .098], [.723, .725], [.285, .727]];
new ResizeObserver(() => fitProjection(frame, phone, display)).observe(frame);

document.querySelector('.feature-fallback').hidden = true;
featureList.hidden = false;
featureList.addEventListener('click', async (event) => {
  const button = event.target.closest('button');
  if (!button) return;
  const { feature } = button.dataset;
  if (proof.dataset.feature === feature) return;

  proof.dataset.feature = feature;
  const [alt, description, asset = feature] = previews[feature];
  const src = `assets/${asset}.webp`;
  const next = new Image();
  next.src = src;

  for (const option of featureList.querySelectorAll('button')) {
    option.setAttribute('aria-pressed', String(option === button));
  }
  try { await next.decode(); } catch { /* Let the browser render accessible alt text if the image failed. */ }
  if (proof.dataset.feature !== feature) return;
  image.src = src;
  image.alt = alt;
  caption.textContent = description;
  if (!motion.matches) {
    image.getAnimations().forEach((animation) => animation.cancel());
    image.animate([{ opacity: .5 }, { opacity: 1 }], { duration: 230, easing: 'ease-out' });
  }
});
