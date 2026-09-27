#!/usr/bin/env python3
"""Generates synthetic sample photos used for emulator screenshots / manual tests.
(All artwork procedural – no third-party images.)"""
from PIL import Image, ImageDraw, ImageFilter
import random, math, os, sys
random.seed(42)
out = sys.argv[1] if len(sys.argv) > 1 else "samples"
os.makedirs(out, exist_ok=True)

def noise(img, amt):
    import numpy as np
    a = np.asarray(img).astype("int16")
    a = a + np.random.default_rng(1).integers(-amt, amt + 1, a.shape, dtype="int16")
    return Image.fromarray(a.clip(0, 255).astype("uint8"))

# 1) Landscape "photo" 4000x3000
W, H = 4000, 3000
img = Image.new("RGB", (W, H))
d = ImageDraw.Draw(img)
for y in range(H):
    t = y / H
    if y < H * 0.55:
        c = (int(70 + 120 * t), int(140 + 90 * t), int(225 + 20 * t))
    else:
        c = (int(40 + 30 * t), int(90 + 40 * t), int(120 + 30 * t))
    d.line([(0, y), (W, y)], fill=c)
d.ellipse((2900, 380, 3350, 830), fill=(255, 236, 170))
for i, (base, col) in enumerate([(0.50, (95, 110, 150)), (0.56, (60, 90, 110)), (0.62, (40, 80, 60))]):
    pts = [(0, H)]
    x = 0
    yb = H * base
    while x <= W:
        pts.append((x, yb - random.randint(0, 420 - i * 110) - 200 * math.sin(x / (500 + i * 200))))
        x += random.randint(120, 260)
    pts.append((W, H))
    d.polygon(pts, fill=col)
for _ in range(90):
    x = random.randint(0, W); y = random.randint(int(H * 0.66), H)
    s = random.randint(60, 180)
    d.polygon([(x, y - s * 2), (x - s // 2, y), (x + s // 2, y)], fill=(20 + random.randint(0, 30), 70 + random.randint(0, 40), 35))
img = img.filter(ImageFilter.GaussianBlur(2))
img = noise(img, 10)
img.save(os.path.join(out, "landscape.jpg"), quality=94)

# 2) Portrait against a light wall 2400x3200
W, H = 2400, 3200
img = Image.new("RGB", (W, H))
d = ImageDraw.Draw(img)
for y in range(H):
    v = int(214 + 18 * (y / H))
    d.line([(0, y), (W, y)], fill=(v, v + 2, v + 6))
cx = W // 2
# shoulders / shirt
d.ellipse((cx - 1150, 2350, cx + 1150, 4300), fill=(38, 64, 110))
d.polygon([(cx - 260, 2380), (cx, 2750), (cx + 260, 2380)], fill=(240, 240, 240))
# neck
d.rectangle((cx - 230, 1850, cx + 230, 2450), fill=(203, 150, 118))
d.ellipse((cx - 230, 2300, cx + 230, 2560), fill=(203, 150, 118))
# head
d.ellipse((cx - 560, 900, cx + 560, 2280), fill=(214, 162, 128))
# ears
d.ellipse((cx - 640, 1500, cx - 480, 1800), fill=(205, 152, 120))
d.ellipse((cx + 480, 1500, cx + 640, 1800), fill=(205, 152, 120))
# hair
d.chord((cx - 600, 760, cx + 600, 1700), 180, 360, fill=(40, 28, 22))
d.ellipse((cx - 610, 1000, cx - 430, 1650), fill=(40, 28, 22))
d.ellipse((cx + 430, 1000, cx + 610, 1650), fill=(40, 28, 22))
# eyes & brows
for sx in (-1, 1):
    ex = cx + sx * 230
    d.ellipse((ex - 105, 1530, ex + 105, 1620), fill=(250, 250, 250))
    d.ellipse((ex - 45, 1530, ex + 45, 1620), fill=(70, 45, 30))
    d.ellipse((ex - 18, 1560, ex + 18, 1596), fill=(15, 10, 8))
    d.line([(ex - 130, 1450), (ex + 120, 1430 if sx > 0 else 1470)], fill=(45, 30, 24), width=34)
# nose & mouth
d.line([(cx, 1620), (cx - 40, 1830), (cx + 30, 1850)], fill=(170, 115, 90), width=16)
d.arc((cx - 190, 1860, cx + 190, 2030), 20, 160, fill=(150, 70, 70), width=22)
img = img.filter(ImageFilter.GaussianBlur(3))
img = noise(img, 7)
# frame it like a typical phone portrait (head a bit above centre)
img = img.crop((180, 367, 2220, 2987))
img.save(os.path.join(out, "portrait.jpg"), quality=93)

# 3) Signature on paper, 3000x2000, uneven light + shadow
W, H = 3000, 2000
img = Image.new("RGB", (W, H))
d = ImageDraw.Draw(img)
for y in range(H):
    for_x = None
    v = 222 - int(26 * (y / H))
    d.line([(0, y), (W, y)], fill=(v + 8, v + 5, v - 6))
shadow = Image.new("L", (W, H), 0)
ImageDraw.Draw(shadow).ellipse((-900, 900, 1500, 3200), fill=70)
shadow = shadow.filter(ImageFilter.GaussianBlur(220))
img = Image.composite(Image.new("RGB", (W, H), (120, 115, 105)), img, shadow)
d = ImageDraw.Draw(img)
# faint ruled line
d.line([(300, 1330), (2700, 1330)], fill=(170, 180, 200), width=6)

def bez(p0, p1, p2, p3, n=120):
    return [((1-t)**3*p0[0]+3*(1-t)**2*t*p1[0]+3*(1-t)*t**2*p2[0]+t**3*p3[0],
             (1-t)**3*p0[1]+3*(1-t)**2*t*p1[1]+3*(1-t)*t**2*p2[1]+t**3*p3[1]) for t in [i/n for i in range(n+1)]]
ink = (25, 28, 60)
strokes = [
    [(520, 1250), (560, 700), (760, 650), (720, 1000)],
    [(720, 1000), (690, 1200), (500, 1150), (650, 950)],
    [(650, 950), (900, 800), (950, 1250), (1050, 1100)],
    [(1050, 1100), (1150, 900), (1180, 1250), (1300, 1080)],
    [(1300, 1080), (1400, 900), (1420, 1300), (1560, 1050)],
    [(1560, 1050), (1650, 850), (1700, 1350), (1850, 1000)],
    [(1850, 1000), (1950, 780), (2050, 1300), (2200, 980)],
    [(2200, 980), (2300, 850), (2350, 1200), (2480, 1060)],
    [(450, 1320), (1100, 1260), (1900, 1300), (2550, 1180)],
]
for s in strokes:
    pts = bez(*s)
    for i in range(len(pts) - 1):
        w = 13 + int(5 * math.sin(i / 9))
        d.line([pts[i], pts[i + 1]], fill=ink, width=w)
img = img.filter(ImageFilter.GaussianBlur(1.6))
img = noise(img, 9)
img.save(os.path.join(out, "signature.jpg"), quality=92)
print("samples written to", out)
