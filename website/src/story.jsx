import React, { useEffect, useRef, useState } from 'react';
import { createTimeline } from 'animejs/timeline';
import { useReducedMotion } from 'motion/react';

const scenes = [
  {
    name: 'Lost remote',
    title: 'Where’s the remote?',
    caption: 'It’s always somewhere between the cushions.',
    art: 'remote',
  },
  {
    name: 'Slow typing',
    title: 'The longest way to type.',
    caption: 'One letter at a time, across an on-screen keyboard.',
    art: 'typing',
  },
  {
    name: 'Crooked picture',
    title: 'Why is the picture tilted?',
    caption: 'The projector is sitting wherever it fits.',
    art: 'tilt',
  },
  {
    name: 'The fix',
    title: 'You’re holding the fix.',
    caption: 'Point, type, and straighten the picture from your phone.',
    art: 'solution',
  },
];

const sceneDuration = 4250;

export function Story() {
  const [index, setIndex] = useState(0);
  const [playing, setPlaying] = useState(false);
  const reduceMotion = useReducedMotion();
  const projection = useRef(null);
  const beam = useRef(null);
  const scene = scenes[index];
  const finalScene = index === scenes.length - 1;

  useEffect(() => {
    if (reduceMotion) return;

    const timeline = createTimeline({ defaults: { ease: 'out(4)' } })
      .add(beam.current, { opacity: [0, .85, .38], duration: 770 }, 0)
      .add(projection.current, {
        opacity: [.32, 1],
        scale: [.975, 1],
        duration: 520,
      }, 75);

    return () => timeline.revert();
  }, [index, reduceMotion]);

  useEffect(() => {
    if (!playing) return;

    const timer = window.setTimeout(() => {
      if (finalScene) setPlaying(false);
      else setIndex(index + 1);
    }, sceneDuration);

    return () => window.clearTimeout(timer);
  }, [finalScene, index, playing]);

  function pickScene(next) {
    setPlaying(false);
    setIndex(next);
  }

  function togglePlayback() {
    if (!playing && finalScene) setIndex(0);
    setPlaying(!playing);
  }

  return (
    <div className="story" aria-label="The ProjectorConnect story">
      <div ref={beam} className="story-beam" aria-hidden="true" />
      <div className="projection" ref={projection} data-scene={scene.art}>
        <div className="projected-art" aria-hidden="true">
          <div className={'scene-icon scene-' + scene.art}>
            {scene.art === 'typing' && <span>M O V<span className="cursor">_</span></span>}
          </div>
        </div>
        <div aria-live={playing ? 'off' : 'polite'} aria-atomic="true">
          <p className="story-title">{scene.title}</p>
          <p className="story-caption">{scene.caption}</p>
        </div>
      </div>
      <div className="story-controls">
        <div className="scene-picker" role="group" aria-label="Choose a scene">
          {scenes.map(({ name }, number) => (
            <button
              key={name}
              type="button"
              onClick={() => pickScene(number)}
              aria-pressed={index === number}
              aria-label={name}
              title={name}
            >
              {number + 1}
            </button>
          ))}
        </div>
        <button type="button" className="play-story" onClick={togglePlayback}>
          <span aria-hidden="true">{playing ? 'Ⅱ' : '▶'}</span>
          {playing ? 'Pause story' : finalScene ? 'Replay story' : 'Play story'}
        </button>
      </div>
    </div>
  );
}
