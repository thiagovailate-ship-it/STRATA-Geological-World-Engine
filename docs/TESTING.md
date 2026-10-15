# STRATA Testing

## Suites A–J (`tools/run-tests.sh`, `TestRunner`)

| # | Suite | Method | Thresholds |
|---|-------|--------|-----------|
| A | Reproducibility | 4 seeds x 400 coords, sampled in scrambled vs sequential order, `sameAs` bit-compare | exact |
| B | Seed variation | 600-sample biome census + mean \|dH\| across 3 seeds | >=10 biomes/seed, mean \|dH\| > 8 |
| C | Bounds | 20000 columns: heights, stack order, materials, biomes, water tops | height in [2,maxSurface], tops descending, no nulls |
| D | Continuity | 1500 chunk/tile-border pairs vs 1500 interior pairs: dH, dRiver, dConf, dCont, dWet | border excess ~0 (negative = seamless), maxH <= 12 |
| E | Numeric safety | far-field coords + hostile configs (0/negative/huge) | all finite, heights valid |
| F | Coupling | convergent-land vs stable-land vs ocean mean heights; ridge vs abyss | conv > stable + 15, ocean < stable, ridge > abyss |
| G | Integration | 1024 columns through `(x*16+z)*256+y` + biome index math | exact layout |
| H | Performance | cold/warm/contiguous timings + cache stats | local guard: cold <4 ms/column; warm <1 ms/column; chunk <120 ms; hosted CI reports only |
| I | Hydrology coordinates | cell-center mapping for positive/negative grid indices | exact at centers/midpoints; invalid size rejected |
| J | Tectonic rank continuity | fixed-seed F1/F2 and F3/F4 rank-swap probes | no gross height/continent/regime discontinuity |

Run: `tools/run-tests.sh` (builds core, runs legacy guards, runs A–J,
about 160 s in this sandbox). Full log: `out/test-output.txt`. No JUnit:
the runner is a plain `main` with explicit asserts so it works anywhere.

## GitHub Actions build

`.github/workflows/forge-build.yml` runs on pushes and pull requests; the
manual **Actions → Forge 1.7.10 build → Run workflow** trigger is available once
the workflow is present on the repository's default branch. It selects Temurin JDK 8,
performs the real `setupDecompWorkspace build` against Minecraft 1.7.10 / Forge
10.13.4.1614, uploads `build/libs/STRATA-1.0.0.jar` as the `STRATA-1.0.0`
artifact for 14 days, then runs the A–J suite. The build and artifact upload
are intentionally before the tests, so a regression-test failure does not
prevent retrieval of a successfully compiled jar. Test H still measures and
prints its timing data, but Actions sets `STRATA_PERF_REPORT_ONLY=true` because
absolute wall-clock limits vary by runner; local runs retain the documented
budgets. The Gradle `test` task is disabled because these tests are a standalone
`main()` harness rather than JUnit tests; Actions runs them explicitly through
`tools/run-tests.sh` and adds the tail of the output to the workflow summary.

## Deterministic terrain audit (`tools/audit-terrain.sh`)

This is a measurement tool, not a pass/fail realism score. Example:

```bash
tools/audit-terrain.sh 12345 0 0 2048 4 out/before.csv
tools/audit-terrain.sh 12345 0 0 2048 4 out/after.csv out/before.csv
# Large spatial window without expensive edge-pair sampling:
tools/audit-terrain.sh 12345 0 0 8192 16 --stats-only
```

It reports height quantiles/histogram, sea bands, slope categories, connected
components of `C>=0` and final land, enclosed (island-like) land components,
hydrology pit/lake components, strong-orogen components, and exact 1-block
pairs across chunk/tile borders with matched interior controls. Component
counts are 4-neighbour measurements at the stated sample spacing; area is
estimated as `cells*step^2`. `--stats-only` is useful for a broad spatial
census; use a local window without that flag for exact border measurements.
An optional CSV compare reports same-coordinate deltas. The measured run is
summarized in `GEOGRAPHIC_AUDIT.md`.

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
sharp Voronoi edges BY DESIGN (argmax flips at boundaries). Relief/hydrology
should be continuous at chunk/tile borders; use the audit's border-vs-control
statistics rather than judging a visual line alone. Ridge/scarp lineaments
can follow tectonic boundaries. Lakes render cyan in hydro mode; below-sea
carved channels render drowned-blue (fjords/estuaries).

## What "pass" does not cover

- GitHub Actions run `38718259143` passed the real Forge 1.7.10 build, jar
  checks/upload, and all A–J regression suites. A previous hosted run measured
  5.871 ms/column for cold sampling, above the 4 ms local-only budget; the
  workflow now reports host-specific timing without gating the build. An
  in-game smoke test is still required (create a STRATA world, fly, check F3
  biomes, `/locate Stronghold`, eyes of ender, village/mineshaft presence, and
  logs).
- Gameplay balance (ore rates, river width, mountain frequency) is tuned
  by map inspection only; expect one balance pass after playtesting.
- Performance H was measured on a 2-core sandbox JVM, not a gaming rig.
