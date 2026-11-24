import './style.css';
import * as THREE from 'three';
import gsap from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import Lenis from 'lenis';
import { animate, spring } from 'motion';
import { Stage } from './stage.js';

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

// Desktop: hand on the left (its cropped arm leaves the frame), wall floating up-right.
function keyframes(portrait) {
  if (!portrait) {
    const w = { wz: -15, key: 0, wpow: 1, cz: 9, cx: 0, cy: 0, lx: 0, ly: 0.2 };
    return [
      { ...w, hx: -2.45, hy: -0.4, hs: 5.9, hrx: 0, hry: 0.22, hrz: 0, wx: 2.4, wy: 4.0, ws: 0.5, beam: 0.9, dust: 0.5, warm: 0.2, spill: 0.55, scroll: 0, title: 0, proj: 1 },
      { ...w, hx: -2.3, hy: -0.55, hs: 6.3, hrx: 0.04, hry: 0.12, hrz: 0.03, wx: 2.55, wy: 4.3, ws: 0.44, beam: 1.0, dust: 0.8, warm: 0.35, spill: 0.8, scroll: 0.4, cx: -0.2, title: 0, proj: 0.7 },
      { ...w, hx: -2.35, hy: -0.7, hs: 6.5, hrx: 0.06, hry: 0.2, hrz: -0.02, wx: 2.5, wy: 4.3, ws: 0.44, beam: 1.0, dust: 0.8, warm: 0.45, spill: 0.9, scroll: 0.8, cx: 0.1, title: 0, proj: 0.6 },
      { ...w, hx: -2.3, hy: -0.5, hs: 6.2, hrx: 0, hry: 0.28, hrz: -0.05, wx: 2.5, wy: 4.3, ws: 0.44, beam: 1.0, dust: 0.9, warm: 0.5, spill: 0.8, scroll: 1.2, cx: -0.1, title: 0, proj: 0.5 },
      { ...w, hx: 0, hy: -1.05, hs: 3.3, hrx: 0.03, hry: 0, hrz: 0, wx: 0, wy: 2.4, ws: 1.0, wpow: 0.42, beam: 1.1, dust: 1.0, warm: 0.3, spill: 0.6, scroll: 1.6, cz: 9.4, ly: 0.4, title: 1, proj: 0 },
      { ...w, hx: -3.1, hy: -1.2, hs: 5.0, hrx: 0, hry: 0.3, hrz: 0, wx: 2.8, wy: 3.4, ws: 0.5, wpow: 0, beam: 0.25, dust: 0.6, warm: 0.5, spill: 0.5, scroll: 2.0, cz: 10, ly: 0.4, title: 0, proj: 0 },
    ];
  }
  const b = { cx: 0, cy: 0, cz: 10, lx: 0, ly: 0, wz: -15, wpow: 0.5, key: 0, title: 0, proj: 0 };
  return [
    { ...b, hx: -0.1, hy: -2.5, hs: 6.3, hrx: 0, hry: 0.15, hrz: 0, wx: 0, wy: 2.6, ws: 0.4, beam: 0.8, dust: 0.5, warm: 0.2, spill: 0.5, scroll: 0 },
    { ...b, hx: -0.15, hy: -2.7, hs: 6.5, hrx: 0.04, hry: 0.1, hrz: 0, wx: 0, wy: 2.7, ws: 0.4, beam: 0.9, dust: 0.8, warm: 0.35, spill: 0.8, scroll: 0.4 },
    { ...b, hx: -0.15, hy: -2.7, hs: 6.5, hrx: 0.06, hry: 0.16, hrz: 0.02, wx: 0, wy: 2.7, ws: 0.4, beam: 0.9, dust: 0.8, warm: 0.45, spill: 0.9, scroll: 0.8 },
    { ...b, hx: -0.1, hy: -2.6, hs: 6.4, hrx: 0, hry: 0.2, hrz: -0.04, wx: 0, wy: 2.65, ws: 0.4, beam: 0.9, dust: 0.9, warm: 0.5, spill: 0.8, scroll: 1.2 },
    { ...b, hx: 0, hy: -1.5, hs: 2.6, hrx: 0.02, hry: 0, hrz: 0, wx: 0, wy: 2.9, ws: 0.62, wpow: 0.55, beam: 1.0, dust: 1.0, warm: 0.3, spill: 0.7, scroll: 1.6, title: 1 },
    { ...b, hx: -0.6, hy: -4.4, hs: 5.2, hrx: 0, hry: 0.2, hrz: 0, wx: 0, wy: 3.4, ws: 0.4, beam: 0.4, dust: 0.6, warm: 0.5, spill: 0.4, scroll: 2.0, wpow: 0 },
  ];
}

