import { animate, stagger } from 'motion';

/* Animated <details>.
 *
 * The native element snaps open and shut. Here the summary click is taken over: the element's height eases between the
 * closed and open size while its content rises into place, and the plus turns at the same moment (via .closing, since
 * [open] stays until the height has finished). Without scripting, or with reduced motion, the plain element still works.
 * `name=` exclusivity is replaced by data-group so that the one that was open can close with an animation too,
 * instead of the browser shutting it instantly.
 */
const reduce = matchMedia('(prefers-reduced-motion: reduce)');
const EASE = [0.22, 1, 0.36, 1];

function settle(d) {
  d.style.height = '';
  d.style.overflow = '';
  d._anim = null;
}

function open(d) {
  d._anim?.stop();
  const from = d.offsetHeight;
  d.classList.remove('closing');
  d.open = true;
  const to = d.scrollHeight;
  const body = [...d.children].filter((c) => c.tagName !== 'SUMMARY');
  d.style.overflow = 'hidden';
  d.style.height = from + 'px';
  d._anim = animate(d, { height: [from + 'px', to + 'px'] }, { duration: 0.6, ease: EASE });
  d._anim.finished.then(() => settle(d), () => {});
  animate(body, { opacity: [0, 1], y: [14, 0] }, { duration: 0.55, delay: stagger(0.05, { startDelay: 0.12 }), ease: EASE });
}

function close(d) {
  d._anim?.stop();
  const summary = d.querySelector(':scope > summary');
  const from = d.offsetHeight;
  const to = summary.offsetHeight + (d.offsetHeight - d.clientHeight);   // + the borders
  const body = [...d.children].filter((c) => c.tagName !== 'SUMMARY');
  d.classList.add('closing');
  d.style.overflow = 'hidden';
  d.style.height = from + 'px';
  animate(body, { opacity: [1, 0] }, { duration: 0.2, ease: 'easeIn' });
  d._anim = animate(d, { height: [from + 'px', to + 'px'] }, { duration: 0.45, ease: EASE });
  d._anim.finished.then(() => { d.open = false; d.classList.remove('closing'); body.forEach((b) => { b.style.opacity = ''; b.style.transform = ''; }); settle(d); }, () => {});
}

export function initAccordions() {
  document.querySelectorAll('details').forEach((d) => {
    const group = d.getAttribute('name');
    if (group) { d.dataset.group = group; d.removeAttribute('name'); }
    d.querySelector(':scope > summary')?.addEventListener('click', (e) => {
      if (reduce.matches) return;                       // native behaviour, instantly
      e.preventDefault();
      const isOpen = d.open && !d.classList.contains('closing');
      if (isOpen) { close(d); return; }
      if (d.dataset.group) {
        document.querySelectorAll(`details[data-group="${d.dataset.group}"]`).forEach((o) => { if (o !== d && o.open && !o.classList.contains('closing')) close(o); });
      }
      open(d);
    });
  });
}
