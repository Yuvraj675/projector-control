import './style.css';
import * as THREE from 'three';
import gsap from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import Lenis from 'lenis';
import { animate, spring } from 'motion';
import { Stage } from './stage.js';
import { initNavGlass } from './glass.js';
import { initAccordions } from './accordion.js';
import { initCinema, cue, timecode } from './cinema.js';
import kb from './keyboard-layout.json';

gsap.registerPlugin(ScrollTrigger);

const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const wait = (ms) => new Promise((r) => setTimeout(r, ms));

/* ------------------------------------------------------------------ loader */
const manager = new THREE.LoadingManager();
const numEl = $('#loader-num');
const shown = { v: 0 };
let target = 0;
manager.onProgress = (_u, loaded, total) => { target = (loaded / total) * 100; };
gsap.ticker.add((_t, dtMs) => {
  shown.v += (target - shown.v) * (1 - Math.exp(-(dtMs / 1000) * 9));
  numEl.textContent = Math.min(100, Math.round(shown.v + 0.4));
});

let stage;
try {
  stage = new Stage($('#gl'), manager);
} catch (e) {
  console.error('WebGL unavailable', e);
  document.body.classList.remove('is-loading');
  $('#loader').classList.add('done');
}

/* ----------------------------------------------------------- chapter table */
const NAMES = ['Welcome', 'Remote', 'Type and speak', 'Apps and files', 'Auto keystone', 'Set up'];

const metrics = () => {
  const tan = Math.tan((stage.camera.fov * Math.PI) / 360);
  const H0 = 2 * 9 * tan;                      // visible height at the hand's depth (camera z = 9)
  return { tan, H0, W0: H0 * (window.innerWidth / window.innerHeight) };
};

/** Fit the projected wall into the band between the nav and the chapter's headline, so it never overlaps text
 *  and never leaves a hole, whatever the window size. Returns world transform values for the wall. */
function fitWall(i, cz = 9, wz = -15, ly = 0.2) {
  const W = window.innerWidth, H = window.innerHeight;
  const r = copies[i].getBoundingClientRect();
  const top = Math.round($('#nav-wrap').getBoundingClientRect().bottom) + 14;
  const bottom = r.top - 28;
  const right = W - 36;
  let h = Math.max(110, Math.min(bottom - top, H * 0.52));
  let w = (h * 16) / 9;
  if (w > right - r.left) { w = right - r.left; h = (w * 9) / 16; }
  const cx = r.left + w / 2;                       // shares its left edge with the copy
  const cy = top + (bottom - top) / 2;
  // unproject through a camera identical to the scene's (same fov, aspect, position and look-at) so the result is exact
  const cam = new THREE.PerspectiveCamera(stage.camera.fov, W / H, 0.1, 120);
  cam.position.set(0, 0, cz);
  cam.lookAt(0, ly, -4);
  cam.updateMatrixWorld(true);
  const at = (px, py) => {
    const d = new THREE.Vector3((px / W) * 2 - 1, -(py / H) * 2 + 1, 0.5).unproject(cam).sub(cam.position).normalize();
    return cam.position.clone().addScaledVector(d, (wz - cam.position.z) / d.z);
  };
  const c = at(cx, cy), t = at(cx, cy - h / 2), b = at(cx, cy + h / 2);
  return { wx: c.x, wy: c.y, ws: (t.y - b.y) / 9 };
}

