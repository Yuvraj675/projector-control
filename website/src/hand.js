import * as THREE from 'three';
import gsap from 'gsap';
import frames from './frames.json';
import { homography, toMat3 } from './math.js';

/* The hand is a flipbook: a graph of photographed poses with generated and
   flow-interpolated in-betweens. goTo() walks the shortest path through the
   graph and the rig plays it at ~18 fps, blending neighbouring frames so the
   motion reads smoothly at any refresh rate. The flat magenta screen of each
   frame is replaced by the live app screen through a per-frame homography. */

const vert = /* glsl */ `
varying vec2 vUv;
void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }`;

const frag = /* glsl */ `
precision highp float;
varying vec2 vUv;
uniform sampler2D uA; uniform sampler2D uB;
uniform mat3 uHA; uniform mat3 uHB;
uniform float uMix;
uniform float uOpacity;
uniform sampler2D uScrA; uniform sampler2D uScrB;
uniform float uScrMix; uniform float uScrShift;
uniform float uGlow; uniform float uTouch; uniform vec2 uTouchAt; uniform float uSheen;
uniform float uFlash;

// key on chroma (how magenta the colour is), not brightness, so the thumb's shadow on the screen (dark magenta) keys cleanly
float chroma(vec3 c){ return (min(c.r, c.b) - c.g) / max(max(c.r, c.b), 0.05); }
float magenta(vec3 c){ return smoothstep(0.5, 0.82, chroma(c)) * smoothstep(0.03, 0.1, max(c.r, c.b)); }

vec3 screenAt(vec2 su){
  vec2 a = su + vec2(0.0, -uScrShift * uScrMix);
  vec2 b = su + vec2(0.0,  uScrShift * (1.0 - uScrMix));
  return mix(texture2D(uScrA, a).rgb, texture2D(uScrB, b).rgb, uScrMix);
}

vec4 layer(sampler2D tex, mat3 H, vec2 uv){
  vec4 tx = texture2D(tex, uv);
  vec3 c = tx.rgb;
  float k = magenta(c);
  float shade = clamp(max(c.r, c.b) / 0.92, 0.0, 1.0);        // how much light reaches the glass here (the thumb's shadow)
  float spill = clamp(chroma(c) * 3.0, 0.0, 1.0) * (1.0 - k);
  c.rb = mix(c.rb, vec2(c.g * 1.1), spill);
  vec3 q = H * vec3(uv, 1.0);
  vec2 su = q.xy / q.z;
  vec3 s = screenAt(clamp(su, 0.001, 0.999));
  float d = distance(su * vec2(0.5, 1.0), uTouchAt * vec2(0.5, 1.0));
  float rip = uTouch * (smoothstep(0.1, 0.0, d) * 0.14 + smoothstep(0.016, 0.0, abs(d - (1.0 - uTouch) * 0.18)) * 0.3);
  s += vec3(1.0, 0.45, 0.2) * rip;
  s += smoothstep(0.0, 1.0, 1.0 - abs((su.x + su.y * 0.55) - uSheen) * 6.0) * 0.012;
  s *= mix(1.0, mix(0.35, 1.0, shade), k);                    // thumb shadow falls on the app UI
  s = s * uGlow + uFlash;
  float al = max(tx.a, k);                 // silhouette alpha baked into the frame (solid bezel)
  return vec4(mix(c, s, k) * al, al);
}

void main(){
  vec4 a = layer(uA, uHA, vUv);
  vec4 b = layer(uB, uHB, vUv);
  // feather the image border: where the photographed arm is cut off by the frame it fades into the dark instead of showing a hard edge
  float edge = smoothstep(0.0, 0.07, vUv.x) * smoothstep(0.0, 0.07, 1.0 - vUv.x) * smoothstep(0.0, 0.05, vUv.y) * smoothstep(0.0, 0.05, 1.0 - vUv.y);
  gl_FragColor = mix(a, b, uMix) * uOpacity * edge;
}`;

const DEFAULT_TIP = [0.5, 0.35];

