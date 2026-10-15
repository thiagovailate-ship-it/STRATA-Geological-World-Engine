#!/usr/bin/env python3
"""Generate STRATA's 12 ORIGINAL 16x16 rock textures (stdlib only).

Each texture is deterministic value-noise stone with a rock-specific palette
and grain structure. No third-party art is used or imitated; every pixel is
computed here. Output: src/main/resources/assets/strata/textures/blocks/.
"""
import math
import os
import struct
import zlib

OUT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                       "src", "main", "resources", "assets", "strata",
                       "textures", "blocks")

# name -> (base rgb, dark rgb, light rgb, grain, speckle, bands)
ROCKS = {
    "granite":      ((168, 126, 118), (110, 80, 76),  (214, 178, 168), 0.55, 0.30, 0.0),
    "basalt":       ((52, 52, 58),    (28, 28, 34),   (88, 88, 98),    0.40, 0.10, 0.0),
    "limestone":    ((196, 188, 160), (150, 140, 114), (228, 222, 198), 0.35, 0.08, 0.25),
    "sandstone":    ((203, 178, 130), (158, 132, 92),  (232, 208, 164), 0.30, 0.12, 0.55),
    "shale":        ((116, 112, 120), (74, 72, 80),    (152, 150, 158), 0.30, 0.05, 0.85),
    "marble":       ((226, 226, 230), (178, 182, 192), (246, 246, 250), 0.25, 0.04, 0.0),
    "gneiss":       ((150, 138, 150), (100, 90, 104),  (196, 184, 198), 0.45, 0.10, 0.65),
    "schist":       ((128, 134, 148), (82, 88, 104),   (172, 180, 196), 0.50, 0.16, 0.75),
    "quartzite":    ((210, 206, 200), (164, 158, 150), (240, 238, 232), 0.45, 0.22, 0.15),
    "slate":        ((84, 92, 104),   (52, 58, 68),    (120, 130, 144), 0.35, 0.06, 0.90),
    "andesite":     ((136, 136, 140), (96, 96, 100),   (176, 176, 182), 0.50, 0.18, 0.10),
    "conglomerate": ((170, 158, 140), (120, 108, 92),  (212, 200, 182), 0.60, 0.55, 0.0),
}


def hash01(x, y, seed):
    h = (seed * 0x9E3779B97F4A7C15 + x * 0xBF58476D1CE4E5B9 + y * 0x94D049BB133111EB) & 0xFFFFFFFFFFFFFFFF
    h = (h ^ (h >> 30)) * 0xBF58476D1CE4E5B9 & 0xFFFFFFFFFFFFFFFF
    h = (h ^ (h >> 27)) * 0x94D049BB133111EB & 0xFFFFFFFFFFFFFFFF
    h ^= h >> 31
    return (h & 0xFFFFFFFFFFFF) / float(0xFFFFFFFFFFFF)


def fade(t):
    return t * t * t * (t * (t * 6 - 15) + 10)


def vnoise(x, y, seed):
    xi, yi = math.floor(x), math.floor(y)
    xf, yf = x - xi, y - yi
    a = hash01(xi, yi, seed)
    b = hash01(xi + 1, yi, seed)
    c = hash01(xi, yi + 1, seed)
    d = hash01(xi + 1, yi + 1, seed)
    u, v = fade(xf), fade(yf)
    return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v


def write_png(path, w, h, flat):
    def chunk(ctype, data):
        c = struct.pack(">I", len(data)) + ctype + data
        return c + struct.pack(">I", zlib.crc32(ctype + data) & 0xFFFFFFFF)

    # flat is a w*h*3 list of channel bytes, row-major.
    data = b""
    idx = 0
    for _ in range(h):
        data += b"\x00" + bytes(flat[idx:idx + w * 3])
        idx += w * 3
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(data, 9))
           + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)


def make_texture(name, spec):
    base, dark, light, grain, speckle, bands = spec
    seed = sum((i + 1) * (ord(c) + 7) for i, c in enumerate(name)) & 0xFFFFFFFF
    px = []
    for y in range(16):
        for x in range(16):
            n1 = vnoise(x * 0.55 + seed, y * 0.55, seed)
            n2 = vnoise(x * 0.22 + 40, y * 0.22 + 9, seed ^ 0x51F15E)
            band = 0.5 + 0.5 * math.sin((x * 0.7 + y * 1.15) + n2 * 5.0)
            g = grain * (n1 - 0.5) * 2.0
            if bands:
                g = g * (1 - bands) + (band - 0.5) * 2.0 * bands
            t = max(0.0, min(1.0, 0.5 + g * 0.5))
            if t < 0.5:
                k = t * 2.0
                r = dark[0] + (base[0] - dark[0]) * k
                g_ = dark[1] + (base[1] - dark[1]) * k
                b = dark[2] + (base[2] - dark[2]) * k
            else:
                k = (t - 0.5) * 2.0
                r = base[0] + (light[0] - base[0]) * k
                g_ = base[1] + (light[1] - base[1]) * k
                b = base[2] + (light[2] - base[2]) * k
            s = hash01(x * 3 + 1, y * 5 + 2, seed ^ 0xBEEF)
            if s < speckle * 0.12:
                r, g_, b = light
            elif s > 1.0 - speckle * 0.10:
                r, g_, b = dark
            px += [int(max(0, min(255, r))), int(max(0, min(255, g_))), int(max(0, min(255, b)))]
    return px


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for name, spec in sorted(ROCKS.items()):
        path = os.path.join(OUT_DIR, "rock_%s.png" % name)
        write_png(path, 16, 16, make_texture(name, spec))
        print("wrote %s" % path)


if __name__ == "__main__":
    main()
