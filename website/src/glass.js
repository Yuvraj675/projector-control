import { animate, spring } from 'motion';

/* Liquid-glass nav lens.
 *
 * - the glass bar (.nav-glass), the links (.nav) and the lens (.nav-lens) are SIBLINGS. A backdrop-filter
 *   nested inside another backdrop-filter only sees its parent, which is what made the first version look flat.
 * - the lens refracts the page through an SVG displacement map generated per item size: a convex squircle
 *   surface, the refraction angle from Snell's law (air 1.0 -> glass 1.5), encoded as R/G offsets around 128.
 * - the lens keeps its aspect ratio: only x/width move on a spring and it pops with a uniform scale,
 *   so it never stretches.
 * - backdrop-filter: url(#svg) only works in Chromium; other engines get a plain frosted lens.
 */

const SVG_NS = 'http://www.w3.org/2000/svg';
const chromium = /Chrome\/|Chromium\//.test(navigator.userAgent);

const surface = (t) => Math.pow(1 - Math.pow(1 - t, 4), 0.25); // convex squircle height profile

function lensMap(w, h, bezel, glass = 1.5, depth = 28) {
  const c = document.createElement('canvas');
  c.width = w; c.height = h;
  const ctx = c.getContext('2d');
  const img = ctx.createImageData(w, h);
  const r = h / 2;
  const mags = new Float32Array(w * h);
  const dirs = new Float32Array(w * h * 2);
  let max = 0;
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const px = x + 0.5, py = y + 0.5;
      const cx = Math.min(Math.max(px, r), w - r);
      const dx = px - cx, dy = py - h / 2;
      const dist = Math.hypot(dx, dy);
      const inside = r - dist;                         // distance from the edge, >0 inside the pill
      if (inside <= 0) continue;
      const t = Math.min(1, inside / bezel);
      const e = 0.002;
      const slope = (surface(Math.min(1, t + e)) - surface(Math.max(0, t - e))) / (Math.min(1, t + e) - Math.max(0, t - e));
      const th1 = Math.atan(slope * 0.9);              // surface tilt = incidence angle
      const th2 = Math.asin(Math.sin(th1) / glass);    // Snell: sin(th1) = n * sin(th2)
      const m = Math.tan(th1 - th2) * depth * (1 - t * 0.0); // lateral shift on the background
      const i = y * w + x;
      mags[i] = m * (t < 1 ? 1 : 0);
      const nx = dist > 0 ? dx / dist : 0, ny = dist > 0 ? dy / dist : 0;
      dirs[i * 2] = nx; dirs[i * 2 + 1] = ny;
      if (mags[i] > max) max = mags[i];
    }
  }
  // combine: rim refraction (pulls the scene inwards at the bezel) + a gentle overall magnification
  const ZOOM = 1.0, zk = 1 - 1 / ZOOM; // no field zoom: nearest-neighbour displacement would stair-step the text
  const dxs = new Float32Array(w * h), dys = new Float32Array(w * h);
  let big = 1e-6;
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const i = y * w + x;
      const m = max ? mags[i] / max : 0;
      const rim = max * 0.55;                                 // px
      const px = x + 0.5 - w / 2, py = y + 0.5 - h / 2;
      const inPill = mags[i] > 0 || (r - Math.hypot(Math.max(Math.abs(px) - (w / 2 - r), 0), py)) > 0;
      const dx = (-dirs[i * 2] * m * rim - px * zk) * (inPill ? 1 : 0);
      const dy = (-dirs[i * 2 + 1] * m * rim - py * zk) * (inPill ? 1 : 0);
      dxs[i] = dx; dys[i] = dy;
      big = Math.max(big, Math.abs(dx), Math.abs(dy));
    }
  }
  for (let i = 0; i < w * h; i++) {
    img.data[i * 4] = 128 + (dxs[i] / big) * 127;
    img.data[i * 4 + 1] = 128 + (dys[i] / big) * 127;
    img.data[i * 4 + 2] = 128;
    img.data[i * 4 + 3] = 255;
  }
  ctx.putImageData(img, 0, 0);
  return { url: c.toDataURL('image/png'), scale: big * 2 }; // feDisplacementMap shifts by scale * (channel - 0.5)
}

