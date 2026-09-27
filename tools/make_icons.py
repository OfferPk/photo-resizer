#!/usr/bin/env python3
"""Generates the launcher icons (adaptive + legacy) and the 512px Play Store icon.

Original artwork: a photo card with a "KB" badge. Drawn procedurally with Pillow
on a 108x108 "dp" design grid (the adaptive-icon canvas), so every density is
rendered from the same geometry. Run from the repo root:  python3 tools/make_icons.py
"""
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
for cand in ["/usr/share/fonts/truetype/sand-box/google/Roboto/Roboto-VariableFont_wdth,wght.ttf"]:
    pass

BG_TOP = (21, 101, 192)
BG_BOTTOM = (8, 52, 128)
SS = 4  # supersampling


def gradient(size, top, bottom):
    w, h = size
    img = Image.new("RGBA", size)
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = (x + y) / (w + h - 2)
            px[x, y] = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,)
    return img


def background(px):
    return gradient((px, px), BG_TOP, BG_BOTTOM)


def foreground(px, mono=False):
    """Foreground layer on a 108-unit grid rendered to px*px."""
    S = px * SS / 108.0
    img = Image.new("RGBA", (px * SS, px * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    u = lambda *v: [c * S for c in v]
    white = (255, 255, 255, 255)
    # soft shadow for card
    if not mono:
        sh = Image.new("RGBA", img.size, (0, 0, 0, 0))
        ImageDraw.Draw(sh).rounded_rectangle(u(25, 30.5, 67, 70.5), radius=6 * S, fill=(0, 0, 0, 90))
        sh = sh.filter(ImageFilter.GaussianBlur(2.2 * S))
        img.alpha_composite(sh)
    # photo card
    d.rounded_rectangle(u(24, 28, 66, 68), radius=6 * S, fill=white)
    if mono:
        # cut-outs so the themed icon still reads as a photo
        d.rounded_rectangle(u(28, 32, 62, 64), radius=3 * S, fill=(0, 0, 0, 0))
        d.polygon(u(28, 64, 39, 47, 46, 56, 52, 50, 62, 64), fill=white)
        d.ellipse(u(51, 36, 58, 43), fill=white)
    else:
        d.rounded_rectangle(u(28, 32, 62, 64), radius=3 * S, fill=(220, 235, 255, 255))
        d.ellipse(u(51, 36, 58, 43), fill=(255, 179, 0, 255))
        d.polygon(u(28, 64, 39, 47, 46, 56, 52, 50, 62, 64), fill=(30, 136, 229, 255))
        d.polygon(u(28, 64, 39, 47, 44, 54.5, 36, 64), fill=(13, 71, 161, 255))
    # KB badge
    badge = u(50, 58, 80, 78)
    if not mono:
        sh = Image.new("RGBA", img.size, (0, 0, 0, 0))
        ImageDraw.Draw(sh).rounded_rectangle(u(50.8, 59.3, 80.8, 79.3), radius=9 * S, fill=(0, 0, 0, 110))
        sh = sh.filter(ImageFilter.GaussianBlur(1.8 * S))
        img.alpha_composite(sh)
        d = ImageDraw.Draw(img)
    d.rounded_rectangle(badge, radius=9 * S, fill=(255, 255, 255, 255) if mono else (255, 122, 0, 255))
    font = ImageFont.truetype(FONT, int(12.5 * S))
    txt = "KB"
    bb = d.textbbox((0, 0), txt, font=font)
    tw, th = bb[2] - bb[0], bb[3] - bb[1]
    cx, cy = 65 * S, 68 * S
    d.text((cx - tw / 2 - bb[0], cy - th / 2 - bb[1]), txt, font=font,
           fill=(0, 0, 0, 0) if mono else white)
    return img.resize((px, px), Image.LANCZOS)


def compose(px, content_units=108):
    """Full icon (bg + fg). content_units < 108 zooms into the centre."""
    big = int(px * 108 / content_units)
    bg = background(big)
    bg.alpha_composite(foreground(big))
    off = (big - px) // 2
    return bg.crop((off, off, off + px, off + px))


def circle_mask(img):
    m = Image.new("L", (img.width * SS, img.height * SS), 0)
    ImageDraw.Draw(m).ellipse((0, 0, m.width - 1, m.height - 1), fill=255)
    m = m.resize(img.size, Image.LANCZOS)
    out = img.copy()
    out.putalpha(m)
    return out


def rounded_mask(img, r):
    m = Image.new("L", (img.width * SS, img.height * SS), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, m.width - 1, m.height - 1), radius=r * SS, fill=255)
    m = m.resize(img.size, Image.LANCZOS)
    out = img.copy()
    out.putalpha(m)
    return out


def main():
    dens = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
    for name, f in dens.items():
        d = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(d, exist_ok=True)
        a = int(108 * f)
        background(a).save(os.path.join(d, "ic_launcher_background.png"))
        foreground(a).save(os.path.join(d, "ic_launcher_foreground.png"))
        foreground(a, mono=True).save(os.path.join(d, "ic_launcher_monochrome.png"))
        l = int(48 * f)
        full = compose(l, content_units=72)
        rounded_mask(full, l * 0.18).save(os.path.join(d, "ic_launcher.png"))
        circle_mask(full).save(os.path.join(d, "ic_launcher_round.png"))
    any_dir = os.path.join(RES, "mipmap-anydpi-v26")
    os.makedirs(any_dir, exist_ok=True)
    xml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
"""
    for n in ("ic_launcher.xml", "ic_launcher_round.xml"):
        open(os.path.join(any_dir, n), "w").write(xml)
    store = os.path.join(ROOT, "store")
    os.makedirs(store, exist_ok=True)
    compose(512, content_units=76).convert("RGB").save(os.path.join(store, "icon-512.png"))
    print("icons written")


if __name__ == "__main__":
    main()
