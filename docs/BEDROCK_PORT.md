# STRATA Bedrock port — native add-on and scripted geology dimension

- **Target:** Minecraft Bedrock Edition 1.26.52
- **Pack version:** 1.2.0
- **Artifact:** `bedrock/releases/STRATA-Geological-World-Engine-Bedrock-1.26.52.mcaddon`
- **Scope:** native behavior pack + resource pack; this is not a Java/Forge binary recompiled for Bedrock.

## What the add-on contains

- Twelve STRATA rock materials are separate Bedrock blocks (`strata:<rock>`), with their existing 16×16 PNG textures copied directly from the Forge resources. Each has a block/item visual, map color, mining/explosion values, stone sound, and an English creative-inventory name. All retain Forge's shared 1.6 hardness / 10.0 blast-resistance basis; Bedrock's `seconds_to_destroy` is not a one-to-one Forge hardness measure, and Forge's pickaxe harvest-level-1 gate is not ported yet. Mining/drop parity needs an in-game check.
- Eight formation-themed custom biomes use Bedrock's `minecraft:replace_biomes` to modify selected vanilla Overworld biomes. They change surface/subsurface materials, climate and client-side water/plant tints; they do **not** change the Overworld height field.
- One underground rock-vein feature/rule pair is attached to each custom formation biome. The feature replaces vanilla stone and deepslate with the formation's representative STRATA materials.
- An optional, script-generated custom dimension, `strata:geology`, uses deterministic plate-inspired elevation, local drainage, erosion, water and rock layers. It is entered with the non-consuming **STRATA Geological Compass** item. It is separate from the Overworld and does not replace or edit its terrain. See [`BEDROCK_GEOLOGY.md`](BEDROCK_GEOLOGY.md) for the algorithm, measurements, references, tests and caveats.

| STRATA formation | Vanilla replacement targets | Bedrock surface/vein material examples |
|---|---|---|
| Orogen | stony/jagged/frozen peaks, windswept gravelly hills | gneiss, schist, granite |
| Volcanic | badlands, eroded badlands | basalt, andesite |
| Rift | savanna, windswept savanna | sandstone, shale, basalt |
| Carbonate | desert | limestone, shale |
| Shield | forest, birch forest, old-growth birch forest, taiga | gneiss, granite |
| Clastic basin | plains, sunflower plains, meadow | shale, sandstone |
| Alluvial | rivers, swamps, mangrove swamp | conglomerate, sandstone |
| Oceanic | temperate, cold, lukewarm and warm Overworld ocean families (frozen oceans stay vanilla) | basalt, andesite |

The target identifiers above are Bedrock identifiers, which differ from Java for some biomes: for example, Badlands is `mesa`, Eroded Badlands is `mesa_bryce`, Swamp is `swampland`, Windswept Savanna is `savanna_mutated`, Old Growth Birch Forest is `birch_forest_mutated`, and Windswept Gravelly Hills is `extreme_hills_mutated`. The validator guards the exact vanilla target IDs used by this slice.

`marble`, `quartzite`, and `slate` are registered as usable custom blocks, but are not yet selected by the first-pass surface/vein rules. The native port is incremental; their precise world-generation roles should be added after the in-game slice is inspected. Current biome replacement amounts are 0.10–0.16, noise scales 26–32, and each formation feature has one placement iteration with a 12-block vein count. These are provisional implementation defaults, not measured biome coverage targets.

## Engine boundary and custom dimension

Bedrock custom-biome replacement cannot replace the Overworld's terrain-height generator. The first behavior/resource-pack slice therefore only ports geological materials and regional biome composition into selected existing Overworld biomes.

The optional `strata:geology` dimension is a separate alternative: the stable Script API registers a void custom dimension at startup, and the behavior-pack script incrementally writes deterministic columns as chunks are visited. It does **not** feed terrain into Bedrock's native chunk-generation pipeline, and it does not alter the Overworld heightmap. Custom-dimension terrain is consequently a script-built region, not a native replacement for the Forge chunk generator. The Overworld keeps its Bedrock terrain, biomes and vanilla generation outside the existing partial biome replacements.