// Desktop: the hand on the left (its cropped arm leaves the frame), the wall in the free band above the copy.
function keyframes(portrait) {
  if (!portrait) {
    const { H0, W0 } = metrics();
    const hand = (fx, fy, fs, ry, rx = 0, rz = 0) => ({ hx: fx * W0, hy: fy * H0, hs: fs * H0, hrx: rx, hry: ry, hrz: rz });
    const b = { wz: -15, key: 0, wpow: 1, cz: 9, cx: 0, cy: 0, lx: 0, ly: 0.2, title: 0, proj: 0, room: 0 };
    return [
      { ...b, ...hand(-0.3, -0.03, 1.14, 0.22), ...fitWall(0), beam: 0.9, dust: 0.5, warm: 0.2, spill: 0.55, scroll: 0, proj: 1 },
      { ...b, ...hand(-0.29, -0.065, 1.22, 0.12, 0.04, 0.03), ...fitWall(1), beam: 1.0, dust: 0.8, warm: 0.35, spill: 0.8, scroll: 0.4, proj: 0.7 },
      { ...b, ...hand(-0.3, -0.085, 1.26, 0.2, 0.06, -0.02), ...fitWall(2), beam: 1.0, dust: 0.8, warm: 0.45, spill: 0.9, scroll: 0.8, proj: 0.6 },
      { ...b, ...hand(-0.29, -0.055, 1.2, 0.28, 0, -0.05), ...fitWall(3), beam: 1.0, dust: 0.9, warm: 0.5, spill: 0.8, scroll: 1.2, proj: 0.5 },
      { ...b, ...hand(0, -0.2, 0.58, 0, 0.03, 0), wx: 0, wy: 2.4, ws: 1.0, wpow: 0, beam: 0.15, dust: 1.0, warm: 0.3, spill: 0.6, scroll: 1.6, title: 1, room: 1, ly: 0.3 },
      { ...b, ...hand(-0.3, -0.17, 1.0, 0.3), wx: 2.8, wy: 3.4, ws: 0.5, wpow: 0, beam: 0.25, dust: 0.6, warm: 0.5, spill: 0.5, scroll: 2.0, cz: 10 },
    ];
  }
  return portraitFrames();
}

/** Portrait (phones, portrait tablets): copy on top, the wall right under it, then the whole phone in the hand, all fitted from the real
 *  viewport through a camera identical to the scene's, so nothing overlaps and nothing is left empty. */
function portraitFrames() {
  const W = window.innerWidth, H = window.innerHeight;
  const pad = Math.max(16, W * 0.04);
  const cz = 10, ly = 0, wz = -15;
  const cam = new THREE.PerspectiveCamera(stage.camera.fov, W / H, 0.1, 120);
  cam.position.set(0, 0, cz);
  cam.lookAt(0, ly, -4);
  cam.updateMatrixWorld(true);
  const at = (px, py, z) => {
    const d = new THREE.Vector3((px / W) * 2 - 1, -(py / H) * 2 + 1, 0.5).unproject(cam).sub(cam.position).normalize();
    return cam.position.clone().addScaledVector(d, (z - cam.position.z) / d.z);
  };
  const place = (cx, top, bottom, z) => {                      // a box given in pixels -> world centre + world height
    const c = at(cx, (top + bottom) / 2, z), t = at(cx, top, z), b = at(cx, bottom, z);
    return { x: c.x, y: c.y, h: t.y - b.y };
  };
  const base = { wz, key: 0, cx: 0, cy: 0, cz, lx: 0, ly, proj: 0, title: 0, room: 0, hrx: 0, hrz: 0 };
  const fx = [
    { beam: 0.8, dust: 0.5, warm: 0.2, spill: 0.5, scroll: 0 }, { beam: 0.9, dust: 0.8, warm: 0.35, spill: 0.8, scroll: 0.4 },
    { beam: 0.9, dust: 0.8, warm: 0.45, spill: 0.9, scroll: 0.8 }, { beam: 0.9, dust: 0.9, warm: 0.5, spill: 0.8, scroll: 1.2 },
    { beam: 0.3, dust: 1.0, warm: 0.3, spill: 0.7, scroll: 1.6 }, { beam: 0.25, dust: 0.6, warm: 0.5, spill: 0.4, scroll: 2.0 },
  ];
  const tilt = [0.1, 0.06, 0.12, 0.16, 0, 0.12];
  const out = [];
  for (let i = 0; i < 4; i++) {                                 // hero, Remote, Type, Apps: copy / wall / phone
    const r = copies[i].getBoundingClientRect();
    const copyBottom = r.bottom;
    const wallMaxH = ((W - 2 * pad - 24) * 9) / 16;
    const wallH = Math.max(84, Math.min(wallMaxH, H - H * 0.44 - copyBottom - 18));
    const wallTop = copyBottom + 8;
    const wp = place(W / 2 - 8, wallTop, wallTop + wallH, wz);
    const phoneTop = wallTop + wallH + 12;
    const hp = (H - phoneTop - 2) / 0.662;                      // plane height in px so the whole phone fits down to the bottom edge
    const planeTop = phoneTop - 0.0876 * hp;
    const hpl = place(W * 0.5, planeTop, planeTop + hp, 0);
    out.push({ ...base, ...fx[i], hx: hpl.x, hy: hpl.y, hs: hpl.h, hry: tilt[i], wx: wp.x, wy: wp.y, ws: wp.h / 9, wpow: 1 });
  }
  { // Auto keystone: the two-hand landscape shot across the bottom, the room behind everything
    const hpx = (W * 1.24) / 1.5;
    const cyPx = H * 0.795 + 0.03 * hpx;
    const hpl = place(W / 2, cyPx - hpx / 2, cyPx + hpx / 2, 0);
    out.push({ ...base, ...fx[4], hx: hpl.x, hy: hpl.y, hs: hpl.h, hry: 0, wx: 0, wy: 2.6, ws: 0.6, wpow: 0, title: 1, room: 1 });
  }
  { // Set up: the phone stays at the top while the instructions slide up over it
    const hpx = H * 0.62;
    const planeTop = 84 - 0.0876 * hpx;
    const hpl = place(W * 0.52, planeTop, planeTop + hpx, 0);
    out.push({ ...base, ...fx[5], hx: hpl.x, hy: hpl.y, hs: hpl.h, hry: 0.18, wx: 0, wy: 3.4, ws: 0.4, wpow: 0 });
  }
  return out;
}