class Rig {
  constructor(name, shared, loader, renderer) {
    this.name = name;
    this.renderer = renderer;
    this.def = frames[name];
    this.loader = loader;
    this.cache = new Map();
    this.fps = 18;
    this.graph = new Map();
    for (const e of this.def.edges) {
      this._edge(e.a, e.b, e.frames);
      this._edge(e.b, e.a, [...e.frames].reverse());
    }
    this.mats = {};
    for (const [id, f] of Object.entries(this.def.frames)) {
      const q = f.quad.map(([x, y]) => [x, 1 - y]);
      this.mats[id] = toMat3(homography(q, [[0, 1], [1, 1], [1, 0], [0, 0]]));
    }
    this.seq = [this.def.rest];
    this.pos = 0;
    this.t = 0;
    this.current = this.def.rest;
    this.uniforms = {
      uA: { value: this.tex(this.def.rest) },
      uB: { value: this.tex(this.def.rest) },
      uHA: { value: this.mats[this.def.rest] },
      uHB: { value: this.mats[this.def.rest] },
      uMix: { value: 0 },
      uOpacity: { value: 1 },
      ...shared,
    };
    const [w, h] = this.def.size;
    this.mesh = new THREE.Mesh(
      new THREE.PlaneGeometry(w / h, 1),
      new THREE.ShaderMaterial({
        vertexShader: vert, fragmentShader: frag, uniforms: this.uniforms,
        transparent: true, premultipliedAlpha: true, depthWrite: false,
        blending: THREE.CustomBlending, blendEquation: THREE.AddEquation,
        blendSrc: THREE.OneFactor, blendDst: THREE.OneMinusSrcAlphaFactor,
      })
    );
    this.mesh.renderOrder = 0.5;                                // after the projector overlay (0), before dust, particles and light (1+)
    this.group = new THREE.Group();
    this.group.add(this.mesh);
    const q = this.def.frames[this.def.rest].quad;
    const asp = w / h;
    this.screenTop = new THREE.Vector3(((q[0][0] + q[1][0]) / 2 - 0.5) * asp, 0.5 - (q[0][1] + q[1][1]) / 2, 0.01);
    this.screenCentre = new THREE.Vector3(((q[0][0] + q[2][0]) / 2 - 0.5) * asp, 0.5 - (q[0][1] + q[2][1]) / 2, 0.01);
  }

  _edge(a, b, list) {
    if (!this.graph.has(a)) this.graph.set(a, []);
    this.graph.get(a).push({ to: b, frames: list });
  }

  tex(id) {
    let t = this.cache.get(id);
    if (!t) {
      // no mipmaps (the frames are drawn close to 1:1) so uploads are cheap, and upload the moment the image
      // is decoded instead of in the middle of the first frame that needs it
      t = this.loader.load(`/assets/hand/${this.def.dir}/${id}.webp`, (tex) => { try { this.renderer?.initTexture(tex); } catch {} });
      t.colorSpace = THREE.SRGBColorSpace;
      t.generateMipmaps = false;
      t.minFilter = THREE.LinearFilter;
      t.magFilter = THREE.LinearFilter;
      this.cache.set(id, t);
    }
    return t;
  }

  preload(ids) { ids.forEach((i) => this.tex(i)); }

  /** shortest keyframe path a -> b, expanded to every in-between frame */
  path(a, b) {
    if (a === b) return [a];
    const prev = new Map([[a, null]]);
    const q = [a];
    while (q.length) {
      const n = q.shift();
      if (n === b) break;
      for (const e of this.graph.get(n) || []) {
        if (!prev.has(e.to)) { prev.set(e.to, { from: n, frames: e.frames }); q.push(e.to); }
      }
    }
    if (!prev.has(b)) return [a, b];
    const hops = [];
    for (let n = b; prev.get(n); n = prev.get(n).from) hops.unshift(prev.get(n).frames);
    const out = [a];
    hops.forEach((f) => out.push(...f.slice(1)));
    return out;
  }

  goTo(id, fps) {
    if (!this.def.frames[id]) return;
    if (fps) this.fps = fps;
    const here = this.seq[Math.min(this.seq.length - 1, Math.round(this.pos))];
    const start = this.nearestKey(here);
    this.seq = this.path(start, id);
    this.pos = 0;
    this.t = 0;
    this.preload(this.seq);
  }

  nearestKey(id) {
    if (this.graph.has(id)) return id;
    const [a] = id.split('__');
    return this.graph.has(a) ? a : this.def.rest;
  }

  update(dt) {
    const last = this.seq.length - 1;
    // ease the whole path: the thumb accelerates out of rest and settles into the target
    this.t += dt;
    const lin = last > 0 ? Math.min(1, this.t / (last / this.fps)) : 1;
    const eased = lin * 0.35 + (lin * lin * (3 - 2 * lin)) * 0.65;
    this.pos = last * eased;
    const i = Math.floor(this.pos);
    const f = this.pos - i;
    const A = this.seq[i];
    const B = this.seq[Math.min(last, i + 1)];
    const u = this.uniforms;
    const ta = this.tex(A), tb = this.tex(B);
    const ready = (t) => t.image && t.image.width;
    if (ready(ta)) { u.uA.value = ta; u.uHA.value = this.mats[A]; }
    if (ready(tb)) { u.uB.value = tb; u.uHB.value = this.mats[B]; }
    u.uMix.value = ready(tb) ? f * f * (3 - 2 * f) : 0;
    this.current = f > 0.5 ? B : A;
    this.evict(A, B);
  }

  evict(A, B) {
    if (this.cache.size <= 22) return;
    const keep = new Set([A, B, this.def.rest, ...this.seq]);
    for (const [id, t] of this.cache) {
      if (this.cache.size <= 18) break;
      if (!keep.has(id)) { t.dispose(); this.cache.delete(id); }
    }
  }
}

