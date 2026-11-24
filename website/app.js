const previews = {
  remote: {
    alt: 'ProjectorConnect touchpad and remote buttons',
    caption: 'Swipe to move the pointer. Tap to click.',
  },
  keyboard: {
    alt: 'ProjectorConnect screen for typing, dictation, and opening links',
    caption: 'Say a search out loud or paste a link from your phone.',
  },
  apps: {
    alt: 'ProjectorConnect list of installed apps and projector controls',
    caption: 'Launch apps from your phone and pin favorites for later.',
  },
  'fine-tune': {
    alt: 'ProjectorConnect corner handles on a projected picture',
    caption: 'Drag one corner at a time until the picture fits.',
  },
};

const buttons = document.querySelectorAll('.feature-list button');
const stage = document.querySelector('.stage');
const image = document.querySelector('#feature-image');
const caption = document.querySelector('#feature-caption');

for (const button of buttons) {
  button.addEventListener('click', () => {
    const feature = button.dataset.feature;
    const preview = previews[feature];
    if (stage.dataset.feature === feature) return;

    for (const option of buttons) {
      option.setAttribute('aria-pressed', String(option === button));
    }
    image.src = `assets/${feature}.webp`;
    image.alt = preview.alt;
    caption.textContent = preview.caption;
    stage.dataset.feature = feature;
  });
}
