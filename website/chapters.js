import { animate } from 'motion';

const routes = {
  '#top': 'room',
  '#picture': 'room',
  '#features': 'remote',
  '#setup': 'setup',
  '#help': 'setup',
};

export function setupChapters(motion, onChapterChange) {
  const root = document.documentElement;
  const tabList = document.querySelector('.chapter-tabs');
  const tabs = [...tabList.querySelectorAll('[data-chapter-tab]')];
  const panels = [...document.querySelectorAll('.chapter')];
  const indicator = tabList.querySelector('.chapter-indicator');
  let active;
  let markerAnimation;
  let panelAnimation;
  let markerPosition = { x: 0, width: 0 };

  function moveIndicator(transition) {
    const current = tabs.find((tab) => tab.dataset.chapterTab === active);
    if (!current) return;

    markerAnimation?.stop();
    const start = markerPosition;
    const end = { x: current.offsetLeft, width: current.offsetWidth };
    const draw = (x, width) => {
      markerPosition = { x, width };
      indicator.style.transform = `translateX(${x}px) scaleX(${width})`;
    };

    if (transition && !motion.matches && start.width) {
      markerAnimation = animate(0, 1, {
        type: 'spring',
        stiffness: 390,
        damping: 34,
        onUpdate: (progress) => draw(
          start.x + (end.x - start.x) * progress,
          start.width + (end.width - start.width) * progress,
        ),
      });
    } else {
      draw(end.x, end.width);
    }
  }

  function open(hash, { transition = true, focus = false, scroll = null } = {}) {
    const name = routes[hash] || 'room';
    const changed = active !== name;
    const panel = panels.find((item) => item.dataset.chapter === name);

    if (changed) {
      // Remove pins while their original panel still has measurable dimensions.
      onChapterChange(false);
      panelAnimation?.stop();
      if (active) panels.find((item) => item.dataset.chapter === active).style.opacity = '';
      active = name;
      root.dataset.chapter = name;
      panels.forEach((item) => { item.hidden = item !== panel; });
      for (const tab of tabs) {
        const selected = tab.dataset.chapterTab === name;
        tab.setAttribute('aria-selected', String(selected));
        tab.tabIndex = selected ? 0 : -1;
      }
      moveIndicator(transition);
    }

    const destination = document.getElementById(hash.slice(1)) || panel;
    if (focus) {
      const heading = destination.querySelector('h1, h2');
      if (heading) {
        heading.tabIndex = -1;
        heading.focus({ preventScroll: true });
      }
    }
    destination.scrollIntoView({ block: 'start', behavior: 'instant' });

    if (changed && name === 'remote') onChapterChange(true);
    if (Number.isFinite(scroll)) window.scrollTo({ top: scroll, behavior: 'instant' });
    if (changed && transition && !motion.matches) {
      panelAnimation = animate(panel, { opacity: [.45, 1] }, { duration: .32, ease: 'easeOut' });
    }
  }

  tabList.setAttribute('role', 'tablist');
  tabList.setAttribute('aria-label', 'ProjectorConnect chapters');
  for (const tab of tabs) {
    tab.setAttribute('role', 'tab');
    tab.setAttribute('aria-controls', `chapter-${tab.dataset.chapterTab}`);
  }
  for (const panel of panels) {
    panel.setAttribute('role', 'tabpanel');
    panel.setAttribute('aria-labelledby', `tab-${panel.dataset.chapter}`);
  }

  tabList.addEventListener('keydown', (event) => {
    const index = tabs.indexOf(event.target);
    const positions = { ArrowLeft: (index + tabs.length - 1) % tabs.length, ArrowRight: (index + 1) % tabs.length, Home: 0, End: tabs.length - 1 };
    if (index < 0 || !Object.hasOwn(positions, event.key)) return;
    const tab = tabs[positions[event.key]];
    event.preventDefault();
    tab.click();
    tab.focus({ preventScroll: true });
  });

  document.addEventListener('click', (event) => {
    const link = event.target.closest('a[href^="#"]');
    if (!link || !routes[link.hash] || event.defaultPrevented || event.button !== 0 ||
        event.altKey || event.metaKey || event.shiftKey || event.ctrlKey) return;

    event.preventDefault();
    if (location.hash !== link.hash) {
      history.replaceState({ ...history.state, scroll: window.scrollY }, '');
      history.pushState(null, '', link.hash);
    }
    open(link.hash, { focus: !link.hasAttribute('data-chapter-tab') });
  });

  history.scrollRestoration = 'manual';
  window.addEventListener('hashchange', () => open(location.hash || '#top', {
    transition: false,
    scroll: history.state?.scroll,
  }));
  new ResizeObserver(() => moveIndicator(false)).observe(tabList);
  document.fonts.ready.then(() => moveIndicator(false));
  root.classList.add('chapters-ready');
  indicator.hidden = false;
  open(location.hash || '#top', { transition: false });
}
