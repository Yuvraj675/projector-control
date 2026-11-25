import * as THREE from 'three';

const rand = (a, b) => a + Math.random() * (b - a);

/* ------------------------------------------------------------------ beam */
export class Beam {
  constructor() {
    this.apex = new THREE.Vector3(-9, 9, 10);
    const geo = new THREE.BufferGeometry();
    // 4 side faces: apex + two corners. attributes: aT (0 apex..1 wall), aS (0..1 across)
    const pos = new Float32Array(4 * 3 * 3);
    const aT = new Float32Array(4 * 3);
    const aS = new Float32Array(4 * 3);
    for (let f = 0; f < 4; f++) {
      aT.set([0, 1, 1], f * 3);
      aS.set([0.5, 0, 1], f * 3);
    }
    geo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
    geo.setAttribute('aT', new THREE.BufferAttribute(aT, 1));
    geo.setAttribute('aS', new THREE.BufferAttribute(aS, 1));
    this.uniforms = { uTime: { value: 0 }, uPower: { value: 1 }, uColor: { value: new THREE.Color(0.75, 0.82, 1.0) } };
    this.mesh = new THREE.Mesh(
      geo,
      new THREE.ShaderMaterial({
        uniforms: this.uniforms,
        transparent: true,
        depthWrite: false,
        side: THREE.DoubleSide,
        blending: THREE.AdditiveBlending,
        vertexShader: /* glsl */ `
          attribute float aT; attribute float aS;
          varying float vT; varying float vS; varying vec3 vW;
          void main(){
            vT = aT; vS = aS;
            vec4 w = modelMatrix * vec4(position,1.0);
            vW = w.xyz;
            gl_Position = projectionMatrix * viewMatrix * w;
          }`,
        fragmentShader: /* glsl */ `
          precision highp float;
          varying float vT; varying float vS; varying vec3 vW;
          uniform float uTime; uniform float uPower; uniform vec3 uColor;
          float h(float n){ return fract(sin(n)*43758.5453); }
          float noise(float x){ float i=floor(x), f=fract(x); return mix(h(i),h(i+1.0),f*f*(3.0-2.0*f)); }
          void main(){
            float edge = smoothstep(0.0, 0.35, vS) * smoothstep(1.0, 0.65, vS);
            float streak = 0.55 + 0.45 * noise(vS * 38.0 + vT * 6.0 - uTime * 0.6);
            streak *= 0.7 + 0.3 * noise(vS * 9.0 - uTime * 0.25 + vT * 2.0);
            float along = smoothstep(0.02, 0.5, vT) * (0.55 + 0.45 * vT);
            float a = edge * streak * along * 0.2 * uPower;
            gl_FragColor = vec4(uColor * a, a);
          }`,
      })
    );
    this.mesh.frustumCulled = false;
    this.mesh.renderOrder = 1;
  }

  /** corners in world space, TL TR BR BL */
  update(worldCorners, t) {
    const p = this.mesh.geometry.attributes.position.array;
    for (let f = 0; f < 4; f++) {
      const a = worldCorners[f], b = worldCorners[(f + 1) % 4];
      p.set([this.apex.x, this.apex.y, this.apex.z], f * 9);
      p.set([a.x, a.y, a.z], f * 9 + 3);
      p.set([b.x, b.y, b.z], f * 9 + 6);
    }
    this.mesh.geometry.attributes.position.needsUpdate = true;
    this.uniforms.uTime.value = t;
  }
}

