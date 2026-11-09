#!/usr/bin/env python3
"""Generate every Sankalp icon from the one image the brand owns.

The mark is a watercolour brush stroke. It is not a shape that can be redrawn as a
vector without becoming a different mark, so this script never draws it: it reads
`branding/sankalp-mark.png` - the artwork exactly as it was supplied - and only ever
scales it, never crops it, never recolours it.

Two properties are enforced for every target:

  * aspect ratio preserved - the mark is 1.83:1, and a square box that scales it to
    fill would cut both loops off. This is the "X" that appeared on the splash
    screen: a 512px-wide bitmap handed a 108dp box, drawn at its own size, clipped.
  * one pixel-perfect size per density bucket - a bitmap in `drawable-nodpi` is
    placed at its pixel count on every screen, so a 108px file was 108dp on a
    4x phone and drawn from 108 source pixels, then upscaled. Blurry. Each density
    folder now gets its own render, so the file is always exactly its dp size.

Usage:  python3 scripts/brand_assets.py [--preview PATH]
"""

from __future__ import annotations

import argparse
import os
import sys
from typing import Iterable

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(ROOT, "branding", "sankalp-mark.png")
ANDROID_RES = os.path.join(ROOT, "android", "src", "main", "res")
WEB_ASSETS = os.path.join(ROOT, "web", "assets")

# The background the artwork was delivered on. It is not #FFFFFF - it is a very pale
# lavender, and treating it as white leaves a grey halo around every stroke.
SOURCE_BACKGROUND = (250, 247, 253)

# Ink colour, measured from the densest pixels of the artwork. Used only for type and
# for the notification silhouette, never to recolour the mark.
INK = (37, 0, 119)

DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}

# How much of the box each target's mark occupies, as a fraction of the box width.
# The launcher foreground is the tight one: an adaptive icon is masked to a circle and
# only the centre 66dp of its 108dp box survives, so a wide mark has to stay inside it.
LAUNCHER_LEGACY_WIDTH = 0.78
LAUNCHER_FOREGROUND_WIDTH = 58 / 108
# The splash box, in dp. Bigger than the launcher's 108dp: on a 360dp-wide phone a 108dp
# box left the mark reading as a speck.
SPLASH_BOX_DP = 160

# How much of that box the mark may fill. Android 12 draws the splash icon inside a circle
# and cuts whatever falls outside it, and the mark is 1.83:1 - so at full width both outer
# loops were sliced off on the phone. 0.66 of the box keeps the whole stroke inside the
# circle with room to spare; a wide mark has to be measured against the circle, not the box.
SPLASH_WIDTH = 0.66
IN_APP_WIDTH = 0.94
NOTIFICATION_WIDTH = 0.92
WEB_ICON_WIDTH = 0.78


def load_mark() -> Image.Image:
    """The artwork with its pale lavender background removed.

    Every pixel is un-composited against the known background, so the soft washes -
    which are real, low-opacity brush strokes - keep their transparency instead of
    being flattened to white. Composited back onto white this reproduces the supplied
    image exactly; on the app's lilac tile it no longer shows a white square.
    """
    src = Image.open(SOURCE).convert("RGB")
    px = src.load()
    width, height = src.size
    bg = SOURCE_BACKGROUND

    out = Image.new("RGBA", src.size, (0, 0, 0, 0))
    dst = out.load()

    for y in range(height):
        for x in range(width):
            r, g, b = px[x, y]
            # Coverage of the darkest channel against the known background.
            alpha = 1.0 - min(r / bg[0], g / bg[1], b / bg[2])
            if alpha < 0.03:
                continue
            alpha = min(alpha, 1.0)
            nr = (r - bg[0] * (1.0 - alpha)) / alpha
            ng = (g - bg[1] * (1.0 - alpha)) / alpha
            nb = (b - bg[2] * (1.0 - alpha)) / alpha
            dst[x, y] = (
                int(max(0, min(255, round(nr)))),
                int(max(0, min(255, round(ng)))),
                int(max(0, min(255, round(nb)))),
                int(round(alpha * 255)),
            )

    box = out.split()[3].getbbox()
    if box is None:
        raise SystemExit(f"{SOURCE} has no ink in it")
    return out.crop(box)


