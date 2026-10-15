# STRATA Bedrock port — first native add-on slice

- **Target:** Minecraft Bedrock Edition 1.26.52
- **Artifact:** `bedrock/releases/STRATA-Geological-World-Engine-Bedrock-1.26.52.mcaddon`
- **Scope:** a native behavior pack + resource pack; this is not a Java/Forge binary recompiled for Bedrock.

## What this slice ports

- The twelve STRATA rock materials are separate Bedrock blocks (`strata:<rock>`), with their existing 16×16 PNG textures copied directly from the Forge resources. Each has a block/item visual, map color, mining/explosion values, stone sound, and an English creative-inventory name. All retain Forge's shared 1.6 hardness / 10.0 blast-resistance basis; Bedrock's `seconds_to_destroy` is not a one-to-one Forge hardness measure, and Forge's pickaxe harvest-level-1 gate is not ported yet. Mining/drop parity needs an in-game check.
- Eight formation-themed custom biomes use Bedrock's `minecraft:replace_biomes` to modify selected vanilla Overworld biomes. They change surface/subsurface materials, climate and client-side water/plant tints; they do **not** change the Overworld height field.
- One underground rock-vein feature/rule pair is attached to each custom formation biome. The feature replaces vanilla stone and deepslate with the formation's representative STRATA materials.

| STRATA formation | Vanilla replacement targets | Bedrock surface/vein material examples |
|---|---|---|
| Orogen | stony/jagged/frozen peaks, windswept gravelly hills | gneiss, schist, granite |
| Volcanic | badlands, eroded badlands | basalt, andesite |
| Rift | savanna, windswept savanna | sandstone, shale, basalt |
| Carbonate | desert | limestone, shale |
| Shield | forest, birch forest, old-growth birch forest, taiga | gneiss, granite |
| Clastic basin | plains, sunflower plains, meadow | shale, sandstone |
| Alluvial | rivers, swamps, mangrove swamp | conglomerate, sandstone |
| Oceanic | Temperate, cold, lukewarm and warm Overworld ocean families (frozen oceans stay vanilla) | basalt, andesite |

The target identifiers above are Bedrock identifiers, which differ from Java for some biomes: for example, Badlands is `mesa`, Eroded Badlands is `mesa_bryce`, Swamp is `swampland`, Windswept Savanna is `savanna_mutated`, Old Growth Birch Forest is `birch_forest_mutated`, and Windswept Gravelly Hills is `extreme_hills_mutated`. The validator guards the exact vanilla target IDs used by this slice.

`marble`, `quartzite`, and `slate` are registered as usable custom blocks, but are not yet selected by the first-pass surface/vein rules. The native port is incremental; their precise world-generation roles should be added after the first in-game slice is inspected. Current biome replacement amounts are 0.10–0.16, noise scales 26–32, and each formation feature has one placement iteration with a 12-block vein count. These are provisional implementation defaults, not measured coverage targets.

## Important engine boundary

Bedrock add-ons cannot replace STRATA's custom Overworld terrain-height/relief generator through custom biomes. Custom biomes can replace portions of selected vanilla biomes and change their climate, surface materials, tags and features, but the underlying Overworld terrain geometry remains Bedrock's. This add-on therefore ports geological materials and regional composition, **not** plate-driven mountain/trench/rift geometry, STRATA's drainage/erosion pipeline, Java biome placement, or the Forge chunk generator. It does not promise equal terrain at the same coordinates or seed across editions.

The biome replacement `amount`, noise scales and feature placement settings in this first slice are implementation defaults, not measured geological targets and not a claim of geological realism. No Bedrock runtime or distribution measurements were available in this build environment. Treat those parameters as provisional: before tuning them, sample the same fixed seeds and coordinates with the pack off/on in Bedrock and record biome/material coverage. Do not infer geological accuracy from a successful JSON/package check.

## Build and static validation

From the repository root, using Python 3.9 or later and only the standard library:

