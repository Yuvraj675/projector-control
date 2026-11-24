import * as THREE from 'three';
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js';
import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';

const V = (x, y, z) => new THREE.Vector3(x, y, z);
const asset = (file) => `${import.meta.env.BASE_URL}assets/${file}`;

function surface({ color, horizontal = false, repeat = [1, 1], variation = .08 }) {
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = 128;
  const ctx = canvas.getContext('2d');
  const data = ctx.createImageData(128, 128);
  const channels = color.match(/../g).map((part) => parseInt(part, 16));
  let seed = 4549;
  for (let i = 0; i < 16384; i++) {
    seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
    const x = i % 128, y = Math.floor(i / 128);
    const grain = horizontal ? Math.sin(y * .55 + Math.sin(x * .095)) * .45 : ((x + y) % 3 - 1) * .23;
    const shade = 1 + ((seed / 4294967296 - .5) + grain) * variation;
    channels.forEach((channel, channelIndex) => { data.data[i * 4 + channelIndex] = channel * shade; });
    data.data[i * 4 + 3] = 255;
  }
  ctx.putImageData(data, 0, 0);
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.wrapS = texture.wrapT = THREE.RepeatWrapping;
  texture.repeat.set(...repeat);
  return new THREE.MeshStandardMaterial({ map: texture, roughness: .86 });
}

