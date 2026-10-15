# STRATA Architecture

## Layering

```
+--------------------- 1.7.10 game ----------------------------------+
| WorldType -> StrataWorldChunkManager -> StrataChunkProvider -> Chunk |
| populate: structures, lakes, dungeons, decorate, animals, ice/snow  |
| IWorldGenerator: StrataOreGenerator (veins, runs after populate)    |
+---------------------------------------------------------------------+
| dev.strata.forge   thin binding: config snapshot, ThreadLocal       |
| dev.strata.blocks  GeoMaterial -> (Block, metadata) translation     |
+=========================== pure line ===============================+
| dev.strata.core    Forge-free engine. Knows no Block, no World.     |
|   sampleColumn(x, z, ColumnData) is the ONLY entry point the game   |
|   layer calls. Everything is a pure function of (seed, x, z).       |
+---------------------------------------------------------------------+
```

`tools/check-legacy-api.sh` enforces the pure line: nothing under
`core/`, `debug/` or `test/` may import `net.minecraft*`, `cpw.*` or
`net.minecraftforge.*`. The engine therefore compiles and runs in plain
Java (tests, lab), while the game binding is ordinary 1.7.10/Forge code
(API signatures cross-checked against RTG 1.7.10 sources).

## Data flow: one column

`StrataWorldModel.sampleColumn` runs nine stages, each reading smoothed
shared fields (never raw randomness):

1. **Plates** — `PlateField` finds the 3 nearest warped-Voronoi centers
   (F1/F2/F3), symmetric property blends, blended boundary intensities.
2. **Base relief** — `ElevationModel` builds continent mask C, branch
   relief (ocean/land), orogen/trench/ridge/rift/scarp, noise detail.
3. **Drainage** — `HydrologyModel` resolves D8 flow on a Priority-Flood
   filled surface, bounded-radius confluence, lakes, wet flats.
4. **Climate** — temperature/moisture from latitude, height, ocean.
5. **Erosion** — slope/rainfall lowering, deposition raising (fans, deltas).
6. **Rivers/lakes** — carve channels (suppressed inside lake basins),
   wetland pans, river/tarn water tops.
7. **Biome** — `BiomeMapper` from climate + water + slope context.
8. **Strata** — `FormationModel` picks the formation; `StrataColumn`
   builds the faulted layer stack; surfaces (top/filler/soil/sediment).
9. **Answer** — everything lands in one `ColumnData`.

`ColumnData` is the contract: provider, ore generator, lab and tests all
read the same object. It is caller-reused (no allocation per column in
hot paths) and `sameAs` compares it field-by-field for Test A.

## Threading

Minecraft queries biomes and generates chunks on several threads. Engine
instances keep warm caches (`BoundedCache` hydrology tiles, noise tables)
that are not safe to share, so the Forge layer hands each thread its own
`StrataWorldModel` + scratch `ColumnData` via `ThreadLocal` (in both the
chunk manager and the provider; the ore step reuses the provider's model
on the populate thread, so its columns are already cache-hot).

## Determinism

Same `(seed, x, z, config)` -> same `ColumnData`, bit-identical, whatever
order chunks generate in. Rules that make this true:

- No `java.util.Random` in core; all hashes go through `SeedMixer`
  (splitmix-style, salted per system).
- No tile keeps state a neighbour tile depends on: hydrology tiles
  rebuild their patch (with margins) from base heights alone.
- Southern/eastern neighbours are never read during a tile build
  (only margins already owned by the patch).
- Relief never reads rank-unstable quantities (see `ALGORITHMS.md`).

## 1.7.10 integration points

- `StrataWorldType extends WorldType("STRATA")`; overworld only, other
  dimensions get vanilla providers. Cloud height 208 (above the peaks).
- `StrataChunkProvider implements IChunkProvider` (all 13 methods, incl.
  `func_147416_a` for stronghold locating): fills `Block[65536]` +
  `byte[65536]` at index `(x*16+z)*256+y`, runs caves/ravines/structures
  exactly like vanilla, writes the vanilla-ID biome array.
- `StrataWorldChunkManager` answers every biome query from the engine
  (vanilla `BiomeGenBase` instances — decoration, spawning, villages and
  structure placement behave exactly like vanilla for the chosen biome).
- `StrataOreGenerator` (`IWorldGenerator`, weight 0) grows veins that
  replace strata rock as well as stone, gated by formation/biome.
- One content block: `strata:strataRock` (metadata 0-11), registered with
  a named-subtype `ItemBlock`; textures are procedural 16x16 PNGs.
- Config: `StrataForgeConfig` loads `strata.cfg` once in preInit into an
  immutable snapshot; worlds copy it. No GUI screen in v1 (file only).

## Files of note

- `core/StrataWorldModel.java` — the nine-stage pipeline.
- `core/ColumnData.java` — the per-column contract + `sameAs`.
- `core/tectonics/PlateField.java` — symmetric three-plate blends.
- `core/hydrology/HydrologyModel.java` — Priority-Flood + D8.
- `forge/StrataChunkProvider.java` — terrain + populate.
- `forge/StrataWorldChunkManager.java` — biome answers.
- `tools/stubcheck/stubs/` — minimal MC/Forge API surface for sandbox
  compilation (never shipped; signatures verified against RTG 1.7.10).
