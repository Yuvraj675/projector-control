// Renders the exact simple-icons glyphs onto app-icon tiles (public/assets/icons/*.png).
// prime/jiohotstar/chrome come from Codex (art/raw3/icon-*.png) and are tiled by make_icons.py.
import { Resvg } from '@resvg/resvg-js';
import * as si from 'simple-icons';
import fs from 'node:fs';
const OUT = new URL('../public/assets/icons/', import.meta.url).pathname;
const tiles = {
  youtube:  { icon: si.siYoutube,  bg: '#ffffff', fg: '#FF0000' },
  netflix:  { icon: si.siNetflix,  bg: '#000000', fg: '#E50914' },
  spotify:  { icon: si.siSpotify,  bg: '#000000', fg: '#1ED760' },
  vlc:      { icon: si.siVlcmediaplayer, bg: '#ffffff', fg: '#FF8800' },
  stremio:  { icon: si.siStremio,  bg: '#' + si.siStremio.hex, fg: '#ffffff' },
};
for (const [name, t] of Object.entries(tiles)) {
  const s = 512, g = 0.58 * s, off = (s - g) / 2, k = g / 24;
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${s}" height="${s}" viewBox="0 0 ${s} ${s}">
    <rect width="${s}" height="${s}" rx="${s * 0.22}" fill="${t.bg}"/>
    <g transform="translate(${off} ${off}) scale(${k})"><path d="${t.icon.path}" fill="${t.fg}"/></g></svg>`;
  fs.writeFileSync(OUT + name + '.png', new Resvg(svg).render().asPng());
  console.log(name);
}
