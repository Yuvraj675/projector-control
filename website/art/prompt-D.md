Generate photoreal images with your image generation tool, strictly one at a time, sequentially. Do NOT write code, do NOT ask questions.

REFERENCE FRAMES (same hand, sleeve, phone, lighting, pure black background): all PNGs in /home/yuvraj/Documents/Code_Projects/projector-redesign/AutoKeystone/website/art/raw/ (hero-hold, thumb-dpad, thumb-tap, thumb-swipe) and /home/yuvraj/Documents/Code_Projects/projector-redesign/AutoKeystone/website/art/raw2/ (reach-1, reach-2, dpad-up, dpad-left, dpad-right, key-low-left, key-low-right, key-mid, swipe-start, swipe-mid, tilt-left, tilt-right). They are the keyframes of a thumb animation. We need extra in-between poses so that consecutive frames differ only by a SMALL thumb movement (about 1/3 of the distance between neighbours). Each new image is an EDIT of the nearest reference: keep sleeve, fingers on the right edge, phone, flat magenta #FF00FF screen, rim lighting, pure #000000 background, framing, phone size and phone position IDENTICAL (pixel-registered, the phone must not move at all, except the two roll frames) and change ONLY the thumb (shape, bend, position, and its shadow). The thumb must look anatomically continuous with its neighbours: same skin, same nail, no extra fingers, no duplicate thumb, no floating parts. Output 1024x1536 portrait. The screen stays flat uniform magenta with no UI and no glare, hidden only where the thumb covers it.

Save into /home/yuvraj/Documents/Code_Projects/projector-redesign/AutoKeystone/website/art/raw3/ with exactly these names. Pose = halfway-ish between the two named neighbours:
1. sw-a.png  (between thumb-tap and swipe-start: thumb lifted about 5 mm above the glass, sliding from the tap spot toward the centre, hovering)
2. sw-b.png  (between sw-a and swipe-start: thumb just about to land at the swipe-start spot, hovering 2 mm above the glass)
3. sw-c.png  (between swipe-start and swipe-mid: thumb touching the glass about 45% up the screen, centred)
4. sw-d.png  (between swipe-mid and thumb-swipe: thumb touching the glass about 62% up the screen)
5. kl-1.png  (between key-mid and key-low-left: thumb pressing at about 26% up, 44% across)
6. kr-1.png  (between key-mid and key-low-right: thumb pressing at about 26% up, 56% across)
7. up-1.png  (between thumb-tap and dpad-up: thumb pressing about 52% up, centred horizontally)
8. left-1.png  (between thumb-tap and dpad-left: thumb pressing about 45% across, 40% up)
9. right-1.png  (between thumb-tap and dpad-right: thumb pressing about 78% across, 40% up)
10. tl-1.png  (between hero-hold and tilt-left: same hold but rolled about 5 degrees counter-clockwise)
11. tr-1.png  (between hero-hold and tilt-right: same hold but rolled about 5 degrees clockwise)
12. lift.png  (between thumb-tap and thumb-dpad: thumb just released from the glass, hovering 3 mm above the same spot)
Print the saved paths when done.
