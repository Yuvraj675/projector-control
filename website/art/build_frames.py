"""Build the hand flipbook.

1. read every keyframe (generated with Codex, see prompt-*.md) from raw/, raw2/, raw3/
2. detect the flat-magenta screen quad of EVERY frame (rotation-safe)
3. make in-betweens with RIFE (rife-ncnn-vulkan, the engine Flowframes uses) between neighbouring keyframes
4. export webp frames to public/assets/hand and the pose graph to src/frames.json

Needs: numpy, opencv-python, Pillow and the rife-ncnn-vulkan binary in art/tools (see README).
"""
import json, os, subprocess, sys, shutil, numpy as np, cv2
from PIL import Image

ROOT = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(ROOT, '..', 'public', 'assets', 'hand')
RIFE_DIR = os.path.join(ROOT, 'tools', 'rife-ncnn-vulkan-20221029-ubuntu')
RIFE = os.path.join(RIFE_DIR, 'rife-ncnn-vulkan')
MODEL = os.path.join(RIFE_DIR, 'rife-v4.6')
STEPS = [0.25, 0.5, 0.75]          # in-betweens per edge
CACHE = os.path.join(ROOT, 'cache')  # raw RIFE output, reused between runs

P = {'hold': 'raw/hero-hold', 'dpad-hover': 'raw/thumb-dpad', 'tap': 'raw/thumb-tap', 'swipe-end': 'raw/thumb-swipe'}
for n in ['reach-1','reach-2','dpad-up','dpad-left','dpad-right','key-low-left','key-low-right','key-mid','swipe-start','swipe-mid','tilt-left','tilt-right']:
    P[n] = f'raw2/{n}'
for n in ['sw-a','sw-b','sw-c','sw-d','kl-1','kr-1','up-1','left-1','right-1','tl-1','tr-1','lift']:
    P[n] = f'raw3/{n}'

SETS = {
  'portrait': {
    'size': (768, 1152), 'rest': 'hold', 'dir': 'portrait', 'src': {k: v + '.png' for k, v in P.items()},
    'edges': [('hold','reach-1'),('reach-1','reach-2'),('reach-2','dpad-hover'),('dpad-hover','lift'),('lift','tap'),
              ('tap','up-1'),('up-1','dpad-up'),('tap','left-1'),('left-1','dpad-left'),('tap','right-1'),('right-1','dpad-right'),
              ('tap','key-mid'),('key-mid','kl-1'),('kl-1','key-low-left'),('key-mid','kr-1'),('kr-1','key-low-right'),
              ('tap','sw-a'),('sw-a','sw-b'),('sw-b','swipe-start'),('swipe-start','sw-c'),('sw-c','swipe-mid'),('swipe-mid','sw-d'),('sw-d','swipe-end'),
              ('hold','tl-1'),('tl-1','tilt-left'),('hold','tr-1'),('tr-1','tilt-right')],
  },
  'landscape': {
    'size': (1152, 768), 'rest': 'land-hold', 'dir': 'landscape',
    'src': {'land-hold': 'raw2/land-hold.png', 'land-press-1': 'raw2/land-press-1.png', 'land-press-2': 'raw2/land-press-2.png'},
    'edges': [('land-hold','land-press-1'),('land-press-1','land-press-2')],
  },
}

def magenta_mask(a):
    a = a.astype(np.int32)
    return ((np.minimum(a[..., 0], a[..., 2]) - a[..., 1]) > 120).astype(np.uint8)

