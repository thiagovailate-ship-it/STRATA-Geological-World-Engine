# STRATA Validation Report — v1.0.0 (2026-10-15)

## Environment and scope

Sandbox: Linux amd64, 2 cores, Temurin JRE 25 (to RUN), ECJ 3.45 or
javac (to COMPILE, `-source 1.8`: ECJ cannot target 1.7 anymore, so the
1.7 ceiling is enforced by `check-legacy-api.sh` + `build.gradle`
`sourceCompatibility = 1.7` for the real Forge build). No network to
Forge/Mojang (only GitHub + Maven Central + PyPI reachable): the full
ForgeGradle linkage was impossible here and REMAINS REQUIRED on a
connected machine (JDK 8 + `./gradlew setupDecompWorkspace build`).

Validated in-sandbox: core engine, lab, tests, Forge-layer
self-consistency (stubcheck), legacy guards, resources.

## Build evidence

- `tools/build-core.sh`: 33 sources -> 50 classes, zero errors; purity +
  legacy guards pass.
- `tools/stubcheck.sh`: 75 sources (core + forge + blocks + 44 API stubs)
  -> 91 classes, zero errors.
- Resources: 12 procedural `rock_*.png` + `en_US.lang` + `mcmod.info`
  (version-expanded by `build.gradle`).

## Test results (final run, exit 0 — 8 passed, 0 failed)

- **A**: 4 seeds x 400 coords order-independent bit-identical.
- **B**: 15/17/17 biomes; mean |dH| 23.2 / 20.4 / 22.6 blocks.
- **C**: 20000 columns, height [23, 188], all stacks/biomes/surfaces valid.
- **D**: border maxH 4.0 / interior maxH 4.0; border excess dH -0.021
  (negative = seamless), dRiver -0.002, dConf -0.0005.
- **E**: 4721 far-field + hostile columns, all finite and valid.
- **F**: convergent-land 124.5 / stable-land 77.3 / ocean 48.1;
  mid-ocean ridge 48.6 vs abyss 45.3.
- **G**: 1024 columns through the 1.7.10 chunk index math, exact.
- **H**: cold 1.93 ms/col, warm 0.004 ms/col, contiguous 1.3 ms/chunk.

## Lab evidence (seed 12345)

- Ocean window (0,0, 2048 blocks): height [33,128], ocean 84%,
  river 1.65%, lake 0.27% (was 0.00% before Priority-Flood), 11 biomes,
  5 formations. No seams; boundary lineaments read as ridge/transform
  segments (straight over km = correct geology).
- Mountain window (-4000,3000): height [43,162], ocean 41%,
  OROGEN 5%, CARBONATE present, 7 formations, 13 biomes incl.
  EXTREME_HILLS; dendritic rivers; cyan lake dots in hydro mode.
- Cross-sections show flat-lying strata under relief, shelf->ocean
  transitions, river notches.

## Defects found and fixed during validation

1. Boundary creases (V-shaped signed-distance blends) -> symmetric
   F1/F2 weights + `sideW` + flat-top profiles.
2. 22-block interior cliffs (F2-identity flicker at triple junctions)
   -> symmetric three-plate blends + pair-weighted classification +
   continent-field type gates (Test D maxH 22 -> 4).
3. Zero lakes (jitter micro-pits) -> Priority-Flood fill; lakes exist
   (0.07–0.27%), rivers route through basins, carve suppressed in lakes.
4. Dense/perched rivers -> retuned thresholds, wet flats hold no water.
5. Block-array size: corrected to full-height 65536 from RTG sources.

## Known limitations and residual risks

- **Forge linkage unverified**: API signatures were cross-checked
  against RTG 1.7.10 sources, but a first `gradle build` + in-game
  smoke test is mandatory (see TESTING.md checklist).
- Balance is map-tuned, not playtested (ore rates, river density ~6%
  in mountains, mountain frequency).
- Confluence is a 96-block local proxy, not global watersheds; carving
  does not re-route rivers (documented honest limits).
- No config GUI screen in v1 (file only); no custom biomes by design
  (vanilla 1:1 mapping).
- Max surface 248 + cloud height 208: extreme configs
  (`mountainIntensity` 3) can poke through clouds — accepted.

## Acceptance vs the master prompt

World type + chunk provider + tectonics/terrain/hydrology/erosion/
lithology/caves/vanilla structures/ores/vanilla-mapped biomes: DONE.
Config: DONE (file). Lab/diagnostics: DONE. Tests A–H: 8/8 PASS.
README + ARCHITECTURE + ALGORITHMS + TESTING + this report: DONE.
JAR: buildable via `build.gradle` on a connected machine (sandbox
could not link Forge artifacts — the one deliverable requiring a
networked build).
