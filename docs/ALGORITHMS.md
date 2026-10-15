# STRATA Algorithms

Notation: `ss(a,b,x)` = smoothstep, `ssd(a,b,x)` = smoothstep-down,
`C` = continent mask (positive land, negative ocean), `edge` = distance
to the nearest plate boundary in blocks. All world samples use absolute
Minecraft block coordinates `(x,z)`; the exact spatial scales and integration
path are summarized in `GEOGRAPHIC_AUDIT.md`.

## 1. Tectonics — warped Voronoi plates

`PlateField` owns a jittered grid (default cell 1536). Each cell has a
deterministic center, oceanic flag, base offset and velocity from
`SeedMixer` hashes. Before lookup the query point is warped by low-frequency
noise (amplitude 320, wavelength 2800), bending plate boundaries.

A sample finds the **three** nearest centers (F1/F2/F3 over a 3x3-cell
stencil). `edge = (F2-F1)/2` approximates distance to the nearest Voronoi
edge. All nine centers, not only F1/F2/F3, feed the continuous regional
blends. The pre-audit found that the earlier F1/F2/F3 cutoff left F3 with
nonzero weight and dropped F4 at a rank swap.

For each stencil center `i`, `di` is its distance to the warped query and
`wi = 1/(di^4+1)`. Plate bias and base offset are the normalized weighted
sums over all nine centers. Relative-motion classification uses symmetric
weighted pair covariances; the covariance and variance sums are evaluated
algebraically in O(9), not by selecting a rank-dependent plate pair. With
`W = Σwi`, `P = (W²-Σwi²)/2`, `ri = center_i-query`, `vi = velocity_i`,
`R = Σwi ri`, `V = Σwi vi`, `D = Σwi(vi·ri)`, and
`L = sqrt((WΣwi|ri|²-|R|²)/P)`, the implementation computes
`convN = (V·R-WD)/(3PL)`. `shearN` is the residual weighted relative-speed
RMS after the convergence component. These smoothed fields are multiplied
by `prox = clamp01(1-edge/classifyWidth)` and mapped through the documented
smoothstep thresholds in `PlateField`.

The transform-fault side value is a weighted blend of signed projections
onto all candidate-pair perpendicular bisectors. Pair orientations use stable
lattice-center coordinates, not F-rank order; the field is clamped over 60
blocks and multiplied continuously into the scarp offset. Nearest-pair IDs,
strike and axis remain diagnostic fields and may change at rank swaps.

## 2. Elevation — branch blend, ranges, scarps

The continent mask is `C = blendedPlateBias + 1.25·fBm(x/2400,z/2400)`
(three octaves, lacunarity 2.03, gain 0.5). The ocean and land branches are
computed everywhere and blended with `ss(-0.08,0.08,C)`; this is the current
code threshold (older text incorrectly documented `-0.5..0.5`). The blend
is continuous, though its coastline transition is relatively narrow.

- **Ocean branch:** `sea-6 - 26·clamp01(-C/1.2)` plus four blocks of
  abyssal fBm. Divergent boundaries add a submarine ridge swell up to 16
  blocks over width 220.
- **Land branch:** `sea+3 + 24·clamp01(C/1.2)`, the blended plate-base
  offset, and regional fBm with wavelength 600/amplitude 13. Continental
  rifts add a 22-block graben and 18-block shoulders when enabled.
- **Orogens/trenches:** convergence adds a boundary-distance profile
  (range width 150, widening by 25% into continents) and a broad foot
  (width 330). The maximum class varies smoothly from 55 to 110 blocks,
  times `mountainIntensity`, with ridged crest noise. A moat at
  `edge≈50` is 12 blocks deep on land to 30 offshore.
- **Transform scarp:** `8·ss(0,60,edge)·sideW·transform01`; the same
  computed offset is passed to stratigraphy as `faultShift`.
- **Detail and global scale:** local fBm at wavelengths 95 and 23 is
  scaled by `roughnessIntensity`; finally all relief is scaled around sea
  level by `reliefIntensity`.

## 3. Hydrology — Priority-Flood D8 with bounded confluence

Base (pre-carve) heights are sampled on an 8-block grid at **cell centers**:
`(cellIndex+0.5)·8`. Bilinear sampling therefore converts a block coordinate
with `cellIndex = x/8 - 0.5` (same for `z`). The former code used `x/8`,
shifting hydrology by half a cell (4 blocks per axis); that coordinate phase
is now corrected and has a regression test.

Each hydrology tile contains 16x16 inner cells plus a 12-cell margin:

1. **Priority-Flood:** patch-edge and ocean cells are sinks; the flood fills
   depressions with an epsilon gradient toward spill points. Sub-block
   micro-pits vanish; deeper basins keep their fill depth.
2. **D8 flow:** deterministic ±1 cm jitter breaks flat ties outside filled
   areas, never inside (where it would drown the epsilon gradient).
3. **Bounded confluence:** each patch cell follows downstream flow for at
   most **64** steps (the earlier document's 512 was stale); inner cells
   within Chebyshev radius 12 of the walk start count visits.
   `conf = clamp01((visits-1)/48)`.
4. **Lakes/wet flats:** lake strength combines fill depth and visits;
   high-confluence gentle cells can be wet flats without standing water.

Rivers (`ss(0.20,0.55,conf)`) can carve up to 8 blocks, suppressed inside
lake basins. Deep channels hold 1–3 blocks of water below their banks;
contained tarns use `lakeWaterTop`. Confluence is a local proxy (96-block
radius), not global watershed accumulation; carving does not re-route flow.

## 4. Final surface, climate and materials

`StrataWorldModel.sampleColumn` computes
`finalD = baseHeight - erosionLower + depositionRaise - riverCarve`, rounds
to the nearest integer, then clamps to `[2,maxSurfaceHeight]`. The chunk
provider writes that block as the top solid surface. If `finalHeight < sea`,
it fills from `finalHeight+1` through sea level; above-sea lake and river
water tops are applied separately. Biomes use the final height and sea-level
classification, while climate receives the pre-carve base height. All these
queries use the same absolute block `(x,z)` coordinates.

Climate noise wavelengths are 3000 (temperature) and 2600 (moisture).
Formation variants use a 173-block lattice; strata thickness noise uses
200 blocks; ocean sediment and alluvium use 130 and 90 blocks. These are
different intentional spatial scales, not coordinate-unit conversions.

- **Erosion:** slope/drainage lowering and gentle-slope deposition are
  analytic one-pass terms (`erosionStrength` scales both; 0 disables).
- **Formations:** plates, boundary regime, continent mask, slope, drainage
  and climate select SHIELD, OROGEN, VOLCANIC, CARBONATE, CLASTIC_BASIN,
  RIFT, OCEANIC or ALLUVIAL. A template builds the rock layer stack.
- **Biomes:** climate/elevation/water map to vanilla `BiomeGenBase` values.
- **Ores:** post-populate random-walk veins are gated by formation/biome.

## 5. Continuity and diagnostic limits

The intended invariants are: no chunk-dependent random state; relief reads
continuous regional fields rather than plate booleans; hydrology tiles rebuild
from local patches and margins. These are now checked with explicit rank-swap
regressions, one-block chunk/tile-edge statistics, and deterministic area
audits. The measured results and remaining limits are in
`GEOGRAPHIC_AUDIT.md`; continuity is not inferred from compilation alone.
