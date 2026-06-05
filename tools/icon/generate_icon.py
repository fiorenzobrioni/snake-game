#!/usr/bin/env python3
"""Rasterise the launcher icon into PNG density buckets.

The app ships an adaptive icon (`mipmap-anydpi-v26`) for API 26+, but had no
bitmap fallback, so on API 24-25 (and on launchers that won't render a vector
`mipmap-anydpi` icon) the icon could fail to appear. This renders square and
round PNG launcher icons for every density, faithfully reproducing the adaptive
icon's design (dark tile + green snake + gold food). Pillow only.

Run from anywhere:

    python3 tools/icon/generate_icon.py
"""

from __future__ import annotations

import os

from PIL import Image, ImageDraw

RES = os.path.normpath(
    os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "res")
)

# Design in the 108x108 viewport of the adaptive icon, as (cx, cy, r, color).
BG = (16, 20, 24, 255)  # #101418
BODY = (63, 163, 77, 255)  # #3FA34D
HEAD = (124, 252, 0, 255)  # #7CFC00
EYE = (16, 20, 24, 255)
FOOD = (255, 193, 7, 255)  # #FFC107

CIRCLES = [
    (38, 76, 9, BODY),
    (52, 74, 9, BODY),
    (64, 66, 9, BODY),
    (66, 52, 9, BODY),
    (58, 42, 9, BODY),
    (46, 32, 12, HEAD),
    (50, 29, 3, EYE),
    (74, 40, 7, FOOD),
]

# Launcher icon baseline is 48dp; px per density bucket.
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

# Render at 4x then downsample for crisp anti-aliased edges.
SS = 4
VIEW = 108.0


def render(size: int, round_icon: bool) -> Image.Image:
    big = size * SS
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    scale = big / VIEW

    if round_icon:
        draw.ellipse([0, 0, big - 1, big - 1], fill=BG)
        mask = Image.new("L", (big, big), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, big - 1, big - 1], fill=255)
    else:
        draw.rounded_rectangle([0, 0, big - 1, big - 1], radius=big * 0.12, fill=BG)
        mask = None

    for cx, cy, r, color in CIRCLES:
        x, y, rr = cx * scale, cy * scale, r * scale
        draw.ellipse([x - rr, y - rr, x + rr, y + rr], fill=color)

    if mask is not None:
        img.putalpha(mask)
    return img.resize((size, size), Image.LANCZOS)


def main() -> None:
    for density, size in DENSITIES.items():
        out_dir = os.path.join(RES, f"mipmap-{density}")
        os.makedirs(out_dir, exist_ok=True)
        render(size, round_icon=False).save(os.path.join(out_dir, "ic_launcher.png"))
        render(size, round_icon=True).save(os.path.join(out_dir, "ic_launcher_round.png"))
        print(f"  wrote mipmap-{density}/ic_launcher(.|_round.)png  ({size}px)")
    print("Done.")


if __name__ == "__main__":
    main()