function furnish(scene) {
  const material = (color, options = {}) => new THREE.MeshStandardMaterial({ color, roughness: .72, ...options });
  const plaster = surface({ color: '566570', repeat: [3, 3], variation: .09 });
  const floor = surface({ color: '4a3227', horizontal: true, repeat: [1, 10], variation: .26 });
  const walnut = surface({ color: '66422c', horizontal: true, repeat: [1, 2], variation: .12 });
  const cloth = surface({ color: '364858', repeat: [3, 3], variation: .32 });
  const linen = surface({ color: '9c795f', repeat: [2, 2], variation: .24 });
  const dark = material(0x19232b);
  const trim = material(0x252f34);
  const trimLight = material(0x685144);
  const cream = material(0xcbbca5);

  function box(parent, size, pos, mat, radius = 0) {
    const geometry = radius ? new RoundedBoxGeometry(...size, 2, radius) : new THREE.BoxGeometry(...size);
    const mesh = new THREE.Mesh(geometry, mat);
    mesh.position.set(...pos);
    parent.add(mesh);
    mesh.castShadow = true;
    mesh.receiveShadow = true;
    return mesh;
  }

  function cylinder(parent, top, bottom, height, pos, mat) {
    const mesh = new THREE.Mesh(new THREE.CylinderGeometry(top, bottom, height, 36), mat);
    mesh.position.set(...pos);
    mesh.castShadow = mesh.receiveShadow = true;
    parent.add(mesh);
    return mesh;
  }

  box(scene, [12, .2, 14.4], [0, -.1, .6], floor);
  box(scene, [12, 5.3, .2], [0, 2.65, -4.3], plaster);
  box(scene, [12, .12, .09], [0, .11, -4.16], trim);
  box(scene, [.2, 5.3, 13], [5.1, 2.65, .6], plaster);
  box(scene, [.2, 5.3, 13], [-5.9, 2.65, .6], plaster);
  box(scene, [12, .14, 14.4], [0, 5.31, .6], trim);
  box(scene, [12, 5.3, .2], [0, 2.65, 7.65], plaster);
  box(scene, [12, .07, .10], [0, 5.21, -4.13], trim);

  for (let i = 0; i < 23; i++) box(scene, [.013, .004, 12], [-5.6 + i * .49, .003, .5], dark);

  const windowFrame = new THREE.Group();
  windowFrame.position.set(-3.27, 2.44, -4.17);
  scene.add(windowFrame);
  const glass = new THREE.MeshBasicMaterial({ color: 0x355474 });
  box(windowFrame, [2.35, 2.74, .025], [0, 0, 0], glass);
  const skyline = new THREE.MeshBasicMaterial({ color: 0x1a293f });
  const litWindow = new THREE.MeshBasicMaterial({ color: 0xd59960 });
  for (let i = 0; i < 10; i++) {
    const x = -1.06 + i * .232;
    const height = .32 + .11 * ((i * 7) % 9);
    box(windowFrame, [.19, height, .015], [x, -1.33 + height / 2, .025], skyline);
    for (let j = 0; j < 3; j++) {
      if (.13 + j * .17 < height && (i + j) % 3 !== 0) {
        box(windowFrame, [.043, .051, .009], [x + .02, -1.22 + j * .17, .038], litWindow);
      }
    }
  }
  for (const x of [-1.19, 0, 1.19]) box(windowFrame, [.052, 2.84, .07], [x, 0, .075], trim);
  for (const y of [-1.39, .4, 1.39]) box(windowFrame, [2.43, .052, .07], [0, y, .075], trim);
  box(windowFrame, [2.57, .06, .2], [0, -1.43, .14], walnut);

  const rug = surface({ color: '513543', repeat: [4, 4], variation: .35 });
  box(scene, [5.5, .018, 4.65], [-.34, .014, .55], rug, .006);
  for (const x of [-2.98, 2.30]) box(scene, [.025, .003, 4.46], [x, .024, .55], linen);
  for (const z of [-1.68, 2.78]) box(scene, [5.29, .003, .025], [-.34, .024, z], linen);

  const sofa = new THREE.Group();
  sofa.position.set(-2.0, 0, 2.42);
  sofa.rotation.y = -.10;
  scene.add(sofa);
  box(sofa, [3.05, .31, 1.1], [0, .32, 0], cloth, .1);
  box(sofa, [3.1, .77, .24], [0, .75, .48], cloth, .1);
  for (const x of [-1.36, 1.36]) box(sofa, [.34, .51, 1.15], [x, .61, 0], cloth, .12);
  for (const x of [-.78, 0, .78]) {
    box(sofa, [.76, .21, .85], [x, .56, -.105], cloth, .08);
    box(sofa, [.76, .48, .22], [x, .84, .25], cloth, .08).rotation.x = -.16;
  }
  const cushion = box(sofa, [.53, .55, .18], [-.99, .91, .09], linen, .08);
  cushion.rotation.set(-.22, .22, -.30);
  const remote = box(sofa, [.105, .022, .29], [.79, .683, -.23], dark, .011);
  remote.rotation.y = .35;
  for (const x of [-.024, .024]) {
    for (const z of [-.043, .006, .055]) box(remote, [.025, .008, .024], [x, .014, z], cream, .003);
  }

  const table = new THREE.Group();
  table.position.set(2.25, 0, .83);
  scene.add(table);
  cylinder(table, .84, .84, .085, [0, .69, 0], walnut);
  for (const angle of [0, 2.094, 4.189]) {
    cylinder(table, .041, .028, .67, [Math.cos(angle) * .57, .335, Math.sin(angle) * .57], walnut);
  }
  const projector = new THREE.Group();
  projector.position.set(-.045, .86, -.08);
  projector.rotation.y = Math.PI - .15;
  table.add(projector);
  box(projector, [.83, .25, .53], [0, 0, 0], cream, .046);
  box(projector, [.76, .20, .018], [0, 0, .264], trim, .008);
  const lensMount = cylinder(projector, .094, .094, .053, [.232, .004, .289], dark);
  lensMount.rotation.x = Math.PI / 2;
  const lensMat = new THREE.MeshBasicMaterial({ color: 0xffe0a4 });
  const lens = cylinder(projector, .056, .056, .009, [.232, .004, .32], lensMat);
  lens.rotation.x = Math.PI / 2;
  for (let i = 0; i < 8; i++) box(projector, [.102, .008, .014], [-.21, -.066 + i * .018, .277], dark);
  const mug = cylinder(table, .087, .072, .155, [.4, .814, .43], material(0x8eaaaf));
  const coffee = cylinder(table, .071, .071, .006, [.4, .886, .43], material(0x37261d));
  const handle = new THREE.Mesh(new THREE.TorusGeometry(.048, .01, 8, 20), mug.material);
  handle.position.set(.492, .817, .43);
  table.add(handle);
  coffee.castShadow = false;

  const lampX = -4.54;
  cylinder(scene, .19, .23, .07, [lampX, .04, -1.4], dark);
  cylinder(scene, .025, .027, 1.95, [lampX, 1, -1.4], dark);
  const lampShade = material(0xb78954, { emissive: 0xd57929, emissiveIntensity: .34, side: THREE.DoubleSide });
  const shade = new THREE.Mesh(new THREE.CylinderGeometry(.18, .36, .31, 36, 1, true), lampShade);
  shade.position.set(lampX, 1.99, -1.4);
  scene.add(shade);
  const practical = new THREE.PointLight(0xffab59, 20, 5, 2);
  practical.position.set(lampX, 1.86, -1.4);
  scene.add(practical);

  box(scene, [.19, 3.65, .12], [4.66, 1.83, -4.07], trimLight);
  box(scene, [1.02, 3.65, .055], [4.12, 1.82, -4.09], walnut);
  const knob = new THREE.Mesh(new THREE.SphereGeometry(.046, 12, 8), cream);
  knob.position.set(3.75, 1.04, -4.04);
  scene.add(knob);
  const consoleX = 3.83;
  box(scene, [2.15, .49, .51], [consoleX, .37, 5.76], walnut, .02);
  box(scene, [2.18, .072, .58], [consoleX, .65, 5.76], walnut, .018);
  for (const x of [consoleX - .9, consoleX + .9]) {
    for (const z of [5.58, 5.91]) box(scene, [.053, .28, .07], [x, .14, z], trim);
  }
  const covers = [0x8f5b53, 0x476477, 0x746b52, 0x9e865f];
  for (let i = 0; i < covers.length; i++) {
    box(scene, [.09, .27 + i * .025, .21], [3.15 + .11 * i, .828 + i * .013, 5.72], material(covers[i]), .007);
  }
  cylinder(scene, .025, .025, .40, [4.31, .885, 5.72], trim);
  const deskShade = new THREE.Mesh(new THREE.CylinderGeometry(.105, .188, .18, 28, 1, true), lampShade);
  deskShade.position.set(4.31, 1.12, 5.72);
  scene.add(deskShade);
  const deskLight = new THREE.PointLight(0xffad62, 8, 3.5);
  deskLight.position.set(4.31, 1.05, 5.72);
  scene.add(deskLight);

  const phone = new THREE.Group();
  phone.position.set(-.37, .756, .41);
  phone.rotation.y = .41;
  table.add(phone);
  box(phone, [.16, .021, .31], [0, 0, 0], dark, .01);
  const displayMat = new THREE.MeshBasicMaterial({ color: 0xffffff });
  const display = new THREE.Mesh(new THREE.PlaneGeometry(.146, .288), displayMat);
  display.rotation.x = -Math.PI / 2;
  display.position.y = .011;
  phone.add(display);
  return { lens, displayMat };
}

