import React, { useEffect, useRef, useState } from 'react';
import { animate, useMotionValue, useMotionValueEvent, useReducedMotion } from 'motion/react';

const crooked = [[11, 24], [88, 7], [85, 89], [15, 75]];
const straight = [[12, 13], [88, 13], [88, 87], [12, 87]];

export function PictureDemo() {
  const correction = useMotionValue(0);
  const reduceMotion = useReducedMotion();
  const [value, setValue] = useState(0);
  const animation = useRef(null);
  useEffect(() => () => animation.current?.stop(), []);
  useMotionValueEvent(correction, 'change', (progress) => setValue(Math.max(0, Math.min(100, Math.round(progress)))));

  const corners = crooked.map(([x, y], i) => [
    x + (straight[i][0] - x) * value / 100,
    y + (straight[i][1] - y) * value / 100,
  ]);
  const polygon = `polygon(${corners.map(([x, y]) => `${x}% ${y}%`).join(', ')})`;

  function setCorrection(next, spring) {
    animation.current?.stop();
    if (!spring || reduceMotion) {
      correction.set(next);
      return;
    }
    animation.current = animate(correction, next, { type: 'spring', stiffness: 350, damping: 34 });
  }

  return (
    <div className="picture-demo">
      <div className="demo-wall" aria-hidden="true">
        <div className="demo-target"></div>
        <div className="demo-projection" style={{ clipPath: polygon }}>
          <div className="demo-sun"></div>
          <div className="demo-ridge"></div>
        </div>
        {corners.map(([x, y], i) => (
          <span key={i} className="demo-corner" style={{ left: x + '%', top: y + '%' }} />
        ))}
      </div>
      <div className="demo-console">
        <div className="demo-label">
          <label htmlFor="correction">Picture correction</label>
          <output htmlFor="correction">{value}%</output>
        </div>
        <input
          id="correction"
          type="range"
          min="0"
          max="100"
          value={value}
          onChange={(event) => setCorrection(event.currentTarget.valueAsNumber, false)}
        />
        <div className="demo-actions">
          <button type="button" className="button outline" onClick={() => setCorrection(0, true)}>Reset</button>
          <button type="button" className="button primary" onClick={() => setCorrection(100, true)}>Straighten picture</button>
        </div>
      </div>
    </div>
  );
}