def detect_quad(img):
    """Quad of the magenta screen in normalised coords [TL,TR,BR,BL]; robust to rotation and rounded corners."""
    m = magenta_mask(np.asarray(img.convert('RGB')))
    m = cv2.morphologyEx(m, cv2.MORPH_OPEN, np.ones((5, 5), np.uint8))
    cnts, _ = cv2.findContours(m, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    c = max(cnts, key=cv2.contourArea)
    pts = c.reshape(-1, 2).astype(np.float64)
    H, W = m.shape
    box = cv2.boxPoints(cv2.minAreaRect(c))              # 4 corners of the best rotated rectangle
    # order TL, TR, BR, BL for an upright-ish rectangle
    s = box.sum(1); d = box[:, 0] - box[:, 1]
    box = np.array([box[s.argmin()], box[d.argmax()], box[s.argmax()], box[d.argmin()]])
    lines = []
    for i in range(4):
        a, b = box[i], box[(i + 1) % 4]
        e = b - a; L = np.linalg.norm(e); u = e / L; nrm = np.array([-u[1], u[0]])
        rel = pts - a
        t = rel @ u; dist = np.abs(rel @ nrm)
        sel = pts[(t > 0.2 * L) & (t < 0.8 * L) & (dist < 0.025 * L + 3)]
        if len(sel) < 6: sel = pts[(t > 0.1 * L) & (t < 0.9 * L) & (dist < 0.05 * L + 4)]
        lines.append(cv2.fitLine(sel.astype(np.float32), cv2.DIST_L2, 0, 0.01, 0.01).ravel())
    def inter(p, q):
        ax, ay, ax0, ay0 = p; bx, by, bx0, by0 = q
        t = np.linalg.solve(np.array([[ax, -bx], [ay, -by]]), [bx0 - ax0, by0 - ay0])
        return [ax0 + t[0] * ax, ay0 + t[0] * ay]
    q = [inter(lines[3], lines[0]), inter(lines[0], lines[1]), inter(lines[1], lines[2]), inter(lines[2], lines[3])]
    return [[round(float(x) / W, 5), round(float(y) / H, 5)] for x, y in q]

def clean_magenta(arr, quad):
    """Snap near-magenta pixels inside the glass to pure magenta so the shader key stays crisp."""
    h, w = arr.shape[:2]
    poly = np.zeros((h, w), np.uint8)
    cv2.fillConvexPoly(poly, np.array([[x * w, y * h] for x, y in quad], np.int32), 1)
    poly = cv2.erode(poly, np.ones((5, 5), np.uint8))
    a = arr.astype(np.int32)
    mag = ((np.minimum(a[..., 0], a[..., 2]) - a[..., 1]) > 110) & (poly > 0)
    out = arr.copy(); out[mag] = (255, 0, 255)
    return out

def rife(a, b, step, out):
    os.makedirs(os.path.dirname(out), exist_ok=True)
    if os.path.exists(out): return
    subprocess.run([RIFE, '-0', a, '-1', b, '-o', out, '-s', str(step), '-m', MODEL], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

def save(arr, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(arr).save(path, quality=88, method=6)

if not os.path.exists(RIFE):
    sys.exit(f'rife-ncnn-vulkan not found at {RIFE}')
shutil.rmtree(OUT, ignore_errors=True)
out = {}
for name, S in SETS.items():
    size = S['size']
    missing = [k for k, v in S['src'].items() if not os.path.exists(os.path.join(ROOT, v))]
    if missing:
        sys.exit(f'{name}: missing {missing}')
    frames, edges, srcs = {}, [], {k: os.path.join(ROOT, v) for k, v in S['src'].items()}
    for k, path in srcs.items():
        im = Image.open(path).convert('RGB')
        frames[k] = {'quad': detect_quad(im)}
        save(np.asarray(im.resize(size, Image.LANCZOS)), os.path.join(OUT, S['dir'], f'{k}.webp'))
    for a, b in S['edges']:
        ids = [a]
        for i, step in enumerate(STEPS, 1):
            fid = f'{a}__{b}__{i}'
            raw = os.path.join(CACHE, S['dir'], f'{fid}.png')
            rife(srcs[a], srcs[b], step, raw)
            im = Image.open(raw).convert('RGB')
            q = detect_quad(im)
            arr = clean_magenta(np.asarray(im.resize(size, Image.LANCZOS)), q)
            save(arr, os.path.join(OUT, S['dir'], f'{fid}.webp'))
            frames[fid] = {'quad': q}
            ids.append(fid)
        ids.append(b)
        edges.append({'a': a, 'b': b, 'frames': ids})
        print(name, a, '->', b, flush=True)
    out[name] = {'size': list(size), 'rest': S['rest'], 'dir': S['dir'], 'frames': frames, 'edges': edges}
json.dump(out, open(os.path.join(ROOT, '..', 'src', 'frames.json'), 'w'))
print('frames:', {k: len(v['frames']) for k, v in out.items()})