/* ------------------------------------------------------------------ dust */
export class Dust {
  constructor(count = 7000) {
    this.count = count;
    const pos = new Float32Array(count * 3);
    const seed = new Float32Array(count * 4);
    for (let i = 0; i < count; i++) {
      pos.set([rand(-16, 16), rand(-9, 11), rand(-14, 9)], i * 3);
      seed.set([Math.random(), Math.random(), Math.random(), Math.random()], i * 4);
    }
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
    geo.setAttribute('aSeed', new THREE.BufferAttribute(seed, 4));
    this.uniforms = {
      uTime: { value: 0 },
      uPx: { value: 1 },
      uApex: { value: new THREE.Vector3() },
      uEnd: { value: new THREE.Vector3() },
      uMouse: { value: new THREE.Vector3(0, 0, 0) },
      uPower: { value: 1 },
      uBeam: { value: 1 },
      uScroll: { value: 0 },
    };
    this.points = new THREE.Points(
      geo,
      new THREE.ShaderMaterial({
        uniforms: this.uniforms,
        transparent: true,
        depthWrite: false,
        blending: THREE.AdditiveBlending,
        vertexShader: /* glsl */ `
          attribute vec4 aSeed;
          uniform float uTime, uPx, uBeam, uScroll;
          uniform vec3 uApex, uEnd, uMouse;
          varying float vA; varying vec3 vC;
          void main(){
            vec3 p = position;
            float t = uTime;
            // lazy drift
            p.x += sin(t * 0.13 * (0.5 + aSeed.x) + aSeed.y * 40.0) * 0.9;
            p.y += cos(t * 0.11 * (0.5 + aSeed.z) + aSeed.w * 40.0) * 0.8 + mod(t * 0.05 * (0.3 + aSeed.x), 1.0) * 0.0;
            p.z += sin(t * 0.09 + aSeed.y * 30.0) * 0.7;
            p.y += uScroll * (aSeed.z - 0.5) * 2.0;
            // mouse repel
            vec2 dm = p.xy - uMouse.xy;
            float dl = length(dm);
            p.xy += normalize(dm + 1e-4) * smoothstep(3.2, 0.0, dl) * 1.1;
            // inside the beam?
            vec3 ab = uEnd - uApex;
            float u = clamp(dot(p - uApex, ab) / dot(ab, ab), 0.0, 1.0);
            vec3 onAxis = uApex + ab * u;
            float rad = 0.4 + u * 8.0;
            float inBeam = smoothstep(rad, rad * 0.35, distance(p, onAxis)) * smoothstep(0.0, 0.2, u);
            vec4 mv = viewMatrix * vec4(p, 1.0);
            gl_Position = projectionMatrix * mv;
            float tw = 0.6 + 0.4 * sin(t * (1.0 + aSeed.x * 2.0) + aSeed.y * 30.0);
            float size = mix(1.1, 2.6, aSeed.z) * (1.0 + inBeam * 1.8);
            gl_PointSize = size * uPx * (7.0 / -mv.z);
            vA = (0.12 + inBeam * uBeam * 1.3) * tw;
            vC = mix(vec3(0.55, 0.62, 0.8), vec3(1.0, 0.72, 0.5), aSeed.w * 0.7 + inBeam * 0.2);
          }`,
        fragmentShader: /* glsl */ `
          precision highp float;
          varying float vA; varying vec3 vC;
          uniform float uPower;
          void main(){
            vec2 d = gl_PointCoord - 0.5;
            float r = length(d) * 2.0;
            float a = smoothstep(1.0, 0.0, r);
            a *= a;
            gl_FragColor = vec4(vC * a * vA * uPower, a * vA * uPower);
          }`,
      })
    );
    this.points.frustumCulled = false;
    this.points.renderOrder = 2;
  }
}