function projection(scene, lens) {
  const wall = V(.95, 2.31, -4.18);
  const geometry = new THREE.PlaneGeometry(3.97, 2.18);
  const position = geometry.getAttribute('position');
  position.setY(0, .82);
  position.setY(1, 1.13);
  position.setY(2, -1.03);
  position.setY(3, -.99);
  const material = new THREE.MeshBasicMaterial({ color: 0x8fa6b6, toneMapped: false });
  const image = new THREE.Mesh(geometry, material);
  image.position.copy(wall);
  scene.add(image);

  scene.updateMatrixWorld(true);
  const source = lens.getWorldPosition(new THREE.Vector3());
  const beam = new THREE.Group();
  const axis = wall.clone().sub(source).normalize();
  const tangent = axis.clone().cross(V(0, 1, 0)).normalize();
  const sheetMaterial = new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, side: THREE.DoubleSide, blending: THREE.AdditiveBlending,
    vertexShader: `varying vec2 vUv; void main() { vUv=uv; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.); }`,
    fragmentShader: `varying vec2 vUv; void main() {
      float crossFade = pow(sin(3.14159 * vUv.x), 2.7);
      float depthFade = smoothstep(0., .065, vUv.y) * (1. - smoothstep(.48, 1., vUv.y));
      gl_FragColor=vec4(.66,.78,.96,crossFade * depthFade * .046);
    }`,
  });
  for (let i = 0; i < 5; i++) {
    const across = tangent.clone().applyAxisAngle(axis, i * Math.PI / 5);
    const corners = [
      source.clone().addScaledVector(across, -.065),
      source.clone().addScaledVector(across, .065),
      wall.clone().addScaledVector(across, -1.47),
      wall.clone().addScaledVector(across, 1.47),
    ];
    const sheet = new THREE.BufferGeometry();
    sheet.setAttribute('position', new THREE.Float32BufferAttribute(corners.flatMap((point) => point.toArray()), 3));
    sheet.setAttribute('uv', new THREE.Float32BufferAttribute([0,0, 1,0, 0,1, 1,1], 2));
    sheet.setIndex([0,1,2, 1,3,2]);
    beam.add(new THREE.Mesh(sheet, sheetMaterial));
  }
  scene.add(beam);
  const halo = new THREE.Mesh(new THREE.PlaneGeometry(5.3, 3.55), new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, blending: THREE.AdditiveBlending,
    vertexShader: `varying vec2 vUv; void main() { vUv=uv; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.); }`,
    fragmentShader: `varying vec2 vUv; void main() {
      vec2 edge = max(abs(vUv - .5) - vec2(.30,.27), 0.);
      gl_FragColor = vec4(.64,.72,.88, .12 * exp(-length(edge) * 24.));
    }`,
  }));
  halo.position.copy(wall).add(V(0, 0, -.01));
  scene.add(halo);

  const light = new THREE.PointLight(0x9aafd9, 5, 3.6, 2);
  light.position.copy(wall).add(V(0, 0, .75));
  scene.add(light);
  const dustPositions = [];
  for (let i = 0; i < 64; i++) {
    const phase = i * 2.39996;
    const depth = ((i * 19) % 67) / 67;
    const offset = source.clone().lerp(wall, depth);
    const radius = .68 * depth;
    offset.add(V(Math.sin(phase) * radius, Math.cos(phase) * radius, 0));
    dustPositions.push(offset.x, offset.y, offset.z);
  }
  const dustGeometry = new THREE.BufferGeometry();
  dustGeometry.setAttribute('position', new THREE.Float32BufferAttribute(dustPositions, 3));
  const dust = new THREE.Points(dustGeometry, new THREE.PointsMaterial({
    size: .021, color: 0xcad9ed, opacity: .54, transparent: true, depthWrite: false,
  }));
  scene.add(dust);
  return { dust, material, image, beam, halo, light };
}