def place(
    mark: Image.Image,
    canvas: tuple[int, int],
    width_fraction: float,
    background: tuple[int, int, int, int] | None = (255, 255, 255, 255),
) -> Image.Image:
    """Centre the whole mark, scaled to `width_fraction` of the canvas width."""
    cw, ch = canvas
    target_w = max(1, int(round(cw * width_fraction)))
    target_h = max(1, int(round(target_w * mark.height / mark.width)))
    if target_h > ch:
        # A mark too tall for the box shrinks to fit instead of being clipped.
        target_h = ch
        target_w = max(1, int(round(target_h * mark.width / mark.height)))

    scaled = mark.resize((target_w, target_h), Image.LANCZOS)
    out = Image.new("RGBA", canvas, background or (0, 0, 0, 0))
    out.alpha_composite(scaled, ((cw - target_w) // 2, (ch - target_h) // 2))
    return out


def silhouette(mark: Image.Image, canvas: tuple[int, int], width_fraction: float) -> Image.Image:
    """White shape, alpha from the mark - what a status-bar notification icon needs.

    Android draws a small icon from its alpha channel only and tints it, so a
    photograph of a purple brush stroke shows up as a solid white block.
    """
    placed = place(mark, canvas, width_fraction, background=None)
    white = Image.new("RGBA", canvas, (255, 255, 255, 255))
    white.putalpha(placed.split()[3])
    return white


def write(image: Image.Image, path: str, opaque: bool = False) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if opaque and image.mode == "RGBA":
        flat = Image.new("RGB", image.size, (255, 255, 255))
        flat.paste(image, (0, 0), image)
        image = flat
    image.save(path, optimize=True)
    print(f"  {os.path.relpath(path, ROOT)}  {image.size[0]}x{image.size[1]}")


def android(mark: Image.Image) -> None:
    for name, scale in DENSITIES.items():
        def px(dp: float) -> int:
            return int(round(dp * scale))

        # Legacy launcher icon: API 25 and below, and any launcher that ignores
        # adaptive icons. Full-bleed white square, mark comfortably inside it.
        write(
            place(mark, (px(48), px(48)), LAUNCHER_LEGACY_WIDTH),
            os.path.join(ANDROID_RES, f"mipmap-{name}", "ic_launcher.png"),
            opaque=True,
        )

        # Adaptive icon foreground: transparent, sits on the white background layer.
        write(
            place(mark, (px(108), px(108)), LAUNCHER_FOREGROUND_WIDTH, background=None),
            os.path.join(ANDROID_RES, f"mipmap-{name}", "ic_launcher_foreground.png"),
        )

        # Splash, both the Android 12 icon and the pre-12 window background. Nothing
        # masks this one, so the mark can be larger than the launcher's.
        write(
            place(mark, (px(SPLASH_BOX_DP), px(SPLASH_BOX_DP)), SPLASH_WIDTH, background=None),
            os.path.join(ANDROID_RES, f"drawable-{name}", "sankalp_splash.png"),
        )

        # In-app mark: drawn at 72dp and 52dp by Compose, so it is supplied as a
        # 108dp square and scaled down by the layout.
        write(
            place(mark, (px(108), px(108)), IN_APP_WIDTH, background=None),
            os.path.join(ANDROID_RES, f"drawable-{name}", "sankalp_logo.png"),
        )

        write(
            silhouette(mark, (px(24), px(24)), NOTIFICATION_WIDTH),
            os.path.join(ANDROID_RES, f"drawable-{name}", "sankalp_notification.png"),
        )


def web(mark: Image.Image) -> None:
    # The inline mark the site draws in its header, drawers and auth screens. Native
    # resolution of the artwork: it is displayed at 24-72 CSS px, so it is already
    # 5x at the largest size and never has to be upscaled.
    write(mark, os.path.join(WEB_ASSETS, "mark.png"))

    for size in (32, 48, 96, 180, 192, 512):
        write(
            place(mark, (size, size), WEB_ICON_WIDTH),
            os.path.join(WEB_ASSETS, f"icon-{size}.png"),
            opaque=True,
        )

    og = Image.new("RGB", (1200, 630), (255, 255, 255))
    mark_w = 500
    mark_h = int(round(mark_w * mark.height / mark.width))
    og_mark = place(mark, (mark_w, mark_h), 1.0)
    og.paste(og_mark, (600 - mark_w // 2, 58), og_mark)

    title_font = _font(84)
    sub_font = _font(34)
    draw = ImageDraw.Draw(og)
    title = "Sankalp"
    sub = "by Indo Workspace"
    tw = draw.textbbox((0, 0), title, font=title_font)
    sw = draw.textbbox((0, 0), sub, font=sub_font)
    draw.text((600 - (tw[2] - tw[0]) / 2, 352), title, font=title_font, fill=INK)
    draw.text((600 - (sw[2] - sw[0]) / 2, 466), sub, font=sub_font, fill=(96, 84, 128))
    write(og, os.path.join(WEB_ASSETS, "og-image.png"), opaque=True)

    favicon_frames = [
        place(mark, (s, s), WEB_ICON_WIDTH).convert("RGBA") for s in (16, 32, 48)
    ]
    favicon_path = os.path.join(ROOT, "web", "favicon.ico")
    favicon_frames[-1].save(
        favicon_path, format="ICO", sizes=[(16, 16), (32, 32), (48, 48)]
    )
    print(f"  web/favicon.ico  16/32/48")


def _font(size: int) -> ImageFont.FreeTypeFont:
    candidates = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for path in candidates:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def preview(mark: Image.Image, path: str) -> None:
    """A contact sheet: what each surface will actually show, at phone scale."""
    sheet = Image.new("RGB", (1560, 1060), (245, 243, 250))
    draw = ImageDraw.Draw(sheet)
    label = _font(24)
    small = _font(19)

    def caption(x: int, y: int, text: str) -> None:
        draw.text((x, y), text, font=label, fill=INK)

    def note(x: int, y: int, text: str) -> None:
        draw.text((x, y), text, font=small, fill=(90, 78, 120))

    # 1. Splash, drawn on a 360x640dp phone at 2x.
    phone = Image.new("RGB", (720, 1280), (255, 255, 255))
    splash = place(mark, (320, 320), SPLASH_WIDTH, background=None)
    phone.paste(splash, ((720 - 320) // 2, (1280 - 320) // 2), splash)
    sheet.paste(phone.resize((360, 640), Image.LANCZOS), (40, 90))
    caption(40, 50, "Splash")

    # 2. Launcher icons, with the masks Android actually applies.
    fg = place(mark, (432, 432), LAUNCHER_FOREGROUND_WIDTH)
    white = Image.new("RGBA", (432, 432), (255, 255, 255, 255))
    white.alpha_composite(fg)
    flat = white.convert("RGB")

    round_icon = Image.new("RGB", (432, 432), (245, 243, 250))
    rmask = Image.new("L", (432, 432), 0)
    ImageDraw.Draw(rmask).ellipse((0, 0, 431, 431), fill=255)
    round_icon.paste(flat, (0, 0), rmask)
    sheet.paste(round_icon.resize((216, 216), Image.LANCZOS), (450, 90))
    caption(450, 50, "Launcher, round")

    squircle = Image.new("RGB", (432, 432), (245, 243, 250))
    smask = Image.new("L", (432, 432), 0)
    ImageDraw.Draw(smask).rounded_rectangle((0, 0, 431, 431), radius=110, fill=255)
    squircle.paste(flat, (0, 0), smask)
    sheet.paste(squircle.resize((216, 216), Image.LANCZOS), (730, 90))
    caption(730, 50, "Launcher, squircle")

    legacy = place(mark, (192, 192), LAUNCHER_LEGACY_WIDTH).convert("RGB")
    sheet.paste(legacy.resize((216, 216), Image.LANCZOS), (1010, 90))
    caption(1010, 50, "Legacy, API 25")

    # 3. In-app header, as AuthBrand draws it: a 72dp mark beside the name.
    header = Image.new("RGB", (520, 180), (255, 255, 255))
    logo = place(mark, (144, 144), IN_APP_WIDTH)
    header.paste(logo, (18, 18), logo)
    ImageDraw.Draw(header).text((186, 62), "Sankalp", font=_font(44), fill=INK)
    sheet.paste(header.resize((390, 135), Image.LANCZOS), (450, 430))
    caption(450, 390, "In-app header, 72dp mark")

    # 4. Web header mark and the account tile it sits in.
    web_mark = place(mark, (152, 152), 1.0)
    sheet.paste(web_mark, (900, 450), web_mark)
    caption(900, 390, "Web header, 38px box")

    tile = Image.new("RGB", (116, 116), (242, 236, 255))
    tm = place(mark, (68, 68), 1.0)
    tile.paste(tm, (24, 24), tm)
    sheet.paste(tile, (1080, 460))
    note(1080, 590, "Account tile")

    # 5. What a search engine and a chat preview will show.
    og = Image.open(os.path.join(WEB_ASSETS, "og-image.png")).resize((600, 315), Image.LANCZOS)
    sheet.paste(og, (40, 760))
    caption(40, 720, "og:image, 1200x630")

    sheet.save(path)
    print(f"preview -> {path}")


def main(argv: Iterable[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--preview", help="also write a contact sheet to this path")
    args = parser.parse_args(list(argv))

    if not os.path.exists(SOURCE):
        raise SystemExit(f"missing brand source: {SOURCE}")

    mark = load_mark()
    print(f"source {os.path.relpath(SOURCE, ROOT)} -> mark {mark.width}x{mark.height}")

    print("android:")
    android(mark)
    print("web:")
    web(mark)
    if args.preview:
        preview(mark, args.preview)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
