#!/usr/bin/env python3
"""Builds store/feature-graphic-1024x500.png and captioned 1080x1920 screenshots
from the real UI renders in store/screenshots/raw (produced by StoreScreenshots)."""
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STORE = os.path.join(ROOT, "store")
RAW = os.path.join(STORE, "screenshots", "raw")
P = "/usr/share/fonts/truetype/sand-box/google/Poppins/"
BOLD = P + "Poppins-Bold.ttf"
SEMI = P + "Poppins-SemiBold.ttf"
MED = P + "Poppins-Medium.ttf"
TOP, BOTTOM = (21, 101, 192), (7, 45, 112)
ORANGE = (255, 122, 0)


def gradient(w, h, a=TOP, b=BOTTOM):
    g = Image.new("RGB", (w, h))
    px = g.load()
    for y in range(h):
        for x in range(w):
            t = min(1.0, (0.35 * x / w + 0.65 * y / h))
            px[x, y] = tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))
    return g


def rounded(img, r):
    m = Image.new("L", (img.width * 3, img.height * 3), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, m.width - 1, m.height - 1), radius=r * 3, fill=255)
    m = m.resize(img.size, Image.LANCZOS)
    out = img.convert("RGBA")
    out.putalpha(m)
    return out


def paste_shadowed(canvas, img, xy, r, blur=28, off=16, alpha=110):
    sh = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle((xy[0], xy[1] + off, xy[0] + img.width, xy[1] + img.height + off), radius=r, fill=(0, 0, 0, alpha))
    sh = sh.filter(ImageFilter.GaussianBlur(blur))
    canvas.alpha_composite(sh)
    canvas.alpha_composite(img, xy)


def phone(shot, width):
    """Screenshot inside a simple dark device frame."""
    s = Image.open(shot).convert("RGB")
    h = int(s.height * width / s.width)
    s = s.resize((width, h), Image.LANCZOS)
    bezel = max(10, width // 40)
    frame = Image.new("RGBA", (width + 2 * bezel, h + 2 * bezel), (0, 0, 0, 0))
    ImageDraw.Draw(frame).rounded_rectangle((0, 0, frame.width - 1, frame.height - 1), radius=bezel * 4, fill=(20, 24, 30, 255))
    frame.alpha_composite(rounded(s, bezel * 3), (bezel, bezel))
    return frame


def captioned(src, dst, title, sub):
    W, H = 1080, 1920
    c = gradient(W, H).convert("RGBA")
    d = ImageDraw.Draw(c)
    ft = ImageFont.truetype(BOLD, 76)
    fs = ImageFont.truetype(MED, 42)
    y = 80
    for line in title.split("\n"):
        w = d.textlength(line, font=ft)
        d.text(((W - w) / 2, y), line, font=ft, fill="white")
        y += 96
    w = d.textlength(sub, font=fs)
    d.text(((W - w) / 2, y + 10), sub, font=fs, fill=(255, 214, 170))
    ph = phone(src, 820)
    paste_shadowed(c, ph, ((W - ph.width) // 2, 395), r=40)
    c.convert("RGB").save(dst, optimize=True)


def feature_graphic():
    W, H = 1024, 500
    c = gradient(W, H).convert("RGBA")
    d = ImageDraw.Draw(c)
    # soft decorative circles
    deco = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dd = ImageDraw.Draw(deco)
    dd.ellipse((600, -220, 1200, 380), fill=(255, 255, 255, 18))
    dd.ellipse((-160, 300, 260, 720), fill=(255, 255, 255, 12))
    c.alpha_composite(deco)
    icon = rounded(Image.open(os.path.join(STORE, "icon-512.png")).resize((132, 132), Image.LANCZOS), 30)
    paste_shadowed(c, icon, (56, 64), r=30, blur=14, off=8, alpha=90)
    d.text((56, 214), "Photo Resizer", font=ImageFont.truetype(BOLD, 60), fill="white")
    d.text((58, 292), "KB · Passport · Signature", font=ImageFont.truetype(SEMI, 32), fill=(255, 214, 170))
    y = 356
    for t in ("Compress to exact KB", "Passport & ID photos + 4×6 sheet", "100% offline – photos stay on your phone"):
        d.ellipse((60, y + 12, 72, y + 24), fill=ORANGE)
        d.text((86, y), t, font=ImageFont.truetype(MED, 24), fill="white")
        y += 38
    # two real screens on the right, cropped by the bottom edge
    p1 = phone(os.path.join(RAW, "03-compress-result.png"), 250)
    p2 = phone(os.path.join(RAW, "05-passport-sheet.png"), 250)
    paste_shadowed(c, p2, (752, 70), r=26, blur=18, off=10)
    paste_shadowed(c, p1, (590, 40), r=26, blur=18, off=10)
    c.convert("RGB").save(os.path.join(STORE, "feature-graphic-1024x500.png"), optimize=True)


CAPTIONS = [
    ("03-compress-result", "Hit any KB limit", "3.8 MB photo to under 50 KB"),
    ("04-passport", "Passport & ID photos", "35×45 mm · 2×2 in · custom · 300 DPI"),
    ("05-passport-sheet", "Print-ready 4×6 sheet", "8 copies, cut guides, 300 DPI"),
    ("07-signature-result", "Clean signature", "Black-on-white or transparent PNG"),
    ("01-home", "4 tools, one app", "Compress · Resize · Passport · Signature"),
    ("08-resize", "Resize your way", "Pixels, percent or presets"),
    ("06-signature", "Crop & clean in seconds", "Threshold slider removes shadows"),
    ("09-about", "100% offline", "Your photos never leave your phone"),
]

if __name__ == "__main__":
    feature_graphic()
    for i, (name, title, sub) in enumerate(CAPTIONS, 1):
        slug = title.lower().replace("&", "and").replace("×", "x")
        slug = "".join(ch if ch.isalnum() else "-" for ch in slug).strip("-")
        while "--" in slug: slug = slug.replace("--", "-")
        captioned(os.path.join(RAW, name + ".png"), os.path.join(STORE, "screenshots", f"{i:02d}-{slug}.png"), title, sub)
    print("store graphics written")
