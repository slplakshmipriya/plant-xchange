#!/usr/bin/env python3
"""Regenerate the Gardenia legacy launcher PNGs (API 23-25).

The adaptive icon (API 26+) uses res/drawable/ic_gardenia.xml directly.
These PNGs exist only for pre-26 launchers. They are binary, so they
cannot be pushed through the GitHub connector (text-only) -- run this
script once after pulling the branch, then commit the regenerated PNGs.

Requires: pip install pillow
"""
import math
from PIL import Image, ImageDraw

BG = (4, 120, 87, 255)        # brand_icon_bg #047857
PETAL = (255, 255, 255, 255)
PETAL_LINE = (207, 224, 204, 255)
CENTER = (242, 193, 78, 255)
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def gardenia(size, background):
    ss = 4
    s = size * ss
    img = Image.new("RGBA", (s, s), background)
    d = ImageDraw.Draw(img)
    cx = cy = s / 2
    r = s * 0.30
    for ring, count, scale in ((1.0, 6, 1.0), (0.62, 5, 0.72)):
        for i in range(count):
            a = 2 * math.pi * i / count + ring
            px, py = cx + math.cos(a) * r * 0.42 * ring, cy + math.sin(a) * r * 0.42 * ring
            w, h = r * scale, r * 0.58 * scale
            petal = Image.new("RGBA", (int(w * 2), int(h * 2)), (0, 0, 0, 0))
            pd = ImageDraw.Draw(petal)
            pd.ellipse([0, 0, petal.width - 1, petal.height - 1], fill=PETAL, outline=PETAL_LINE, width=ss)
            petal = petal.rotate(-math.degrees(a), expand=True, resample=Image.BICUBIC)
            img.alpha_composite(petal, (int(px - petal.width / 2), int(py - petal.height / 2)))
    d = ImageDraw.Draw(img)
    cr = r * 0.20
    d.ellipse([cx - cr, cy - cr, cx + cr, cy + cr], fill=CENTER)
    return img.resize((size, size), Image.LANCZOS)


for density, px in SIZES.items():
    folder = f"app/src/main/res/mipmap-{density}"
    gardenia(px, BG).convert("RGB").save(f"{folder}/ic_launcher.png")
    gardenia(px, (0, 0, 0, 0)).save(f"{folder}/ic_launcher_foreground.png")
    print("wrote", folder, px)
