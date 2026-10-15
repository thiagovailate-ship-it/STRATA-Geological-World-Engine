# STRATA — Geological World Engine

A plate-tectonics world generator for **Minecraft Java Edition 1.7.10**
(Forge 10.13.4.1614). Pick the **STRATA** world type on world creation and
the overworld is built by simulated geology instead of vanilla noise:

- **Drifting tectonic plates** (warped Voronoi field) with geometric
  convergent / divergent / transform classification — no random biome dice.
- **Coupled relief**: collision orogens, island arcs, subduction trenches,
  mid-ocean ridges, continental rifts and transform scarps, all continuous
  across chunk borders by construction.
- **Real drainage**: Priority-Flood depression filling + D8 flow + bounded
  confluence gives dendritic rivers, mountain tarns, lowland lakes and
  wetlands; rivers carve canyons and stop at the sea.
- **Erosion, stratigraphy and climate**: slope/rainfall erosion with
  deposition fans, eight rock formations in faulted layer stacks (twelve
  rock types), temperature/moisture biomes mapped 1:1 to vanilla biomes.
- **Vanilla stays vanilla**: caves, ravines, villages, strongholds,
  mineshafts, temples, dungeons, decoration, mobs and snow all work; ores
  are strata-aware (coal in basins, gold in orogens, emeralds in high hills).

The Nether and the End keep their vanilla generators. All STRATA code is
original; RTG 1.7.10 sources were used as an API reference only (no code
copied — see `docs/TESTING.md`).

## Quick start (players)

1. Install Minecraft 1.7.10 with Forge 10.13.4.1614 and Java 8.
2. Build the jar (see below) or drop a release `STRATA-1.0.0.jar` into `mods/`.
3. Create a world, set **World Type: STRATA**. Done — explore.

Tuning lives in `config/strata.cfg` (`worldgen` = engine intensities,
`structures` = vanilla feature toggles). Changes need a restart and only
affect unexplored chunks. Sea level, relief, mountains, rivers, erosion,
plate size and ocean fraction are all adjustable; hostile values are
clamped by `StrataWorldConfig.validate()`.

## Building

Full Forge build (needs network + JDK 8; wrapper pins Gradle 2.0):

```bash
./gradlew setupDecompWorkspace build   # -> build/libs/STRATA-1.0.0.jar
```

Sandbox-safe checks that need no network (ECJ or javac, Java 8+ to run):

```bash
tools/build-core.sh    # compile core engine + lab + tests (1.8 in sandbox; 1.7 bytecode in the Forge build)
tools/run-tests.sh     # suites A-H: determinism, bounds, seams, coupling, perf
tools/run-lab.sh ...   # headless map/section renderer (PPM -> tools/ppm_to_png.py)
tools/stubcheck.sh     # compile the Forge layer against minimal MC/Forge API stubs
tools/check-legacy-api.sh  # purity (core imports nothing from the game) + no post-1.7 APIs
```

## Testing

Eight automated suites, all passing (see `docs/TESTING.md` for methods and
thresholds, `docs/VALIDATION.md` for the signed-off report):

| Suite | What it proves |
|-------|----------------|
| A Reproducibility | order-independent, bit-identical columns |
| B Seed variation | seeds change terrain (~21 blocks mean) and biomes |
| C Bounds | 20000 columns: heights, stacks, biomes, surfaces valid |
| D Continuity | no chunk/tile seams (border-vs-interior distributions match) |
| E Numeric safety | far-field + hostile configs stay finite |
| F Coupling | mountains sit on convergence, ridges above abyss |
| G Integration | 1024 columns through the 1.7.10 chunk index math |
| H Performance | ~1.6 ms/chunk contiguous, ~0.004 ms/column warm |

## Project layout

```
src/main/java/dev/strata/
  core/          Forge-free engine: tectonics, terrain, hydrology, erosion,
                 stratigraphy, climate, config, ColumnData, StrataWorldModel
  forge/         1.7.10 binding: @Mod, world type, chunk provider, biome
                 manager, Forge config, strata-aware ores
  blocks/        the twelve-variant rock block + material translation
  debug/         StrataLab headless renderer (ships in the jar, tiny)
src/main/resources/  mcmod.info, en_US.lang, 12 procedural rock textures
src/test/java/dev/strata/test/   suites A-H + TestRunner (no JUnit needed)
tools/           build-core, run-tests, run-lab, stubcheck, legacy guards,
                 texture generator, API stubs (tools/stubcheck/stubs)
docs/            ARCHITECTURE, ALGORITHMS, TESTING, VALIDATION
```

## License

MIT — see `LICENSE`. All engine, Forge-layer, texture-generator and test
code in this repository is original work written for STRATA. RTG 1.7.10
(GPL-3.0) was consulted solely to confirm 1.7.10/Forge API signatures; no
RTG code, assets or text appear here.
