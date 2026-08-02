# -*- coding: utf-8 -*-
"""Fix baked corners by per-row curve scan, erase watermark, rebuild mask sheet."""
from PIL import Image, ImageDraw

SRC = r"C:\Users\Lenovo\Desktop\H书法风格应用图标.png"
FIXED = r"C:\Users\Lenovo\Desktop\icon-h-fixed.png"
SHEET = r"C:\Users\Lenovo\Desktop\icon-h-masks.png"

im = Image.open(SRC).convert("RGB")
W, H = im.size
px = im.load()
cream = px[W // 2, 100]

def is_dark(p, t=70):
    return p[0] < t and p[1] < t and p[2] < t

BAND = 420  # generous corner band to scan

def first_light_from_left(y):
    for x in range(W // 2):
        if not is_dark(px[x, y]):
            return x
    return W // 2

def first_light_from_right(y):
    for x in range(W - 1, W // 2, -1):
        if not is_dark(px[x, y]):
            return x
    return W // 2

filled_rows = 0
for y in list(range(0, BAND)) + list(range(H - BAND, H)):
    xl = first_light_from_left(y)
    xr = first_light_from_right(y)
    changed = False
    for x in range(0, min(xl + 2, W)):
        if is_dark(px[x, y]):
            px[x, y] = cream; changed = True
    for x in range(max(xr - 1, 0), W):
        if is_dark(px[x, y]):
            px[x, y] = cream; changed = True
    if changed:
        filled_rows += 1
print("rows touched:", filled_rows)

# left/right edge columns beyond the band, just in case
for x in range(0, 8):
    for y in range(H):
        if is_dark(px[x, y]):
            px[x, y] = cream
for x in range(W - 8, W):
    for y in range(H):
        if is_dark(px[x, y]):
            px[x, y] = cream

# watermark bottom-left
d2 = ImageDraw.Draw(im)
d2.rectangle((int(0.005 * W), int(0.918 * H), int(0.135 * W), int(0.985 * H)), fill=cream)

# corner squares: wipe residual edge-stroke pixels that deviate from local paper tone
def color_dist(a, b):
    return abs(a[0]-b[0]) + abs(a[1]-b[1]) + abs(a[2]-b[2])

CS = 460
wiped = 0
for y in list(range(0, CS)) + list(range(H - CS, H)):
    local = px[CS + 40, y]  # paper zone: left of underline (x≈553+), right of corner square
    for x in list(range(0, CS)) + list(range(W - CS, W)):
        if color_dist(px[x, y], local) > 30:
            px[x, y] = local; wiped += 1
print("edge-stroke pixels wiped:", wiped)

# verify: dark pixels in outer 6px border
border_dark = 0
for x in range(W):
    for y in list(range(6)) + list(range(H - 6, H)):
        if is_dark(px[x, y]):
            border_dark += 1
for y in range(H):
    for x in list(range(6)) + list(range(W - 6, W)):
        if is_dark(px[x, y]):
            border_dark += 1
print("dark pixels in outer border:", border_dark)

im.save(FIXED)
print("saved", FIXED)

def apply_mask(img, size, kind):
    ic = img.resize((size, size), Image.LANCZOS)
    mk = Image.new("L", (size, size), 0)
    dd = ImageDraw.Draw(mk)
    if kind == "circle":
        dd.ellipse([0, 0, size - 1, size - 1], fill=255)
    elif kind == "squircle":
        dd.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.42), fill=255)
    else:
        dd.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.20), fill=255)
    out = Image.new("RGB", (size, size), (232, 229, 224))
    out.paste(ic, (0, 0), mk)
    return out

tiles = [
    apply_mask(im, 256, "circle"),
    apply_mask(im, 256, "squircle"),
    apply_mask(im, 256, "rounded"),
    apply_mask(im, 48, "circle"),
    apply_mask(im, 48, "squircle"),
]
gap, padx, pady = 24, 24, 24
sheet = Image.new("RGB", (padx * 2 + 256 * 3 + gap * 2, pady * 2 + 256 + gap + 48), (200, 196, 190))
for i, t in enumerate(tiles[:3]):
    sheet.paste(t, (padx + i * (256 + gap), pady))
sheet.paste(tiles[3], (padx, pady + 256 + gap))
sheet.paste(tiles[4], (padx + 48 + gap, pady + 256 + gap))
sheet.save(SHEET)
print("saved", SHEET)
