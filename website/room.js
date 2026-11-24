import { fitProjection } from './perspective.js';

// Coordinates on the locked camera plate. Keep the glow and beam aligned to the lens.
const lens = [.735, .737];
const screen = [[.51, .26], [.91, .18], [.90, .59], [.52, .61]];
const beamTarget = [.685, .42];

const vertexSource = `
  attribute vec2 aVertex;
  varying vec2 vPosition;
  void main() {
    vPosition = vec2(aVertex.x * .5 + .5, .5 - aVertex.y * .5);
    gl_Position = vec4(aVertex, 0., 1.);
  }
`;

const fragmentSource = `
  precision mediump float;
  varying vec2 vPosition;
  uniform vec2 uLens;
  uniform vec2 uTarget;
  uniform float uAspect;

  void main() {
    vec2 aspect = vec2(uAspect, 1.);
    vec2 relative = (vPosition - uLens) * aspect;
    vec2 axis = (uTarget - uLens) * aspect;
    vec2 across = normalize(vec2(-axis.y, axis.x));
    float depth = dot(relative, axis) / dot(axis, axis);
    float distanceToAxis = dot(relative, across);
    float width = mix(.008, .21, clamp(depth, 0., 1.));
    float beamFade = smoothstep(0., .09, depth) * (1. - smoothstep(.68, 1.11, depth));
    float normalizedDistance = distanceToAxis / width;
    float cone = exp(-3.7 * normalizedDistance * normalizedDistance) * beamFade;
    float spot = length(relative);
    float halo = exp(-spot * 34.) * .29 + exp(-spot * 150.) * .63;
    float light = min(cone * .17 + halo, .94);
    gl_FragColor = vec4(vec3(.95, .90, .78) * light, light);
  }
`;

function compile(gl, kind, source) {
  const shader = gl.createShader(kind);
  gl.shaderSource(shader, source);
  gl.compileShader(shader);
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    gl.deleteShader(shader);
    return null;
  }
  return shader;
}

const particleVertex = `
  attribute vec3 aParticle;
  varying float vLight;

  void main() {
    vLight = aParticle.z;
    gl_PointSize = 4.;
    gl_Position = vec4(aParticle.xy, 0., 1.);
  }
`;

const particleFragment = `
  precision mediump float;
  varying float vLight;

  void main() {
    float falloff = 1. - smoothstep(.08, .5, length(gl_PointCoord - .5));
    float light = vLight * falloff;
    gl_FragColor = vec4(vec3(1., .94, .79) * light, light);
  }
`;

function link(gl, vertexCode, fragmentCode) {
  const vertex = compile(gl, gl.VERTEX_SHADER, vertexCode);
  const fragment = compile(gl, gl.FRAGMENT_SHADER, fragmentCode);
  if (!vertex || !fragment) return null;
  const program = gl.createProgram();
  gl.attachShader(program, vertex);
  gl.attachShader(program, fragment);
  gl.linkProgram(program);
  gl.deleteShader(vertex);
  gl.deleteShader(fragment);
  return gl.getProgramParameter(program, gl.LINK_STATUS) ? program : null;
}