const SCENES = [
  { rig: 'portrait', pose: 'hold', screen: 'remote', wall: 'film' },
  { rig: 'portrait', pose: 'dpad-hover', screen: 'dpad', wall: 'tv' },
  { rig: 'portrait', pose: 'key-mid', screen: 'keyboard', wall: 'search' },
  { rig: 'portrait', pose: 'swipe-start', screen: 'apps', wall: 'apps' },
  { rig: 'landscape', pose: 'land-hold', screen: 'capture-idle', wall: 'pattern' },
  { rig: 'portrait', pose: 'hold', screen: 'install', wall: 'setup' },
];

let active = -1;
let portrait = window.innerWidth / window.innerHeight < 0.85;
let tl;
let lockedToSetup = false;
const copies = $$('.copy');
const copyStack = $('#copy');
const REST = '.kicker, .lede, .try, .cta-row, .note, .ks-col, .ks-actions, .ks-status';

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
  startDemo(i);
  if (i === 4) { resetKeystone(true); keystoneAutoRun(); }

  $('#hud-i').textContent = i + 1;
  $('#hud-label').textContent = NAMES[i];
  $$('.nav a').forEach((a) => a.classList.toggle('on', +a.dataset.go === i));
  const inSetup = i === 5;
  $('.site-header').classList.toggle('solid', inSetup);
  copyStack.classList.toggle('off', inSetup);
  $('.hud').classList.toggle('off', inSetup);
}

const DIR_POSE = { up: 'dpad-up', left: 'dpad-left', right: 'dpad-right', down: 'tap', ok: 'tap' };
function remoteKey(dir) {
  stage.thumb(DIR_POSE[dir], { wall: dir, burst: 22 });
  clearTimeout(remoteKey.t);
  remoteKey.t = setTimeout(() => active === 1 && stage.hand.setPose('dpad-hover', 18), 650);
}
const KEYS = ['key-low-left', 'key-mid', 'key-low-right', 'key-mid'];
let keyI = 0;
function typeKey(value) {
  stage.wall.setTyped(value);
  stage.thumb(KEYS[keyI++ % KEYS.length], { burst: 5, fps: 26 });
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
    demoTimer = setInterval(() => { if (idle()) remoteKey(seq[n++ % seq.length]); }, 1500);
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
  stage.state.key = 1;
  stage.state.fix = 0;
  if (instant) stage.cur.key = 1;
  ksStatus.textContent = 'The projector is tilted. The picture is a trapezoid.';
  ksBtn.disabled = false;
}

async function runKeystone() {
  if (ksBusy || !stage) return;
  ksBusy = true;
  ksBtn.disabled = true;
  stage.lastInput = performance.now();
  const s = stage.state;
  ksStatus.textContent = 'Hold steady…';
  stage.hand.setPose('land-press-1', 16);
  await wait(450);
  stage.hand.setPose('land-press-2', 20);
  await wait(320);
  stage.hand.setScreen('capture-ready', 1);
  stage.hand.flash(1.1);
  stage.signals.burst(stage.time, 40);
  ksStatus.textContent = 'Photo taken. Finding the four circles…';
  await wait(700);
  stage.hand.setPose('land-hold', 14);
  ksStatus.textContent = 'Solving for the largest rectangle…';
  gsap.to(s, { key: 0, duration: 1.9, ease: 'expo.inOut' });
  gsap.to(s, { fix: 1, duration: 1.0, delay: 1.5, ease: 'power2.inOut' });
  await wait(1700);
  stage.signals.burst(stage.time, 60);
  stage.wall.uniforms.uFlash.value = 0.5;
  await wait(900);
  stage.hand.setScreen('capture-idle', -1);
  ksStatus.textContent = 'Straight. Done.';
  ksBusy = false;
  ksBtn.disabled = false;
  ksBtn.querySelector('span').textContent = 'Take the photo';
}

function keystoneAutoRun() {
  if (ksRan || reduce) return;
  ksRan = true;
  setTimeout(() => { if (active === 4) runKeystone(); }, 2400);
}

