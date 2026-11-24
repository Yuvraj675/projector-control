"""Build the hand flipbook: detect magenta screen quads, interpolate in-betweens with optical flow,
export webp frames and src/frames.json."""
import json, os, sys, numpy as np, cv2
from PIL import Image

ROOT = os.path.dirname(os.path.abspath(__file__))  # art/raw, raw2 hold the generated originals (see README)
OUT = os.path.join(ROOT, '..', 'public', 'assets', 'hand')

SETS = {
  'portrait': {
    'size': (768, 1152), 'rest': 'hold', 'dir': 'portrait',
    'src': {'hold': 'raw/hero-hold.png', 'dpad-hover': 'raw/thumb-dpad.png', 'tap': 'raw/thumb-tap.png', 'swipe-end': 'raw/thumb-swipe.png',
            **{n: f'raw2/{n}.png' for n in ['reach-1','reach-2','dpad-up','dpad-left','dpad-right','key-low-left','key-low-right','key-mid','swipe-start','swipe-mid','tilt-left','tilt-right']}},
    'edges': [('hold','reach-1'),('reach-1','reach-2'),('reach-2','dpad-hover'),('dpad-hover','tap'),
              ('tap','dpad-up'),('tap','dpad-left'),('tap','dpad-right'),('tap','key-mid'),
              ('key-mid','key-low-left',0),('key-mid','key-low-right',0),
              ('tap','swipe-start',0),('swipe-start','swipe-mid',0),('swipe-mid','swipe-end'),
              ('hold','tilt-left'),('hold','tilt-right')],
    'moving': {'tilt-left','tilt-right'},
  },
  'landscape': {
    'size': (1152, 768), 'rest': 'land-hold', 'dir': 'landscape',
    'src': {'land-hold': 'raw2/land-hold.png', 'land-press-1': 'raw2/land-press-1.png', 'land-press-2': 'raw2/land-press-2.png'},
    'edges': [('land-hold','land-press-1'),('land-press-1','land-press-2')],
    'moving': set(),
  },
}
INBETWEEN = int(os.environ.get('INBETWEEN', 2))

def detect_quad(path):
    a = np.asarray(Image.open(path).convert('RGB')).astype(np.int32)
    m = ((np.minimum(a[...,0], a[...,2]) - a[...,1]) > 140).astype(np.uint8)
    cnts,_ = cv2.findContours(m, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    c = max(cnts, key=cv2.contourArea)
    pts = c.reshape(-1,2).astype(float)
    H,W = m.shape
    xs, ys = pts[:,0], pts[:,1]
    x0,x1,y0,y1 = xs.min(),xs.max(),ys.min(),ys.max()
    dx,dy = x1-x0, y1-y0
    sel = {
      'top': pts[(ys < y0+0.02*dy) & (xs>x0+0.2*dx) & (xs<x1-0.2*dx)],
      'bot': pts[(ys > y1-0.02*dy) & (xs>x0+0.2*dx) & (xs<x1-0.2*dx)],
      'lef': pts[(xs < x0+0.03*dx) & (ys>y0+0.2*dy) & (ys<y1-0.2*dy)],
      'rig': pts[(xs > x1-0.03*dx) & (ys>y0+0.2*dy) & (ys<y1-0.2*dy)],
    }
    def fit(p): return cv2.fitLine(p.astype(np.float32), cv2.DIST_L2, 0, 0.01, 0.01).ravel()
    L = {k: fit(v) for k,v in sel.items()}
    def inter(a,b):
        ax,ay,ax0,ay0=a; bx,by,bx0,by0=b
        M=np.array([[ax,-bx],[ay,-by]]); t=np.linalg.solve(M,[bx0-ax0,by0-ay0])
        return [ax0+t[0]*ax, ay0+t[0]*ay]
    q = [inter(L['top'],L['lef']), inter(L['top'],L['rig']), inter(L['bot'],L['rig']), inter(L['bot'],L['lef'])]
    return [[round(float(x)/W,5), round(float(y)/H,5)] for x,y in q]

def load(path, size):
    return Image.open(os.path.join(ROOT, path)).convert('RGB').resize(size, Image.LANCZOS)

def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, quality=86, method=6)

dis = cv2.DISOpticalFlow_create(cv2.DISOPTICAL_FLOW_PRESET_MEDIUM)
def warp(img, flow, t):
    h,w = flow.shape[:2]
    gx,gy = np.meshgrid(np.arange(w, dtype=np.float32), np.arange(h, dtype=np.float32))
    return cv2.remap(img, gx - flow[...,0]*t, gy - flow[...,1]*t, cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)
