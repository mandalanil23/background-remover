#!/usr/bin/env python3
"""
Regenerates every raster launcher / store asset for Background Remover - PNG Maker
so the artwork stays consistent with the vector drawables in res/drawable.

The mark: a white subject silhouette standing over a background that is solid brand
gradient on the left and a transparency checkerboard on the right - "before | after".

Run:  python3 tools/generate_icons.py
Requires: Pillow
"""
import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
STORE = os.path.join(ROOT, "play-store", "graphics")

# Brand palette (kept in sync with res/values/colors.xml and ui/theme/Color.kt)
NAVY = (23, 26, 51)
INDIGO = (74, 74, 246)
MINT = (34, 211, 166)
WHITE = (255, 255, 255)

SS = 4  # supersampling factor for smooth edges


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def brand_gradient(size):
    """Diagonal navy -> indigo -> mint gradient."""
    img = Image.new("RGB", (size, size))
    px = img.load()
    last = max(1, size - 1)
    for y in range(size):
        for x in range(size):
            t = (0.65 * x + 0.35 * y) / last
            if t < 0.5:
                px[x, y] = lerp(NAVY, INDIGO, t / 0.5)
            else:
                px[x, y] = lerp(INDIGO, MINT, (t - 0.5) / 0.5)
    return img


def checkerboard_right_half(size, cell_ratio=1 / 9.0, alpha=64):
    """Transparency checkerboard covering the right half of the canvas."""
    overlay = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)
    cell = max(1, int(round(size * cell_ratio)))
    n = size // cell + 2
    for row in range(n):
        for col in range(n):
            if (row + col) % 2 == 0:
                x0, y0 = col * cell, row * cell
                if x0 + cell <= size * 0.5:
                    continue
                d.rectangle([max(x0, size * 0.5), y0, x0 + cell, y0 + cell],
                            fill=(255, 255, 255, alpha))
    # Mint seam marking where the background was cut away
    d.rectangle([size * 0.5 - size * 0.004, 0, size * 0.5 + size * 0.004, size],
                fill=MINT + (150,))
    return overlay


def draw_silhouette(draw, s, color=WHITE):
    """Subject silhouette on a 108-unit grid, scaled by `s`."""

    def u(v):
        return v * s

    # Head
    draw.ellipse([u(41), u(21), u(67), u(47)], fill=color)
    # Shoulders: domed top ...
    draw.pieslice([u(28), u(54), u(80), u(106)], start=180, end=360, fill=color)
    # ... with a flat base
    draw.rectangle([u(28), u(79), u(80), u(88)], fill=color)


def make_launcher(size, round_icon=False, mask_corners=True):
    big = size * SS
    base = brand_gradient(big).convert("RGBA")
    base = Image.alpha_composite(base, checkerboard_right_half(big))

    layer = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw_silhouette(ImageDraw.Draw(layer), big / 108.0)
    icon = Image.alpha_composite(base, layer)

    if round_icon:
        mask = Image.new("L", (big, big), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, big - 1, big - 1], fill=255)
        icon.putalpha(mask)
    elif mask_corners:
        mask = Image.new("L", (big, big), 0)
        ImageDraw.Draw(mask).rounded_rectangle(
            [0, 0, big - 1, big - 1], radius=int(big * 0.22), fill=255
        )
        icon.putalpha(mask)

    return icon.resize((size, size), Image.LANCZOS)


def load_font(size, bold=True):
    candidates = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold
        else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf" if bold
        else "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ]
    for path in candidates:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def make_feature_graphic(width=1024, height=500):
    img = Image.new("RGB", (width, height))
    px = img.load()
    for y in range(height):
        for x in range(width):
            t = (x / width) * 0.8 + (y / height) * 0.2
            px[x, y] = lerp(NAVY, INDIGO, t * 1.1)
    d = ImageDraw.Draw(img)

    logo = make_launcher(216)
    img.paste(logo, (72, (height - 216) // 2), logo)

    text_x = 320
    avail = width - text_x - 56

    def fitted(text, start_size, bold=True):
        size = start_size
        font = load_font(size, bold)
        while size > 12 and d.textlength(text, font=font) > avail:
            size -= 2
            font = load_font(size, bold)
        return font

    title = "Background Remover"
    subtitle = "PNG Maker"
    d.text((text_x, 150), title, font=fitted(title, 54), fill=WHITE)
    d.text((text_x, 214), subtitle, font=fitted(subtitle, 40), fill=MINT)
    line = "Remove any background. Save a transparent PNG."
    d.text((text_x, 286), line, font=fitted(line, 28, bold=False),
           fill=(212, 218, 255))
    return img


def main():
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, size in densities.items():
        out_dir = os.path.join(RES, folder)
        os.makedirs(out_dir, exist_ok=True)
        make_launcher(size).save(os.path.join(out_dir, "ic_launcher.png"))
        make_launcher(size, round_icon=True).save(
            os.path.join(out_dir, "ic_launcher_round.png")
        )
        print("wrote", folder, size)

    os.makedirs(STORE, exist_ok=True)
    # Play Console requires a 512x512 32-bit PNG with no transparency.
    icon = make_launcher(512, mask_corners=False).convert("RGB")
    icon.save(os.path.join(STORE, "play_store_icon_512.png"))
    make_feature_graphic().save(os.path.join(STORE, "feature_graphic_1024x500.png"))
    print("wrote play store graphics")


if __name__ == "__main__":
    main()
