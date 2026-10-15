# STRATA Algorithms

Notation: `ss(a,b,x)` = smoothstep, `ssd(a,b,x)` = smoothstep-down,
`C` = continent mask (positive land, negative ocean), `edge` = distance
to the nearest plate boundary in blocks.

## 1. Tectonics — warped Voronoi plates with symmetric blends

`PlateField` owns a jittered grid (default cell 1536). Each cell has a
deterministic center, oceanic flag, base offset and velocity from
`SeedMixer` hashes. Before lookup the query point is warped by
low-frequency noise (amplitude 320, wavelength 2800), bending boundaries
into organic curves without changing topology.

A sample finds the **three** nearest centers (F1/F2/F3 over 3x3 cells).
`edge = (F2-F1)/2` approximates distance to the shared Voronoi edge. The
side coordinate `sideW = (F1^4-F2^4)/(F1^4+F2^4)` is smooth, ±1 inside
provinces, 0 at the boundary, and invariant under F1/F2 swaps.

**Why three plates.** The 2nd/3rd plates swap ranks along order-2 Voronoi
lines (most visibly near triple junctions). Anything reading "plate 2's"
identity alone jumps there — by tens of blocks in early versions. So:

- Per-plate properties (bias, base offset) enter relief only through
  symmetric inverse-4th-power blends over all three plates.
- Classification blends the (1,2) and (1,3) pairs by flat-top edge
  proximity; at a rank swap both pairs carry equal weight, so the blend
  is exactly continuous. Triple junctions become subdued nodes (Afar-like).
- Type gates (ridge/rift/orogen-width/trench-depth) read the smooth
  continent field, never plate booleans. Only diagnostic fields (strike,
  axis, plate-2 identity) may change off boundaries — relief never does.

Convergence `convN` / shear `shearN` come from relative plate velocity
projected on each pair axis, normalized by typical speeds; intensities
are `ss(0.06,0.32,±convN)·prox`, `ss(0.05,0.28,shearN)·prox` with
`prox = clamp01(1-edge/classifyWidth)`.

## 2. Elevation — branches, ranges, trenches, scarps

The continent mask `C = blendBias + fBm(x/2400)` (independent wavelength
so coasts do not coincide with plate edges). Relief is built in two
branches blended by `ss(-0.5,0.5,C)`:

- **Ocean branch**: shelf rolloff to -22, abyssal plain, +16 mid-ocean
  ridge swell (`exp(-(edge/220)^2)`, gated divergent+oceanic).
- **Land branch**: base + plateBase blend + regional fBm; continental
  rifts carve a 22-block graben with +18 shoulders (gated divergent+land).

**Orogens** (convergent): `prof = exp(-(edge/wEff)^2)` plus a wide foot;
`wEff` grows 25% into continents; height class follows C smoothly from
55 (island arcs) to 110 (collisions) times `mountainIntensity`. Crest
noise (ridged fBm) multiplies the profile so ranges have peaks and cols.

**Trenches**: a moat ring `exp(-((edge-50)/90)^2)`, 12 blocks deep on
land (foreland basins) to 30 in ocean (trenches), following C.

**Transform scarps**: a TRUE step, `8·ss(0,60,edge)·sign·T` — flat
provinces, ramp at the fault — plus `faultShift` lagging the strata
identically so layers match the surface scarp.

Detail: ridged + billow fBm octaves scaled by `roughnessIntensity`,
all relief scaled by `reliefIntensity` around sea level.

## 3. Hydrology — Priority-Flood D8 with bounded confluence

Base (pre-carve) heights are sampled on an 8-block grid. Each tile
(16x16 inner cells + 12-cell margins) resolves drainage independently:

1. **Priority-Flood (Barnes)**: patch-edge and ocean cells are sinks;
   the flood fills depressions with an epsilon gradient toward spill
   points. Micro-pits (sub-block fill) vanish; real basins keep depth.
2. **D8 flow** on the filled surface (deterministic ±1 cm jitter breaks
   flat ties outside filled areas, never inside — it would drown the
   epsilon gradient). Rivers route through lakes, as in nature.
3. **Bounded confluence**: every patch cell walks downstream (cap 512);
   inner cells within Chebyshev radius 12 of the walk start count
   visits. `conf = clamp01((visits-1)/48)`. The radius is uniform, so
   tile borders are seamless (Test D).
4. **Lakes**: `clamp01((fillDepth-1)/3) · clamp01(visits/14)` — deep
   basins with real catchments hold water; micro-relief does not.
5. **Wet flats**: high-confluence + gentle-slope cells get `wet01`
   (clay pans, wetlands) without standing water.

Rivers (`ss(0.20,0.55,conf)`) carve up to 8 blocks, suppressed inside
lake basins (lakes are depositional); deep channels hold 1-3 blocks of
water below the banks (`riverWaterTop`); tarns hold `lakeWaterTop`.

Honest limits: confluence is a local proxy (96-block radius), not global
watershed accumulation; carving does not re-route rivers (no feedback).

## 4. Erosion, stratigraphy, climate, biomes, ores

- **Erosion**: slope/rainfall lowering on high ground, deposition raising
  in fans/deltas/floodplains (`erosionStrength` scales both; 0 disables).
- **Formations** (from plates + water + relief + deposition): SHIELD,
  OROGEN, VOLCANIC, CARBONATE, CLASTIC_BASIN, RIFT, OCEANIC, ALLUVIAL
  (cap). Each owns a layer template over the 12 STRATA rocks + vanilla
  sediments; stacks hold ≤12 layers, surface down, last extends to y=0.
- **Climate**: temperature from latitude + altitude + ocean moderation;
  moisture from ocean proximity + orographic lift (wet windward, dry
  leeward). Biomes map 1:1 to vanilla (`BiomeKind` -> `BiomeGenBase`).
- **Ores** (`IWorldGenerator`, post-populate): random-walk veins replace
  strata rock and stone; coal→sedimentary basins, gold→orogen/rift,
  lapis→orogen, emerald→extreme hills, iron/redstone/diamond anywhere.

## 5. Continuity design rules (the three invariants)

1. Relief blends use symmetric weights / `sideW` / flat-top edge
   profiles — never the V-shaped signed distance (it creases at every
   boundary) and never rank-unstable plate identity.
2. Every boundary-type gate reads a continuous field (C, intensities),
   never a boolean.
3. No tile reads state outside its own patch; jitter ties break at the
   centimeter scale only.

These were learned the hard way: early builds showed boundary creases
(V-shaped blends), 22-block interior cliffs (F2-identity flicker at
triple junctions) and zero lakes (jitter micro-pits). Each is now a
regression net: Test D (seams), Test F (coupling), lab lake stats.