const SCENES = [
  { rig: 'portrait', pose: 'hold', screen: 'home', wall: 'film' },
  { rig: 'portrait', pose: 'dpad-hover', screen: 'dpad', wall: 'tv' },
  { rig: 'portrait', pose: 'key-mid', screen: 'keyboard', wall: 'search' },
  { rig: 'portrait', pose: 'swipe-start', screen: 'apps', wall: 'apps' },
  { rig: 'landscape', pose: 'land-hold', screen: 'capture-idle', wall: 'film' },
  { rig: 'portrait', pose: 'hold', screen: 'connect', wall: 'film' },
];

let active = -1;
let portrait = window.innerWidth / window.innerHeight < 0.85;
let tl;
let lockedToSetup = false;
const copies = $$('.copy');
const copyStack = $('#copy');
const REST = '.kicker, .lede, .lede-short, .try, .cta-row, .note, .ks-col, .ks-actions, .ks-status';

/* ------------------------------------------------------- copy transitions */
function prepLines() {
  $$('.display .line').forEach((l) => {
    if (!l.dataset.ready) { l.innerHTML = `<span>${l.innerHTML}</span>`; l.dataset.ready = '1'; }
  });
}

function showCopy(i) {
  copies.forEach((el, k) => {
    const on = k === i;
    if (on === el.classList.contains('is-active') && !(on && el.dataset.shown !== '1')) return;
    const lines = $$('.line > span', el);
    const rest = $$(REST, el);
    gsap.killTweensOf([lines, rest]);
    if (on) {
      el.classList.add('is-active');
      el.dataset.shown = '1';
      gsap.fromTo(lines, { yPercent: 115, rotate: 3 }, { yPercent: 0, rotate: 0, duration: 1.15, ease: 'expo.out', stagger: 0.09, delay: 0.12 });
      gsap.fromTo(rest, { y: 24, opacity: 0 }, { y: 0, opacity: 1, duration: 0.9, ease: 'power3.out', stagger: 0.07, delay: 0.3 });
    } else {
      el.dataset.shown = '0';
      gsap.to(lines, { yPercent: -115, duration: 0.55, ease: 'power3.in', stagger: 0.04 });
      gsap.to(rest, { opacity: 0, y: -14, duration: 0.35, ease: 'power2.in', onComplete: () => { if (el.dataset.shown === '0') el.classList.remove('is-active'); } });
    }
  });
}

/* ------------------------------------------------------ chapter behaviour */
let demoTimer = null;
const idle = () => performance.now() - (stage?.lastInput || 0) > 5500;

function setChapter(i, from) {
  if (i === active || !stage) return;
  active = i;
  const sc = SCENES[i];
  const dir = from == null || i >= from ? 1 : -1;
  stage.hand.setRig(sc.rig);
  stage.hand.setPose(sc.pose, 20);
  stage.hand.setScreen(sc.screen, dir);
  stage.wall.setMode(sc.wall);
  showCopy(i);
  cue();
  startDemo(i);
  if (i === 4) { resetKeystone(true); keystoneAutoRun(); }

  $('#announce').textContent = `${NAMES[i]}, section ${i + 1} of 6`;
  $('#hud-i').textContent = i + 1;
  $('#hud-label').textContent = NAMES[i];
  $$('.nav a').forEach((a) => a.classList.toggle('on', +a.dataset.go === i));
  $$('.rail a').forEach((a) => a.classList.toggle('on', +a.dataset.go === i));
  $('.footnote').classList.toggle('ks-hide', i === 4);
  const inSetup = i === 5;
  $('.site-header').classList.toggle('solid', inSetup);
  copyStack.classList.toggle('off', inSetup);
  $('.hud').classList.toggle('off', inSetup);
  $('.footnote').classList.toggle('off', inSetup);
}

