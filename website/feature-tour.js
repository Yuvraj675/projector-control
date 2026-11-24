import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import { animate } from 'motion';
import { fitProjection } from './perspective.js';

gsap.registerPlugin(ScrollTrigger);

const moments = [
  {
    asset: 'dpad',
    alt: 'ProjectorConnect directional pad and playback buttons',
    situation: 'Someone walks in halfway through the good part.',
    caption: 'Use your phone as a D-pad, touchpad, or air mouse.',
    action: 'Pause the preview',
    initial: 'Playing',
  },
  {
    asset: 'keyboard',
    alt: 'ProjectorConnect screen for typing, dictation, and opening links',
    situation: 'You know the title. Picking out each letter on the wall takes forever.',
    caption: 'Type or speak on your phone, then see it on the projector.',
    initial: 'Waiting for a title',
  },
  {
    asset: 'apps',
    alt: 'ProjectorConnect list of installed apps and projector controls',
    situation: 'Everyone’s ready, but the streaming app is buried in a menu.',
    caption: 'Open projector apps, send files, and pin your favorites.',
    action: 'Open YouTube',
    initial: 'Projector home screen',
  },
  {
    asset: 'fine-tune',
    alt: 'ProjectorConnect corner handles on a projected picture',
    situation: 'The table is off to one side, and the picture lands crooked.',
    caption: 'Use auto keystone or nudge each corner on a compatible Hisilicon projector.',
    initial: 'Picture tilted',
  },
];

export function setupFeatureTour(motion) {
  const section = document.querySelector('.showcase');
  const tabs = section.querySelector('.feature-list');
  const buttons = [...tabs.querySelectorAll('button')];
  const proof = section.querySelector('.feature-proof');
  const frame = proof.querySelector('.feature-frame');
  const camera = frame.querySelector('.feature-camera');
  const phone = camera.querySelector('.phone');
  const image = phone.querySelector('#feature-image');
  const incoming = phone.querySelector('.phone-incoming');
  const caption = proof.querySelector('#feature-caption');
  const example = section.querySelector('.feature-example');
  const situation = example.querySelector('.feature-situation');
  const intro = section.querySelector('.feature-intro');
  const meter = example.querySelector('.playback-meter');
  const action = example.querySelector('.example-button');
  const search = example.querySelector('.example-search');
  const input = search.querySelector('input');
  const link = example.querySelector('.example-link');
  const output = example.querySelector('output');

  const previews = new Map();
  const screen = [[.278, .098], [.712, .098], [.723, .725], [.285, .727]];
  let index = 0;
  let paused = false;
  let launched = false;
  let scrollStory;
  let media;
  let phoneTransition;
  let textTransition;
  let revision = 0;

  new ResizeObserver(() => fitProjection(camera, phone, screen)).observe(frame);

  function preload(asset) {
    if (!previews.has(asset)) {
      const preloadImage = new Image();
      preloadImage.src = `${import.meta.env.BASE_URL}assets/${asset}.webp`;
      previews.set(asset, preloadImage);
    }
    return previews.get(asset);
  }

  async function revealScreen(moment, ticket) {
    const next = preload(moment.asset);
    try { await next.decode(); } catch { /* The browser can still show the image or its alt text. */ }
    if (ticket !== revision) return;

    if (!motion.matches) {
      incoming.src = next.src;
      incoming.hidden = false;
      phoneTransition = animate(incoming, { opacity: [0, 1], transform: ['translateY(5%)', 'translateY(0)'] }, {
        duration: .32,
        ease: 'easeOut',
        onComplete: commitScreen,
      });
    } else {
      commitScreen();
    }

    function commitScreen() {
      if (ticket !== revision) return;
      image.src = next.src;
      image.alt = moment.alt;
      incoming.hidden = true;
    }
  }

  function renderExample() {
    const moment = moments[index];
    situation.textContent = moment.situation;
    action.hidden = !moment.action;
    search.hidden = index !== 1;
    link.hidden = index !== 3;
    meter.hidden = index !== 0;
    meter.dataset.paused = String(paused);
    action.textContent = [
      paused ? 'Resume the preview' : moment.action,
      '',
      launched ? 'Return to projector home' : moment.action,
    ][index] || '';
    output.textContent = [
      paused ? 'Paused' : moment.initial,
      input.value.trim() ? `Searching for “${input.value.trim()}”` : moment.initial,
      launched ? 'YouTube open' : moment.initial,
    ][index] || moment.initial;
  }

  function select(next) {
    if (index === next) return;

    // Keep focus in a visible control if scroll replaces the active example.
    if (example.contains(document.activeElement)) buttons[index].focus({ preventScroll: true });
    index = next;
    const moment = moments[index];
    const ticket = ++revision;
    phoneTransition?.stop();
    incoming.hidden = true;
    proof.dataset.feature = buttons[index].dataset.feature;
    buttons.forEach((button, i) => button.setAttribute('aria-pressed', String(i === index)));
    renderExample();
    caption.textContent = moment.caption;
    revealScreen(moment, ticket);
    if (!motion.matches) {
      textTransition?.stop();
      textTransition = animate(situation, { opacity: [.5, 1], transform: ['translateY(5px)', 'translateY(0)'] }, { duration: .25, ease: 'easeOut' });
    }
  }

  buttons.forEach((button, i) => button.addEventListener('click', () => {
    select(i);
    if (scrollStory) {
      const { start, end } = scrollStory;
      window.scrollTo({ top: start + (end - start) * (i + .15) / moments.length, behavior: 'instant' });
      ScrollTrigger.update();
    }
  }));

  action.addEventListener('click', () => {
    if (index === 0) paused = !paused;
    if (index === 2) launched = !launched;
    renderExample();
  });
  input.addEventListener('input', renderExample);

  section.querySelector('.feature-fallback').hidden = true;
  example.hidden = false;
  tabs.hidden = false;
  renderExample();

  return (active) => {
    media?.revert();
    media = null;
    scrollStory = null;
    if (!active) return;

    moments.forEach(({ asset }) => preload(asset));
    media = gsap.matchMedia();

    media.add('(min-width: 928px) and (min-height: 670px) and (prefers-reduced-motion: no-preference)', () => {
      intro.textContent = 'Scroll through movie night, or pick a moment.';
      const headerOffset = () => document.querySelector('.site-header').getBoundingClientRect().height;
      const push = gsap.timeline({
        scrollTrigger: {
          trigger: section,
          pin: true,
          start: () => `top top+=${headerOffset()}`,
          end: () => `+=${Math.round(window.innerHeight * 1.32)}`,
          scrub: .35,
          invalidateOnRefresh: true,
          onUpdate: ({ progress }) => select(Math.min(Math.floor(progress * moments.length), moments.length - 1)),
        },
      });
      push.fromTo(camera,
        { scale: 1, yPercent: 0, rotation: 0 },
        { scale: 1.31, yPercent: .7, rotation: -1.5, ease: 'power2.out' },
      );
      scrollStory = push.scrollTrigger;
      return () => {
        intro.textContent = 'Pick a moment from movie night.';
        scrollStory = null;
      };
    });

    media.add('(max-width: 927px) and (prefers-reduced-motion: no-preference)', () => {
      gsap.fromTo(camera,
        { scale: 1 },
        { scale: 1.22, ease: 'none', scrollTrigger: { trigger: frame, start: 'top bottom', end: 'center center', scrub: .28 } },
      );
    });

    ScrollTrigger.refresh();
  };
}
