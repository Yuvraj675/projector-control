#!/usr/bin/env python3
"""Post-process the built hand frames in public/assets/hand (run after build_frames.py).

1. Sharpness: RIFE in-betweens come out softer than the keyframes they connect (down to 0.4x), which shows as a blur pulse in the
   middle of every thumb move. Each in-between gets an unsharp mask whose strength is searched so its detail matches the
   geometric mean of its two end frames (capped), applied only to the hand (never the magenta screen, so keying is untouched).
2. Tone: generated keyframes differ in exposure by up to ~19 levels, which shimmers while two of them cross-fade. Every keyframe
   gets a gain towards the rig's median hand brightness (clamped to +-8%); in-betweens get the interpolated gain.
3. Writes the half-size mobile sets (hand/portrait-m, hand/landscape-m) from the polished frames (premultiplied resize, no dark fringes).

Idempotent only on the first run of a fresh build_frames.py output: run it once per build. Needs numpy, Pillow, scipy."""
import json, sys, os
import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter, binary_dilation

ROOT = os.path.join(os.path.dirname(__file__), '..')
frames = json.load(open(os.path.join(ROOT, 'src/frames.json')))
MAX_GAIN = 0.08
MOBILE = {'portrait': (576, 864), 'landscape': (864, 576)}

def load(p): return np.asarray(Image.open(p).convert('RGBA')).astype(np.float32)

def hand_mask(a):
    rgb, al = a[..., :3], a[..., 3]
    chroma = (np.minimum(rgb[..., 0], rgb[..., 2]) - rgb[..., 1]) / np.maximum(np.maximum(rgb[..., 0], rgb[..., 2]), 12)
    mag = chroma > 0.4
    lum = rgb @ np.array([.3, .59, .11], np.float32)
    m = (al > 200) & ~mag & (lum > 35)
    return m, binary_dilation(mag, iterations=3)          # hand pixels, and the halo around the screen to leave alone

def detail(a, m):
    g = a[..., :3] @ np.array([.3, .59, .11], np.float32)
    lap = -4 * g[1:-1, 1:-1] + g[:-2, 1:-1] + g[2:, 1:-1] + g[1:-1, :-2] + g[1:-1, 2:]
    mm = m[1:-1, 1:-1]
    return float(lap[mm].var()) if mm.sum() > 200 else 0.0

def unsharp(a, m, halo, amount):
    out = a.copy()
    blur = np.stack([gaussian_filter(a[..., c], 1.1) for c in range(3)], -1)
    w = (m & ~halo)[..., None].astype(np.float32)
    out[..., :3] = np.clip(a[..., :3] + (a[..., :3] - blur) * amount * w, 0, 255)
    return out

def save(a, path):
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), 'RGBA').save(path, 'WEBP', quality=92, alpha_quality=100, method=6)

for rig, d in frames.items():
    base = os.path.join(ROOT, 'public/assets/hand', d['dir'])
    ids = list(d['frames'])
    keys = [i for i in ids if '__' not in i]
    img = {i: load(f'{base}/{i}.webp') for i in ids}
    masks = {i: hand_mask(img[i]) for i in ids}
    det0 = {i: detail(img[i], masks[i][0]) for i in ids}
    # ---- tone
    bright = {i: float(img[i][..., :3][masks[i][0]].mean()) if masks[i][0].sum() > 200 else 0 for i in keys}
    med = float(np.median([v for v in bright.values() if v > 0]))
    gain = {i: float(np.clip(med / bright[i], 1 - MAX_GAIN, 1 + MAX_GAIN)) if bright[i] > 0 else 1.0 for i in keys}
    # gain of every frame: key frames direct, in-betweens interpolated along their edge
    g = dict(gain)
    for e in d['edges']:
        seq = e['frames']; n = len(seq) - 1
        for k, i in enumerate(seq):
            if '__' in i: g[i] = gain.get(e['a'], 1.0) * (1 - k / n) + gain.get(e['b'], 1.0) * (k / n)
    # ---- sharpness target of every in-between (from the polished-tone end frames' detail)
    target = {}
    for e in d['edges']:
        seq = e['frames']
        a, b = det0[seq[0]], det0[seq[-1]]
        for i in seq[1:-1]:
            if i not in target and a > 0 and b > 0: target[i] = (a * b) ** 0.5
    changed = 0
    for i in ids:
        a = img[i].copy()
        m, halo = masks[i]
        if g.get(i, 1.0) != 1.0:
            a[..., :3] = np.clip(a[..., :3] * ((1 + (g[i] - 1) * (m | halo)[..., None]) if False else np.where(m[..., None], g[i], 1.0)), 0, 255)
        if '__' in i and i in target and det0[i] > 0 and det0[i] < 0.9 * target[i]:
            best, bestd = 0.0, 1e9
            for amt in (0.3, 0.5, 0.7, 0.9, 1.1, 1.3):
                s = unsharp(a, m, halo, amt)
                dd = abs(detail(s, m) - target[i])
                if dd < bestd: best, bestd = amt, dd
            a = unsharp(a, m, halo, best); changed += 1
        save(a, f'{base}/{i}.webp')
        img[i] = a
    print(rig, 'frames', len(ids), 'sharpened', changed, 'median brightness', round(med, 1), 'gain range', round(min(gain.values()), 3), round(max(gain.values()), 3))
    # ---- mobile set
    mw, mh = MOBILE[rig]
    out = os.path.join(ROOT, 'public/assets/hand', d['dir'] + '-m'); os.makedirs(out, exist_ok=True)
    for i in ids:
        a = img[i]; al = a[..., 3:4] / 255.0
        pm = np.concatenate([a[..., :3] * al, a[..., 3:4]], -1).astype(np.uint8)
        # resize premultiplied channels separately with Lanczos
        chans = [np.asarray(Image.fromarray(pm[..., c]).resize((mw, mh), Image.LANCZOS)).astype(np.float32) for c in range(4)]
        al2 = chans[3] / 255.0
        rgb = np.stack(chans[:3], -1) / np.maximum(al2[..., None], 1e-3)
        r = np.concatenate([np.clip(rgb, 0, 255), chans[3][..., None]], -1)
        save(r, f'{out}/{i}.webp')