// D-pad: a deliberate click. The thumb floats over the button, presses down, holds for a beat, then lifts and comes back.
// Presses are queued so quick clicks play one after another instead of flicking.
const DIR_HOVER = { up: 'hv-up', left: 'hv-left', right: 'hv-right', down: 'lift', ok: 'lift' };
const DIR_PRESS = { up: 'up-1', left: 'left-1', right: 'right-1', down: 'tap', ok: 'tap' };
let padChain = Promise.resolve();
let padPending = 0;
function remoteKey(dir) {
  if (!stage || padPending >= 3) return;
  padPending++;
  stage.lastInput = performance.now();
  padChain = padChain.then(() => padPress(dir)).catch(() => {}).finally(() => { padPending--; });
}
async function padPress(dir) {
  if (active !== 1) return;
  const hover = stage.hand.hasPose(DIR_HOVER[dir]) ? DIR_HOVER[dir] : null;
  const press = DIR_PRESS[dir];
  if (hover) await stage.hand.play(hover, 12);
  if (active !== 1) return;
  await stage.hand.play(press, 12);                       // the thumb reaches the glass: this is the click
  const [x, y] = stage.tipOf(press);
  stage.hand.tap(x, y);
  stage.pulse = 1;
  stage.signals.burst(stage.time, 22);
  stage.wall.move(dir);
  stage.lastInput = performance.now();
  await wait(340);                                         // hold
  if (active !== 1) return;
  if (hover) await stage.hand.play(hover, 11);
  await stage.hand.play('dpad-hover', 11);                // and return
}
const KEYS = ['key-low-left', 'key-mid', 'key-low-right', 'key-mid'];
let keyI = 0;
function typeKey(value) {
  stage.wall.setTyped(value);
  stage.thumb(KEYS[keyI++ % KEYS.length], { burst: 5, fps: 26 });
}

/** type a character by tapping a key on the phone: the thumb goes to that third of the keyboard, the glass ripples at the key */
function typeAt(value, p) {
  stage.wall.setTyped(value);
  const pose = p.u < 0.4 ? 'key-low-left' : p.u > 0.62 ? 'key-low-right' : 'key-mid';
  stage.hand.setPose(pose, 20);
  stage.hand.tap(p.u, 1 - p.v);
  stage.signals.burst(stage.time, 5);
  stage.pulse = 1;
  stage.lastInput = performance.now();
}

function openApp(i) {
  stage.thumb(['swipe-mid', 'tap', 'swipe-end'][i % 3], { fps: 22, burst: 26 });
  stage.wall.openApp(i);
}

/** a tap on the phone's own glass does what the matching on-page control does */
const PAD = { up: [0.625, 0.512], down: [0.625, 0.678], left: [0.445, 0.595], right: [0.805, 0.595], ok: [0.625, 0.595] };   // matches ui/dpad.webp: the pad sits where the thumb lands
const APP_GRID = { x: [0.198, 0.501, 0.805], y: [0.224, 0.356, 0.487] };
function phoneTap(p) {
  stage.lastInput = performance.now();
  if (active === 1) {
    let best = null, bd = 0.085;
    for (const [dir, [u, v]] of Object.entries(PAD)) { const d = Math.hypot(p.u - u, (p.v - v) * 0.46); if (d < bd) { bd = d; best = dir; } }
    if (best) remoteKey(best);
  } else if (active === 2) {
    const k = kb.keys.find((q) => Math.abs(p.u - q.u) <= q.w / 2 + 0.004 && Math.abs(p.v - q.v) <= q.h / 2 + 0.006);
    const input = $('#try-input');
    if (!k || !input) return;
    let val = input.value;
    if (k.k === 'BACKSPACE') val = val.slice(0, -1);
    else if (k.k === 'ENTER') { stage.signals.burst(stage.time, 40); stage.hand.setPose('tap', 20); return; }
    else if (val.length < 22) val += k.k === ' ' ? ' ' : /[A-Z]/.test(k.k) ? k.k.toLowerCase() : k.k;
    input.value = val;
    typeAt(val, p);
  } else if (active === 3) {
    let best = -1, bd = 0.1;                                    // nearest icon wins (rows are close together)
    for (let r = 0; r < 3; r++) {
      for (let c = 0; c < 3; c++) {
        const i = r * 3 + c;
        if (i >= 8) continue;
        const d = Math.hypot(p.u - APP_GRID.x[c], (p.v - APP_GRID.y[r]) * 0.46);
        if (d < bd) { bd = d; best = i; }
      }
    }
    if (best >= 0) openApp(best);
  } else if (active === 4 && stage.hand.rigName === 'landscape') {
    if (Math.hypot(p.u - 0.5, (p.v - 0.86) * 2.1) < 0.09) ksBtn?.click();
  } else {
    stage.hand.tap(p.u, 1 - p.v);
    stage.signals.burst(stage.time, 14);
  }
}