ksBtn?.addEventListener('click', () => { if (stage.state.key < 0.05) { resetKeystone(); setTimeout(runKeystone, 700); } else runKeystone(); });
ksReset?.addEventListener('click', () => {
  if (ksBusy) return;
  stage.lastInput = performance.now();
  gsap.to(stage.state, { key: 1, duration: 1.2, ease: 'expo.inOut' });
  gsap.to(stage.state, { fix: 0, duration: 0.5 });
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
  $$('[data-app]').forEach((b) => b.addEventListener('click', () => {
    stage.thumb(['swipe-mid', 'tap', 'swipe-end'][+b.dataset.app % 3], { fps: 22, burst: 26 });
    stage.wall.openApp(+b.dataset.app);
  }));
  // click anywhere on the scene to tap the phone
  window.addEventListener('pointerdown', (e) => {
    if (!stage || active === 4 || e.target.closest('a,button,summary,details,input,label,.setup-inner,.faq,.try')) return;
    stage.lastInput = performance.now();
    stage.hand.tap(0.3 + Math.random() * 0.4, 0.25 + Math.random() * 0.4);
    stage.signals.burst(stage.time, 26);
  });
}

/* ------------------------------------------------------------- nav lens */
function navLens() {
  const nav = $('#nav');
  const lens = $('.nav-lens');
  if (!nav || !matchMedia('(hover: hover)').matches) return;
  const links = $$('a', nav);
  let shownOn = false;
  const place = (a, px) => {
    const r = a.getBoundingClientRect(), n = nav.getBoundingClientRect();
    const cx = r.left - n.left + r.width / 2;
    const pull = px == null ? 0 : (px - (r.left + r.width / 2)) * 0.22;
    const w = r.width + 22;
    animate(lens, { x: cx - w / 2 + pull, width: w }, { type: spring, stiffness: shownOn ? 380 : 900, damping: shownOn ? 30 : 60 });
  };
  links.forEach((a) => {
    a.addEventListener('pointerenter', (e) => {
      links.forEach((l) => l.classList.toggle('hot', l === a));
      place(a, e.clientX);
      shownOn = true;
      animate(lens, { opacity: 1, scaleX: 1.04, scaleY: 1.42 }, { type: spring, stiffness: 320, damping: 20 });
    });
    a.addEventListener('pointermove', (e) => place(a, e.clientX));
  });
  nav.addEventListener('pointerleave', () => {
    shownOn = false;
    links.forEach((l) => l.classList.remove('hot'));
    animate(lens, { opacity: 0, scaleX: 0.9, scaleY: 1 }, { duration: 0.28 });
  });
}

/* --------------------------------------------------------------- scrolling */
let lenis;
function setupScroll() {
  lenis = new Lenis({ lerp: 0.085, wheelMultiplier: 0.9, smoothWheel: !reduce });
  lenis.on('scroll', (e) => {
    ScrollTrigger.update();
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
  Object.assign(s, first);
  tl = gsap.timeline({
    defaults: { ease: 'power2.inOut' },
    scrollTrigger: {
      trigger: '#story', start: 'top top', end: 'bottom bottom', scrub: 0.5,
      onUpdate: (self) => {
        const p = self.progress * (n - 1);
        $('#hud-fill').style.transform = `scaleX(${self.progress})`;
        const next = Math.round(p);
        if (next !== active && !lockedToSetup) setChapter(next, active);
      },
    },
  });
  for (let i = 1; i < n; i++) {
    const { key: _k, ...rest } = kf[i];
    tl.to(s, { ...rest, duration: 1 }, i - 1);
  }
}

function watchSetup() {
  ScrollTrigger.create({
    trigger: '#setup', start: 'top 70%',
    onEnter: () => { lockedToSetup = true; setChapter(5, active); },
    onLeaveBack: () => { lockedToSetup = false; setChapter(4, 5); },
  });
}

function cursor() {
  const el = $('.cursor');
  if (!el || !matchMedia('(hover: hover) and (pointer: fine)').matches) return;
  let x = 0, y = 0, tx = 0, ty = 0;
  window.addEventListener('pointermove', (e) => { tx = e.clientX; ty = e.clientY; });
  gsap.ticker.add(() => { x += (tx - x) * 0.22; y += (ty - y) * 0.22; el.style.transform = `translate3d(${x}px,${y}px,0)`; });
  document.addEventListener('pointerover', (e) => el.classList.toggle('is-link', !!e.target.closest('a,button,summary,input')));
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
    const y = i >= 5 ? $('#setup').offsetTop : (i / 5) * total + story.offsetTop;
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
  navLens();
  cursor();
  magnetic();
  copies.forEach((c) => { c.classList.remove('is-active'); c.dataset.shown = '0'; });

  let lastPortrait = portrait, rt;
  window.addEventListener('resize', () => {
    clearTimeout(rt);
    rt = setTimeout(() => { if (stage.portrait !== lastPortrait) { lastPortrait = stage.portrait; buildStory(); } ScrollTrigger.refresh(); }, 200);
  });

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