```bash
python3 tools/validate_bedrock_addon.py
python3 tools/package_bedrock_addon.py \
  --output bedrock/releases/STRATA-Geological-World-Engine-Bedrock-1.26.52.mcaddon
python3 tools/validate_bedrock_addon.py \
  --archive bedrock/releases/STRATA-Geological-World-Engine-Bedrock-1.26.52.mcaddon
```

The validator checks JSON syntax, manifest UUIDs/dependencies/target version, custom block and biome identifiers, texture/language references, feature/rule links and tags, plus the nested `.mcpack` ZIPs inside the `.mcaddon`. Packaging is deterministic for an unchanged source tree. The CI workflow also checks that the tracked release copy matches the freshly built package.

These are **static** checks. They cannot prove that Minecraft 1.26.52 accepts every component or that the new biomes/features look right in a generated world.

## Import and in-game smoke test

1. Build the `.mcaddon` with the command above, then open the resulting file on a Bedrock device to import both packs.
2. Create a **new** test world. Current component references say partial biome replacement is non-experimental starting in 1.21.110, while the creator tutorial still says the **Custom Biomes** experiment may be needed if a biome does not render. For 1.26.52, first test without it; if the biome is missing and the toggle is available, enable it on a disposable test world and retry. Existing chunks are not retroactively regenerated.
3. Confirm that the STRATA behavior pack is enabled. Its manifest depends on the matching resource pack, which should activate with it; confirm both under the world's pack settings.
4. In Creative, search for `STRATA` or use commands such as `/give @s strata:basalt` and `/give @s strata:quartzite` to check the twelve block/item textures and names.
5. With cheats enabled, use `/locate biome strata:orogenic_uplands` (and the other identifiers in `bedrock/STRATA_BehaviorPack/biomes/`) to find newly generated replacement areas. Inspect terrain surfaces and mine below them to check the custom rock materials. Check the content log for pack/feature errors.
6. For a meaningful distribution comparison, create paired new worlds with the **same seed**, one with STRATA disabled and one enabled. Sample identical coordinate windows; record biome shares, surface-block shares and underground rock frequency before changing `amount`, `noise_frequency_scale`, or vein counts. The pack's current defaults have not yet passed this runtime test.

No in-game test has been performed as part of this repository change. Compilation and static validation are not substitutes for the smoke/visual/distribution check above.

## Next increments

1. Import and validate this first slice in Bedrock 1.26.52; measure its biome and block distributions on fixed seeds.
2. Use those measurements to tune replacement coverage and formation-to-vanilla-biome mapping rather than guessing at target proportions.
3. Expand native ore/mineral/strata features and surface exposure while preserving vanilla decoration where possible.
4. Revisit any systems that need Script API only after confirming the API version supported by the target runtime. Script API cannot restore the unavailable custom Overworld terrain-height hook.

## References

- [Microsoft: World generation in Bedrock](https://learn.microsoft.com/en-us/minecraft/creator/documents/world-generation?view=minecraft-bedrock-stable)
- [Microsoft: Custom partial biome replacement](https://learn.microsoft.com/en-us/minecraft/creator/documents/biomes/custompartialbiomereplacement?view=minecraft-bedrock-stable)
- [Microsoft: `minecraft:replace_biomes` reference](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/biomesreference/examples/components/minecraftbiomes_replace_biomes?view=minecraft-bedrock-stable)
- [Microsoft: Experimental feature toggles](https://learn.microsoft.com/en-us/minecraft/creator/documents/experimentalfeaturestoggle?view=minecraft-bedrock-stable)
- [Bedrock Wiki: Biomes and replacement targets](https://wiki.bedrock.dev/world-generation/biomes)
- [Bedrock Wiki: Biome tags and Bedrock identifiers](https://wiki.bedrock.dev/world-generation/biome-tags)
- [Microsoft: Add custom blocks](https://learn.microsoft.com/en-us/minecraft/creator/documents/addcustomdieblock?view=minecraft-bedrock-stable)
- [Microsoft: Pack manifest reference](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/addonsreference/packmanifest?view=minecraft-bedrock-stable)