function startDemo(i) {
  clearInterval(demoTimer);
  demoTimer = null;
  if (reduce) return;
  let n = 0;
  if (i === 0) {
    const poses = ['tilt-left', 'hold', 'tilt-right', 'hold'];
    demoTimer = setInterval(() => { if (idle()) stage.hand.setPose(poses[n++ % 4], 14); }, 2400);
  } else if (i === 1) {
    const seq = ['right', 'right', 'down', 'left', 'up', 'ok'];
    demoTimer = setInterval(() => { if (idle() && padPending === 0) remoteKey(seq[n++ % seq.length]); }, 3400);
  } else if (i === 2) {
    const word = 'interstellar';
    demoTimer = setInterval(() => {
      if (!idle()) return;
      const t = stage.wall.typed;
      typeKey(t.length < word.length ? word.slice(0, t.length + 1) : '');
    }, 330);
  } else if (i === 3) {
    const steps = ['swipe-mid', 'swipe-end', 'swipe-mid', 'swipe-start'];
    demoTimer = setInterval(() => {
      if (!idle()) return;
      const s = steps[n++ % 4];
      stage.thumb(s, { fps: 18, burst: 8, wall: s === 'swipe-end' ? 'right' : null });
    }, 1100);
  }
}

/* ----------------------------------------------------------- auto keystone */
let ksBusy = false;
let ksRan = false;
const ksBtn = $('#ks-capture'), ksReset = $('#ks-reset'), ksStatus = $('#ks-status');

function resetKeystone(instant) {
  stage.state.fix = 0;
  if (instant) stage.cur.fix = 0;
  ksStatus.textContent = 'The projector is tilted. The picture is a trapezoid.';
  ksBtn.disabled = false;
}

async function runKeystone() {
  if (ksBusy || !stage) return;
  ksBusy = true;
  ksBtn.disabled = true;
  stage.lastInput = performance.now();
  const s = stage.state;
  // the sequence is async: if the visitor scrolls away mid-way it must stop touching the shared phone screen and hand
  const alive = async (ms) => { await wait(ms); if (active !== 4) { ksBusy = false; ksBtn.disabled = false; return false; } return true; };
  ksStatus.textContent = 'Hold steady…';
  stage.hand.setPose('land-press-1', 18);
  if (!(await alive(420))) return;
  stage.hand.setPose('land-press-2', 22);
  if (!(await alive(300))) return;
  stage.hand.setScreen('capture-ready', 1);
  stage.hand.flash(1.1);
  stage.room.uniforms.uFlash.value = 0.35;
  stage.signals.burst(stage.time, 40);
  ksStatus.textContent = 'Photo taken. Finding the four circles…';
  if (!(await alive(650))) return;
  stage.hand.setPose('land-hold', 16);
  ksStatus.textContent = 'Solving for the largest rectangle…';
  if (!(await alive(700))) return;
  stage.room.uniforms.uFlash.value = 0.55;           // the projector redraws the picture straight
  gsap.to(s, { fix: 1, duration: 0.9, ease: 'power2.inOut' });
  stage.signals.burst(stage.time, 60);
  if (!(await alive(1200))) return;
  stage.hand.setScreen('capture-idle', -1);
  ksStatus.textContent = 'Straight. Done.';
  ksBusy = false;
  ksBtn.disabled = false;
}