/* --------------------------------------------------------------- signals */
export class Signals {
  constructor(count = 260) {
    this.count = count;
    this.cursor = 0;
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.BufferAttribute(new Float32Array(count * 3), 3));
    this.birth = new Float32Array(count).fill(-100);
    this.seed = new Float32Array(count * 3);
    for (let i = 0; i < count; i++) this.seed.set([rand(-1, 1), rand(-1, 1), Math.random()], i * 3);
    geo.setAttribute('aBirth', new THREE.BufferAttribute(this.birth, 1));
    geo.setAttribute('aSeed', new THREE.BufferAttribute(this.seed, 3));
    this.uniforms = {
      uTime: { value: 0 },
      uPx: { value: 1 },
      uStart: { value: new THREE.Vector3() },
      uCtrl: { value: new THREE.Vector3() },
      uEnd: { value: new THREE.Vector3() },
    };
    this.points = new THREE.Points(
      geo,
      new THREE.ShaderMaterial({
        uniforms: this.uniforms,
        transparent: true,
        depthWrite: false,
        blending: THREE.AdditiveBlending,
        vertexShader: /* glsl */ `
          attribute float aBirth; attribute vec3 aSeed;
          uniform float uTime, uPx; uniform vec3 uStart, uCtrl, uEnd;
          varying float vA; varying float vT;
          void main(){
            float life = 1.15 + aSeed.z * 0.5;
            float t = (uTime - aBirth) / life;
            float live = step(0.0, t) * step(t, 1.0);
            float e = 1.0 - pow(1.0 - clamp(t, 0.0, 1.0), 2.2);
            vec3 p = mix(mix(uStart, uCtrl, e), mix(uCtrl, uEnd, e), e);
            float wob = sin(e * 3.14159) * (0.5 + aSeed.z);
            p += vec3(aSeed.x, aSeed.y, aSeed.x * aSeed.y) * wob * 0.55;
            vec4 mv = viewMatrix * vec4(p, 1.0);
            gl_Position = projectionMatrix * mv;
            gl_PointSize = live * (3.5 + aSeed.z * 5.0) * (1.0 - e * 0.5) * uPx * (7.0 / -mv.z);
            vA = live * sin(clamp(t, 0.0, 1.0) * 3.14159);
            vT = e;
          }`,
        fragmentShader: /* glsl */ `
          precision highp float;
          varying float vA; varying float vT;
          void main(){
            vec2 d = gl_PointCoord - 0.5;
            float r = length(d) * 2.0;
            float a = smoothstep(1.0, 0.0, r); a *= a;
            vec3 c = mix(vec3(1.0, 0.42, 0.18), vec3(1.0, 0.9, 0.78), vT);
            gl_FragColor = vec4(c * a * vA * 1.6, a * vA);
          }`,
      })
    );
    this.points.frustumCulled = false;
    this.points.renderOrder = 4;
  }

  burst(time, n = 28) {
    const attr = this.points.geometry.attributes.aBirth;
    for (let i = 0; i < n; i++) {
      this.birth[this.cursor] = time + Math.random() * 0.22;
      this.cursor = (this.cursor + 1) % this.count;
    }
    attr.needsUpdate = true;
  }
}

/* ------------------------------------------------------------ atmosphere */
export class Atmosphere {
  constructor() {
    this.uniforms = {
      uTime: { value: 0 },
      uAspect: { value: 1 },
      uScroll: { value: 0 },
      uMouse: { value: new THREE.Vector2() },
      uWarm: { value: 0.5 },
    };
    this.mesh = new THREE.Mesh(
      new THREE.PlaneGeometry(2, 2),
      new THREE.ShaderMaterial({
        uniforms: this.uniforms,
        depthTest: false,
        depthWrite: false,
        vertexShader: `varying vec2 vUv; void main(){ vUv = uv; gl_Position = vec4(position.xy, 0.0, 1.0); }`,
        fragmentShader: /* glsl */ `
          precision highp float;
          varying vec2 vUv;
          uniform float uTime, uAspect, uScroll, uWarm;
          uniform vec2 uMouse;
          float hash(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453); }
          float vn(vec2 p){
            vec2 i = floor(p), f = fract(p); f = f*f*(3.0-2.0*f);
            return mix(mix(hash(i), hash(i+vec2(1,0)), f.x), mix(hash(i+vec2(0,1)), hash(i+vec2(1,1)), f.x), f.y);
          }
          float fbm(vec2 p){ float s=0.0, a=0.5; for(int i=0;i<5;i++){ s+=a*vn(p); p=p*2.03+7.1; a*=0.5; } return s; }
          void main(){
            vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0);
            float t = uTime * 0.03;
            vec2 q = p * 1.6 + vec2(t, -t * 0.7) + uMouse * 0.05;
            float n = fbm(q + fbm(q * 1.3 + t));
            float cloud = smoothstep(0.35, 0.9, n);
            vec3 cool = vec3(0.05, 0.09, 0.17);
            vec3 warm = vec3(0.30, 0.10, 0.04);
            vec3 col = mix(cool, warm, uWarm) * cloud * 0.42;
            // deep base + vignette
            float vg = smoothstep(1.15, 0.2, length(p * vec2(0.8, 1.0)));
            col *= vg;
            col += vec3(0.008, 0.01, 0.016);
            gl_FragColor = vec4(col, 1.0);
          }`,
      })
    );
    this.mesh.renderOrder = -10;
    this.mesh.frustumCulled = false;
  }
}
