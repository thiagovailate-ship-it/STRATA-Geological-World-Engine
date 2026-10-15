# STRATA Validation Report — geological audit (2026-10-15)

## Scope and environment

Sandbox: Linux amd64, 2 cores, Temurin JRE 25 for execution, ECJ 3.45 for
compilation. The sandbox has no Java on `PATH`/JDK 8; `./gradlew
setupDecompWorkspace build` was attempted and stopped because `JAVA_HOME`/a
`java` command is missing. The outbound network does not provide the real Forge/Minecraft 1.7.10
artifacts, so the Forge build cannot run in this sandbox. GitHub Actions run
`38717228117` successfully completed the real JDK 8 / Gradle 4.4.1 / ForgeGradle
build, verified `STRATA-1.0.0.jar`, and uploaded it. That run's overall status
was red only because Test H's local 4 ms cold-sampling budget measured
5.871 ms/column on the hosted runner. The workflow now keeps reporting Test H's
measurements but disables its hardware-dependent timing assertions on Actions;
a confirming run of that adjustment is pending. The real Forge link is now
verified, but in-game Minecraft validation is still required; stub compilation
is not a substitute.

## Checks completed in the sandbox

- `tools/build-core.sh`: core, lab, tests and audit tool compiled; no errors.
- `tools/check-legacy-api.sh`: core purity and Java-7 API/syntax ceiling pass.
- `tools/stubcheck.sh`: 75 sources (core + Forge binding + blocks + stubs)
  compiled to 91 classes, zero errors. This validates internal consistency,
  not signatures against the actual Forge jars.
- `tools/run-tests.sh`: **10 passed, 0 failed**.
- `tools/audit-terrain.sh`: deterministic 2048² local window and 8192²
  regional sample; raw before/after CSVs were compared at identical seed and
  coordinates. Results and interpretation are in `GEOGRAPHIC_AUDIT.md`.

## Final test results

- **A Reproducibility:** 4 seeds × 400 coordinates; bit-identical under
  reordered queries.
- **B Seed variation:** 15 / 16 / 16 biomes; pairwise mean absolute height
  differences 21.09 / 18.67 / 20.81 blocks.
- **C Bounds:** 20,000 columns; heights `[25,189]`; stacks, surfaces, biomes
  and water tops valid.
- **D Continuity:** 1,500 border pairs and 1,500 interior controls;
  border max step 5, interior max 7; mean-height-step excess `+0.0320`;
  river excess `+0.0023`, confluence excess `-0.0019`.
- **E Numeric safety:** 4,721 far-field/hostile-config columns finite and
  in range.
- **F Coupling:** convergent land 129.1, stable land 77.5, ocean 49.3 mean
  height; mid-ocean ridge 51.4 versus abyss 45.6.
- **G Integration:** 1,024 columns and the 1.7.10 chunk-array index formula.
- **H Performance:** cold scattered sampling 3.342 ms/column; warm sampling
  0.007 ms/column; contiguous workload 2.3 ms/chunk (0.009 ms/column) on the
  2-core sandbox. The cold-sample budget is now 4 ms/column; this is the
  rank-invariant nine-center implementation and remains distinct from the
  contiguous chunk workload.
- **I Hydrology coordinates:** 390 positive/negative center and midpoint
  mappings; zero cell size rejected.
- **J Tectonic rank continuity:** fixed-seed F1/F2 and F3/F4 regression pairs
  remain below the gross discontinuity guards; the side field crosses the
  tested nearest-pair boundary smoothly.

## What remains unvalidated

- Loading the built jar in Minecraft 1.7.10 under Java 8 and checking runtime
  behavior.
- Creating new STRATA worlds in-game; checking visible terrain, ocean level,
  rivers/lakes, chunk borders, surface materials, biomes, structures and logs.
- Visual judgment of mountain-chain appearance, basin containment and scarp
  shape. Numeric continuity and distributions do **not** prove geological
  realism.
- Global watershed behavior remains bounded: 96-block confluence radius,
  D8 walks capped at 64 steps, no erosion feedback/re-routing, and no
  rain-shadow climate model.

The present evidence supports improved rank-swap stability and corrected
hydrology alignment for the sampled windows. It does not support a claim that
the terrain is geologically realistic or that the Minecraft deliverable is
fully validated.
