from __future__ import annotations

import json
import math
import sys
from pathlib import Path

from PIL import Image


def center_crop_to_aspect(image: Image.Image, target_ratio: float) -> Image.Image:
    width, height = image.size
    if width <= 0 or height <= 0:
        return image
    ratio = width / height
    if abs(ratio - target_ratio) < 1e-6:
        return image
    if ratio > target_ratio:
        new_width = int(round(height * target_ratio))
        left = max(0, (width - new_width) // 2)
        return image.crop((left, 0, left + new_width, height))
    new_height = int(round(width / target_ratio))
    top = max(0, (height - new_height) // 2)
    return image.crop((0, top, width, top + new_height))


def resize(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    return image.resize(size, Image.Resampling.LANCZOS)


def rms_diff(a: Image.Image, b: Image.Image) -> float:
    a_rgb = a.convert("RGB")
    b_rgb = b.convert("RGB")
    if a_rgb.size != b_rgb.size:
        raise ValueError("image size mismatch")
    pixels_a = list(a_rgb.getdata())
    pixels_b = list(b_rgb.getdata())
    acc = 0.0
    for (ra, ga, ba), (rb, gb, bb) in zip(pixels_a, pixels_b):
        acc += (ra - rb) ** 2 + (ga - gb) ** 2 + (ba - bb) ** 2
    mse = acc / (len(pixels_a) * 3)
    return math.sqrt(mse)


def brightness_stats(image: Image.Image) -> dict[str, float]:
    rgb = image.convert("RGB")
    pixels = list(rgb.getdata())
    total = 0.0
    dark = 0
    for r, g, b in pixels:
        luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        total += luminance
        if luminance < 20:
            dark += 1
    count = max(1, len(pixels))
    return {
        "avg_luma": total / count,
        "dark_ratio": dark / count,
    }


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: aui_compare.py <reference> <actual>", file=sys.stderr)
        return 2

    reference_path = Path(sys.argv[1])
    actual_path = Path(sys.argv[2])
    if not reference_path.exists() or not actual_path.exists():
        print(json.dumps({"ok": False, "reason": "missing-file"}))
        return 1

    ref = Image.open(reference_path)
    actual = Image.open(actual_path)
    ref_ratio = ref.width / ref.height
    actual_cropped = center_crop_to_aspect(actual, ref_ratio)
    actual_resized = resize(actual_cropped, ref.size)

    diff = rms_diff(ref, actual_resized)
    stats = brightness_stats(actual_resized)
    similar = diff < 70 and stats["dark_ratio"] < 0.55 and stats["avg_luma"] > 40

    print(json.dumps({
        "ok": similar,
        "rms": round(diff, 3),
        "avg_luma": round(stats["avg_luma"], 3),
        "dark_ratio": round(stats["dark_ratio"], 4),
        "reference": str(reference_path),
        "actual": str(actual_path),
    }, ensure_ascii=False))
    return 0 if similar else 1


if __name__ == "__main__":
    raise SystemExit(main())