def soft_mask(img):
    a = img.astype(np.float32)
    return np.clip((np.minimum(a[...,0], a[...,2]) - a[...,1] - 120) / 100, 0, 1)

def interp(A, B, t, quad):
    """Screen-aware flow interpolation: magenta is 'no content' and is re-painted at the end."""
    h, w = A.shape[:2]
    MA, MB = soft_mask(A), soft_mask(B)
    FA = A.astype(np.float32) * (1 - MA[..., None]); FB = B.astype(np.float32) * (1 - MB[..., None])
    ga = np.clip(FA.max(axis=2), 0, 255).astype(np.uint8); gb = np.clip(FB.max(axis=2), 0, 255).astype(np.uint8)
    F = dis.calc(ga, gb, None)
    Aw = warp(FA, F, t); Bw = warp(FB, F, -(1 - t))
    pA = warp(1 - MA, F, t); pB = warp(1 - MB, F, -(1 - t))
    wA = pA * (1 - t); wB = pB * t
    rgb = (wA[..., None] * Aw + wB[..., None] * Bw) / np.maximum((wA + wB)[..., None], 1e-4)
    tot = np.maximum(pA, pB)
    region = np.zeros((h, w), np.uint8)
    cv2.fillConvexPoly(region, np.array([[x * w, y * h] for x, y in quad], np.int32), 1)
    region = cv2.GaussianBlur(region.astype(np.float32), (0, 0), 1.2)
    c = np.clip(tot * 1.15 - 0.05, 0, 1)
    mag = np.array([255, 0, 255], np.float32)
    cover = region * (1 - c)                      # inside the glass where nobody has content
    # drop disconnected specks of warped content that float over the glass
    content = ((1 - cover) > 0.5).astype(np.uint8)
    n, lab, stats, _ = cv2.connectedComponentsWithStats(content, connectivity=8)
    big = int(stats[1:, cv2.CC_STAT_AREA].max()) if n > 1 else 0
    for i in range(1, n):
        if stats[i, cv2.CC_STAT_AREA] < max(900, big * 0.02) and (region[lab == i].mean() > 0.5):
            cover[lab == i] = 1.0
    out = rgb * (1 - cover[..., None]) + mag * cover[..., None]
    # outside the glass nothing may be magenta-filled: keep warped content
    return np.clip(out, 0, 255).astype(np.uint8)

out = {}
for name, S in SETS.items():
    missing = [k for k,v in S['src'].items() if not os.path.exists(os.path.join(ROOT, v))]
    if missing:
        print(name, 'missing', missing); sys.exit(1)
    size = S['size']
    quads = {k: detect_quad(os.path.join(ROOT, v)) for k,v in S['src'].items()}
    rest = quads[S['rest']]
    for k,q in quads.items():
        dev = max(abs(a-b) for p,r in zip(q,rest) for a,b in zip(p,r))
        if k not in S['moving'] and dev > 0.012:
            print(f'  {k}: quad deviates {dev:.4f}, using rest quad'); quads[k] = rest
    frames = {}
    imgs = {}
    for k in S['src']:
        im = load(S['src'][k], size); imgs[k] = np.asarray(im)
        save(im, os.path.join(OUT, S['dir'], f'{k}.webp'))
        frames[k] = {'quad': quads[k]}
    edges = []
    for edge in S['edges']:
        a, b = edge[0], edge[1]
        n = edge[2] if len(edge) > 2 else INBETWEEN
        ids = [a]
        for i in range(1, n+1):
            t = i/(n+1)
            fid = f'{a}__{b}__{i}'
            save(Image.fromarray(interp(imgs[a], imgs[b], t, [[pa+(pb-pa)*t for pa,pb in zip(p,r)] for p,r in zip(quads[a], quads[b])])), os.path.join(OUT, S['dir'], f'{fid}.webp'))
            frames[fid] = {'quad': [[round(pa+(pb-pa)*t,5) for pa,pb in zip(p,r)] for p,r in zip(quads[a], quads[b])]}
            ids.append(fid)
        ids.append(b)
        edges.append({'a': a, 'b': b, 'frames': ids})
    out[name] = {'size': list(size), 'rest': S['rest'], 'dir': S['dir'], 'frames': frames, 'edges': edges}
    print(name, len(frames), 'frames', {k: v['quad'][0] for k,v in list(frames.items())[:2]})
json.dump(out, open(os.path.join(ROOT, '..', 'src', 'frames.json'), 'w'))
