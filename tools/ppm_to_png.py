#!/usr/bin/env python3
"""Convert binary PPM (P6) lab maps to PNG (stdlib only). Usage:
   tools/ppm_to_png.py in.ppm [out.png]
"""
import struct
import sys
import zlib


def read_ppm(path):
    with open(path, "rb") as f:
        magic = f.readline().strip()
        assert magic == b"P6", "not a P6 ppm: %s" % path
        line = f.readline()
        while line.startswith(b"#"):
            line = f.readline()
        w, h = [int(x) for x in line.split()]
        maxval = int(f.readline().strip())
        assert maxval == 255
        data = f.read(w * h * 3)
        assert len(data) == w * h * 3, "truncated ppm"
    return w, h, data


def write_png(path, w, h, data):
    def chunk(ctype, payload):
        c = struct.pack(">I", len(payload)) + ctype + payload
        return c + struct.pack(">I", zlib.crc32(ctype + payload) & 0xFFFFFFFF)

    raw = b"".join(b"\x00" + data[y * w * 3:(y + 1) * w * 3] for y in range(h))
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 6))
           + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)


def main():
    src = sys.argv[1]
    dst = sys.argv[2] if len(sys.argv) > 2 else src.rsplit(".", 1)[0] + ".png"
    w, h, data = read_ppm(src)
    write_png(dst, w, h, data)
    print("wrote %s (%dx%d)" % (dst, w, h))


if __name__ == "__main__":
    main()
