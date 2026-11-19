# AutoKeystone

An Android app that fixes the keystone (trapezium) of a Hisilicon-based Android projector using only your phone.

> Status: work in progress. See [CLAUDE.md](CLAUDE.md) for the engineering notes.

## Idea

Some budget projectors (Newlink / Zhiying firmware on a Hisilicon Hi3751 board, e.g. "HY300-style") advertise an
*auto keystone* feature but ship without the camera / tilt sensor it needs. This app replaces the missing sensors with
your phone:

1. It finds the projector on your Wi-Fi (ADB over the network) and connects with one tap.
2. It shows a test pattern (four markers) on the projector.
3. You take a few photos of the wall, guided by a live level indicator.
4. It measures the marker positions, removes the phone's own perspective using the phone's gravity sensor and camera
   intrinsics, solves for the four corner offsets that make the picture a clean rectangle, and applies them.
5. You say whether it looks right. The app learns from your answer.

The app is independent of any computer: the ADB client is built in.
