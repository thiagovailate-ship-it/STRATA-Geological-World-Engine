#!/usr/bin/env python3
"""Build reproducible .mcpack files and bundle them as a Bedrock .mcaddon."""

from __future__ import annotations

import argparse
import io
import sys
import zipfile
from pathlib import Path

from validate_bedrock_addon import ValidationError, validate_archive, validate_source

ROOT = Path(__file__).resolve().parents[1]
BP = ROOT / "bedrock" / "STRATA_BehaviorPack"
RP = ROOT / "bedrock" / "STRATA_ResourcePack"
BP_NAME = "STRATA-Geological-World-Engine-BP.mcpack"
RP_NAME = "STRATA-Geological-World-Engine-RP.mcpack"

# DOS ZIP timestamps cannot predate 1980. A fixed timestamp makes identical
# source trees produce byte-for-byte identical packages.
ZIP_TIMESTAMP = (2020, 1, 1, 0, 0, 0)


def add_tree(archive: zipfile.ZipFile, base: Path) -> None:
    for path in sorted(p for p in base.rglob("*") if p.is_file()):
        relative = path.relative_to(base).as_posix()
        info = zipfile.ZipInfo(relative, date_time=ZIP_TIMESTAMP)
        info.compress_type = zipfile.ZIP_DEFLATED
        info.create_system = 3
        info.external_attr = 0o100644 << 16
        archive.writestr(info, path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)


def pack_bytes(base: Path) -> bytes:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        add_tree(archive, base)
    return buffer.getvalue()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("build/bedrock/STRATA-Geological-World-Engine-Bedrock-1.26.52.mcaddon"),
        help="output .mcaddon path (default: build/bedrock/STRATA-Geological-World-Engine-Bedrock-1.26.52.mcaddon)",
    )
    args = parser.parse_args()
    output = args.output if args.output.is_absolute() else ROOT / args.output

    try:
        if output.suffix.lower() != ".mcaddon":
            raise ValidationError("output filename must end in .mcaddon")
        bp_manifest, rp_manifest, _ = validate_source()
        output.parent.mkdir(parents=True, exist_ok=True)
        bp_bytes = pack_bytes(BP)
        rp_bytes = pack_bytes(RP)
        with zipfile.ZipFile(output, "w") as addon:
            for name, payload in ((BP_NAME, bp_bytes), (RP_NAME, rp_bytes)):
                info = zipfile.ZipInfo(name, date_time=ZIP_TIMESTAMP)
                info.compress_type = zipfile.ZIP_DEFLATED
                info.create_system = 3
                info.external_attr = 0o100644 << 16
                addon.writestr(info, payload, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
        size = validate_archive(output, bp_manifest, rp_manifest)
    except (ImportError, OSError, zipfile.BadZipFile, ValueError, ValidationError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    try:
        display_path = output.relative_to(ROOT)
    except ValueError:
        display_path = output
    print(f"Built {display_path} ({size:,} bytes)")
    print("Contents: one behavior .mcpack and one resource .mcpack; import this .mcaddon into Bedrock.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