const views = {
  sofa: { eye: V(-.35, 1.91, 6.27), target: V(-1.32, 1.81, -3.55) },
  projector: { eye: V(.36, 1.59, -1.58), target: V(2.63, .98, .73) },
  wall: { eye: V(.49, 2.22, .88), target: V(.91, 2.25, -4.16) },
};

export async function mountWorld(opening, motion) {
  const stage = opening.querySelector('.world-render');
  const shot = stage.closest('.shot');
  let renderer;
  try {
    renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'low-power' });
  } catch {
    return false;
  }
  const loader = new THREE.TextureLoader();
  let film;
  try {
    film = await loader.loadAsync(asset('film.webp'));
  } catch {
    renderer.dispose();
    return false;
  }
  if (motion.matches) {
    film.dispose();
    renderer.dispose();
    return false;
  }

  const scene = new THREE.Scene();
  scene.background = new THREE.Color(0x131c29);
  const camera = new THREE.PerspectiveCamera(48, 1, .1, 38);
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.25;
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.autoUpdate = false;
  renderer.shadowMap.needsUpdate = true;
  renderer.shadowMap.type = THREE.PCFShadowMap;
  const ambient = new THREE.HemisphereLight(0x9fbbe3, 0x664d49, 1.2);
  scene.add(ambient);
  const moon = new THREE.SpotLight(0xa5c7ff, 86, 26, .78, .85);
  moon.position.set(-3.32, 3.8, -2.3);
  moon.target.position.set(-.25, .55, 2.7);
  moon.castShadow = true;
  moon.shadow.mapSize.set(512, 512);
  moon.shadow.bias = -.0003;
  scene.add(moon, moon.target);
  const fill = new THREE.DirectionalLight(0xbad2e4, .76);
  fill.position.set(2, 4, 8);
  scene.add(fill);
  const { lens, displayMat } = furnish(scene);
  const { dust, material, image, beam, halo, light } = projection(scene, lens);
  film.colorSpace = THREE.SRGBColorSpace;
  material.map = film;
  material.color.setHex(0xc5cbd1);
  material.needsUpdate = true;
  loader.load(asset('dpad.webp'), (screen) => {
    screen.colorSpace = THREE.SRGBColorSpace;
    displayMat.map = screen;
    displayMat.needsUpdate = true;
  });

  stage.append(renderer.domElement);
  const controls = opening.querySelector('.world-controls');
  const buttons = [...controls.querySelectorAll('[data-view]')];
  const pointer = new THREE.Vector2();
  const eye = views.sofa.eye.clone();
  const aim = views.sofa.target.clone();
  const desiredEye = eye.clone();
  const desiredAim = aim.clone();
  const drawEye = eye.clone();
  const drawAim = aim.clone();
  let choice = 'sofa';
  let scroll = 0;
  let visible = false;
  let lost = false;
  let frame = 0;
  let lastFrame = -Infinity;
  let powered = true;
  const power = opening.querySelector('.world-power');
  const handoff = opening.querySelector('.continue');
  const title = opening.querySelector('h1');
  const description = opening.querySelector('.opening-text p');
  const cameraCopy = {
    sofa: [title.textContent, description.textContent],
    projector: ['One light fills the room.', 'Switch the projector off and watch the room change.'],
    wall: ['Make the picture fit.', 'Take the corners into your own hands.'],
  };

  function resize() {
    const { clientWidth: width, clientHeight: height } = shot;
    if (!width || !height) return;
    camera.aspect = width / height;
    camera.fov = camera.aspect < 1 ? 61 : 48;
    camera.updateProjectionMatrix();
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.5, 1600 / width));
    renderer.setSize(width, height, false);
  }

  function render(time = 0, immediate = false) {
    const from = views[choice];
    const sequence = choice === 'sofa' && camera.aspect > 1.2 ? scroll : 0;
    desiredEye.copy(from.eye).lerp(views.wall.eye, sequence * .68);
    desiredAim.copy(from.target).lerp(views.wall.target, sequence);
    if (camera.aspect < 1 && choice === 'projector') desiredAim.x = 2.13;
    if (camera.aspect < 1 && choice === 'sofa') {
      desiredEye.x = .8;
      desiredAim.x = .95;
      desiredEye.z += .5;
    }
    if (motion.matches || immediate) {
      eye.copy(desiredEye);
      aim.copy(desiredAim);
    } else {
      eye.lerp(desiredEye, .085);
      aim.lerp(desiredAim, .085);
    }
    drawEye.copy(eye);
    drawAim.copy(aim);
    if (!motion.matches) {
      drawEye.x += pointer.x * .21;
      drawEye.y -= pointer.y * .10;
      dust.position.y = Math.sin(time * .00042) * .05;
    }
    camera.position.copy(drawEye);
    camera.lookAt(drawAim);
    renderer.render(scene, camera);
  }

  function tick(time) {
    if (time - lastFrame >= 30) {
      lastFrame = time;
      render(time);
    }
    frame = requestAnimationFrame(tick);
  }

  function update() {
    const running = visible && !document.hidden && !lost && !motion.matches;
    if (running && !frame) frame = requestAnimationFrame(tick);
    if (!running && frame) {
      cancelAnimationFrame(frame);
      frame = 0;
    }
    if (visible && motion.matches && !lost) render(0, true);
  }

  buttons.forEach((button) => button.addEventListener('click', () => {
    choice = button.dataset.view;
    opening.dataset.camera = choice;
    [title.textContent, description.textContent] = cameraCopy[choice];
    power.hidden = choice !== 'projector';
    handoff.href = choice === 'wall' ? '#picture' : '#features';
    handoff.lastChild.textContent = choice === 'wall' ? 'Try corner controls' : 'Follow the phone';
    buttons.forEach((other) => other.setAttribute('aria-pressed', String(button === other)));
    if (motion.matches) render(0, true);
  }));
  power.addEventListener('click', () => {
    powered = !powered;
    for (const effect of [image, beam, halo, light, dust]) effect.visible = powered;
    lens.material.color.setHex(powered ? 0xffe0a4 : 0x3a4651);
    power.textContent = powered ? 'Turn projector off' : 'Turn projector on';
    if (motion.matches) render(0, true);
  });
  opening.addEventListener('pointermove', (event) => {
    if (event.pointerType !== 'mouse') return;
    pointer.set((event.clientX / innerWidth - .5) * 2, (event.clientY / innerHeight - .5) * 2);
  });
  opening.addEventListener('pointerleave', () => pointer.set(0, 0));

  gsap.registerPlugin(ScrollTrigger);
  const media = gsap.matchMedia();
  media.add('(min-width: 58rem) and (min-height: 38rem) and (prefers-reduced-motion: no-preference)', () => {
    ScrollTrigger.create({
      trigger: opening, start: 'top top', end: 'bottom bottom',
      onUpdate: ({ progress }) => { scroll = progress; },
    });
    return () => { scroll = 0; };
  });
  new ResizeObserver(() => { resize(); if (visible && !lost) render(0, true); }).observe(shot);
  new IntersectionObserver(([entry]) => {
    visible = entry.intersectionRatio >= .015;
    update();
  }, { threshold: .015 }).observe(shot);
  document.addEventListener('visibilitychange', update);
  motion.addEventListener('change', update);
  renderer.domElement.addEventListener('webglcontextlost', () => {
    lost = true;
    opening.classList.remove('world-ready');
    controls.hidden = true;
    update();
  });

  // Refresh camera travel after the chapter switches from display:none.
  new MutationObserver(() => {
    if (document.documentElement.dataset.chapter === 'room') ScrollTrigger.refresh();
  }).observe(document.documentElement, { attributes: true, attributeFilter: ['data-chapter'] });

  resize();
  render(0, true);
  eye.z += .62;
  eye.x -= .22;
  render();
  opening.classList.add('world-ready');
  controls.hidden = false;
  return true;
}
