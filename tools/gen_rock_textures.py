#!/usr/bin/env python3
"""Procedural 16x16 rock textures for the STRATA rock block.

Deterministic (seeded stdlib random only): 12 variants, one per GeoMaterial
rock, written to src/main/resources/assets/strata/textures/blocks/.
"""
import os
import random
import struct
import sys
import zlib

SIZE = 16
OUT = os.path.join("src", "main", "resources", "assets", "strata",
                   "textures", "blocks")

# name -> (base RGB, style)
ROCKS = [
    ("granite", (168, 134, 118), "speckle"),
    ("basalt", (48, 48, 54), "plain"),
    ("limestone", (214, 205, 178), "plain"),
    ("sandstone", (216, 194, 140), "strata"),
    ("shale", (96, 100, 108), "strata"),
    ("marble", (232, 230, 224), "vein"),
    ("gneiss", (150, 138, 140), "band"),
    ("schist", (130, 128, 135), "sparkle"),
    ("quartzite", (206, 198, 190), "plain"),
    ("slate", (110, 122, 138), "strata"),
    ("andesite", (142, 142, 146), "speckle"),
    ("conglomerate", (160, 148, 132), "pebble"),
]


def clamp(v):
    return max(0, min(255, int(round(v))))


def write_png(path, pixels):
    def chunk(typ, data):
        out = struct.pack(">I", len(data)) + typ + data
        return out + struct.pack(">I", zlib.crc32(typ + data) & 0xFFFFFFFF)

    raw = bytearray()
    for y in range(SIZE):
        raw.append(0)
        for x in range(SIZE):
            r, g, b = pixels[y][x]
            raw += bytes((clamp(r), clamp(g), clamp(b)))
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 2, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(bytes(raw)))
           + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)


def gen(name, base, style, seed):
    rng = random.Random(seed)
    px = [[list(base) for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            n = rng.gauss(0.0, 9.0)
            px[y][x] = [c + n for c in base]
    if style == "speckle":
        for _ in range(26):
            x, y = rng.randrange(SIZE), rng.randrange(SIZE)
            d = -26 if rng.random() < 0.6 else 30
            px[y][x] = [c + d for c in px[y][x]]
    elif style == "strata":
        for y in range(SIZE):
            if (y + rng.randrange(2)) % 4 == 0:
                for x in range(SIZE):
                    px[y][x] = [c - 22 for c in px[y][x]]
    elif style == "band":
        for y in range(SIZE):
            import math
            band = math.sin(y * 1.1 + rng.gauss(0, 0.3)) * 13.0
            for x in range(SIZE):
                r, g, b = px[y][x]
                px[y][x] = [r + band, g + band * 0.4, b - band * 0.5]
    elif style == "sparkle":
        for _ in range(10):
            x, y = rng.randrange(SIZE), rng.randrange(SIZE)
            px[y][x] = [235, 235, 245]
    elif style == "vein":
        for _ in range(3):
            x, y = rng.randrange(SIZE), rng.randrange(SIZE)
            for _ in range(14):
                px[y][x] = [c + 14 for c in px[y][x]]
                x = (x + rng.choice((-1, 0, 1))) % SIZE
                y = (y + 1) % SIZE
    elif style == "pebble":
        for _ in range(11):
            cx, cy = rng.randrange(SIZE), rng.randrange(SIZE)
            rad = rng.choice((1, 1, 2))
            shade = rng.gauss(0, 34)
            for y in range(SIZE):
                for x in range(SIZE):
                    dx = min(abs(x - cx), SIZE - abs(x - cx))
                    dy = abs(y - cy)
                    if dx * dx + dy * dy <= rad * rad:
                        px[y][x] = [c + shade for c in px[y][x]]
    # Clamp once at the end (keeps veins/speckles vivid).
    return [[tuple(clamp(c) for c in p) for p in row] for row in px]


def main():
    os.makedirs(OUT, exist_ok=True)
    for i, (name, base, style) in enumerate(ROCKS):
        pixels = gen(name, base, style, 0x51A7 + i)
        path = os.path.join(OUT, "rock_" + name + ".png")
        write_png(path, pixels)
        print("wrote " + path)


if __name__ == "__main__":
    main()
