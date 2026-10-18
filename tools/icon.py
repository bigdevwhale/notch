"""Draws the launcher icon (a honey-wood door jamb with pencil notches on a cream wall) and docs/logo.png.

python tools/icon.py
"""
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
WALL = (241, 230, 210)
WOOD = (214, 178, 132)
WOOD_LIGHT = (232, 207, 169)
WOOD_DARK = (156, 115, 71)
GRAIN = (185, 143, 94)
PENCIL = (69, 68, 76)
CRAYONS = [(217, 87, 59), (62, 127, 193), (91, 149, 72)]
S = 8  # supersampling


def jamb(size: int) -> Image.Image:
    """The jamb and its notches alone, transparent around them, on a size x size canvas (108 dp units)."""
    W = size * S
    im = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    u = W / 108
    cx = W / 2
    left, right = cx - 17 * u, cx - 1 * u
    top, bot = 22 * u, 90 * u
    # Shadow on the wall.
    sh = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).rectangle((right, top + 2 * u, right + 5 * u, bot), fill=(90, 60, 30, 70))
    im = Image.alpha_composite(im, sh.filter(ImageFilter.GaussianBlur(2.5 * u)))
    d = ImageDraw.Draw(im)
    d.rectangle((left, top, right, bot), fill=WOOD)
    d.rectangle((left + 2 * u, top, left + 5.5 * u, bot), fill=WOOD_LIGHT)
    d.rectangle((right - 2.4 * u, top, right, bot), fill=WOOD_DARK)
    rnd = random.Random(4)
    for _ in range(7):
        x = left + (1 + rnd.random() * 13) * u
        d.line((x, top, x + (rnd.random() - 0.5) * 2 * u, bot), fill=GRAIN + (150,), width=max(1, int(0.5 * u)))
    # The notches: pencil lines with a carved V, and a crayon mark beside the newest.
    for i, (y, length, col) in enumerate(((36, 26, CRAYONS[0]), (52, 20, PENCIL), (67, 23, CRAYONS[1]), (80, 16, PENCIL))):
        yy = y * u
        x0 = right - 9 * u
        d.line((x0, yy, x0 + length * u, yy), fill=col, width=int(2.6 * u))
        d.polygon([(x0 - 0.5 * u, yy - 2.2 * u), (x0 + 3 * u, yy), (x0 - 0.5 * u, yy + 2.2 * u)], fill=WOOD_DARK)
    return im.resize((size, size), Image.LANCZOS)


def flatten(fg: Image.Image) -> Image.Image:
    return Image.alpha_composite(Image.new("RGBA", fg.size, WALL + (255,)), fg)


def main():
    dens = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
    for name, k in dens.items():
        d = RES / f"mipmap-{name}"
        d.mkdir(parents=True, exist_ok=True)
        fg = jamb(int(108 * k))
        fg.save(d / "ic_launcher_foreground.png")
        n = int(48 * k)
        full = flatten(fg)
        crop = int(fg.size[0] * 18 / 108)
        flat = full.crop((crop, crop, fg.size[0] - crop, fg.size[0] - crop)).resize((n, n), Image.LANCZOS)
        sq = Image.new("L", (n * 4, n * 4), 0)
        ImageDraw.Draw(sq).rounded_rectangle((0, 0, n * 4 - 1, n * 4 - 1), radius=n * 4 // 5, fill=255)
        out = Image.new("RGBA", (n, n), (0, 0, 0, 0))
        out.paste(flat, (0, 0), sq.resize((n, n), Image.LANCZOS))
        out.save(d / "ic_launcher.png")
        rd = Image.new("L", (n * 4, n * 4), 0)
        ImageDraw.Draw(rd).ellipse((0, 0, n * 4 - 1, n * 4 - 1), fill=255)
        out = Image.new("RGBA", (n, n), (0, 0, 0, 0))
        out.paste(flat, (0, 0), rd.resize((n, n), Image.LANCZOS))
        out.save(d / "ic_launcher_round.png")
    big = 512
    fg = jamb(int(big * 108 / 72))
    full = flatten(fg)
    c = (fg.size[0] - big) // 2
    flat = full.crop((c, c, c + big, c + big))
    m = Image.new("L", (big * 4, big * 4), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, big * 4 - 1, big * 4 - 1), radius=big * 4 // 5, fill=255)
    logo = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    logo.paste(flat, (0, 0), m.resize((big, big), Image.LANCZOS))
    (ROOT / "docs").mkdir(exist_ok=True)
    logo.save(ROOT / "docs/logo.png")
    print("ok")


if __name__ == "__main__":
    main()
