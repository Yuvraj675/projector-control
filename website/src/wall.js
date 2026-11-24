import * as THREE from 'three';
import gsap from 'gsap';
import { homography, toMat3, clamp } from './math.js';

/* The projected picture. Contents are drawn into a 2D canvas (the "Android TV"
   the phone is controlling) and shown through a homography shader so the four
   corners can be moved: that is the keystone chapter. */

const W = 1280;
const H = 720;

const vert = /* glsl */ `
varying vec2 vPos;
void main(){
  vPos = position.xy;
  gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0);
}`;

const frag = /* glsl */ `
precision highp float;
varying vec2 vPos;
uniform sampler2D uTex;
uniform mat3 uH;        // wall xy -> picture uv
uniform float uPower;
uniform float uTime;
uniform float uGrid;
uniform float uFlash;
uniform vec3 uBounce;

float hash(vec2 p){ return fract(sin(dot(p, vec2(41.3,289.1))) * 43758.5453); }

void main(){
  vec3 q = uH * vec3(vPos, 1.0);
  vec2 uv = q.xy / q.z;
  vec2 e = abs(uv - 0.5) - 0.5;
  float outside = max(e.x, e.y);
  float inside = 1.0 - smoothstep(-0.002, 0.004, outside);

  vec3 pic = texture2D(uTex, clamp(uv, 0.0, 1.0)).rgb;
  // projector hotspot + falloff
  float v = 1.0 - 0.38 * pow(length((uv - vec2(0.5, 0.58)) * vec2(0.9, 1.1)), 1.8);
  pic *= v * 1.25;
  pic += vec3(uFlash) * 0.5;
  // calibration grid
  vec2 g = abs(fract(uv * vec2(16.0, 9.0)) - 0.5);
  float gl = (smoothstep(0.485, 0.5, max(g.x, g.y))) * uGrid;
  pic = mix(pic, vec3(1.0, 0.42, 0.2), gl * 0.55);
  // projector pixel grain
  pic *= 0.97 + 0.05 * hash(floor(uv * vec2(${W}.0, ${H}.0)) + floor(uTime * 24.0));

  // soft bounce light on the wall around the picture
  float d = max(outside, 0.0);
  float halo = exp(-d * 7.0) * 0.16;
  vec3 col = pic * inside + uBounce * halo * (1.0 - inside) ;
  col += uBounce * 0.05 * exp(-d * 2.0) * inside;
  float a = max(inside, halo * 1.4);
  gl_FragColor = vec4(col * uPower, a * uPower);
}`;

const APPS = [
  ['Stream', '#ff6a35'], ['Player', '#4f8cff'], ['Music', '#2fd1a0'], ['Files', '#f5b83d'],
  ['Browser', '#a56bff'], ['Kids', '#ff5b9a'], ['Games', '#46c2ff'], ['Photos', '#8ddc57'],
];
const POSTERS = [
  ['#2b3a67', '#e2a97e'], ['#5a2a3c', '#f08a5d'], ['#1f4f4a', '#b8e0a5'],
  ['#3a2f66', '#8e7dff'], ['#6a3a1f', '#ffcf6e'], ['#1d3d5c', '#6fd0ff'],
];
const TITLES = ['Evening Light', 'The Long Way', 'Paper Moon', 'Orbit', 'Last Train', 'Salt & Wind'];

