import * as THREE from 'three';

/* Big cinematic title that sits in the scene behind the hand. Drawn once to a canvas
   in the display face; a moving band of projector light sweeps across it. */
export class Title {
  constructor(text = 'AUTO KEYSTONE') {
    this.text = text;
    this.canvas = document.createElement('canvas');
    this.canvas.width = 2800;
    this.canvas.height = 760;
    this.tex = new THREE.CanvasTexture(this.canvas);
    this.tex.colorSpace = THREE.SRGBColorSpace;
    this.tex.anisotropy = 8;
    this.uniforms = {
      uTex: { value: this.tex },
      uOpacity: { value: 0 },
      uTime: { value: 0 },
      uMouse: { value: new THREE.Vector2() },
      uReveal: { value: 0 },
    };
    this.mesh = new THREE.Mesh(
      new THREE.PlaneGeometry(11.5, 11.5 * 760 / 2800),
      new THREE.ShaderMaterial({
        uniforms: this.uniforms, transparent: true, depthWrite: false,
        vertexShader: 'varying vec2 vUv; void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }',
        fragmentShader: /* glsl */ `
          precision highp float;
          varying vec2 vUv;
          uniform sampler2D uTex; uniform float uOpacity, uTime, uReveal; uniform vec2 uMouse;
          void main(){
            vec2 uv = vUv;
            // vertical reveal wipe
            float wipe = smoothstep(uReveal - 0.2, uReveal, 1.0 - uv.y + 0.2 * (uReveal - 0.0));
            vec4 t = texture2D(uTex, uv);
            float band = smoothstep(0.35, 0.0, abs(uv.x - fract(uTime * 0.045 + uMouse.x * 0.1) * 1.6 + 0.3));
            vec3 col = t.rgb * (0.78 + 0.55 * band);
            float a = t.a * uOpacity * clamp(uReveal * 2.0 - (1.0 - uv.y) * 0.9, 0.0, 1.0);
            gl_FragColor = vec4(col, a);
          }`,
      })
    );
    this.mesh.renderOrder = -1;
  }

  /** one wide line on landscape screens, two stacked lines on portrait */
  layout(portrait) {
    if (this.stack === portrait) return;
    this.stack = portrait;
    this.canvas.width = portrait ? 1500 : 2800;
    this.canvas.height = portrait ? 1000 : 760;
    this.mesh.geometry.dispose();
    const w = portrait ? 4.2 : 11.5;
    this.mesh.geometry = new THREE.PlaneGeometry(w, w * this.canvas.height / this.canvas.width);
    this.draw();
  }

  async draw() {
    try { await document.fonts.load('800 400px "Bricolage Grotesque Variable"'); } catch {}
    const c = this.canvas.getContext('2d');
    const W = this.canvas.width, H = this.canvas.height;
    c.clearRect(0, 0, W, H);
    if (this.stack) {
      c.textAlign = 'center'; c.textBaseline = 'middle';
      let size = 560;
      c.font = `800 ${size}px "Bricolage Grotesque Variable", sans-serif`;
      if ('letterSpacing' in c) c.letterSpacing = `${-size * 0.045}px`;
      size = Math.floor(size * Math.min(1, (W * 0.97) / c.measureText('KEYSTONE').width));
      c.font = `800 ${size}px "Bricolage Grotesque Variable", sans-serif`;
      if ('letterSpacing' in c) c.letterSpacing = `${-size * 0.045}px`;
      const g = c.createLinearGradient(0, 0, 0, H);
      g.addColorStop(0, '#fbf6ee'); g.addColorStop(1, '#9d968e');
      c.fillStyle = g;
      c.fillText('AUTO', W / 2, H * 0.3);
      c.fillText('KEYSTONE', W / 2, H * 0.3 + size * 0.86);
      this.tex.needsUpdate = true;
      return;
    }
    c.textAlign = 'center';
    c.textBaseline = 'middle';
    let size = 520;
    c.font = `800 ${size}px "Bricolage Grotesque Variable", sans-serif`;
    if ('letterSpacing' in c) c.letterSpacing = `${-size * 0.045}px`;
    const m = c.measureText(this.text).width;
    size = Math.floor(size * Math.min(1, (W * 0.97) / m));
    c.font = `800 ${size}px "Bricolage Grotesque Variable", sans-serif`;
    if ('letterSpacing' in c) c.letterSpacing = `${-size * 0.045}px`;
    const g = c.createLinearGradient(0, H * 0.15, 0, H * 0.9);
    g.addColorStop(0, '#fbf6ee');
    g.addColorStop(1, '#9d968e');
    c.fillStyle = g;
    c.fillText(this.text, W / 2, H / 2 + size * 0.04);
    this.tex.needsUpdate = true;
  }
}
