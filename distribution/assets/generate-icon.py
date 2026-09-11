"""Regenerates distribution/assets/gz-companion.ico from the shapes in
gz-companion-icon-source.svg. Requires Python 3 + Pillow (`pip install pillow`).

Run from anywhere: `python distribution/assets/generate-icon.py`.

Pillow has no SVG rasterizer, so the shapes are duplicated here as plain polygons rather than
parsed from the .svg - if you change one, change the other to match.
"""

import os

from PIL import Image, ImageDraw

# Must match GZCompanion.Installer.App/Theme.cs exactly, so the icon and the installer UI never
# look like two different products.
NAVY = (0x0D, 0x14, 0x1C, 255)      # Theme.PanelBg
MINT = (0x34, 0xD3, 0x99, 255)      # Theme.Mint
EMERALD = (0x10, 0xB9, 0x81, 255)   # Theme.Emerald (thin shield outline)

# Normalized 0-100 unit box, y-down - keep in sync with gz-companion-icon-source.svg.
SHIELD = [(22, 20), (78, 20), (78, 52), (50, 88), (22, 52)]
DIAMOND = [(50, 34), (63, 50), (50, 66), (37, 50)]

SIZES = [16, 24, 32, 48, 64, 128, 256]


def _scaled(points, scale):
    return [(x * scale, y * scale) for x, y in points]


def render(size, supersample=8):
    """Draws at supersample*size then downsamples, so edges stay crisp at every icon size."""
    ss = size * supersample
    img = Image.new("RGBA", (ss, ss), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    radius = int(ss * 0.18)
    draw.rounded_rectangle([0, 0, ss - 1, ss - 1], radius=radius, fill=NAVY)

    scale = ss / 100.0
    shield = _scaled(SHIELD, scale)
    outline_w = max(1, int(ss * 0.018))
    draw.polygon(shield, fill=MINT)
    draw.line(shield + [shield[0]], fill=EMERALD, width=outline_w, joint="curve")

    draw.polygon(_scaled(DIAMOND, scale), fill=NAVY)

    return img.resize((size, size), Image.LANCZOS)


def main():
    out_dir = os.path.dirname(os.path.abspath(__file__))
    images = [render(s) for s in SIZES]
    ico_path = os.path.join(out_dir, "gz-companion.ico")
    images[-1].save(
        ico_path,
        format="ICO",
        sizes=[(im.width, im.height) for im in images],
        append_images=images[:-1],
    )
    print(f"Wrote {ico_path}")


if __name__ == "__main__":
    main()