export class Wall {
  constructor(manager) {
    this.canvas = document.createElement('canvas');
    this.canvas.width = W;
    this.canvas.height = H;
    this.ctx = this.canvas.getContext('2d');
    this.tex = new THREE.CanvasTexture(this.canvas);
    this.tex.colorSpace = THREE.SRGBColorSpace;
    this.tex.anisotropy = 4;

    this.film = new Image();
    this.filmReady = false;
    manager?.itemStart('film');
    this.film.onload = () => { this.filmReady = true; manager?.itemEnd('film'); };
    this.film.onerror = () => manager?.itemEnd('film');
    this.film.src = '/assets/film.webp';

    this.uniforms = {
      uTex: { value: this.tex },
      uH: { value: new THREE.Matrix3() },
      uPower: { value: 1 },
      uTime: { value: 0 },
      uGrid: { value: 0 },
      uFlash: { value: 0 },
      uBounce: { value: new THREE.Color(0.55, 0.62, 0.8) },
    };
    this.mesh = new THREE.Mesh(
      new THREE.PlaneGeometry(60, 34),
      new THREE.ShaderMaterial({
        vertexShader: vert, fragmentShader: frag, uniforms: this.uniforms,
        transparent: true, depthWrite: false, blending: THREE.NormalBlending,
      })
    );

    // corners in wall space (TL TR BR BL), base rect + keystone distortion
    this.corners = [[-8, 4.5], [8, 4.5], [8, -4.5], [-8, -4.5]];
    this.cornerLerp = this.corners.map((c) => [...c]);

    this.mode = 'film';
    this.modeT = 0;
    this.sel = 0;
    this.typed = '';
    this.appSel = 0;
    this.launch = 0;
    this.row = 1;
    this.fix = 0;
    this.pressPulse = 0;
    this.lastDraw = 0;
    this.setCorners(this.corners);
  }

  setCorners(c) {
    this.corners = c;
    const H = homography(c, [[0, 1], [1, 1], [1, 0], [0, 0]]);
    toMat3(H, this.uniforms.uH.value);
  }

  setMode(m) {
    if (m === this.mode) return;
    this.mode = m;
    this.modeT = 0;
    this.sel = 0;
    this.typed = '';
    this.appSel = 0;
    this.launch = 0;
    this.row = 1;
    this.uniforms.uFlash.value = 0.7;
  }

  /** a press from the remote: advances the selection in the current mode */
  press() {
    this.pressPulse = 1;
    if (this.mode === 'tv') this.sel = (this.sel + 1) % POSTERS.length;
    if (this.mode === 'apps') this.appSel = (this.appSel + 1) % APPS.length;
  }

  move(dir) {
    this.pressPulse = 1;
    if (this.mode === 'tv') {
      if (dir === 'left') this.sel = (this.sel + POSTERS.length - 1) % POSTERS.length;
      if (dir === 'right') this.sel = (this.sel + 1) % POSTERS.length;
      if (dir === 'up') this.row = 0;
      if (dir === 'down') this.row = 1;
      if (dir === 'ok') this.playing = !this.playing;
    }
    if (this.mode === 'apps') {
      const n = APPS.length;
      if (dir === 'left') this.appSel = (this.appSel + n - 1) % n;
      if (dir === 'right') this.appSel = (this.appSel + 1) % n;
      if (dir === 'up') this.appSel = (this.appSel + n - 4) % n;
      if (dir === 'down') this.appSel = (this.appSel + 4) % n;
    }
  }

  setTyped(s) { this.typed = s; this.pressPulse = 1; }

  openApp(i) {
    this.appSel = i;
    this.pressPulse = 1;
    this.launchName = APPS[i][0];
    this.launchColor = APPS[i][1];
    gsap.killTweensOf(this);
    gsap.fromTo(this, { launch: 0 }, { launch: 1, duration: 0.7, ease: 'expo.out' });
    gsap.to(this, { launch: 0, duration: 0.5, ease: 'power2.in', delay: 2.0 });
  }

  typeNext(word) {
    if (this.typed.length < word.length) this.typed = word.slice(0, this.typed.length + 1);
    else this.typed = '';
    this.pressPulse = 1;
  }

  update(dt, t) {
    this.modeT += dt;
    this.pressPulse = Math.max(0, this.pressPulse - dt * 3);
    this.uniforms.uTime.value = t;
    this.uniforms.uFlash.value *= Math.exp(-dt * 5);
    // 30 fps canvas redraw is plenty for UI content
    if (t - this.lastDraw > 1 / 30) {
      this.lastDraw = t;
      this.draw(t);
      this.tex.needsUpdate = true;
    }
  }

