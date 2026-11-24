import { fitProjection, isConvex } from './perspective.js';

const crooked = [[.12, .23], [.9, .08], [.86, .83], [.10, .86]];
const squared = [[.10, .14], [.90, .14], [.90, .86], [.10, .86]];
const arrows = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] };

export function setupCorrection(demonstration, motion) {
  const wall = demonstration.querySelector('.demo-wall');
  const projection = demonstration.querySelector('.demo-image');
  const handles = demonstration.querySelector('.demo-handles');
  const corners = [...handles.querySelectorAll('button')];
  const controls = demonstration.querySelector('.demo-controls');
  const status = demonstration.querySelector('.demo-status');
  let points = crooked;
  let animation = 0;

  function draw(announce = false) {
    fitProjection(wall, projection, points);
    corners.forEach((corner, i) => {
      const [x, y] = points[i];
      corner.style.left = `${x * 100}%`;
      corner.style.top = `${y * 100}%`;
    });
    if (announce) {
      const aligned = points.every(([x, y], i) => Math.hypot(x - squared[i][0], y - squared[i][1]) < .025);
      wall.dataset.aligned = String(aligned);
      const message = aligned ? 'Picture squared' : 'Drag any corner';
      if (status.textContent !== message) status.textContent = message;
    }
  }

  function nudge(index, x, y) {
    const clamp = (value) => Math.max(.055, Math.min(.945, value));
    const next = points.map((point, i) => i === index ? [clamp(x), clamp(y)] : point);
    if (isConvex(next)) {
      points = next;
      draw(true);
    }
  }

  function moveTo(destination) {
    cancelAnimationFrame(animation);
    if (motion.matches) {
      points = destination;
      draw(true);
      return;
    }

    const start = performance.now();
    const initial = points;
    function frame(now) {
      const progress = Math.min((now - start) / 360, 1);
      const eased = 1 - (1 - progress) ** 3;
      points = initial.map(([x, y], i) => [
        x + (destination[i][0] - x) * eased,
        y + (destination[i][1] - y) * eased,
      ]);
      draw(progress === 1);
      if (progress < 1) animation = requestAnimationFrame(frame);
    }
    animation = requestAnimationFrame(frame);
  }

  corners.forEach((corner, index) => {
    let drag = null;

    corner.addEventListener('pointerdown', (event) => {
      if (event.button !== 0 || drag) return;
      cancelAnimationFrame(animation);
      corner.setPointerCapture(event.pointerId);
      drag = { id: event.pointerId, x: event.clientX, y: event.clientY, origin: points[index] };
    });

    corner.addEventListener('pointermove', (event) => {
      if (!drag || drag.id !== event.pointerId) return;
      const { width, height } = wall.getBoundingClientRect();
      nudge(index, drag.origin[0] + (event.clientX - drag.x) / width, drag.origin[1] + (event.clientY - drag.y) / height);
    });

    corner.addEventListener('lostpointercapture', () => { drag = null; });

    corner.addEventListener('keydown', (event) => {
      const vector = arrows[event.key];
      if (!vector) return;
      event.preventDefault();
      cancelAnimationFrame(animation);
      const step = event.shiftKey ? .035 : .008;
      const [x, y] = points[index];
      nudge(index, x + vector[0] * step, y + vector[1] * step);
    });
  });

  controls.querySelector('.reset').addEventListener('click', () => moveTo(crooked));
  controls.querySelector('.straighten').addEventListener('click', () => moveTo(squared));
  document.querySelector('.demo-instructions').textContent = 'Pull a corner toward its outline. Watch the picture stretch into place.';
  handles.hidden = false;
  controls.hidden = false;
  new ResizeObserver(() => draw()).observe(wall);
  draw();
}
