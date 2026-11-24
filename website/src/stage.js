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
  },
  vertexShader: `varying vec2 vUv; void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }`,
  fragmentShader: /* glsl */ `
    precision highp float;
    varying vec2 vUv;
    uniform sampler2D tDiffuse;
    uniform float uTime, uAberr, uGrain, uFade;
    float hash(vec2 p){ return fract(sin(dot(p, vec2(12.9898,78.233)) + uTime) * 43758.5453); }
    void main(){
      vec2 c = vUv - 0.5;
      float d = dot(c, c);
      vec2 off = c * (uAberr + d * 0.0025);
      float r = texture2D(tDiffuse, vUv + off).r;
      float g = texture2D(tDiffuse, vUv).g;
      float b = texture2D(tDiffuse, vUv - off).b;
      vec3 col = vec3(r, g, b);
      col *= smoothstep(0.95, 0.25, length(c * vec2(1.0, 1.15)));
      col += (hash(vUv * 1000.0) - 0.5) * uGrain;
      gl_FragColor = vec4(col * uFade, 1.0);
    }`,
};

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

    this.wallGroup = new THREE.Group();
    this.wall = new Wall(manager);
    this.wallGroup.add(this.wall.mesh);
    this.scene.add(this.wallGroup);

    this.beam = new Beam();
    this.beam.apex.set(-3.0, 0.5, 2.3);
    this.scene.add(this.beam.mesh);

    this.title = new Title('AUTO KEYSTONE');
    this.title.mesh.position.set(0, 1.35, -3.2);
    this.scene.add(this.title.mesh);
    this.title.draw();

    // the projector that casts the beam: a soft, slightly out-of-focus foreground object
    const pj = new THREE.TextureLoader(manager).load('/assets/projector.png');
    pj.colorSpace = THREE.SRGBColorSpace;
    this.projector = new THREE.Mesh(
      new THREE.PlaneGeometry(3.6, 3.6 * 641 / 1100),
      new THREE.MeshBasicMaterial({ map: pj, transparent: true, depthWrite: false, opacity: 0, side: THREE.DoubleSide })
    );
    this.projector.scale.set(-1.5, 1.5, 1); // lens faces right, towards the wall
    this.projector.position.set(-3.5, 1.5, 2.2);
    this.projector.rotation.z = 0.05;
    this.scene.add(this.projector);
    this.dust = new Dust(window.innerWidth < 800 ? 3500 : 7000);
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
      title: 0, proj: 0, hx: 0, hy: -7, hz: 0, hrx: 0.0, hry: 0, hrz: 0, hs: 5.2,
      cx: 0, cy: 0, cz: 9, lx: 0, ly: 0,
      wx: 0, wy: 3, wz: -15, ws: 1, key: 0, wpow: 0,
      beam: 0, dust: 0.0, warm: 0.2, spill: 0, fade: 1, scroll: 0, intro: 0, grid: 0, flash: 0, fix: 0,
    };
    this.cur = { ...this.state };

    this.mouse = new THREE.Vector2();
    this.mouseS = new THREE.Vector2();
    this.vel = 0;
    this.clock = new THREE.Clock();
    this.time = 0;
    this.running = false;
    this.cornerKey = keystoneCorners.square;
    this.world = [0, 1, 2, 3].map(() => new THREE.Vector3());
    this.tmp = new THREE.Vector3();
    this.ray = new THREE.Raycaster();
    this.plane0 = new THREE.Plane(new THREE.Vector3(0, 0, 1), 0);
    this.pulseAt = 0;
    this.lastInput = 0;

    window.addEventListener('pointermove', (e) => {
      this.mouse.set((e.clientX / window.innerWidth) * 2 - 1, -((e.clientY / window.innerHeight) * 2 - 1));
    });
    this.resize();
    window.addEventListener('resize', () => this.resize());
    document.addEventListener('visibilitychange', () => (this.clock.getDelta(), 0));
  }

  get portrait() { return this.aspect < 0.85; }

  resize() {
    const w = window.innerWidth, h = window.innerHeight;
    this.aspect = w / h;
    const dpr = Math.min(window.devicePixelRatio || 1, w < 800 ? 1.75 : 2);
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

  /** thumb tip on the glass (screen uv) for each frame, used to place the touch ripple */
  tipOf(id) {
    return {
      'tap': [0.67, 0.385], 'dpad-hover': [0.66, 0.41], 'dpad-up': [0.69, 0.65], 'dpad-left': [0.23, 0.4], 'dpad-right': [0.88, 0.39],
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
  }

  /** a tap on the phone: ripple on glass + a stream of signals to the wall */
  tap(x, y, n = 26) {
    this.hand.tap(x, y);
    this.signals.burst(this.time, n);
    this.wall.press();
    this.state.flash = 1;
  }

  frame() {
    const dt = Math.min(this.clock.getDelta(), 0.05);
    this.time += dt;
    const t = this.time;
    const s = this.state, c = this.cur;

    for (const k in s) {
      if (typeof s[k] === 'number') c[k] = damp(c[k] ?? s[k], s[k], k === 'hy' || k === 'hs' ? 4.2 : 3.4, dt);
    }

    c.key = s.key; c.fix = s.fix; c.title = damp(c.title, s.title, 5, dt); c.proj = damp(c.proj, s.proj, 4, dt);

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
    this.spill.material.uniforms.uI.value = c.spill * (0.8 + 0.2 * Math.sin(t * 2.3));

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
    this.title.mesh.position.x = -this.mouseS.x * 0.45;
    this.title.mesh.position.y = (this.titleY ?? 1.35) + this.mouseS.y * 0.2;
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

    this.composer.render();
  }
}