  // ---------------------------------------------------------------- drawing
  draw(t) {
    const c = this.ctx;
    c.save();
    c.clearRect(0, 0, W, H);
    switch (this.mode) {
      case 'tv': this.drawTV(c, t); break;
      case 'search': this.drawSearch(c, t); break;
      case 'apps': this.drawApps(c, t); break;
      case 'pattern': this.drawPattern(c, t); break;
      case 'setup': this.drawSetup(c, t); break;
      default: this.drawFilm(c, t, false);
    }
    c.restore();
  }

  rr(c, x, y, w, h, r) {
    c.beginPath();
    c.roundRect(x, y, w, h, r);
  }

  drawFilm(c, t, overlay) {
    const z = 1.04 + Math.sin(t * 0.2) * 0.02;
    c.fillStyle = '#05070b';
    c.fillRect(0, 0, W, H);
    if (this.filmReady) {
      const iw = this.film.width, ih = this.film.height;
      const s = Math.max(W / iw, H / ih) * z;
      c.drawImage(this.film, (W - iw * s) / 2 + Math.sin(t * 0.15) * 14, (H - ih * s) / 2, iw * s, ih * s);
    }
    if (overlay) {
      c.fillStyle = 'rgba(5,8,14,0.18)';
      c.fillRect(0, 0, W, H);
    }
  }

  chrome(c, title) {
    const g = c.createLinearGradient(0, 0, W, H);
    g.addColorStop(0, '#0d1118');
    g.addColorStop(1, '#171c27');
    c.fillStyle = g;
    c.fillRect(0, 0, W, H);
    c.fillStyle = 'rgba(255,255,255,0.55)';
    c.font = '500 26px "Instrument Sans Variable", sans-serif';
    c.fillText(title, 72, 84);
    c.textAlign = 'right';
    const d = new Date();
    c.fillText(`${d.getHours() % 12 || 12}:${String(d.getMinutes()).padStart(2, '0')}`, W - 72, 84);
    c.textAlign = 'left';
  }

  drawTV(c, t) {
    this.chrome(c, 'Home');
    // hero strip with the film
    c.save();
    this.rr(c, 72, 120, W - 144, 270, 22);
    c.clip();
    if (this.filmReady) {
      const s = Math.max((W - 144) / this.film.width, 270 / this.film.height) * 1.1;
      c.drawImage(this.film, 72 - 20, 120 - 30 + Math.sin(t * 0.4) * 6, this.film.width * s, this.film.height * s);
    }
    const sh = c.createLinearGradient(72, 0, W * 0.7, 0);
    sh.addColorStop(0, 'rgba(8,10,16,0.92)');
    sh.addColorStop(1, 'rgba(8,10,16,0)');
    c.fillStyle = sh;
    c.fillRect(72, 120, W, 270);
    c.restore();
    if (this.row === 0) { this.rr(c, 72, 120, W - 144, 270, 22); c.lineWidth = 5; c.strokeStyle = '#ff6a35'; c.stroke(); }
    c.fillStyle = '#fff';
    c.font = '700 58px "Bricolage Grotesque Variable", sans-serif';
    c.fillText(TITLES[this.sel], 112, 290);
    if (this.playing) { c.fillStyle = '#ff6a35'; c.beginPath(); c.moveTo(W - 190, 220); c.lineTo(W - 190, 290); c.lineTo(W - 130, 255); c.closePath(); c.fill(); }
    c.fillStyle = 'rgba(255,255,255,0.6)';
    c.font = '500 22px "Instrument Sans Variable", sans-serif';
    c.fillText('Continue watching  ·  1h 12m left', 114, 334);

    c.fillStyle = 'rgba(255,255,255,0.5)';
    c.font = '500 22px "Instrument Sans Variable", sans-serif';
    c.fillText('Your picks', 72, 452);
    const tw = 178, th = 200, gap = 22;
    for (let i = 0; i < POSTERS.length; i++) {
      const x = 72 + i * (tw + gap);
      const selected = i === this.sel && this.row === 1;
      const lift = selected ? -10 - this.pressPulse * 6 : 0;
      const g = c.createLinearGradient(x, 480, x + tw, 480 + th);
      g.addColorStop(0, POSTERS[i][0]);
      g.addColorStop(1, POSTERS[i][1]);
      c.save();
      c.translate(x + tw / 2, 480 + th / 2 + lift);
      const sc = selected ? 1.08 : 1;
      c.scale(sc, sc);
      c.translate(-tw / 2, -th / 2);
      this.rr(c, 0, 0, tw, th, 16);
      c.fillStyle = g;
      c.fill();
      if (selected) {
        c.lineWidth = 4;
        c.strokeStyle = '#ff6a35';
        c.stroke();
      }
      c.fillStyle = 'rgba(255,255,255,0.92)';
      c.font = '600 21px "Instrument Sans Variable", sans-serif';
      c.fillText(TITLES[i], 16, th - 18);
      c.restore();
    }
  }

