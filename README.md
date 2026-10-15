# STRATA — Geological World Engine

A plate-tectonics world generator for **Minecraft Java Edition 1.7.10**
(Forge 10.13.4.1614). Pick the **STRATA** world type on world creation and
the overworld is built by simulated geology instead of vanilla noise:

- **Drifting tectonic plates** (warped Voronoi field) with geometric
  convergent / divergent / transform classification — no random biome dice.
- **Coupled relief**: collision orogens, island arcs, subduction trenches,
  mid-ocean ridges, continental rifts and transform scarps, with deterministic
  rank-swap and chunk-edge continuity diagnostics.
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

Full Forge build (needs network + JDK 8; wrapper pins Gradle 4.4.1 and the
build uses the HTTPS-compatible ForgeGradle 1.2 fork):

```bash
./gradlew setupDecompWorkspace build   # -> build/libs/STRATA-1.0.0.jar
```

GitHub Actions runs the core regression suite and the real Forge build on every
push and pull request (`.github/workflows/forge-build.yml`). Once this workflow
is on the repository's default branch, you can also build on demand via
**Actions → Forge 1.7.10 build → Run workflow**. Download the resulting
`STRATA-1.0.0` artifact from the completed workflow run.

Sandbox-safe checks that need no network (ECJ or javac, Java 8+ to run):

```bash
tools/build-core.sh    # compile core engine + lab + tests (1.8 in sandbox; 1.7 bytecode in the Forge build)
tools/run-tests.sh     # suites A-J: determinism, bounds, seams, coupling, regressions
tools/audit-terrain.sh # deterministic height/slope/continent/depression/edge statistics
tools/run-lab.sh ...   # headless map/section renderer (PPM -> tools/ppm_to_png.py)
tools/stubcheck.sh     # compile the Forge layer against minimal MC/Forge API stubs
tools/check-legacy-api.sh  # purity (core imports nothing from the game) + no post-1.7 APIs
```

## Bedrock add-on (incremental port)

[Download the prebuilt Bedrock 1.26.52 add-on](bedrock/releases/STRATA-Geological-World-Engine-Bedrock-1.26.52.mcaddon)
(open the file in Bedrock to import both packs). It is a native behavior/resource
pack—not a Forge jar recompiled for Bedrock. It ports twelve rock materials,
eight partial Overworld biome replacements, formation-themed underground rock
features, and an optional script-built `strata:geology` dimension accessed with
the STRATA Geological Compass. Bedrock biome replacement still cannot replace
the Overworld height generator; the scripted geology remains separate and does
not modify vanilla terrain.

To validate and rebuild the checked-in package with Python 3.9+:

```bash
python3 tools/validate_bedrock_addon.py
python3 tools/package_bedrock_addon.py \
  --output bedrock/releases/STRATA-Geological-World-Engine-Bedrock-1.26.52.mcaddon
```

An in-game Bedrock 1.26.52 smoke/performance test is still outstanding. The
custom-dimension API guidance is inconsistent about the Beta APIs toggle, so
runtime registration must be checked in a disposable world. The dedicated
Bedrock workflow statically validates and uploads a `STRATA-Bedrock-1.26.52`
artifact. See [`docs/BEDROCK_PORT.md`](docs/BEDROCK_PORT.md) for install and
runtime testing, and [`docs/BEDROCK_GEOLOGY.md`](docs/BEDROCK_GEOLOGY.md) for
references, algorithm details, measured model distributions and limits.

## Testing

Ten JVM automated suites are defined (see `docs/TESTING.md` for methods and
thresholds, `docs/VALIDATION.md` for results, and `docs/GEOGRAPHIC_AUDIT.md`
for the before/after measurements). The separate Bedrock script model has six
Node tests, a hydrology seam audit and a reproducible distribution audit; see
`docs/BEDROCK_GEOLOGY.md`.

| Suite | What it proves |
|-------|----------------|
| A Reproducibility | order-independent, bit-identical columns |
| B Seed variation | seeds change terrain and biomes |
| C Bounds | 20000 columns: heights, stacks, biomes, surfaces valid |
| D Continuity | chunk/tile-edge versus interior step distributions |
| E Numeric safety | far-field + hostile configs stay finite |
| F Coupling | tectonic regimes measurably affect relief |
| G Integration | 1024 columns through the 1.7.10 chunk index math |
| H Performance | cold/warm/contiguous timings and hydrology cache |
| I Hydro coordinates | cell-center mapping is correct for negative and positive indices |
| J Rank continuity | sampled tectonic rank swaps no longer create gross cliffs |

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
src/test/java/dev/strata/test/   suites A-J + TestRunner (no JUnit needed)
bedrock/         native Bedrock behavior/resource pack source files
tools/           build-core, run-tests, audit-terrain, run-lab, stubcheck,
                 Bedrock packaging/validation/type checks, legacy guards,
                 texture tools, API stubs
docs/            ARCHITECTURE, ALGORITHMS, TESTING, VALIDATION,
                 GEOGRAPHIC_AUDIT, BEDROCK_PORT, BEDROCK_GEOLOGY
```

## License

MIT — see `LICENSE`. All engine, Forge-layer, texture-generator and test
code in this repository is original work written for STRATA. RTG 1.7.10
(GPL-3.0) was consulted solely to confirm 1.7.10/Forge API signatures; no
RTG code, assets or text appear here.
