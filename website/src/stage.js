import { vp } from './viewport.js';
import * as THREE from 'three';
import { EffectComposer } from 'three/examples/jsm/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/examples/jsm/postprocessing/RenderPass.js';
import { UnrealBloomPass } from 'three/examples/jsm/postprocessing/UnrealBloomPass.js';
import { ShaderPass } from 'three/examples/jsm/postprocessing/ShaderPass.js';
import { OutputPass } from 'three/examples/jsm/postprocessing/OutputPass.js';
import { Hand } from './hand.js';
import { Wall, keystoneCorners, lerpCorners } from './wall.js';
import { Beam, Dust, Signals, Atmosphere } from './particles.js';
import { Title } from './title.js';
import { damp, clamp } from './math.js';

const FinalPass = {
  uniforms: {
    tDiffuse: { value: null },
    uTime: { value: 0 },
    uAberr: { value: 0.0006 },
    uGrain: { value: 0.045 },
    uFade: { value: 1 },
    uMotion: { value: new THREE.Vector2() },
  },
  vertexShader: `varying vec2 vUv; void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }`,
  fragmentShader: /* glsl */ `
    precision highp float;
    varying vec2 vUv;
    uniform sampler2D tDiffuse;
    uniform float uTime, uAberr, uGrain, uFade;
    uniform vec2 uMotion;
    float hash(vec2 p){ return fract(sin(dot(p, vec2(12.9898,78.233)) + uTime) * 43758.5453); }
    void main(){
      vec2 c = vUv - 0.5;
      float d = dot(c, c);
      vec2 off = c * (uAberr + d * 0.0025);
      float r = texture2D(tDiffuse, vUv + off).r;
      float g = texture2D(tDiffuse, vUv).g;
      float b = texture2D(tDiffuse, vUv - off).b;
      vec3 col = vec3(r, g, b);
      // subtle motion blur along the scene's current screen-space velocity
      if (length(uMotion) > 0.0003) {
        vec3 acc = vec3(0.0);
        for (int i = 0; i < 7; i++) acc += texture2D(tDiffuse, vUv + uMotion * (float(i) / 6.0 - 0.5)).rgb;
        col = mix(col, acc / 7.0, 0.85);
      }
      col *= mix(0.62, 1.0, smoothstep(1.0, 0.3, length(c * vec2(1.0, 1.15)))); // soft vignette: it used to crush the bottom of the hand to ~35%
      col += (hash(vUv * 1000.0) - 0.5) * uGrain;
      gl_FragColor = vec4(col * uFade, 1.0);
    }`,
};

class Room {
  /** full-screen photograph of the living room: what the phone's camera is looking at in the Auto keystone chapter */
  constructor(manager) {
    const l = new THREE.TextureLoader(manager);
    const ld = (u) => { const t = l.load(u); t.colorSpace = THREE.SRGBColorSpace; return t; };
    this.uniforms = {
      uA: { value: ld('/assets/room-pattern.webp') }, uB: { value: ld('/assets/room-straight.webp') },
      uMix: { value: 0 }, uOpacity: { value: 0 }, uAspect: { value: 1.6 }, uMouse: { value: new THREE.Vector2() }, uFlash: { value: 0 }, uBias: { value: 1.0 },
    };
    this.mesh = new THREE.Mesh(new THREE.PlaneGeometry(2, 2), new THREE.ShaderMaterial({
      uniforms: this.uniforms, transparent: true, depthTest: false, depthWrite: false,
      vertexShader: 'varying vec2 vUv; void main(){ vUv = uv; gl_Position = vec4(position.xy, 0.0, 1.0); }',
      fragmentShader: /* glsl */ `
        precision highp float;
        varying vec2 vUv;
        uniform sampler2D uA, uB; uniform float uMix, uOpacity, uAspect, uFlash, uBias; uniform vec2 uMouse;
        void main(){
          vec2 p = vUv - 0.5;
          float tr = 1.5;                                   // photo aspect
          if (uAspect > tr) p.y *= tr / uAspect; else p.x *= uAspect / tr;
          p = p * 0.94 + 0.5 + uMouse * 0.012;              // slight overscan + parallax
          if (uAspect < 1.0) p.x -= 0.03;                     // portrait crop: keep the projected picture centred
          vec3 col = mix(texture2D(uA, p, uBias).rgb, texture2D(uB, p, uBias).rgb, uMix);
          float d = length((vUv - 0.5) * vec2(1.0, 0.9));
          col *= 0.52 * (1.0 - smoothstep(0.25, 0.95, d) * 0.7);
          col *= 0.65 + 0.35 * smoothstep(0.0, 0.55, vUv.y);   // darker towards the top where the title sits
          col += uFlash;
          gl_FragColor = vec4(col, uOpacity);
        }`,
    }));
    this.mesh.renderOrder = -9;
    this.mesh.frustumCulled = false;
  }
}

