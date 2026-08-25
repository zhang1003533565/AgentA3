"""Generate chubby map pins and numbered cluster bubbles."""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = Path(__file__).resolve().parents[1] / "static" / "icons" / "map"

PIN_COLORS = {
    "pin-teaching.png": (77, 111, 143, 255),
    "pin-canteen.png": (201, 134, 77, 255),
    "pin-sport.png": (78, 138, 105, 255),
    "pin-infra.png": (107, 124, 141, 255),
    "pin-search.png": (90, 110, 130, 255),
    "pin-active.png": (53, 84, 112, 255),
}

def quad(p0, p1, p2, steps=18):
    pts = []
    for i in range(1, steps + 1):
        t = i / steps
        pts.append((
            (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t ** 2 * p2[0],
            (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t ** 2 * p2[1],
        ))
    return pts


def draw_pin_body(draw, cx, head_cy, r, tip_y, tip_r, fill):
    draw.ellipse((cx - r, head_cy - r, cx + r, head_cy + r), fill=fill)
    top = head_cy + r * 0.08
    left = (cx - r * 0.86, top)
    right = (cx + r * 0.86, top)
    tip = (cx, tip_y)
    ctrl_l = (cx - r * 0.2, top + (tip_y - top) * 0.62)
    ctrl_r = (cx + r * 0.2, top + (tip_y - top) * 0.62)
    draw.polygon([left, *quad(left, ctrl_l, tip, 16), *quad(tip, ctrl_r, right, 16), right], fill=fill)


def render_pin(color, dest: Path):
    scale = 4
    w, h = 96 * scale, 128 * scale
    cx = w / 2
    r = 36 * scale
    head_cy = 44 * scale
    tip_y = 114 * scale
    tip_r = 5.2 * scale

    shadow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    draw_pin_body(sd, cx, head_cy + 2.8 * scale, r + 1.6 * scale, tip_y + 2.8 * scale, tip_r + 1.0 * scale, (28, 38, 52, 70))
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=2.4 * scale))

    body = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    bd = ImageDraw.Draw(body)
    draw_pin_body(bd, cx, head_cy, r + 2.6 * scale, tip_y + 1.6 * scale, tip_r + 1.8 * scale, (255, 255, 255, 255))
    draw_pin_body(bd, cx, head_cy, r, tip_y, tip_r, color)

    hole_r = 10.2 * scale
    hx, hy = cx, head_cy - 2.4 * scale
    bd.ellipse((hx - hole_r - 1.8 * scale, hy - hole_r - 1.8 * scale,
                hx + hole_r + 1.8 * scale, hy + hole_r + 1.8 * scale), fill=color)
    bd.ellipse((hx - hole_r, hy - hole_r, hx + hole_r, hy + hole_r), fill=(255, 255, 255, 255))

    out = Image.alpha_composite(shadow, body)
    out = out.resize((96, 128), Image.Resampling.LANCZOS)
    dest.parent.mkdir(parents=True, exist_ok=True)
    out.save(dest)


def load_font(size):
    for path in (
        r"C:\Windows\Fonts\segoeuib.ttf",
        r"C:\Windows\Fonts\arialbd.ttf",
        r"C:\Windows\Fonts\calibrib.ttf",
    ):
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            continue
    return ImageFont.load_default()


def render_cluster(count: int | None, dest: Path, plus=False):
    scale = 4
    size = 112 * scale
    cx = cy = size / 2
    r = 44 * scale

    shadow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.ellipse((cx - r + 2 * scale, cy - r + 4 * scale, cx + r + 2 * scale, cy + r + 4 * scale), fill=(40, 48, 56, 55))
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=2.2 * scale))

    body = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bd = ImageDraw.Draw(body)
    # 蓝底白字聚合气泡（低饱和蓝灰，与校园地图主色一致）
    cluster_blue = (74, 118, 168, 255)
    bd.ellipse((cx - r - 3 * scale, cy - r - 3 * scale, cx + r + 3 * scale, cy + r + 3 * scale), fill=(255, 255, 255, 255))
    bd.ellipse((cx - r, cy - r, cx + r, cy + r), fill=cluster_blue)

    if count is not None:
        text = f"{count}+" if plus else str(count)
        font = load_font(52 * scale if len(text) == 1 else (42 * scale if len(text) == 2 else 34 * scale))
        bbox = bd.textbbox((0, 0), text, font=font)
        tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
        tx = cx - tw / 2 - bbox[0]
        ty = cy - th / 2 - bbox[1] - 1.2 * scale
        bd.text((tx, ty), text, font=font, fill=(255, 255, 255, 255))

    out = Image.alpha_composite(shadow, body).resize((112, 112), Image.Resampling.LANCZOS)
    dest.parent.mkdir(parents=True, exist_ok=True)
    out.save(dest)


def write_clusters():
    render_cluster(None, OUT / "pin-cluster.png")
    for n in range(2, 20):
        render_cluster(n, OUT / f"pin-cluster-{n}.png")
    render_cluster(20, OUT / "pin-cluster-20.png", plus=True)


def main():
    import sys
    if "--clusters-only" not in sys.argv:
        for name, color in PIN_COLORS.items():
            render_pin(color, OUT / name)
    if "--pins-only" not in sys.argv:
        write_clusters()
    print(f"wrote pins and clusters to {OUT}")


if __name__ == "__main__":
    main()
