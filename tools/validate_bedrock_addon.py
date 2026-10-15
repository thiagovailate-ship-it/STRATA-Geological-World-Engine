#!/usr/bin/env python3
"""Static validation for the native STRATA Bedrock 1.26.52 add-on.

This validates pack relationships and referenced files, not runtime acceptance by
Minecraft. The latter still requires importing the pack into Bedrock itself.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import zipfile
from pathlib import Path, PurePosixPath
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
BP = ROOT / "bedrock" / "STRATA_BehaviorPack"
RP = ROOT / "bedrock" / "STRATA_ResourcePack"
EXPECTED_MIN_ENGINE = [1, 26, 52]
EXPECTED_PACK_VERSION = [1, 2, 0]
EXPECTED_SCRIPT_API = "2.9.0"
# Bedrock retains several legacy identifiers where Java uses newer display IDs.
# This is the exact vanilla target set used by the first STRATA biome slice.
PORTED_BEDROCK_TARGETS = {
    "stony_peaks", "jagged_peaks", "frozen_peaks", "extreme_hills_mutated",
    "mesa", "mesa_bryce", "savanna", "savanna_mutated", "desert",
    "forest", "birch_forest", "birch_forest_mutated", "taiga",
    "plains", "sunflower_plains", "meadow", "river", "swampland",
    "mangrove_swamp", "ocean", "deep_ocean", "cold_ocean",
    "deep_cold_ocean", "lukewarm_ocean", "deep_lukewarm_ocean",
    "deep_warm_ocean", "warm_ocean",
}
IDENTIFIER_RE = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")
UUID_RE = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
    re.IGNORECASE,
)


class ValidationError(Exception):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)


def load_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise ValidationError(f"{path.relative_to(ROOT)}: invalid JSON ({exc})") from exc


def get_component(data: dict[str, Any], root_key: str, component: str) -> dict[str, Any]:
    root_obj = data.get(root_key)
    require(isinstance(root_obj, dict), f"missing object {root_key}")
    components = root_obj.get("components", {})
    require(isinstance(components, dict), f"{root_key}.components must be an object")
    result = components.get(component, {})
    require(isinstance(result, dict), f"{root_key}.components.{component} must be an object")
    return result


def validate_source() -> tuple[dict[str, Any], dict[str, Any], int]:
    require(BP.is_dir(), "behavior pack directory is missing")
    require(RP.is_dir(), "resource pack directory is missing")
    bp_manifest = load_json(BP / "manifest.json")
    rp_manifest = load_json(RP / "manifest.json")

    require(bp_manifest.get("format_version") == 2, "behavior manifest format_version must be 2")
    require(rp_manifest.get("format_version") == 2, "resource manifest format_version must be 2")
    manifests = (("behavior", bp_manifest), ("resource", rp_manifest))
    seen_uuids: set[str] = set()
    for label, manifest in manifests:
        header = manifest.get("header", {})
        require(isinstance(header, dict), f"{label} manifest header must be an object")
        require(bool(header.get("name")), f"{label} manifest name is missing")
        require(header.get("min_engine_version") == EXPECTED_MIN_ENGINE,
                f"{label} manifest must target Minecraft 1.26.52")
        require(header.get("version") == EXPECTED_PACK_VERSION,
                f"{label} pack version must be {EXPECTED_PACK_VERSION}")
        modules = manifest.get("modules", [])
        require(isinstance(modules, list), f"{label} modules must be an array")
        for uuid_value in [header.get("uuid"), *[m.get("uuid") for m in modules]]:
            require(isinstance(uuid_value, str) and UUID_RE.fullmatch(uuid_value) is not None,
                    f"{label} manifest contains an invalid UUID: {uuid_value!r}")
            normalized_uuid = uuid_value.lower()
            require(normalized_uuid not in seen_uuids, f"duplicate manifest UUID: {uuid_value}")
            seen_uuids.add(normalized_uuid)

    bp_modules = bp_manifest.get("modules", [])
    require(len(bp_modules) == 2, "behavior pack must contain one data and one script module")
    bp_module_types = {module.get("type") for module in bp_modules}
    require(bp_module_types == {"data", "script"},
            "behavior pack modules must be exactly data and script")
    script_module = next(module for module in bp_modules if module.get("type") == "script")
    require(script_module.get("language") == "javascript", "script module language must be javascript")
    script_entry = script_module.get("entry")
    require(script_entry == "scripts/main.js", "script entry must be scripts/main.js")
    require((BP / script_entry).is_file(), "behavior pack script entry file is missing")
    rp_modules = rp_manifest.get("modules", [])
    require(len(rp_modules) == 1 and rp_modules[0].get("type") == "resources",
            "resource pack must contain exactly one resources module")

    bp_header = bp_manifest["header"]
    rp_header = rp_manifest["header"]
    dependencies = bp_manifest.get("dependencies", [])
    expected_dependencies = [
        {"uuid": rp_header["uuid"], "version": EXPECTED_PACK_VERSION},
        {"module_name": "@minecraft/server", "version": EXPECTED_SCRIPT_API},
    ]
    require(dependencies == expected_dependencies,
            "behavior pack must depend on the matching resource pack and stable @minecraft/server 2.9.0")

    all_json = list(BP.rglob("*.json")) + list(RP.rglob("*.json"))
    for path in all_json:
        load_json(path)

    # Every custom block maps to one existing texture and one localized name.
    block_files = sorted((BP / "blocks").glob("*.json"))
    blocks: dict[str, dict[str, Any]] = {}
    terrain = load_json(RP / "textures" / "terrain_texture.json")
    texture_data = terrain.get("texture_data", {})
    require(isinstance(texture_data, dict), "terrain_texture.json texture_data must be an object")
    language = (RP / "texts" / "en_US.lang").read_text(encoding="utf-8")
    for path in block_files:
        data = load_json(path)
        require(data.get("format_version") == "1.26.50",
                f"{path.relative_to(ROOT)} must use block format 1.26.50")
        definition = data.get("minecraft:block", {})
        identifier = definition.get("description", {}).get("identifier")
        require(isinstance(identifier, str) and IDENTIFIER_RE.fullmatch(identifier) is not None,
                f"{path.relative_to(ROOT)} has an invalid block identifier")
        require(identifier.startswith("strata:"), f"custom block namespace must be strata: ({identifier})")
        require(identifier not in blocks, f"duplicate custom block identifier: {identifier}")
        blocks[identifier] = definition
        components = definition.get("components", {})
        mining = components.get("minecraft:destructible_by_mining", {}).get("seconds_to_destroy")
        explosion = components.get("minecraft:destructible_by_explosion", {}).get("explosion_resistance")
        require(mining == 1.6 and explosion == 10.0,
                f"{identifier} must preserve STRATA's common 1.6/10.0 rock-block basis")
        texture = components.get("minecraft:material_instances", {}).get("*", {}).get("texture")
        require(texture in texture_data, f"{identifier} refers to unregistered texture {texture!r}")
        for visual_name in ("minecraft:material_instances", "minecraft:item_visual"):
            visual = components.get(visual_name, {})
            instances = visual.get("material_instances", visual) if visual_name.endswith("item_visual") else visual
            for instance in instances.values():
                if isinstance(instance, dict) and "texture" in instance:
                    require(instance["texture"] in texture_data,
                            f"{identifier} {visual_name} refers to unknown texture {instance['texture']!r}")
        texture_rel = texture_data[texture].get("textures")
        require(isinstance(texture_rel, str), f"texture {texture!r} must map to a texture path")
        texture_path = RP / f"{texture_rel}.png"
        require(texture_path.is_file(), f"missing block texture {texture_path.relative_to(ROOT)}")
        texture_bytes = texture_path.read_bytes()
        java_texture = ROOT / "src" / "main" / "resources" / "assets" / "strata" / "textures" / "blocks" / texture_path.name
        require(java_texture.is_file(), f"missing Forge source texture {java_texture.relative_to(ROOT)}")
        require(texture_bytes == java_texture.read_bytes(),
                f"Bedrock texture {texture_path.name} has drifted from the existing Forge texture")
        png_header = texture_bytes[:24]
        require(png_header[:8] == b"\x89PNG\r\n\x1a\n", f"{texture_path.name} is not a PNG")
        width = int.from_bytes(png_header[16:20], "big")
        height = int.from_bytes(png_header[20:24], "big")
        require((width, height) == (16, 16), f"{texture_path.name} must stay 16x16 (got {width}x{height})")
        lang_key = f"tile.{identifier}.name="
        require(lang_key in language, f"missing localization entry for {identifier}")

    require(len(blocks) == 12, f"expected the twelve STRATA rock variants, found {len(blocks)}")

    # The scripted dimension is entered through one custom, non-consuming item.
    item_files = sorted((BP / "items").glob("*.json"))
    require(len(item_files) == 1 and item_files[0].name == "geology_compass.json",
            "behavior pack must define exactly the STRATA geology compass item")
    item_data = load_json(item_files[0])
    require(item_data.get("format_version") == "1.21.90",
            "geology compass must use item format 1.21.90")
    item_definition = item_data.get("minecraft:item", {})
    item_id = item_definition.get("description", {}).get("identifier")
    require(item_id == "strata:geology_compass", "geology compass identifier must be strata:geology_compass")
    item_components = item_definition.get("components", {})
    icon_key = item_components.get("minecraft:icon", {}).get("textures", {}).get("default")
    require(icon_key == "strata:geology_compass", "geology compass icon must reference its resource atlas key")
    require(item_components.get("minecraft:use_animation") == "spyglass"
            and item_components.get("minecraft:max_stack_size") == 1,
            "geology compass must be a single-stack usable item")
    item_atlas = load_json(RP / "textures" / "item_texture.json")
    item_textures = item_atlas.get("texture_data", {})
    require(icon_key in item_textures, "geology compass icon key is not registered in the resource pack")
    item_texture_path = item_textures[icon_key].get("textures")
    require(isinstance(item_texture_path, str)
            and (RP / f"{item_texture_path}.png").is_file(),
            "geology compass atlas points to a missing item texture")
    require("item.strata:geology_compass.name=" in language,
            "missing geology compass localization entry")
    script_text = (BP / "scripts" / "main.js").read_text(encoding="utf-8")
    geology_script_text = (BP / "scripts" / "geology_model.js").read_text(encoding="utf-8")
    require('from "@minecraft/server"' in script_text,
            "Bedrock script entry must import the stable Minecraft server API")
    require("registerCustomDimension" in script_text and "world.seed" in script_text,
            "script must register its void dimension and derive terrain from the world seed")
    require("getTopmostBlock" in script_text and "buildSpawnPlatform" in script_text,
            "script must return players to a terrain-safe Overworld location and protect fallback geology spawns")
    require("sampleColumnAsync" in geology_script_text and "buildHydroTileSteps" in geology_script_text,
            "geology model must expose deterministic columns and time-sliced hydrology")
    require(re.search(r"if\s*\(\(\+\+floodSteps\s*&\s*511\)\s*===\s*0\)\s*yield;",
                      geology_script_text) is not None,
            "hydrology flood-fill must yield periodically while visiting priority-queue cells")
    require("flowOrder.sort" in geology_script_text
            and "accumulation[next] += accumulation[current]" in geology_script_text
            and "WALK_CAP" not in geology_script_text,
            "hydrology must accumulate D8 contributing area in descending flow order without a path cap")

    # Validate all biome blocks, identifiers, surface references and replace rules.
    biome_files = sorted((BP / "biomes").glob("*.json"))
    biomes: dict[str, dict[str, Any]] = {}
    biome_tags: set[str] = set()
    replace_targets: dict[str, str] = {}
    for path in biome_files:
        data = load_json(path)
        require(data.get("format_version") == "1.26.50",
                f"{path.relative_to(ROOT)} must use biome format 1.26.50")
        definition = data.get("minecraft:biome", {})
        identifier = definition.get("description", {}).get("identifier")
        require(isinstance(identifier, str) and IDENTIFIER_RE.fullmatch(identifier) is not None,
                f"{path.relative_to(ROOT)} has an invalid biome identifier")
        require(identifier.startswith("strata:"), f"custom biome namespace must be strata: ({identifier})")
        require(identifier not in biomes, f"duplicate custom biome identifier: {identifier}")
        biomes[identifier] = definition
        components = definition.get("components", {})
        climate = components.get("minecraft:climate", {})
        require(isinstance(climate.get("temperature"), (int, float)), f"{identifier} needs temperature")
        require(isinstance(climate.get("downfall"), (int, float)), f"{identifier} needs downfall")
        surface = get_component(data, "minecraft:biome", "minecraft:surface_builder").get("builder", {})
        for field in ("top_material", "mid_material", "foundation_material", "sea_floor_material"):
            material = surface.get(field)
            require(isinstance(material, str), f"{identifier} surface builder missing {field}")
            if material.startswith("strata:"):
                require(material in blocks, f"{identifier} refers to unknown surface block {material}")
        tags = get_component(data, "minecraft:biome", "minecraft:tags").get("tags", [])
        require(isinstance(tags, list), f"{identifier} biome tags must be an array")
        biome_tags.update(tag for tag in tags if isinstance(tag, str) and tag.startswith("strata:"))
        replacements = get_component(data, "minecraft:biome", "minecraft:replace_biomes").get("replacements", [])
        require(isinstance(replacements, list) and replacements, f"{identifier} needs replace_biomes rules")
        for replacement in replacements:
            require(replacement.get("dimension") == "minecraft:overworld",
                    f"{identifier} must replace only the Overworld")
            targets = replacement.get("targets", [])
            amount = replacement.get("amount")
            frequency = replacement.get("noise_frequency_scale")
            require(isinstance(targets, list) and targets, f"{identifier} has no vanilla biome targets")
            require(isinstance(amount, (int, float)) and 0.0 < amount <= 1.0,
                    f"{identifier} replacement amount must be in (0, 1]")
            require(isinstance(frequency, (int, float)) and frequency > 0,
                    f"{identifier} noise_frequency_scale must be positive")
            for target in targets:
                require(isinstance(target, str) and target, f"{identifier} has an empty biome target")
                require(target in PORTED_BEDROCK_TARGETS,
                        f"{identifier} uses an unverified Bedrock biome target {target!r}")
                require(target not in replace_targets,
                        f"vanilla biome {target} is targeted by both {replace_targets.get(target)} and {identifier}")
                replace_targets[target] = identifier

    require(len(biomes) == 8, f"expected eight formation biomes, found {len(biomes)}")
    client_files = sorted((RP / "biomes").glob("*.json"))
    client_ids: set[str] = set()
    for path in client_files:
        data = load_json(path)
        require(data.get("format_version") == "1.21.130",
                f"{path.relative_to(ROOT)} must use client-biome format 1.21.130")
        identifier = data.get("minecraft:client_biome", {}).get("description", {}).get("identifier")
        require(identifier in biomes, f"client biome {identifier!r} has no behavior-pack biome")
        require(identifier not in client_ids, f"duplicate client biome identifier: {identifier}")
        client_ids.add(identifier)
    require(client_ids == set(biomes), "resource pack must define one client biome for every behavior biome")

    # Feature identifiers are matched to their filenames and custom rock IDs.
    feature_files = sorted((BP / "features").glob("*.json"))
    features: dict[str, dict[str, Any]] = {}
    for path in feature_files:
        data = load_json(path)
        require(data.get("format_version") == "1.13.0",
                f"{path.relative_to(ROOT)} must use feature format 1.13.0")
        feature = data.get("minecraft:ore_feature", {})
        identifier = feature.get("description", {}).get("identifier")
        require(isinstance(identifier, str) and IDENTIFIER_RE.fullmatch(identifier) is not None,
                f"{path.relative_to(ROOT)} has an invalid feature identifier")
        require(identifier.rsplit(":", 1)[-1] == path.stem,
                f"feature identifier {identifier} must match filename {path.name}")
        require(identifier not in features, f"duplicate feature identifier: {identifier}")
        rules = feature.get("replace_rules", [])
        require(isinstance(rules, list) and rules, f"{identifier} has no replace_rules")
        for replace_rule in rules:
            block = replace_rule.get("places_block")
            require(isinstance(block, str), f"{identifier} has a replace rule without places_block")
            if block.startswith("strata:"):
                require(block in blocks, f"{identifier} places unknown block {block}")
            may_replace = replace_rule.get("may_replace", [])
            require(isinstance(may_replace, list) and may_replace,
                    f"{identifier} must constrain every rock replacement")
        features[identifier] = feature

    rule_files = sorted((BP / "feature_rules").glob("*.json"))
    rule_features: set[str] = set()
    for path in rule_files:
        data = load_json(path)
        require(data.get("format_version") == "1.13.0",
                f"{path.relative_to(ROOT)} must use feature-rule format 1.13.0")
        rule = data.get("minecraft:feature_rules", {})
        description = rule.get("description", {})
        identifier = description.get("identifier")
        feature_id = description.get("places_feature")
        require(isinstance(identifier, str) and IDENTIFIER_RE.fullmatch(identifier) is not None,
                f"{path.relative_to(ROOT)} has an invalid feature-rule identifier")
        require(identifier.rsplit(":", 1)[-1] == path.stem,
                f"feature-rule identifier {identifier} must match filename {path.name}")
        require(feature_id in features, f"feature rule {identifier} refers to missing feature {feature_id}")
        require(feature_id not in rule_features, f"feature {feature_id} is attached more than once")
        rule_features.add(feature_id)
        conditions = rule.get("conditions", {})
        require(conditions.get("placement_pass") == "underground_pass",
                f"{identifier} must run in underground_pass")
        filters = conditions.get("minecraft:biome_filter", [])
        tag_values = [f.get("value") for f in filters if isinstance(f, dict) and f.get("test") == "has_biome_tag"]
        require(len(tag_values) == 1 and tag_values[0] in biome_tags,
                f"{identifier} must filter on a declared STRATA formation tag")
        distribution = rule.get("distribution", {})
        require(distribution.get("iterations", 0) >= 1,
                f"{identifier} must have at least one placement iteration")

    require(len(feature_files) == len(biomes), "each formation biome must have one rock feature")
    require(len(rule_files) == len(biomes), "each formation biome must have one feature rule")
    require(rule_features == set(features), "every rock feature must be attached exactly once")
    return bp_manifest, rp_manifest, len(all_json)


def validate_archive(archive_path: Path, bp_manifest: dict[str, Any], rp_manifest: dict[str, Any]) -> int:
    require(archive_path.is_file(), f"add-on archive is missing: {archive_path}")
    require(archive_path.stat().st_size > 0, "add-on archive is empty")
    expected_members = {
        "STRATA-Geological-World-Engine-BP.mcpack",
        "STRATA-Geological-World-Engine-RP.mcpack",
    }
    try:
        with zipfile.ZipFile(archive_path) as outer:
            names = {name for name in outer.namelist() if not name.endswith("/")}
            require(names == expected_members,
                    f".mcaddon must contain exactly the two .mcpack files (got {sorted(names)})")
            for pack_name, expected_manifest, source_dir in (
                ("STRATA-Geological-World-Engine-BP.mcpack", bp_manifest, BP),
                ("STRATA-Geological-World-Engine-RP.mcpack", rp_manifest, RP),
            ):
                payload = outer.read(pack_name)
                from io import BytesIO
                with zipfile.ZipFile(BytesIO(payload)) as pack:
                    pack_names = set(pack.namelist())
                    require("manifest.json" in pack_names,
                            f"{pack_name} must have manifest.json at archive root")
                    expected_names = {
                        path.relative_to(source_dir).as_posix()
                        for path in source_dir.rglob("*") if path.is_file()
                    }
                    require(pack_names == expected_names,
                            f"{pack_name} file list differs from the validated source tree")
                    embedded_manifest = json.loads(pack.read("manifest.json").decode("utf-8"))
                    require(embedded_manifest == expected_manifest,
                            f"{pack_name} manifest differs from validated source")
                    require(all(not name.startswith("/") and ".." not in PurePosixPath(name).parts
                                for name in pack_names),
                            f"{pack_name} contains an unsafe archive path")
                    for name in expected_names:
                        require(pack.read(name) == (source_dir / name).read_bytes(),
                                f"{pack_name} contains stale or altered source file {name}")
                    bad_member = pack.testzip()
                    require(bad_member is None, f"{pack_name} has a corrupt member: {bad_member}")
            bad_outer = outer.testzip()
            require(bad_outer is None, f".mcaddon has a corrupt member: {bad_outer}")
    except (OSError, zipfile.BadZipFile, json.JSONDecodeError, UnicodeDecodeError) as exc:
        raise ValidationError(f"cannot read add-on archive {archive_path}: {exc}") from exc
    return archive_path.stat().st_size


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, help="also validate a built .mcaddon archive")
    args = parser.parse_args()
    try:
        bp_manifest, rp_manifest, json_count = validate_source()
        print(f"PASS: manifests, {json_count} JSON files, 12 rock blocks, geology compass, scripted dimension/API link, 8 biomes, 8 features/rules, and texture links")
        if args.archive:
            size = validate_archive(args.archive.resolve(), bp_manifest, rp_manifest)
            print(f"PASS: .mcaddon import container and nested .mcpack archives ({size:,} bytes)")
    except (ValidationError, OSError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
