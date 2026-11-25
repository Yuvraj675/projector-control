/* Small cinema details that sit on top of the scene: grain, vignette and flicker are pure CSS; this file drives
 * the cursor light, the reel-change cue marks, the timecode and the "lights down" switch. */
const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
const $ = (s) => document.querySelector(s);

/** the two circles a projectionist watches for in the top-right corner of a film reel: one warns, one means change over */
export function cue() {
  const el = $('.cue');
  if (!el || reduce) return;
  el.classList.remove('go');
  void el.offsetWidth;            // restart the animation
  el.classList.add('go');
}

/** hh:mm:ss:ff, 24 frames a second, over a ninety second "running time" that follows the scroll */
const RUNTIME = 96;
export function timecode(progress) {
  const el = $('#hud-tc');
  if (!el) return;
  const f = Math.round(Math.min(1, Math.max(0, progress)) * RUNTIME * 24);
  const p = (n) => String(n).padStart(2, '0');
  el.textContent = `${p(Math.floor(f / 86400))}:${p(Math.floor(f / 1440) % 60)}:${p(Math.floor(f / 24) % 60)}:${p(f % 24)}`;
}

/** a warm pool of light that trails the pointer, as if the beam were spilling past the picture */
function cursorLight() {
  const el = $('.glow');
  if (!el || reduce || !matchMedia('(hover: hover) and (pointer: fine)').matches) return;
  let x = innerWidth / 2, y = innerHeight / 2, tx = x, ty = y, on = false;
  addEventListener('pointermove', (e) => { tx = e.clientX; ty = e.clientY; if (!on) { on = true; el.classList.add('on'); } }, { passive: true });
  document.addEventListener('pointerleave', () => { on = false; el.classList.remove('on'); });
  const tick = () => {
    x += (tx - x) * 0.09; y += (ty - y) * 0.09;
    el.style.transform = `translate3d(${x}px, ${y}px, 0)`;
    requestAnimationFrame(tick);
  };
  tick();
}

/** L (or the bulb in the corner) dims the house lights: heavier vignette, brighter beam, quieter chrome */
function lights() {
  const btn = $('#lights');
  const set = (on) => {
    document.body.classList.toggle('lights-down', on);
    btn?.setAttribute('aria-pressed', String(on));
    const a = $('#announce');
    if (a) a.textContent = on ? 'Lights down' : 'Lights up';
    try { sessionStorage.setItem('pc-lights', on ? '1' : '0'); } catch {}
  };
  btn?.addEventListener('click', () => set(!document.body.classList.contains('lights-down')));
  addEventListener('keydown', (e) => {
    if ((e.key === 'l' || e.key === 'L') && !e.metaKey && !e.ctrlKey && !e.altKey && !e.target.closest('input,textarea')) set(!document.body.classList.contains('lights-down'));
  });
  try { if (sessionStorage.getItem('pc-lights') === '1') set(true); } catch {}
}

export function initCinema() {
  cursorLight();
  lights();
}