export class Stage {
  constructor(canvas, manager) {
    this.canvas = canvas;
    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: false, powerPreference: 'high-performance' });
    this.renderer.setClearColor(0x000000, 1);
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.NoToneMapping;

    this.scene = new THREE.Scene();
    this.camera = new THREE.PerspectiveCamera(32, 1, 0.1, 120);
    this.camera.position.set(0, 0, 9);

    this.atmo = new Atmosphere();
    this.scene.add(this.atmo.mesh);

    this.room = new Room(manager);
    this.scene.add(this.room.mesh);

    this.wallGroup = new THREE.Group();
    this.wall = new Wall(manager);
    this.wallGroup.add(this.wall.mesh);
    this.scene.add(this.wallGroup);

    this.beam = new Beam();
    this.beam.apex.set(-3.0, 0.5, 2.3);
    this.scene.add(this.beam.mesh);

    this.title = new Title('AUTO KEYSTONE');
        this.scene.add(this.title.mesh);
    this.title.draw();

    // the projector that casts the beam: a soft, slightly out-of-focus foreground object
    const pj = new THREE.TextureLoader(manager).load('/assets/projector.webp');
    pj.colorSpace = THREE.SRGBColorSpace;
    this.projector = new THREE.Mesh(
      new THREE.PlaneGeometry(3.6, 3.6 * 641 / 1100),
      new THREE.MeshBasicMaterial({ map: pj, transparent: true, depthWrite: false, opacity: 0, side: THREE.DoubleSide })
    );
    this.projector.scale.set(-1.5, 1.5, 1); // lens faces right, towards the wall
    this.projector.position.set(-3.5, 1.5, 2.2);
    this.projector.rotation.z = 0.05;
    this.projector.renderOrder = 0;
    this.scene.add(this.projector);
    this.dust = new Dust(vp.w < 800 ? 3500 : 7000);
    this.scene.add(this.dust.points);

    this.hand = new Hand(this.renderer, manager);
    this.scene.add(this.hand.group);

    this.signals = new Signals();
    this.scene.add(this.signals.points);

    // soft light spill behind the hand (sprite)
    const spill = new THREE.Mesh(
      new THREE.PlaneGeometry(1.5, 1.7),
      new THREE.ShaderMaterial({
        transparent: true, depthWrite: false, blending: THREE.AdditiveBlending,
        uniforms: { uI: { value: 0.0 }, uC: { value: new THREE.Color(1.0, 0.42, 0.18) } },
        vertexShader: `varying vec2 vUv; void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }`,
        fragmentShader: `varying vec2 vUv; uniform float uI; uniform vec3 uC; void main(){ float d = length(vUv-0.5)*2.0; float a = pow(max(1.0-d,0.0),2.6)*uI; gl_FragColor = vec4(uC*a, a); }`,
      })
    );
    spill.position.set(0.05, 0.1, -0.02);
    spill.renderOrder = 0.8;              // light stays in front of the hand
    this.spill = spill;
    this.hand.group.add(spill);

    // composer
    this.composer = new EffectComposer(this.renderer);
    this.composer.addPass(new RenderPass(this.scene, this.camera));
    this.bloom = new UnrealBloomPass(new THREE.Vector2(1, 1), 0.45, 0.6, 0.92);
    this.composer.addPass(this.bloom);
    this.composer.addPass(new OutputPass());
    this.final = new ShaderPass(FinalPass);
    this.composer.addPass(this.final);

    /* Animated state: GSAP tweens these, the loop eases the scene toward them. */
    this.state = {
      room: 0, title: 0, proj: 0, hx: 0, hy: -7, hz: 0, hrx: 0.0, hry: 0, hrz: 0, hs: 5.2,
      cx: 0, cy: 0, cz: 9, lx: 0, ly: 0,
      wx: 0, wy: 3, wz: -15, ws: 1, key: 0, wpow: 0,
      beam: 0, dust: 0.0, warm: 0.2, spill: 0, fade: 1, scroll: 0, intro: 0, grid: 0, flash: 0, fix: 0,
    };
    this.cur = { ...this.state };

    this.mouse = new THREE.Vector2();
    this.mouseS = new THREE.Vector2();
    this.vel = 0;
    this.clock = { t: performance.now(), getDelta() { const n = performance.now(), d = (n - this.t) / 1000; this.t = n; return d; } };
    this.time = 0;
    this.running = false;
    this.cornerKey = keystoneCorners.square;
    this.world = [0, 1, 2, 3].map(() => new THREE.Vector3());
    this.tmp = new THREE.Vector3();
    this.ray = new THREE.Raycaster();
    this.plane0 = new THREE.Plane(new THREE.Vector3(0, 0, 1), 0);
    this.pulseAt = 0;
    this.reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    this.lastInput = 0;
    this.focus = 0;
    this.pulse = 0;
    this.prevC = { hx: 0, hy: 0, hs: 0, cx: 0, cz: 9, wx: 0, wy: 0 };
    this.motion = new THREE.Vector2();

    window.addEventListener('pointermove', (e) => {
      this.mouse.set((e.clientX / vp.w) * 2 - 1, -((e.clientY / vp.h) * 2 - 1));
    });
    // adaptive quality: 2 = full, 1 = lighter, 0 = lightest. Starts lower on phones and only ever steps down when frames run slow.
    this.tier = vp.w < 800 ? 1 : 2;
    this.ema = 1 / 60;
    this.tierClock = 0;
    this.applyTier(false);
    this.resize();
    window.addEventListener('resize', () => this.resize());
    document.addEventListener('visibilitychange', () => (this.clock.getDelta(), 0));
  }

  applyTier(resize = true) {
    const t = this.tier;
    this.maxDpr = [1, 1.35, 2][t];
    this.bloom.enabled = t >= 1;
    const frac = t >= 2 ? 1 : t === 1 ? 0.55 : 0.3;
    this.dust.points.geometry.setDrawRange(0, Math.floor(this.dust.count * frac));
    if (resize) this.resize();
  }

  get portrait() { return this.aspect < 0.85; }

  resize() {
    const w = vp.w, h = vp.h;
    this.aspect = w / h;
    const dpr = Math.min(window.devicePixelRatio || 1, w < 800 ? 1.75 : 2, this.maxDpr ?? 2);
    this.renderer.setPixelRatio(dpr);
    this.renderer.setSize(w, h, false);
    this.composer.setPixelRatio(dpr);
    this.composer.setSize(w, h);
    this.camera.aspect = this.aspect;
    this.camera.fov = this.portrait ? 40 : 32;
    this.camera.updateProjectionMatrix();
    const px = dpr * (h / 900);
    this.dust.uniforms.uPx.value = px;
    this.signals.uniforms.uPx.value = px;
    this.atmo.uniforms.uAspect.value = this.aspect;
    this.title.layout(this.portrait);
    this.titleY = this.portrait ? 1.7 : 1.35;
  }

  start() {
    if (this.running) return;
    this.running = true;
    this.clock.getDelta();
    this.renderer.setAnimationLoop(() => this.frame());
  }

  /** pointer -> position on the phone's screen (uv, v from the top), or null if the pointer is not over the glass */
  hitPhone(clientX, clientY) {
    const r = this.canvas.getBoundingClientRect();
    const ndc = new THREE.Vector2(((clientX - r.left) / r.width) * 2 - 1, -((clientY - r.top) / r.height) * 2 + 1);
    this.ray.setFromCamera(ndc, this.camera);
    this.hand.group.updateMatrixWorld(true);
    return this.hand.hitScreen(this.ray);
  }

  /** thumb tip on the glass (screen uv) for each frame, used to place the touch ripple */
  tipOf(id) {
    return {
      'tap': [0.625, 0.405], 'dpad-hover': [0.625, 0.405], 'up-1': [0.625, 0.488], 'left-1': [0.445, 0.405], 'right-1': [0.805, 0.405], 'dpad-up': [0.69, 0.65], 'dpad-left': [0.23, 0.4], 'dpad-right': [0.88, 0.39],
      'key-mid': [0.5, 0.3], 'key-low-left': [0.38, 0.18], 'key-low-right': [0.66, 0.2],
      'swipe-start': [0.45, 0.38], 'swipe-mid': [0.55, 0.52], 'swipe-end': [0.6, 0.65],
      'land-press-2': [0.9, 0.5], 'land-press-1': [0.93, 0.5],
    }[id] || [0.5, 0.4];
  }

  /** move the thumb to a pose, ripple the glass once it lands and send signals to the wall */
  thumb(id, { fps = 22, burst = 18, wall = null } = {}) {
    this.hand.setPose(id, fps);
    const [x, y] = this.tipOf(id);
    window.setTimeout(() => {
      this.hand.tap(x, y);
      this.signals.burst(this.time, burst);
      if (wall) this.wall.move(wall);
      else this.wall.pressPulse = 1;
    }, 140);
    this.lastInput = performance.now();
    this.pulse = 1;
  }

  /** a tap on the phone: ripple on glass + a stream of signals to the wall */
  tap(x, y, n = 26) {
    this.hand.tap(x, y);
    this.pulse = 1;
    this.signals.burst(this.time, n);
    this.wall.press();
    this.state.flash = 1;
  }

  /** Pin the big title to a fixed screen band (so it never depends on camera or aspect), size it to the viewport
   *  and publish where its glyphs end (--ks-top) so the explanation columns can sit right under it. */
  layoutTitle() {
    const cam = this.camera, tt = this.title, g = tt.glyph;
    const zPlane = -3.2;
    const mid = this.portrait ? 0.235 : 0.265;
    const ndcY = 1 - 2 * mid;
    const dir = new THREE.Vector3(0, ndcY, 0.5).unproject(cam).sub(cam.position).normalize();
    const k = (zPlane - cam.position.z) / dir.z;
    const p = cam.position.clone().addScaledVector(dir, k);
    const depth = cam.position.z - zPlane;
    const visH = 2 * depth * Math.tan(THREE.MathUtils.degToRad(cam.fov / 2));
    const visW = visH * this.aspect;
    let scale = (visW * (this.portrait ? 0.86 : 0.9)) / tt.baseW;
    const glyphH = (g.bottom - g.top) * tt.baseH;
    scale = Math.min(scale, (this.portrait ? 0.3 : 0.19) * visH / glyphH);
    tt.mesh.scale.setScalar(scale);
    // centre the GLYPHS (not the canvas) on the chosen band
    const glyphMid = (g.top + g.bottom) / 2;
    tt.mesh.position.set(p.x - this.mouseS.x * 0.35, p.y + (glyphMid - 0.5) * tt.baseH * scale + this.mouseS.y * 0.15, zPlane);
    if (this.cur.title > 0.4) {
      const bottomY = tt.mesh.position.y - (g.bottom - 0.5) * tt.baseH * scale;
      const v = new THREE.Vector3(0, bottomY, zPlane).project(cam);
      const px = Math.round((1 - v.y) / 2 * vp.h);
      if (Math.abs(px - (this._ksTop ?? -99)) > 1) { this._ksTop = px; document.documentElement.style.setProperty('--ks-top', px + 'px'); window.dispatchEvent(new Event('kstop')); }
    }
  }

  frame() {
    const raw = this.clock.getDelta();
    const dt = Math.min(raw, 0.05);
    this.time += dt;
    // governor: sustained slow frames -> step quality down one tier
    this.ema += (Math.min(raw, 0.2) - this.ema) * 0.05;
    this.tierClock += dt;
    if (this.tierClock > 1.5 && this.time > 4) {
      this.tierClock = 0;
      if (this.ema > 1 / 36 && this.tier > 0) { this.tier--; this.applyTier(); this.ema = 1 / 60; }
    }
    const t = this.time;
    const s = this.state, c = this.cur;

    for (const k in s) {
      if (typeof s[k] === 'number') c[k] = damp(c[k] ?? s[k], s[k], k === 'hy' || k === 'hs' ? 4.2 : 3.4, dt);
    }

    c.key = s.key; c.fix = s.fix; c.title = damp(c.title, s.title, 5, dt); c.proj = damp(c.proj, s.proj, 4, dt); c.room = damp(c.room, s.room, 4, dt);

    this.mouseS.x = damp(this.mouseS.x, this.mouse.x, 4, dt);
    this.mouseS.y = damp(this.mouseS.y, this.mouse.y, 4, dt);

    // camera
    const cam = this.camera;
    cam.position.set(c.cx + this.mouseS.x * 0.35, c.cy + this.mouseS.y * 0.2, c.cz);
    cam.lookAt(c.lx, c.ly, -4);

    // hand: float + pointer tilt
    const float = Math.sin(t * 0.8) * 0.045;
    const hg = this.hand.group;
    const ii = clamp(c.intro, 0, 1);
    const introP = clamp((ii - 0.35) / 0.5, 0, 1);
    hg.position.set(c.hx, c.hy + float - Math.pow(1 - ii, 2) * 6.5, c.hz);
    hg.rotation.set(c.hrx - this.mouseS.y * 0.09 + Math.sin(t * 0.6) * 0.012, c.hry + this.mouseS.x * 0.17, c.hrz + Math.sin(t * 0.5) * 0.008);
    hg.scale.setScalar(c.hs);
    this.hand.shared.uSheen.value = (this.mouseS.x * 0.5 + 0.5) * 1.6 - 0.3;
    this.hand.update(dt);
    this.spill.material.uniforms.uI.value = c.spill * 0.4 * (0.8 + 0.2 * Math.sin(t * 2.3));

    // wall
    this.wallGroup.position.set(c.wx - this.mouseS.x * 0.4, c.wy - this.mouseS.y * 0.25, c.wz);
    this.wallGroup.scale.setScalar(c.ws);
    this.wall.setCorners(lerpCorners(keystoneCorners.square, this.cornerKey, clamp(c.key, 0, 1)));
    this.wall.uniforms.uPower.value = c.wpow * introP;
    this.wall.uniforms.uGrid.value = c.grid * clamp(c.key * 4, 0, 1) * clamp((1 - c.key) * 8 + 0.15, 0, 1) * 0.9;
    this.wall.fix = c.fix;
    this.wall.update(dt, t);
    this.wallGroup.updateMatrixWorld(true);
    for (let i = 0; i < 4; i++) {
      const [x, y] = this.wall.corners[i];
      this.world[i].set(x, y, 0).applyMatrix4(this.wallGroup.matrixWorld);
    }
    this.title.uniforms.uOpacity.value = c.title * introP;
    this.title.uniforms.uReveal.value = clamp(c.title * 1.05, 0, 1);
    this.title.uniforms.uTime.value = t;
    this.title.uniforms.uMouse.value.copy(this.mouseS);
    this.layoutTitle();
    const ru = this.room.uniforms;
    ru.uOpacity.value = clamp(c.room, 0, 1) * introP;
    ru.uMix.value = c.fix;
    ru.uAspect.value = this.aspect;
    ru.uMouse.value.copy(this.mouseS);
    ru.uFlash.value *= Math.exp(-dt * 6);
    this.projector.material.opacity = c.proj * introP * 0.95;
    this.projector.position.y = 1.5 + Math.sin(t * 0.6) * 0.04 - this.mouseS.y * 0.12;
    this.projector.position.x = -3.5 + this.mouseS.x * 0.2;
    this.beam.update(this.world, t);
    this.beam.uniforms.uPower.value = c.beam * introP;
    const centre = this.tmp.set(0, 0, 0).applyMatrix4(this.wallGroup.matrixWorld);

    // dust
    const du = this.dust.uniforms;
    du.uTime.value = t;
    du.uApex.value.copy(this.beam.apex);
    du.uEnd.value.copy(centre);
    du.uPower.value = 0.35 + c.dust * 0.9;
    du.uBeam.value = c.beam;
    du.uScroll.value = c.scroll;
    // pointer on z=0 plane for repulsion
    this.ray.setFromCamera(this.mouseS, cam);
    this.ray.ray.intersectPlane(this.plane0, du.uMouse.value);

    // signals
    hg.updateMatrixWorld(true);
    const su = this.signals.uniforms;
    su.uTime.value = t;
    this.hand.mesh.localToWorld(su.uStart.value.copy(this.hand.screenTop));
    su.uEnd.value.copy(centre).add(new THREE.Vector3(0, 0, 0.2));
    su.uCtrl.value.copy(su.uStart.value).lerp(su.uEnd.value, 0.45).add(new THREE.Vector3(0, 2.6, 2.4));

    // atmosphere
    const au = this.atmo.uniforms;
    au.uTime.value = t;
    au.uWarm.value = c.warm;
    au.uMouse.value.copy(this.mouseS);

    // post
    this.final.uniforms.uTime.value = t % 100;
    this.final.uniforms.uAberr.value = 0.0005 + Math.min(Math.abs(this.vel) * 0.000035, 0.004);
    this.final.uniforms.uFade.value = clamp(c.intro * 2.2, 0, 1);
    this.bloom.strength = 0.5 + c.spill * 0.15;

    // --- cinematic layer: rack focus, motion blur, glow ---------------------------------------------------------
    const recently = performance.now() - this.lastInput < 3200 ? 1 : 0;
    this.focus = damp(this.focus, recently, 2.2, dt);
    this.pulse = Math.max(0, this.pulse - dt * 1.8);
    const p = this.prevC;
    const dx = (c.hx - p.hx) + (c.wx - p.wx) * 0.4 + (c.cx - p.cx) * 1.5;
    const dy = (c.hy - p.hy) + (c.wy - p.wy) * 0.4;
    const dz = Math.abs(c.hs - p.hs) * 0.5 + Math.abs(c.cz - p.cz) * 0.6;
    p.hx = c.hx; p.hy = c.hy; p.hs = c.hs; p.cx = c.cx; p.cz = c.cz; p.wx = c.wx; p.wy = c.wy;
    const k = dt > 0 ? 1 / dt : 0;
    const mvx = clamp(dx * k * 0.0016, -0.006, 0.006);
    const mvy = clamp(dy * k * 0.0016 + this.vel * 0.00005, -0.007, 0.007) + clamp(dz * k * 0.0004, 0, 0.003);
    this.motion.x = damp(this.motion.x, mvx, 8, dt);
    this.motion.y = damp(this.motion.y, mvy, 8, dt);
    if (this.reduce) { this.motion.set(0, 0); this.pulse = 0; }
    this.final.uniforms.uMotion.value.copy(this.motion);
    const moving = Math.min(1, this.motion.length() * 140);
    this.wall.uniforms.uBlur.value = 0.15 + this.focus * 0.55 + moving * 0.7;
    this.room.uniforms.uBias.value = 0.8 + this.focus * 0.9 + moving * 0.8;
    this.spill.material.uniforms.uI.value += this.pulse * 0.25;
    this.bloom.strength = 0.5 + c.spill * 0.15 + this.pulse * 0.18;

    this.composer.render();
  }
}
