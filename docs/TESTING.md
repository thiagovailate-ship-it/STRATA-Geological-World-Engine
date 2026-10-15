# STRATA Testing

## Suites A–H (`tools/run-tests.sh`, `TestRunner`)

| # | Suite | Method | Thresholds |
|---|-------|--------|-----------|
| A | Reproducibility | 4 seeds x 400 coords, sampled in scrambled vs sequential order, `sameAs` bit-compare | exact |
| B | Seed variation | 600-sample biome census + mean \|dH\| across 3 seeds | >=10 biomes/seed, mean \|dH\| > 8 |
| C | Bounds | 20000 columns: heights, stack order, materials, biomes, water tops | height in [2,maxSurface], tops descending, no nulls |
| D | Continuity | 1500 chunk/tile-border pairs vs 1500 interior pairs: dH, dRiver, dConf, dCont, dWet | border excess ~0 (negative = seamless), maxH <= 12 |
| E | Numeric safety | far-field coords + hostile configs (0/negative/huge) | all finite, heights valid |
| F | Coupling | convergent-land vs stable-land vs ocean mean heights; ridge vs abyss | conv > stable + 15, ocean < stable, ridge > abyss |
| G | Integration | 1024 columns through `(x*16+z)*256+y` + biome index math | exact layout |
| H | Performance | cold/warm/contiguous timings + cache stats | report only (budgets in VALIDATION) |

Run: `tools/run-tests.sh` (builds core, runs legacy guards, runs A–H,
~90 s in this sandbox). Full log: `out/test-output.txt`. No JUnit: the
runner is a plain `main` with explicit asserts so it works anywhere.

## Static guards

- `tools/check-legacy-api.sh`: (1) core/debug/tests import nothing from
  the game; (2) no post-Java-7 tokens (`->`, `::`, streams, `var`, ...).
  The sandbox compiler targets 1.8 (ECJ cannot do 1.7 anymore); the real
  Forge build targets 1.7 bytecode from the same source.
- `tools/stubcheck.sh`: compiles core + forge + blocks against 44 minimal
  MC/Forge API stubs — catches OUR type/syntax errors. It cannot verify
  stub signatures; those were cross-checked by reading RTG 1.7.10
  (`WorldTypeRTG`, `ChunkProviderRTG`, `WorldChunkManagerRTG`) method by
  method: `WorldType(String)`, all 13 `IChunkProvider` methods incl.
  `func_147416_a`, all 9 chunk-manager overrides, `MapGenVillage(Map)`
  with size/distance, `(x*16+z)*256+y` block indexing, populate event
  sequence. No RTG code was copied (GPL respected).

## StrataLab (`tools/run-lab.sh`)

Headless renderer for maps and cross-sections; writes PPM + stats:

```
StrataLab <mode> <seed> <centerX> <centerZ> <size> [outPrefix] [step]
modes: elevation plates boundary hydro formation biome section
```

Stats line = the quantitative check: height range, ocean/river/lake/
wet/pit %, plate count; formation/biome/boundary histograms follow.
Convert: `python3 tools/ppm_to_png.py in.ppm out.png`.

Reading the maps: categorical maps (plates/boundary/formation) show
sharp Voronoi edges BY DESIGN (argmax flips at boundaries); relief uses
the underlying continuous intensities, so elevation/hydro must show no
straight seams. Suspect anything ruler-straight in elevation/hydro that
does not match a boundary lineament (scarps and ridges ARE straight over
kilometres — that is geology, not a bug). Lakes render cyan in hydro
mode; below-sea carved channels render drowned-blue (fjords/estuaries).

## What "pass" does not cover

- The Forge build was never linked against real 1.7.10/Forge jars here
  (artifacts unreachable from this sandbox): `gradle build` on a
  connected machine with JDK 8 is still required, then an in-game smoke
  test (create STRATA world, fly, check F3 biomes, `/locate Stronghold`,
  eyes of ender, village/mineshaft presence, no chunk errors in log).
- Gameplay balance (ore rates, river width, mountain frequency) is tuned
  by map inspection only; expect one balance pass after playtesting.
- Performance H was measured on a 2-core sandbox JVM, not a gaming rig.