function makeFilter(defs, id, w, h, map) {
  const f = document.createElementNS(SVG_NS, 'filter');
  f.setAttribute('id', id);
  f.setAttribute('color-interpolation-filters', 'sRGB');
  f.setAttribute('x', '0'); f.setAttribute('y', '0');
  f.setAttribute('width', w); f.setAttribute('height', h);
  f.setAttribute('filterUnits', 'userSpaceOnUse');
  f.setAttribute('primitiveUnits', 'userSpaceOnUse');
  f.innerHTML = `
    <feImage href="${map.url}" x="0" y="0" width="${w}" height="${h}" result="map" preserveAspectRatio="none"/>
    <feDisplacementMap in="SourceGraphic" in2="map" scale="${map.scale.toFixed(2)}" xChannelSelector="R" yChannelSelector="G"/>`;
  defs.appendChild(f);
}

export function initNavGlass() {
  const wrap = document.getElementById('nav-wrap');
  const nav = document.getElementById('nav');
  const lens = wrap?.querySelector('.nav-lens');
  const defs = document.getElementById('glass-defs');
  if (!wrap || !lens || !matchMedia('(hover: hover)').matches) return;
  const links = [...nav.querySelectorAll('a')];
  const PAD_X = 16, PAD_Y = 9;
  let sizes = [];

  const build = () => {
    defs.innerHTML = '';
    const nr = nav.getBoundingClientRect();
    sizes = links.map((a, i) => {
      const r = a.getBoundingClientRect();
      const w = Math.round(r.width + PAD_X * 2), h = Math.round(nr.height + PAD_Y * 2);
      if (chromium) makeFilter(defs, `lens-${i}`, w, h, lensMap(w, h, Math.min(h * 0.42, 20)));
      return { w, h, x: r.left - nr.left + r.width / 2 - w / 2, y: -PAD_Y };
    });
  };
  const ready = () => { build(); };
  (document.fonts?.ready ?? Promise.resolve()).then(ready);
  window.addEventListener('resize', () => { clearTimeout(build.t); build.t = setTimeout(build, 200); });

  let current = -1;
  let visible = false;
  const show = (i, px) => {
    const s = sizes[i];
    if (!s) return;
    const first = !visible;
    lens.style.height = s.h + 'px';
    lens.style.top = s.y + 'px';
    if (chromium) lens.style.backdropFilter = `url(#lens-${i}) saturate(1.6) brightness(1.12)`;
    // a little pull towards the pointer so the lens feels attached to the finger
    const pull = px == null ? 0 : (px - (links[i].getBoundingClientRect().left + links[i].offsetWidth / 2)) * 0.12;
    if (first) {
      lens.style.width = s.w + 'px';
      animate(lens, { x: s.x + pull, scale: 0.86, opacity: 0 }, { duration: 0 });
      animate(lens, { opacity: 1, scale: 1.0 }, { type: spring, stiffness: 380, damping: 22 });
    } else {
      animate(lens, { x: s.x + pull }, { type: spring, stiffness: 420, damping: 34 });
      animate(lens, { width: s.w }, { type: spring, stiffness: 520, damping: 40 });
    }
    visible = true;
    if (current !== i) {
      links.forEach((l, k) => l.classList.toggle('hot', k === i));
      current = i;
    }
  };
  const hide = () => {
    visible = false; current = -1;
    links.forEach((l) => l.classList.remove('hot'));
    animate(lens, { opacity: 0, scale: 0.9 }, { duration: 0.22 });
  };
  links.forEach((a, i) => {
    a.addEventListener('pointerenter', (e) => show(i, e.clientX));
    a.addEventListener('pointermove', (e) => show(i, e.clientX));
  });
  nav.addEventListener('pointerleave', hide);
}
