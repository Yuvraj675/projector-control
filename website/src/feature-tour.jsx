import React, { useState } from 'react';
import { AnimatePresence, m, useReducedMotion } from 'motion/react';

const features = [
  {
    id: 'remote',
    title: 'Move around',
    description: 'Touchpad, D-pad, and air mouse',
    alt: 'ProjectorConnect touchpad and remote buttons',
    caption: 'Swipe to move the pointer. Tap to click.',
  },
  {
    id: 'keyboard',
    title: 'Type or speak',
    description: 'Search, paste text, and open links',
    alt: 'ProjectorConnect screen for typing, dictation, and opening links',
    caption: 'Say a search out loud or paste a link from your phone.',
  },
  {
    id: 'apps',
    title: 'Send and launch',
    description: 'Open apps, install Android apps, and send files',
    alt: 'ProjectorConnect list of installed apps and projector controls',
    caption: 'Launch apps from your phone and pin favorites for later.',
  },
  {
    id: 'fine-tune',
    title: 'Straighten the picture',
    description: 'Auto keystone and corner controls on supported models',
    alt: 'ProjectorConnect corner handles on a projected picture',
    caption: 'Drag one corner at a time until the picture fits.',
  },
];

const assetRoot = `${import.meta.env.BASE_URL}assets/`;

export function FeatureTour() {
  const [feature, select] = useState(features[0]);
  const reduceMotion = useReducedMotion();
  return (
    <>
      <div className="feature-copy">
        <h2 id="features-title">Make the big screen yours.</h2>
        <p className="lead">Put down the tiny remote. Pick what you want to do.</p>
        <div className="feature-list" role="group" aria-label="Feature previews">
          {features.map((item) => (
            <m.button
              key={item.id}
              type="button"
              aria-pressed={feature.id === item.id}
              onClick={() => select(item)}
              whileTap={reduceMotion ? undefined : { transform: 'scale(.99)' }}
            >
              <span>{item.title}</span><small>{item.description}</small>
            </m.button>
          ))}
        </div>
      </div>
      <figure className="stage" data-feature={feature.id}>
        <div className="stage-light" aria-hidden="true"></div>
        <div className="phone">
          <AnimatePresence initial={false}>
            <m.img
              key={feature.id}
              src={assetRoot + feature.id + '.webp'}
              width="500"
              height="1084"
              loading="lazy"
              alt={feature.alt}
              initial={{ opacity: reduceMotion ? 1 : 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: reduceMotion ? 0 : .22 }}
            />
          </AnimatePresence>
        </div>
        <figcaption aria-live="polite">{feature.caption}</figcaption>
      </figure>
    </>
  );
}
