"""Builds the 'Type & speak' phone screen from the REAL app screenshot (no generated image, so it can't be stretched):
 - types a title into the text box
 - trims the card under the toggle
 - draws a dark Gboard-style keyboard at exact proportions
Writes public/assets/ui/keyboard.webp and src/keyboard-layout.json (key rectangles, for tapping keys on the phone)."""
import json, os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(ROOT, 'ref', '26_keyboard.png')
W, H = 1233, 2673
BG, CARD = (11, 11, 13), (21, 21, 23)
KEY, KEY2, ORANGE = (47, 49, 56), (36, 38, 44), (255, 107, 61)
FONT = '/usr/share/fonts/google-noto-vf/NotoSans[wght].ttf'

def font(size, wght=400):
    f = ImageFont.truetype(FONT, size)
    try: f.set_variation_by_axes([100, wght])   # [wdth, wght]
    except Exception:
        try: f.set_variation_by_axes([wght])
        except Exception: pass
    return f

im = Image.open(SRC).convert('RGB')
d = ImageDraw.Draw(im)

# 1. trim the card right under the 'Type as I go' row (rounded bottom corners), keep the original row
orig = Image.open(SRC).convert('RGB')
d.rectangle((0, 925, W, H), fill=BG)
d.rounded_rectangle((72, 730, 1161, 946), radius=64, fill=CARD)
im.paste(orig.crop((72, 730, 1161, 925)), (72, 730))
d.rectangle((0, 946, W, H), fill=BG)

# 2. typed text in the box (placeholder removed)
d.rectangle((140, 430, 1090, 700), fill=CARD)
f = font(58, 330)
text = 'Interstellar'
d.text((180, 452), text, font=f, fill=(232, 232, 238))
tw = d.textlength(text, font=f)
d.rectangle((int(180 + tw + 10), 458, int(180 + tw + 16), 524), fill=ORANGE)

# 3. keyboard
KY = 1500
d.rectangle((0, KY, W, H), fill=(22, 23, 27))
# suggestion bar
sug = ['Interstellar', 'Interstate', 'Internet']
fs = font(48, 400)
for i, s in enumerate(sug):
    cx = W * (2 * i + 1) / 6
    d.text((cx, KY + 62), s, font=fs, fill=(228, 228, 234), anchor='mm')
    if i: d.line((W * i / 3, KY + 28, W * i / 3, KY + 96), fill=(70, 72, 80), width=3)

PAD, GAP, KH = 22, 14, 148
rows = ['1234567890', 'QWERTYUIOP', 'ASDFGHJKL', 'ZXCVBNM']
kw = (W - 2 * PAD - 9 * GAP) / 10
fk = font(60, 400)
layout = []
y = KY + 134
def key(x, y, w, h, label=None, fill=KEY, color=(240, 240, 245), fnt=fk):
    d.rounded_rectangle((x, y, x + w, y + h), radius=30, fill=fill)
    if label: d.text((x + w / 2, y + h / 2 + 2), label, font=fnt, fill=color, anchor='mm')
for r, letters in enumerate(rows):
    n = len(letters)
    if r == 2: x0 = PAD + (kw + GAP) / 2
    elif r == 3: x0 = PAD + kw * 1.5 + GAP
    else: x0 = PAD
    for i, ch in enumerate(letters):
        x = x0 + i * (kw + GAP)
        key(x, y, kw, KH, ch, fill=KEY2 if r == 0 else KEY)
        layout.append({'k': ch, 'u': round((x + kw / 2) / W, 4), 'v': round((y + KH / 2) / H, 4), 'w': round(kw / W, 4), 'h': round(KH / H, 4)})
    if r == 3:                                       # shift and backspace
        sw = kw * 1.5
        key(PAD, y, sw, KH, fill=KEY2)
        d.polygon([(PAD + sw / 2, y + 44), (PAD + sw / 2 + 28, y + 80), (PAD + sw / 2 + 12, y + 80), (PAD + sw / 2 + 12, y + 108), (PAD + sw / 2 - 12, y + 108), (PAD + sw / 2 - 12, y + 80), (PAD + sw / 2 - 28, y + 80)], outline=(240, 240, 245), width=5)
        bx = W - PAD - sw
        key(bx, y, sw, KH, fill=KEY2)
        cx, cy = bx + sw / 2, y + KH / 2
        d.polygon([(cx - 34, cy), (cx - 8, cy - 28), (cx + 34, cy - 28), (cx + 34, cy + 28), (cx - 8, cy + 28)], outline=(240, 240, 245), width=5)
        d.line((cx, cy - 12, cx + 20, cy + 12), fill=(240, 240, 245), width=5); d.line((cx + 20, cy - 12, cx, cy + 12), fill=(240, 240, 245), width=5)
        layout.append({'k': 'BACKSPACE', 'u': round(cx / W, 4), 'v': round(cy / H, 4), 'w': round(sw / W, 4), 'h': round(KH / H, 4)})
    y += KH + 22
# bottom row
sym_w, comma_w, enter_w, dot_w = kw * 1.5, kw, kw * 2, kw
space_w = W - 2 * PAD - sym_w - comma_w - dot_w - enter_w - 4 * GAP
x = PAD
key(x, y, sym_w, KH, '?123', fill=KEY2, fnt=font(46, 500)); x += sym_w + GAP
key(x, y, comma_w, KH, ',', fill=KEY2); x += comma_w + GAP
key(x, y, space_w, KH, None); layout.append({'k': ' ', 'u': round((x + space_w / 2) / W, 4), 'v': round((y + KH / 2) / H, 4), 'w': round(space_w / W, 4), 'h': round(KH / H, 4)}); x += space_w + GAP
key(x, y, dot_w, KH, '.', fill=KEY2); x += dot_w + GAP
key(x, y, enter_w, KH, None, fill=ORANGE)
ex, ey = x + enter_w / 2, y + KH / 2
d.line((ex + 26, ey - 26, ex + 26, ey + 4, ex - 30, ey + 4), fill=(20, 8, 2), width=8, joint='curve')
d.line((ex - 30, ey + 4, ex - 6, ey - 20), fill=(20, 8, 2), width=8); d.line((ex - 30, ey + 4, ex - 6, ey + 28), fill=(20, 8, 2), width=8)
layout.append({'k': 'ENTER', 'u': round(ex / W, 4), 'v': round(ey / H, 4), 'w': round(enter_w / W, 4), 'h': round(KH / H, 4)})
# gesture bar
d.rounded_rectangle((W / 2 - 150, H - 60, W / 2 + 150, H - 48), radius=6, fill=(200, 200, 205))

out = os.path.join(ROOT, '..', 'public', 'assets', 'ui', 'keyboard.webp')
im.resize((720, int(720 * H / W)), Image.LANCZOS).save(out, quality=92)
im.save(os.path.join(ROOT, 'screen-type-built.png'))
json.dump({'keys': layout, 'w': W, 'h': H}, open(os.path.join(ROOT, '..', 'src', 'keyboard-layout.json'), 'w'))
print('ok', len(layout), 'keys')