  drawSearch(c, t) {
    this.chrome(c, 'Search');
    // input
    this.rr(c, 72, 120, W - 144, 96, 22);
    c.fillStyle = 'rgba(255,255,255,0.07)';
    c.fill();
    c.fillStyle = '#fff';
    c.font = '500 46px "Instrument Sans Variable", sans-serif';
    const txt = this.typed || '';
    c.fillText(txt, 112, 186);
    const w = c.measureText(txt).width;
    if (Math.floor(t * 2) % 2 === 0) {
      c.fillStyle = '#ff6a35';
      c.fillRect(116 + w, 144, 4, 52);
    }
    if (!txt) {
      c.fillStyle = 'rgba(255,255,255,0.3)';
      c.fillText('Search movies, shows, apps', 112, 186);
    }
    // keyboard
    const rows = ['ABCDEFG', 'HIJKLMN', 'OPQRSTU', 'VWXYZ  '];
    const last = txt.slice(-1).toUpperCase();
    rows.forEach((row, r) => {
      [...row].forEach((ch, i) => {
        if (ch === ' ') return;
        const x = 72 + i * 70, y = 260 + r * 70;
        this.rr(c, x, y, 60, 60, 14);
        const hit = ch === last && this.pressPulse > 0.05;
        c.fillStyle = hit ? '#ff6a35' : 'rgba(255,255,255,0.08)';
        c.fill();
        c.fillStyle = hit ? '#fff' : 'rgba(255,255,255,0.8)';
        c.font = '600 26px "Instrument Sans Variable", sans-serif';
        c.textAlign = 'center';
        c.fillText(ch, x + 30, y + 40);
        c.textAlign = 'left';
      });
    });
    // results
    const n = Math.min(3, Math.floor(txt.length / 2));
    for (let i = 0; i < n; i++) {
      const x = 640 + (i % 3) * 190;
      const y = 270 + 0;
      const g = c.createLinearGradient(x, y, x + 170, y + 250);
      g.addColorStop(0, POSTERS[(i + 3) % 6][0]);
      g.addColorStop(1, POSTERS[(i + 3) % 6][1]);
      this.rr(c, x, y, 170, 250, 16);
      c.fillStyle = g;
      c.fill();
      c.fillStyle = '#fff';
      c.font = '600 20px "Instrument Sans Variable", sans-serif';
      c.fillText(TITLES[(i + 3) % 6], x + 14, y + 226);
    }
  }

