#!/usr/bin/env python3
"""
Turns the widget renders into the picker previews.

WidgetScreenshotTest draws each widget twice for the picker, on black and
on white (preview_<name>_black.png / _white.png). A pixel's colour on the
two grounds gives its true alpha and colour, so the preview keeps soft
edges and shadows with nothing of either ground left in it:

    alpha = 1 - (white - black)          colour = black / alpha

    ./gradlew recordRoborazziDebug --tests '*WidgetScreenshotTest.preview*'
    python3 tools/widget_previews.py

writes app/src/main/res/drawable-nodpi/widget_preview_<name>.webp, which the
widget's provider info names as its previewImage.
"""
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RENDERS = os.path.join(ROOT, "app/build/outputs/roborazzi")
OUT = os.path.join(ROOT, "app/src/main/res/drawable-nodpi")
NAMES = ["pulse", "lineup", "bloom", "orbit", "pixel", "blocks"]


def matte(black: Image.Image, white: Image.Image) -> Image.Image:
    b = black.convert("RGB")
    w = white.convert("RGB")
    out = Image.new("RGBA", b.size)
    bp, wp, op = b.load(), w.load(), out.load()
    for y in range(b.height):
        for x in range(b.width):
            br, bg, bb = bp[x, y]
            wr, wg, wb = wp[x, y]
            # Alpha from the brightest channel's spread, which is the least noisy.
            spread = max(wr - br, wg - bg, wb - bb)
            a = max(0, min(255, 255 - spread))
            if a == 0:
                op[x, y] = (0, 0, 0, 0)
                continue
            k = 255 / a
            op[x, y] = (min(255, round(br * k)), min(255, round(bg * k)), min(255, round(bb * k)), a)
    return out


def main() -> int:
    os.makedirs(OUT, exist_ok=True)
    for name in NAMES:
        black = os.path.join(RENDERS, f"preview_{name}_black.png")
        white = os.path.join(RENDERS, f"preview_{name}_white.png")
        if not (os.path.exists(black) and os.path.exists(white)):
            print(f"missing renders for {name}; run the preview tests first", file=sys.stderr)
            return 1
        image = matte(Image.open(black), Image.open(white))
        # Previews are shown at most a few hundred dp wide: 2.5x is sharp there.
        scale = 2.5 / 3.0
        image = image.resize((round(image.width * scale), round(image.height * scale)), Image.LANCZOS)
        path = os.path.join(OUT, f"widget_preview_{name}.webp")
        image.save(path, "WEBP", quality=90, method=6)
        print(f"wrote {os.path.relpath(path, ROOT)} {image.size} {os.path.getsize(path) // 1024} KB")
    return 0


if __name__ == "__main__":
    sys.exit(main())
