import React from 'react';
import { createRoot } from 'react-dom/client';
import { LazyMotion, domAnimation } from 'motion/react';
import { Story } from './story';
import { FeatureTour } from './feature-tour';
import { PictureDemo } from './picture-demo';
import './motion.css';

for (const [id, Component] of [
  ['story', Story],
  ['feature-tour', FeatureTour],
  ['picture-demo', PictureDemo],
]) {
  createRoot(document.getElementById(id)).render(
    <LazyMotion features={domAnimation} strict>
      <Component />
    </LazyMotion>,
  );
}