The model has offline Node measurements and tests for a fixed seed and window, but these are not Bedrock-rendered measurements and do not prove geological realism. The API-version declarations used by the pack are stable, but Microsoft's current custom-dimension tutorial still labels the capability experimental and asks for the **Beta APIs** world toggle, while the 1.26.30 creator update lists `DimensionRegistry` among stable `@minecraft/server` APIs. Treat runtime support/toggle requirements as unresolved until verified in Bedrock 1.26.52 on a disposable test world. Do not use static JSON validation or a successful Node test as evidence that Minecraft registered or rendered the dimension.

The dimension is intentionally sparse: its script writes rock strata, top surfaces, ocean water and approximate river/lake water. It does not run the vanilla Overworld's caves, trees, structures, vegetation or feature/decorator passes. Terrain chunks already written are marked complete and are not automatically regenerated after model changes. Back up the test world before changing/removing the add-on.

## Static checks and build

From the repository root, using Python 3.9 or later and Node.js with ES modules:

```bash
python3 tools/validate_bedrock_addon.py
node --check bedrock/STRATA_BehaviorPack/scripts/geology_model.js
node --check bedrock/STRATA_BehaviorPack/scripts/main.js
node --experimental-default-type=module --test tools/test_bedrock_geology.mjs
node --experimental-default-type=module tools/audit_bedrock_geology.mjs
bash tools/check_bedrock_types.sh
python3 tools/package_bedrock_addon.py \
  --output bedrock/releases/STRATA-Geological-World-Engine-Bedrock-1.26.52.mcaddon
python3 tools/validate_bedrock_addon.py \
  --archive bedrock/releases/STRATA-Geological-World-Engine-Bedrock-1.26.52.mcaddon
```

The validator checks JSON syntax, manifest UUIDs/dependencies/target version, custom block and biome identifiers, texture/language/item references, feature/rule links and tags, script/API links, and the nested `.mcpack` ZIPs inside the `.mcaddon`. Packaging is deterministic for an unchanged source tree. The CI workflow checks that the tracked release copy matches a freshly built package.

These are **static/model** checks. They cannot prove that Minecraft 1.26.52 accepts every component, registers the dimension, or produces acceptable performance and visuals.

## Install and in-game smoke test

1. Build the `.mcaddon` with the command above, then open the resulting file on a Bedrock device to import both packs.
2. Create a **new disposable test world**, enable the STRATA behavior pack and its matching resource pack, and keep a backup. The partial Overworld biome replacement is documented as non-experimental starting in 1.21.110, while tutorials still suggest enabling **Custom Biomes** if those biomes fail to render. Custom-dimension documentation is inconsistent about the **Beta APIs** toggle (see above). First test the pack with the world settings you normally use; if the dimension fails to register, use the toggle indicated by the game/documentation in a separate disposable world and check the Content Log. Turning on an experiment may affect achievement eligibility.
3. In Creative, search for **STRATA Geological Compass**, or with cheats run `/give @s strata:geology_compass`. Use it from the Overworld. The script searches for a safe land spawn, generates a nearby 3×3 chunk area in batches, and then teleports you. On an unusual all-ocean search window it builds a small basalt spawn pad. Wait for the “dimension ready” message before moving far away; generation of later chunks is queued as you walk.
4. Use the compass again inside `strata:geology` to return to the surface at the Overworld's default spawn. Check that the return is above the local terrain and that no generation or tick errors appear in the Content Log.
5. Inspect the generated terrain at several scales: ocean/land transitions, margins, mountains/trenches, rifts, channels, lakes, material layers, and joins between adjacent chunks. Try negative coordinates and at least two different world seeds. Confirm that existing Overworld terrain remains Bedrock-generated and is not silently replaced.
6. Search the Creative inventory for `STRATA` or, with cheats, use commands such as `/give @s strata:basalt` and `/give @s strata:quartzite` to check the twelve block/item textures and names. For the Overworld replacement slice, use `/locate biome strata:orogenic_uplands` (and the other IDs in `bedrock/STRATA_BehaviorPack/biomes/`) on newly generated areas; inspect surface materials and mine below them to check rock features.
7. For a meaningful **Overworld biome/material** comparison, make paired new worlds with the same seed, one with STRATA disabled and one enabled, and sample identical coordinate windows. For the **scripted dimension**, make fresh worlds with the same seed and compare the same coordinates; the offline audit in `BEDROCK_GEOLOGY.md` is not a substitute for in-game measurements. Record biome/block shares, time-to-first-area, chunk-generation time and content-log errors before tuning replacement amounts or model parameters.