export class Hand {
  constructor(renderer, manager) {
    const loader = new THREE.TextureLoader(manager);
    this.screens = {};
    const mk = (n, url) => {
      const t = loader.load(url);
      t.colorSpace = THREE.SRGBColorSpace;
      t.anisotropy = 8;
      this.screens[n] = t;
    };
    ['home', 'remote', 'dpad', 'keyboard', 'apps', 'connect'].forEach((n) => mk(n, `/assets/ui/${n}.webp`));
    mk('capture-idle', '/assets/ui/capture-idle.webp');
    mk('capture-ready', '/assets/ui/capture-ready.webp');

    this.shared = {
      uScrA: { value: this.screens.home },
      uScrB: { value: this.screens.home },
      uScrMix: { value: 0 },
      uScrShift: { value: 0 },
      uGlow: { value: 0.9 },
      uTouch: { value: 0 },
      uTouchAt: { value: new THREE.Vector2(...DEFAULT_TIP) },
      uSheen: { value: 0 },
      uFlash: { value: 0 },
    };
    this.rigs = {
      portrait: new Rig('portrait', this.shared, loader, renderer),
      landscape: new Rig('landscape', this.shared, loader, renderer),
    };
    this.group = new THREE.Group();
    this.group.add(this.rigs.portrait.group, this.rigs.landscape.group);
    this.rigs.landscape.group.visible = false;
    this.rigs.landscape.uniforms.uOpacity.value = 0;
    this.rig = this.rigs.portrait;
    this.rigName = 'portrait';
    this.screenName = 'home';
    this.mixTween = null;
    this.touch = 0;
  }

  get screenTop() { return this.rig.screenTop; }
  get mesh() { return this.rig.mesh; }

  /** swap between the portrait and landscape photo sets through a short fade */
  setRig(name) {
    if (name === this.rigName) return;
    const from = this.rig, to = this.rigs[name];
    this.rigName = name;
    this.rig = to;
    to.group.visible = true;
    gsap.killTweensOf([from.uniforms.uOpacity, to.uniforms.uOpacity]);
    gsap.to(from.uniforms.uOpacity, { value: 0, duration: 0.45, ease: 'power2.in', onComplete: () => { if (this.rig !== from) from.group.visible = false; } });
    gsap.to(to.uniforms.uOpacity, { value: 1, duration: 0.6, delay: 0.3, ease: 'power2.out' });
  }

  setPose(id, fps) { this.rig.goTo(id, fps); }

  /** play a pose path and resolve when the thumb has arrived (eased, so the end is a touch longer than frames / fps) */
  play(id, fps = 12) {
    this.rig.goTo(id, fps);
    const frames = Math.max(0, this.rig.seq.length - 1);
    return new Promise((r) => setTimeout(r, (frames / fps) * 1000 + 40));
  }

  hasPose(id) { return !!this.rig.def.frames[id]; }

  /** where on the phone screen (uv, v from the TOP like the screen layouts) a ray lands, or null */
  hitScreen(ray) {
    const rig = this.rig;
    const hits = ray.intersectObject(rig.mesh, false);
    if (!hits.length || !hits[0].uv) return null;
    const uv = hits[0].uv;
    const A = rig.seq[Math.min(rig.seq.length - 1, Math.floor(rig.pos))];
    const e = rig.mats[A].elements;
    const w = e[2] * uv.x + e[5] * uv.y + e[8];
    const su = (e[0] * uv.x + e[3] * uv.y + e[6]) / w, sv = (e[1] * uv.x + e[4] * uv.y + e[7]) / w;
    if (su < 0 || su > 1 || sv < 0 || sv > 1) return null;
    return { u: su, v: 1 - sv };
  }

  setScreen(name, direction = 1) {
    if (name === this.screenName) return;
    const u = this.shared;
    if (this.mixTween) { this.mixTween.kill(); u.uScrA.value = this.screens[this.screenName]; }
    u.uScrB.value = this.screens[name];
    u.uScrMix.value = 0;
    u.uScrShift.value = name.startsWith('capture') && this.screenName.startsWith('capture') ? 0 : 0.05 * direction;
    this.screenName = name;
    this.mixTween = gsap.to(u.uScrMix, {
      value: 1, duration: 0.65, ease: 'power3.inOut',
      onComplete: () => { u.uScrA.value = this.screens[name]; u.uScrMix.value = 0; u.uScrShift.value = 0; this.mixTween = null; },
    });
  }

  tap(x, y) {
    this.shared.uTouchAt.value.set(x, y);
    this.touch = 1;
  }

  flash(v = 0.9) { this.shared.uFlash.value = v; }

  update(dt) {
    this.touch = Math.max(0, this.touch - dt * 1.7);
    this.shared.uTouch.value = this.touch;
    this.shared.uFlash.value *= Math.exp(-dt * 7);
    if (this.rigs.portrait.group.visible) this.rigs.portrait.update(dt);
    if (this.rigs.landscape.group.visible) this.rigs.landscape.update(dt);
  }
}
