/** One stable viewport size for the whole page.
 *  Desktop: exactly innerWidth x innerHeight, live. Touch devices: the height only follows real changes (rotation, keyboard-free resizes),
 *  never the small jumps made by the browser's collapsing address bar, which is what made vh / dvh layouts glitch.
 *  While the bar slides, the smaller height is kept, so nothing is ever cut off at the bottom.
 *  Publishes --vh (one hundredth of the height) for CSS: use calc(var(--vh, 1vh) * N) instead of Nvh. */
const touch = matchMedia('(pointer: coarse)').matches;
let w = window.innerWidth, h = window.innerHeight;

function publish() {
  const root = document.documentElement;
  root.style.setProperty('--vh', h / 100 + 'px');
  // the real viewport can be taller than the stable height (mobile address bar hidden): the canvas keeps the stable size, so it never stretches,
  // and the strip below it is faded into the page colour instead of ending in a hard edge
  root.classList.toggle('has-gap', window.innerHeight - h > 6);
}

function sync() {
  const nw = window.innerWidth, nh = window.innerHeight;
  if (!touch || nw !== w || Math.abs(nh - h) > 160) { w = nw; h = nh; }
  else h = Math.min(h, nh);
  publish();
}

sync();
window.addEventListener('resize', sync);
window.addEventListener('orientationchange', () => setTimeout(sync, 250));

export const vp = { get w() { return w; }, get h() { return h; } };
