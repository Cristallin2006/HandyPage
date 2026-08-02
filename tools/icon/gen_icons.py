# -*- coding: utf-8 -*-
"""Generate full launcher icon set from icon-h-fixed.png."""
from PIL import Image, ImageDraw
import os

SRC = r"C:\Users\Lenovo\Desktop\icon-h-fixed.png"
RES = r"C:\Users\Lenovo\Desktop\Handypage\app\src\main\res"
ART = r"C:\Users\Lenovo\Desktop\Handypage\artwork"

INK = (28, 25, 23)        # #1C1917
CREAM_HEX = "#F4EDE0"     # sampled paper tone
TARGET_BBOX = 0.64        # glyph bbox max-dim as fraction of foreground canvas

FG_SIZES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
LG_SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

im = Image.open(SRC).convert("RGB")
W, H = im.size
px = im.load()

# --- alpha from darkness vs paper tone (per-pixel luminance falloff)
alpha = Image.new("L", (W, H), 0)
ap = alpha.load()
paper_L = 242.0
ink_L = 25.0
for y in range(H):
    for x in range(W):
        r, g, b = px[x, y]
        L = 0.299 * r + 0.587 * g + 0.114 * b
        a = (paper_L - L) / (paper_L - ink_L)
        ap[x, y] = 0 if a <= 0.02 else (255 if a >= 1.0 else int(a * 255))

# --- glyph bbox
bbox = alpha.point(lambda v: 255 if v > 24 else 0).getbbox()
print("glyph bbox:", bbox, "canvas:", (W, H))
glyph_alpha = alpha.crop(bbox)
gw, gh = glyph_alpha.size

# --- foreground canvas: transparent, glyph centered at TARGET_BBOX scale
CW = 1024  # master foreground canvas
scale = (CW * TARGET_BBOX) / max(gw, gh)
sw, sh = int(gw * scale), int(gh * scale)
fg_master = Image.new("RGBA", (CW, CW), (0, 0, 0, 0))
g = glyph_alpha.resize((sw, sh), Image.LANCZOS)
ink_img = Image.new("RGBA", (sw, sh), INK + (255,))
ink_img.putalpha(g)
fg_master.paste(ink_img, ((CW - sw) // 2, (CW - sh) // 2), ink_img)

# --- write per-density foreground + monochrome
for d, s in FG_SIZES.items():
    out = fg_master.resize((s, s), Image.LANCZOS)
    ddir = os.path.join(RES, "mipmap-" + d)
    os.makedirs(ddir, exist_ok=True)
    out.save(os.path.join(ddir, "ic_launcher_foreground.png"))
    out.save(os.path.join(ddir, "ic_launcher_monochrome.png"))
print("foreground+monochrome written")

# --- legacy icons from the fixed full-bleed art
for d, s in LG_SIZES.items():
    ddir = os.path.join(RES, "mipmap-" + d)
    sq = im.resize((s, s), Image.LANCZOS)
    sq.save(os.path.join(ddir, "ic_launcher.png"))
    mk = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mk).ellipse([0, 0, s - 1, s - 1], fill=255)
    rd = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    rd.paste(sq, (0, 0), mk)
    rd.save(os.path.join(ddir, "ic_launcher_round.png"))
print("legacy written")

# --- background color resource
os.makedirs(os.path.join(RES, "values"), exist_ok=True)
with open(os.path.join(RES, "values", "ic_launcher_background.xml"), "w", encoding="utf-8") as f:
    f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
            f'    <color name="ic_launcher_background">{CREAM_HEX}</color>\n</resources>\n')

# --- adaptive icon XMLs
v26 = os.path.join(RES, "mipmap-anydpi-v26")
os.makedirs(v26, exist_ok=True)
xml = ('<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
       '    <background android:drawable="@color/ic_launcher_background"/>\n'
       '    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>\n'
       '    <monochrome android:drawable="@mipmap/ic_launcher_monochrome"/>\n'
       '</adaptive-icon>\n')
for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
    with open(os.path.join(v26, name), "w", encoding="utf-8") as f:
        f.write(xml)
print("adaptive xml written")

# --- Play Store 512
os.makedirs(ART, exist_ok=True)
im.resize((512, 512), Image.LANCZOS).save(os.path.join(ART, "play-icon-512.png"))
print("play icon written")
