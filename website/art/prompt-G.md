Generate photoreal images with your image generation tool, strictly one at a time, sequentially. Do NOT write code, do NOT ask questions.

REFERENCE FRAMES (same hand, sleeve, phone, lighting, pure black background): /home/yuvraj/Documents/Code_Projects/projector-redesign/AutoKeystone/website/art/raw/*.png, /home/yuvraj/Documents/Code_Projects/projector-redesign/AutoKeystone/website/art/raw2/*.png and /home/yuvraj/Documents/Code_Projects/projector-redesign/AutoKeystone/website/art/raw3/*.png (hero-hold, thumb-dpad, thumb-tap, thumb-swipe, reach-1, reach-2, dpad-up, dpad-left, dpad-right, key-low-left, key-low-right, key-mid, swipe-start, swipe-mid, tilt-left, tilt-right, sw-a, sw-b, sw-c, sw-d, kl-1, kr-1, up-1, left-1, right-1, tl-1, tr-1, lift). They are keyframes of a thumb animation. Each new image is the EXACT MIDPOINT pose between the two named neighbours (thumb position, bend, finger-pad flattening, nail angle and its shadow all halfway), made as an EDIT of the nearer reference: keep sleeve, fingers on the right edge, phone, flat magenta #FF00FF screen, rim lighting, pure #000000 background, framing, phone size and phone position IDENTICAL (pixel-registered; the phone must not move at all), and change ONLY the thumb. The thumb must be anatomically continuous with both neighbours: same skin, same nail, no duplicate thumb, no floating or ghosted parts, clean edges against the magenta screen with no pink or purple fringe. Output 1024x1536 portrait. The screen is flat uniform magenta with no UI and no glare, hidden only where the thumb covers it.

Save into /home/yuvraj/Documents/Code_Projects/projector-redesign/AutoKeystone/website/art/raw4/ with exactly these names (midpoint of A and B):
1. m-hold-reach1.png  (hero-hold and reach-1)
2. m-reach1-reach2.png  (reach-1 and reach-2)
3. m-reach2-hover.png  (reach-2 and thumb-dpad)
4. m-tap-right1.png  (thumb-tap and right-1)
5. m-right1-dpadright.png  (right-1 and dpad-right)
6. m-up1-dpadup.png  (up-1 and dpad-up)
7. m-left1-dpadleft.png  (left-1 and dpad-left)
8. m-swstart-swc.png  (swipe-start and sw-c)
9. m-swd-swipeend.png  (sw-d and thumb-swipe)
10. m-kl1-keylowleft.png  (kl-1 and key-low-left)
11. m-kr1-keylowright.png  (kr-1 and key-low-right)
12. m-tap-keymid.png  (thumb-tap and key-mid)
Print the saved paths when done.