  drawApps(c, t) {
    this.chrome(c, 'Apps');
    const cols = 4, tw = 250, th = 180, gap = 36;
    for (let i = 0; i < APPS.length; i++) {
      const col = i % cols, row = Math.floor(i / cols);
      const x = 72 + col * (tw + gap), y = 140 + row * (th + gap);
      const sel = i === this.appSel;
      c.save();
      c.translate(x + tw / 2, y + th / 2);
      const s = sel ? 1.07 : 1;
      c.scale(s, s);
      c.translate(-tw / 2, -th / 2);
      this.rr(c, 0, 0, tw, th, 26);
      c.fillStyle = APPS[i][1];
      c.globalAlpha = sel ? 1 : 0.85;
      c.fill();
      c.globalAlpha = 1;
      if (sel) { c.lineWidth = 5; c.strokeStyle = '#fff'; c.stroke(); }
      c.fillStyle = 'rgba(0,0,0,0.78)';
      c.font = '700 34px "Instrument Sans Variable", sans-serif';
      c.fillText(APPS[i][0], 24, th - 28);
      c.restore();
    }
    c.fillStyle = 'rgba(255,255,255,0.4)';
    c.font = '500 20px "Instrument Sans Variable", sans-serif';
    c.fillText('Send an APK from your phone  ·  Open, stop, uninstall', 72, H - 52);
    if (this.launch > 0.01) {
      const k = this.launch;
      c.globalAlpha = Math.min(1, k * 1.6);
      c.fillStyle = this.launchColor || '#ff6a35';
      const inset = (1 - k) * 260;
      this.rr(c, 72 + inset * 0.6, 140 + inset * 0.4, W - 144 - inset * 1.2, 330 - inset * 0.3 + (H - 470) * k, 26 * (1 - k) + 6);
      c.fill();
      c.fillStyle = 'rgba(0,0,0,0.8)';
      c.font = '800 96px "Bricolage Grotesque Variable", sans-serif';
      c.textAlign = 'center';
      c.fillText(this.launchName || '', W / 2, H / 2 + 20);
      c.textAlign = 'left';
      c.globalAlpha = 1;
    }
  }

  drawPattern(c, t) {
    // the calibration pattern the app puts on the wall, fading into the film once corrected
    c.fillStyle = '#c4c6c9';
    c.fillRect(0, 0, W, H);
    c.fillStyle = '#fff';
    [[110, 90], [W - 110, 90], [110, H - 90], [W - 110, H - 90]].forEach(([x, y]) => { c.beginPath(); c.arc(x, y, 46, 0, 7); c.fill(); });
    c.strokeStyle = 'rgba(255,106,53,0.9)';
    c.lineWidth = 4;
    c.beginPath(); c.moveTo(W / 2 - 60, H / 2); c.lineTo(W / 2 + 60, H / 2); c.moveTo(W / 2, H / 2 - 60); c.lineTo(W / 2, H / 2 + 60); c.stroke();
    if (this.fix > 0.001) {
      c.globalAlpha = this.fix;
      if (this.filmReady) {
        const s = Math.max(W / this.film.width, H / this.film.height);
        c.drawImage(this.film, (W - this.film.width * s) / 2, (H - this.film.height * s) / 2, this.film.width * s, this.film.height * s);
      }
      c.globalAlpha = 1;
    }
  }

  drawSetup(c, t) {
    this.chrome(c, 'Connect');
    c.fillStyle = '#fff';
    c.font = '600 92px "Bricolage Grotesque Variable", sans-serif';
    c.textAlign = 'center';
    c.fillText('Found your projector.', W / 2, 320);
    c.fillStyle = 'rgba(255,255,255,0.55)';
    c.font = '500 28px "Instrument Sans Variable", sans-serif';
    c.fillText('Same Wi-Fi  ·  Debugging allowed  ·  Ready', W / 2, 380);
    const p = (Math.sin(t * 2) + 1) / 2;
    c.beginPath();
    c.arc(W / 2, 500, 20 + p * 8, 0, Math.PI * 2);
    c.fillStyle = `rgba(255,106,53,${0.35 + p * 0.4})`;
    c.fill();
    c.textAlign = 'left';
  }
}

export const keystoneCorners = {
  square: [[-8, 4.5], [8, 4.5], [8, -4.5], [-8, -4.5]],
  // as if the projector sits low and to the left
  skewed: [[-8.6, 5.9], [7.4, 4.1], [8.2, -4.9], [-7.2, -3.4]],
};

export function lerpCorners(a, b, t) {
  return a.map((p, i) => [p[0] + (b[i][0] - p[0]) * t, p[1] + (b[i][1] - p[1]) * t]);
}

export { clamp };
