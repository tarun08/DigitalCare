"""Draws the three break/water animations (self-made, no external art) into public/anim/.

Run: python -I tools/make_animations.py
Writes {eye,stretch,drink}.webp (24-frame loop) and a static .png (first frame) for reduced motion.
"""
import math
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

OUT = Path(__file__).resolve().parent.parent / "public" / "anim"
SIZE, S = 320, 2  # output size, supersampling factor
N, FRAME_MS = 24, 80
TEXT, ACCENT, WATER, DARK = "#e8eef1", "#5ccfb8", "#6bb8ff", "#10161a"


def canvas():
    return Image.new("RGBA", (SIZE * S, SIZE * S), (0, 0, 0, 0))


def finish(img):
    return img.resize((SIZE, SIZE), Image.LANCZOS)


def stroke(d, a, b, width, fill):
    """Line with round caps."""
    d.line([a, b], fill=fill, width=width)
    for x, y in (a, b):
        d.ellipse((x - width / 2, y - width / 2, x + width / 2, y + width / 2), fill=fill)


def eye(i):
    img = canvas()
    cx, cy, w = 160 * S, 160 * S, 130 * S
    blink = {20: 0.5, 21: 0.08, 22: 0.5}.get(i, 1.0)
    h = 70 * S * blink
    lid = [(cx + x, cy - h * (1 - (x / w) ** 2)) for x in range(-w, w + 1, S * 4)]
    lid += [(cx + x, cy + h * (1 - (x / w) ** 2)) for x in range(w, -w - 1, -S * 4)]
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).polygon(lid, fill=255)
    # The iris drifts to one side ("looking far") and back.
    ix = cx + 45 * S * math.sin(2 * math.pi * i / N)
    inner = canvas()
    di = ImageDraw.Draw(inner)
    di.rectangle((0, 0, *img.size), fill=TEXT)
    r = 46 * S
    di.ellipse((ix - r, cy - r, ix + r, cy + r), fill=ACCENT)
    r = 20 * S
    di.ellipse((ix - r, cy - r, ix + r, cy + r), fill=DARK)
    r = 7 * S
    di.ellipse((ix + 12 * S - r, cy - 14 * S - r, ix + 12 * S + r, cy - 14 * S + r), fill=TEXT)
    img.paste(inner, (0, 0), mask)
    ImageDraw.Draw(img).line(lid + [lid[0]], fill=ACCENT, width=8 * S, joint="curve")
    return finish(img)


def stretch(i):
    img = canvas()
    d = ImageDraw.Draw(img)
    width = 14 * S
    head, neck, hip, shoulder = (160 * S, 70 * S), (160 * S, 100 * S), (160 * S, 205 * S), (160 * S, 118 * S)
    r = 26 * S
    d.ellipse((head[0] - r, head[1] - r, head[0] + r, head[1] + r), outline=TEXT, width=width)
    stroke(d, neck, hip, width, TEXT)
    for dx in (-1, 1):
        stroke(d, hip, (hip[0] + dx * 38 * S, 290 * S), width, TEXT)
    # Arms sweep from hanging down to straight overhead and back.
    up = (1 - math.cos(2 * math.pi * i / N)) / 2
    angle = math.radians(-65 + up * 110)  # from horizontal, positive = up; tops out in a wide Y clear of the head
    for dx in (-1, 1):
        end = (shoulder[0] + dx * 85 * S * math.cos(angle), shoulder[1] - 85 * S * math.sin(angle))
        stroke(d, shoulder, end, width, ACCENT)
    return finish(img)


def rotate(points, angle, pivot):
    c, s = math.cos(angle), math.sin(angle)
    return [(pivot[0] + (x - pivot[0]) * c - (y - pivot[1]) * s,
             pivot[1] + (x - pivot[0]) * s + (y - pivot[1]) * c) for x, y in points]


def drink(i):
    img = canvas()
    phase = (1 - math.cos(2 * math.pi * i / N)) / 2  # 0 -> 1 -> 0
    top, bottom = 70 * S, 270 * S
    glass = [(85 * S, top), (235 * S, top), (210 * S, bottom), (110 * S, bottom)]
    glass = rotate(glass, math.radians(-28 * phase), (160 * S, bottom))
    inside = Image.new("L", img.size, 0)
    ImageDraw.Draw(inside).polygon(glass, fill=255)
    level = Image.new("L", img.size, 0)
    surface = top + (0.25 + 0.5 * phase) * (bottom - top)  # the level drops as the glass tilts
    ImageDraw.Draw(level).rectangle((0, surface, *img.size), fill=255)
    water = ImageChops.multiply(inside, level)
    img.paste(Image.new("RGBA", img.size, WATER), (0, 0), water)
    bubbles = canvas()
    db = ImageDraw.Draw(bubbles)
    for k, x in enumerate((135, 160, 185)):
        y = bottom - ((i / N + k / 3) % 1) * (bottom - surface) - 10 * S
        r = (6 + 2 * k) * S
        db.ellipse((x * S - r, y - r, x * S + r, y + r), outline=TEXT, width=3 * S)
    img.paste(bubbles, (0, 0), ImageChops.multiply(bubbles.getchannel("A"), water))
    ImageDraw.Draw(img).line(glass + [glass[0]], fill=ACCENT, width=8 * S, joint="curve")
    return finish(img)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, draw in (("eye", eye), ("stretch", stretch), ("drink", drink)):
        frames = [draw(i) for i in range(N)]
        webp = OUT / f"{name}.webp"
        frames[0].save(webp, save_all=True, append_images=frames[1:], duration=FRAME_MS, loop=0,
                       quality=80, method=6)
        frames[0].save(OUT / f"{name}.png", optimize=True)
        # Self-check: spec size limit and every frame kept (identical neighbours would be merged).
        size = webp.stat().st_size
        assert size <= 300 * 1024, f"{webp} is {size} bytes"
        with Image.open(webp) as im:
            assert im.n_frames == N, f"{webp} has {im.n_frames} frames"
        print(f"{name}: {size // 1024} KB, {N} frames")


if __name__ == "__main__":
    main()