function setupBeam(room, canvas, motion) {
  const gl = canvas.getContext('webgl', { alpha: true, antialias: false, depth: false, premultipliedAlpha: true });
  if (!gl) return;
  const beam = link(gl, vertexSource, fragmentSource);
  const particles = link(gl, particleVertex, particleFragment);
  if (!beam || !particles) return;

  const beamLocation = gl.getAttribLocation(beam, 'aVertex');
  const particleLocation = gl.getAttribLocation(particles, 'aParticle');
  const beamBuffer = gl.createBuffer();
  const particleBuffer = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, beamBuffer);
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
  gl.useProgram(beam);
  gl.uniform2fv(gl.getUniformLocation(beam, 'uLens'), lens);
  gl.uniform2fv(gl.getUniformLocation(beam, 'uTarget'), beamTarget);
  const ratio = gl.getUniformLocation(beam, 'uAspect');
  const particleCount = 28;
  const particlePoints = new Float32Array(particleCount * 3);
  const random = (seed) => {
    const value = Math.sin(seed) * 4217.289;
    return value - Math.floor(value);
  };
  const points = Array.from({ length: particleCount }, (_, i) => ({
    depth: .16 + Math.abs(random(i * 17.3)) * .57,
    drift: random(i * 7.3) * .17 - .08,
    phase: i * 2.13,
  }));
  let visible = false;
  let frame = 0;
  let lastDraw = -Infinity;

  function resize() {
    const { clientWidth, clientHeight } = room;
    if (!clientWidth || !clientHeight) return;
    const maxWidth = matchMedia('(pointer: coarse)').matches ? 525 : 850;
    canvas.width = Math.min(clientWidth, maxWidth);
    canvas.height = Math.round(canvas.width * clientHeight / clientWidth);
    gl.viewport(0, 0, canvas.width, canvas.height);
    gl.useProgram(beam);
    gl.uniform1f(ratio, clientWidth / clientHeight);
  }

  function tick(now) {
    if (now - lastDraw >= 40) {
      lastDraw = now;
      gl.disable(gl.BLEND);
      gl.useProgram(beam);
      gl.bindBuffer(gl.ARRAY_BUFFER, beamBuffer);
      gl.enableVertexAttribArray(beamLocation);
      gl.vertexAttribPointer(beamLocation, 2, gl.FLOAT, false, 0, 0);
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
      gl.disableVertexAttribArray(beamLocation);

      const time = now / 1000;
      points.forEach(({ depth, drift, phase }, i) => {
        const x = lens[0] + (beamTarget[0] - lens[0]) * depth + drift * depth + Math.sin(time * .36 + phase) * .01;
        const y = lens[1] + (beamTarget[1] - lens[1]) * depth + Math.cos(time * .22 + phase) * .012;
        particlePoints.set([x * 2 - 1, 1 - y * 2, .18 + Math.sin(time * .7 + phase) * .1], i * 3);
      });
      gl.useProgram(particles);
      gl.enable(gl.BLEND);
      gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
      gl.bindBuffer(gl.ARRAY_BUFFER, particleBuffer);
      gl.bufferData(gl.ARRAY_BUFFER, particlePoints, gl.DYNAMIC_DRAW);
      gl.enableVertexAttribArray(particleLocation);
      gl.vertexAttribPointer(particleLocation, 3, gl.FLOAT, false, 0, 0);
      gl.drawArrays(gl.POINTS, 0, particleCount);
      gl.disableVertexAttribArray(particleLocation);
    }
    frame = requestAnimationFrame(tick);
  }

  function update() {
    const active = visible && !document.hidden && !motion.matches && !gl.isContextLost();
    canvas.hidden = !active;
    if (active && !frame) frame = requestAnimationFrame(tick);
    if (!active) {
      cancelAnimationFrame(frame);
      frame = 0;
    }
  }

  new ResizeObserver(resize).observe(room);
  new IntersectionObserver(([entry]) => {
    visible = entry.intersectionRatio >= .015;
    update();
  }, { threshold: .015 }).observe(canvas.closest('.shot'));
  motion.addEventListener('change', update);
  document.addEventListener('visibilitychange', update);
  canvas.addEventListener('webglcontextlost', update);
  resize();
}

export function setupRoom(room, motion) {
  const projection = room.querySelector('.room-projection');
  const plate = room.querySelector('.room-plate');
  function placeProjection() {
    fitProjection(room, projection, screen);
  }

  room.style.setProperty('--lens-point', `${lens[0] * 100}% ${lens[1] * 100}%`);
  new ResizeObserver(placeProjection).observe(room);
  plate.addEventListener('load', placeProjection);
  placeProjection();
  function startBeam() {
    if (motion.matches) return;
    motion.removeEventListener('change', startBeam);
    setupBeam(room, room.querySelector('.room-dust'), motion);
  }

  motion.addEventListener('change', startBeam);
  startBeam();
}