No Bedrock in-game test has been performed in the current build environment. Compilation, type declarations, deterministic model tests and static archive validation are not substitutes for the smoke/visual/performance/distribution check above.

## Next increments

1. Import and validate both packs and the scripted dimension in Bedrock 1.26.52; resolve whether the target client requires Beta APIs and measure generation performance.
2. Record fixed-seed Overworld biome/material shares and scripted-dimension terrain/hydrology metrics in Minecraft before tuning either system.
3. Use those measurements and visual review to tune replacement coverage, formation mapping, hydrology and surface exposures rather than assuming the offline distribution is ideal.
4. Expand native ore/mineral/strata features while preserving vanilla decoration where possible. Do not attempt to restore an unavailable native Overworld height hook through biome replacement.

## References

- [USGS: Understanding plate motions](https://pubs.usgs.gov/gip/dynamic/understanding.html)
- [NOAA: The Ocean](https://globalocean.noaa.gov/the-ocean/)
- [O'Callaghan & Mark (1984): drainage networks from digital elevation data](https://doi.org/10.1016/S0734-189X(84)80011-0)
- [Barnes, Lehman & Mulla (2014): Priority-Flood](https://doi.org/10.1016/j.cageo.2013.04.024)
- [Whipple & Tucker (1999): stream-power river-incision model](https://doi.org/10.1029/1999JB900120)
- [Microsoft: Bedrock 1.26.30 creator update and stable `@minecraft/server` APIs](https://learn.microsoft.com/en-us/minecraft/creator/documents/update1.26.30?view=minecraft-bedrock-stable)
- [Microsoft: `DimensionRegistry` API](https://learn.microsoft.com/en-us/minecraft/creator/scriptapi/minecraft/server/dimensionregistry?view=minecraft-bedrock-stable)
- [Microsoft: Custom Dimension API tutorial and current experimental-toggle guidance](https://learn.microsoft.com/en-us/minecraft/creator/documents/scripting/custom-dimension-api-tutorial?view=minecraft-bedrock-stable)
- [Microsoft: World generation in Bedrock](https://learn.microsoft.com/en-us/minecraft/creator/documents/world-generation?view=minecraft-bedrock-stable)
- [Microsoft: Custom partial biome replacement](https://learn.microsoft.com/en-us/minecraft/creator/documents/biomes/custompartialbiomereplacement?view=minecraft-bedrock-stable)
- [Microsoft: `minecraft:replace_biomes` reference](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/biomesreference/examples/components/minecraftbiomes_replace_biomes?view=minecraft-bedrock-stable)
- [Microsoft: Experimental feature toggles](https://learn.microsoft.com/en-us/minecraft/creator/documents/experimentalfeaturestoggle?view=minecraft-bedrock-stable)
- [Bedrock Wiki: Biomes and replacement targets](https://wiki.bedrock.dev/world-generation/biomes)
- [Bedrock Wiki: Biome tags and Bedrock identifiers](https://wiki.bedrock.dev/world-generation/biome-tags)
- [Microsoft: Add custom blocks](https://learn.microsoft.com/en-us/minecraft/creator/documents/addcustomdieblock?view=minecraft-bedrock-stable)
- [Microsoft: Pack manifest reference](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/addonsreference/packmanifest?view=minecraft-bedrock-stable)