function keystoneAutoRun() {
  if (ksRan || reduce) return;
  ksRan = true;
  setTimeout(() => { if (active === 4) runKeystone(); }, 2400);
}

ksBtn?.addEventListener('click', () => { if (stage.state.fix > 0.5) { resetKeystone(); setTimeout(runKeystone, 600); } else runKeystone(); });
ksReset?.addEventListener('click', () => {
  if (ksBusy) return;
  stage.lastInput = performance.now();
  stage.room.uniforms.uFlash.value = 0.3;
  gsap.to(stage.state, { fix: 0, duration: 0.7, ease: 'power2.inOut' });
  ksStatus.textContent = 'The projector is tilted again.';
});

/* ------------------------------------------------------------ interactions */
function interactions() {
  $$('.dpad button').forEach((b) => b.addEventListener('click', () => { if (active === 1) remoteKey(b.dataset.dir); }));
  window.addEventListener('keydown', (e) => {
    if (active !== 1 || e.target.closest('input,textarea,details,summary')) return;
    const m = { ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right', Enter: 'ok', ' ': 'ok' }[e.key];
    if (!m) return;
    e.preventDefault();
    $(`.dpad [data-dir="${m}"]`)?.classList.add('hit');
    setTimeout(() => $(`.dpad [data-dir="${m}"]`)?.classList.remove('hit'), 160);
    remoteKey(m);
  });
  const input = $('#try-input');
  input?.addEventListener('input', () => typeKey(input.value));
  input?.addEventListener('keydown', (e) => { if (e.key === 'Enter') { stage.signals.burst(stage.time, 40); stage.hand.setPose('tap', 20); } });
  $$('[data-app]').forEach((b) => b.addEventListener('click', () => openApp(+b.dataset.app)));
  // tap the phone itself (mouse or finger). A drag is a scroll, not a tap.
  let down = null;
  window.addEventListener('pointerdown', (e) => { down = { x: e.clientX, y: e.clientY, t: performance.now() }; });
  window.addEventListener('pointerup', (e) => {
    if (!stage || !down) return;
    const moved = Math.hypot(e.clientX - down.x, e.clientY - down.y), dt = performance.now() - down.t;
    down = null;
    if (moved > 10 || dt > 600 || e.target.closest('a,button,summary,details,input,label,.setup-inner,.faq,.try,.ks-col,.ks-actions')) return;
    const hit = stage.hitPhone(e.clientX, e.clientY);
    if (hit) phoneTap(hit);
    else if (e.pointerType === 'mouse' && active !== 4) {   // ambient: a click elsewhere just pings the glass
      stage.lastInput = performance.now();
      stage.hand.tap(0.3 + Math.random() * 0.4, 0.25 + Math.random() * 0.4);
      stage.signals.burst(stage.time, 22);
    }
  });
}

/* --------------------------------------------------------------- scrolling */
let lenis;
function setupScroll() {
  lenis = new Lenis({ lerp: 0.085, wheelMultiplier: 0.9, smoothWheel: !reduce });
  lenis.on('scroll', (e) => {
    ScrollTrigger.update();
    syncChapter();
    if (stage) stage.vel = e.velocity;
    $('.site-header').classList.toggle('hide', e.direction === 1 && e.scroll > 160);
  });
  gsap.ticker.add((t) => lenis.raf(t * 1000));
  gsap.ticker.lagSmoothing(0);
}

function buildStory() {
  tl?.scrollTrigger?.kill();
  tl?.kill();
  if (!stage) return;
  portrait = stage.portrait;
  const kf = keyframes(portrait);
  const s = stage.state;
  const n = kf.length;
  const { key, ...first } = kf[0];
  if (!buildStory.done) Object.assign(s, first);
  buildStory.done = true;
  tl = gsap.timeline({
    defaults: { ease: 'power2.inOut' },
    scrollTrigger: {
      trigger: '#story', start: 'top top', end: 'bottom bottom', scrub: 0.5,
      onUpdate: (self) => {
        const p = self.progress * (n - 1);
        $('#hud-fill').style.transform = `scaleX(${self.progress})`;
        timecode(self.progress);
        const next = Math.round(p);
        if (!lockedToSetup && next !== active) setChapter(next, active);
      },
    },
  });
  for (let i = 1; i < n; i++) {
    const { key: _k, ...rest } = kf[i];
    tl.to(s, { ...rest, duration: 1 }, i - 1);
  }
}

/** Single source of truth for which chapter we are in, derived from the scroll position only,
 *  so a fast jump (back button, anchor, restored scroll) can never leave a stale chapter behind. */
function syncChapter() {
  if (!tl?.scrollTrigger) return;
  const inSetup = $('#setup').getBoundingClientRect().top < window.innerHeight * 0.7;
  lockedToSetup = inSetup;
  setChapter(inSetup ? 5 : Math.round(tl.scrollTrigger.progress * 5), active);
}

function watchSetup() {
  ScrollTrigger.create({
    trigger: '#setup', start: 'top 70%',
    onEnter: syncChapter, onLeaveBack: syncChapter, onRefresh: syncChapter,
  });
}

function cursor() {
  const el = $('.cursor');
  if (!el || !matchMedia('(hover: hover) and (pointer: fine)').matches) return;
  let x = 0, y = 0, tx = 0, ty = 0;
  window.addEventListener('pointermove', (e) => { tx = e.clientX; ty = e.clientY; });
  gsap.ticker.add(() => { x += (tx - x) * 0.22; y += (ty - y) * 0.22; el.style.transform = `translate3d(${x}px,${y}px,0)`; });
  document.addEventListener('pointerover', (e) => {
    el.classList.toggle('is-link', !!e.target.closest('a,button,summary,input'));
    el.classList.toggle('hide', !!e.target.closest('.nav-wrap'));   // the glass lens is the feedback there
  });
}

function magnetic() {
  $$('.magnetic').forEach((el) => {
    el.addEventListener('pointermove', (e) => {
      const r = el.getBoundingClientRect();
      animate(el, { x: (e.clientX - (r.left + r.width / 2)) * 0.22, y: (e.clientY - (r.top + r.height / 2)) * 0.3 }, { type: spring, stiffness: 260, damping: 18 });
    });
    el.addEventListener('pointerleave', () => animate(el, { x: 0, y: 0 }, { type: spring, stiffness: 200, damping: 14 }));
  });
}

function anchors() {
  $$('[data-go]').forEach((a) => a.addEventListener('click', (e) => {
    const i = +a.dataset.go;
    e.preventDefault();
    const story = $('#story');
    const total = story.offsetHeight - window.innerHeight;
    const y = i >= 5 ? $('#setup').getBoundingClientRect().top + window.scrollY : (i / 5) * total + story.offsetTop;
    lenis?.scrollTo(y, { duration: 1.8, easing: (t) => 1 - Math.pow(1 - t, 4) });
  }));
}

/* ------------------------------------------------------------------- boot */
prepLines();
if (stage) {
  setupScroll();
  buildStory();
  watchSetup();
  anchors();
  interactions();
  initNavGlass();
  initAccordions();
  initCinema();
  cursor();
  magnetic();
  copies.forEach((c) => { c.classList.remove('is-active'); c.dataset.shown = '0'; });

  let lastPortrait = portrait, rt;
  window.addEventListener('resize', () => {
    clearTimeout(rt);
    rt = setTimeout(() => { lastPortrait = stage.portrait; buildStory(); ScrollTrigger.refresh(); }, 200);
  });

  (document.fonts?.ready ?? Promise.resolve()).then(() => { buildStory(); ScrollTrigger.refresh(); });

  const s = stage.state;
  s.intro = 0;
  stage.cur.intro = 0;

  const begin = () => {
    $('#loader').classList.add('done');
    document.body.classList.remove('is-loading');
    stage.start();
    gsap.to(s, { intro: 1, duration: 2.4, ease: 'power3.out', delay: 0.2 });
    ScrollTrigger.refresh();
    gsap.delayedCall(0.9, () => {
      active = -1;
      ScrollTrigger.refresh();
      const setupTop = $('#setup').getBoundingClientRect().top;
      const p = tl?.scrollTrigger?.progress ?? 0;
      setChapter(setupTop < window.innerHeight * 0.7 ? 5 : Math.round(p * 5));
    });
  };
  let ready = false;
  const go = () => { if (ready) return; ready = true; setTimeout(begin, 450); };
  manager.onLoad = () => { target = 100; go(); };
  setTimeout(go, 9000);
  window.__stage = stage;
  window.__lenis = lenis;
}
