package dev.strata.core;

import dev.strata.core.config.StrataWorldConfig;
import dev.strata.core.erosion.ErosionModel;
import dev.strata.core.hydrology.HydrologyModel;
import dev.strata.core.stratigraphy.Formation;
import dev.strata.core.stratigraphy.FormationModel;
import dev.strata.core.stratigraphy.GeoMaterial;
import dev.strata.core.stratigraphy.StrataColumn;
import dev.strata.core.tectonics.BoundarySample;
import dev.strata.core.tectonics.PlateField;
import dev.strata.core.terrain.BiomeKind;
import dev.strata.core.terrain.BiomeMapper;
import dev.strata.core.terrain.ClimateModel;
import dev.strata.core.terrain.ElevationModel;
import dev.strata.core.util.MathUtil;
import dev.strata.core.util.SeedMixer;

/**
 * Central entry point of the geological engine.
 *
 * <p>One call — {@link #sampleColumn} — resolves a full column: tectonics,
 * base relief, slope, drainage, climate, erosion, rivers, biome, formation,
 * surface plan and the rock layer stack. Every stage reads the shared
 * regional fields; nothing rolls in isolation, which is the entire point of
 * STRATA (see docs/ARCHITECTURE.md).</p>
 *
 * <p>Determinism: the result is a pure function of
 * {@code (worldSeed, config, x, z)}. Order of queries, chunk borders and
 * region borders cannot change it (Tests A and D).</p>
 *
 * <p>Threading: instances are safe for concurrent use. Per-call scratch state
 * is method-local; the only shared mutable structure is the hydrology tile
 * cache, which is internally synchronized.</p>
 */
public final class StrataWorldModel {

    /** Finite-difference step (blocks) for the slope estimate. */
    private static final double SLOPE_STEP = 2.0;

    private final long seed;
    private final StrataWorldConfig cfg;
    private final PlateField plates;
    private final ElevationModel elevation;
    private final ClimateModel climate;
    private final HydrologyModel hydrology;
    private final ErosionModel erosion;
    private final FormationModel formationModel;
    private final StrataColumn strataColumn;

    public StrataWorldModel(long seed, StrataWorldConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.seed = seed;
        this.cfg = config.copy();
        this.cfg.validate();
        this.plates = new PlateField(seed, this.cfg);
        this.elevation = new ElevationModel(seed, this.cfg, this.plates);
        this.climate = new ClimateModel(seed, this.cfg);
        this.hydrology = new HydrologyModel(seed, this.cfg, this.elevation);
        this.erosion = new ErosionModel(this.cfg);
        this.formationModel = new FormationModel(seed, this.cfg);
        this.strataColumn = new StrataColumn(seed, this.cfg);
    }

    public long getSeed() {
        return seed;
    }

    public StrataWorldConfig getConfig() {
        return cfg.copy();
    }

    public PlateField getPlateField() {
        return plates;
    }

    public ElevationModel getElevationModel() {
        return elevation;
    }

    public ClimateModel getClimateModel() {
        return climate;
    }

    public HydrologyModel getHydrologyModel() {
        return hydrology;
    }

    public ErosionModel getErosionModel() {
        return erosion;
    }

    public FormationModel getFormationModel() {
        return formationModel;
    }

    public StrataColumn getStrataColumn() {
        return strataColumn;
    }

    /**
     * Resolves one column. Overwrites every field of {@code out}.
     */
    public void sampleColumn(int x, int z, ColumnData out) {
        out.x = x;
        out.z = z;
        int sea = cfg.seaLevel;

        // 1. Base relief + tectonic context.
        ElevationModel.BaseSample base = new ElevationModel.BaseSample();
        elevation.sampleBase(x, z, base);
        BoundarySample b = base.boundary;

        // 2. Slope by forward differences on base heights (3 evaluations per
        // column total; accurate enough for erosion, surface and scarp work).
        ElevationModel.BaseSample sx = new ElevationModel.BaseSample();
        ElevationModel.BaseSample sz = new ElevationModel.BaseSample();
        elevation.sampleBase(x + SLOPE_STEP, z, sx);
        elevation.sampleBase(x, z + SLOPE_STEP, sz);
        double gx = (sx.height - base.height) / SLOPE_STEP;
        double gz = (sz.height - base.height) / SLOPE_STEP;
        double slope = Math.sqrt(gx * gx + gz * gz);

        // 3. Drainage from base heights (no carve feedback loop, by design).
        HydrologyModel.HydroSample hydro = new HydrologyModel.HydroSample();
        hydrology.sample(x, z, hydro);

        // 4. Climate from base height (final height differs by a few blocks;
        // using base keeps every stage a function of shared smooth fields).
        ClimateModel.ClimateSample cl = new ClimateModel.ClimateSample();
        climate.sample(x, z, base.height, base.continent, hydro.river01, hydro.lake01,
                hydro.wet01, cl);

        // 5. Formation + hardness guess for erosion (soil is soft, cliffs are
        // as hard as their outcrop).
        FormationModel.FormationSample fs = new FormationModel.FormationSample();
        formationModel.sample(x, z, base.continent, b, slope, hydro.confluence01, cl.temp01, fs);
        double hardness = slope > 0.5
                ? FormationModel.surfaceRock(fs.formation, fs.variantHash).getHardness01()
                : 0.2;

        // 6. Erosion / deposition / river carving.
        double lowering = erosion.lowering(hydro.confluence01, slope, hardness);
        double raising = erosion.deposition(hydro.confluence01, slope);
        boolean baseOcean = base.height < sea;
        double riverEff = hydro.river01 * (baseOcean ? 0.3 : 1.0);
        double carve = 0.0;
        if (cfg.enableRivers && riverEff > 0.0) {
            carve = Math.pow(riverEff, 1.2) * 8.0;
            // Lakes are depositional: rivers pass through but do not gouge.
            carve *= 1.0 - MathUtil.smoothstep(0.3, 0.7, hydro.lake01);
        }
        if (!baseOcean && hydro.wet01 > 0.0) {
            carve += hydro.wet01 * 1.5; // shallow wetland pans
        }
        double finalD = base.height - lowering + raising - carve;
        int finalH = MathUtil.clampInt((int) Math.round(finalD), 2, cfg.maxSurfaceHeight);
        int baseH = MathUtil.clampInt((int) Math.round(base.height), 2, cfg.maxSurfaceHeight);

        boolean ocean = finalH < sea;
        boolean deepOcean = sea - finalH > 12;
        boolean beach = !ocean && finalH <= sea + 2 && slope < 0.4 && hydro.river01 < 0.3;

        // 7. Contained tarns: pits with supply hold a few blocks of water even
        // above sea level (the pit guarantees containment at cell scale).
        int lakeWaterTop = -1;
        if (!ocean && hydro.pit && hydro.lake01 > 0.45) {
            lakeWaterTop = Math.min(finalH + 1 + (int) Math.round(hydro.lake01 * 2.0),
                    cfg.maxSurfaceHeight);
        }

        // 7b. River water: deep channels hold a few blocks of water below the
        // banks (carve guarantees containment: water stays under the rim).
        int riverWaterTop = -1;
        if (!ocean && hydro.river01 > 0.45 && carve > 4.5) {
            int depth = Math.min(3, (int) (carve - 4.0));
            riverWaterTop = Math.min(finalH + depth, cfg.maxSurfaceHeight);
        }

        // 8. Biome from climate + elevation + water context.
        BiomeKind biome = BiomeMapper.map(cl.temp01, cl.moist01, finalH, sea, ocean,
                deepOcean, beach, hydro.river01, slope);

        // 9. Fault layer-lag matches the surface scarp exactly.
        double faultShift = ElevationModel.scarpOffset(b.edgeDistance, b.sideW, b.transform01);

        // 10. Surface plan + layer stack.
        planSurface(x, z, finalH, sea, slope, ocean, beach, hydro, cl, biome,
                fs, out);
        out.layers.clear();
        strataColumn.build(x, z, finalH, out.soilDepth, out.surfaceTop, out.surfaceFiller,
                fs.formation == Formation.ALLUVIAL ? Formation.ALLUVIAL : fs.formation,
                fs.variantHash, faultShift, out.sedimentCapForColumn, out.sedimentTopForColumn,
                out.layers);

        // 11. Scalars.
        out.baseHeight = baseH;
        out.finalHeight = finalH;
        out.continent = base.continent;
        out.slope = slope;
        out.orogen01 = base.orogen01;
        out.ocean = ocean;
        out.deepOcean = deepOcean;
        out.beach = beach;
        out.confluence01 = hydro.confluence01;
        out.river01 = hydro.river01;
        out.lake01 = hydro.lake01;
        out.wet01 = hydro.wet01;
        out.pit = hydro.pit;
        out.lakeWaterTop = lakeWaterTop;
        out.riverWaterTop = riverWaterTop;
        out.erosionLower = lowering;
        out.depositionRaise = raising;
        out.riverCarve = carve;
        out.temp01 = cl.temp01;
        out.moist01 = cl.moist01;
        out.biome = biome;
        out.formation = fs.formation;
        out.deposition01 = fs.deposition01;
        out.boundary.copyFrom(b);
        out.plateId = b.plateId1;
        out.plateOceanic = b.oceanic1;
        out.faultShift = faultShift;
    }

    /**
     * Chooses the veneer (top block, filler, soil depth, ocean sediment) from
     * geology first and biome second: rock on cliffs, sand on beaches, gravel
     * in channels, biome tops everywhere else.
     */
    private void planSurface(int x, int z, int finalH, int sea, double slope, boolean ocean,
                             boolean beach, HydrologyModel.HydroSample hydro,
                             ClimateModel.ClimateSample cl, BiomeKind biome,
                             FormationModel.FormationSample fs, ColumnData out) {
        out.sedimentCapForColumn = 0;
        out.sedimentTopForColumn = GeoMaterial.SAND;
        if (ocean) {
            int depth = sea - finalH;
            GeoMaterial sed = strataColumn.oceanSediment(x, z, depth);
            out.surfaceTop = sed;
            out.surfaceFiller = sed;
            out.sedimentTopForColumn = sed;
            out.soilDepth = 1;
            out.sedimentCapForColumn = 2 + (depth > 10 ? 2 : 0);
        } else if (beach) {
            GeoMaterial sand = (cl.temp01 > 0.6 && cl.moist01 < 0.3)
                    ? GeoMaterial.RED_SAND : GeoMaterial.SAND;
            out.surfaceTop = sand;
            out.surfaceFiller = GeoMaterial.SAND;
            out.soilDepth = 3;
        } else if (hydro.river01 > 0.5) {
            out.surfaceTop = GeoMaterial.GRAVEL;
            out.surfaceFiller = GeoMaterial.DIRT;
            out.soilDepth = 2;
        } else if (hydro.lake01 > 0.4 && hydro.pit) {
            long hh = SeedMixer.hashSalted(SeedMixer.SALT_SURFACE, seed, x, z);
            out.surfaceTop = SeedMixer.toUnit(hh) < 0.5 ? GeoMaterial.CLAY : GeoMaterial.GRAVEL;
            out.surfaceFiller = GeoMaterial.DIRT;
            out.soilDepth = 2;
        } else if (slope > 0.55) {
            GeoMaterial sr = FormationModel.surfaceRock(fs.formation, fs.variantHash);
            out.surfaceTop = sr;
            out.surfaceFiller = sr;
            out.soilDepth = 0;
        } else if (fs.formation == Formation.ALLUVIAL || fs.deposition01 > 0.4) {
            out.surfaceTop = strataColumn.alluvium(x, z);
            out.surfaceFiller = GeoMaterial.DIRT;
            out.soilDepth = 3;
        } else if (slope > 0.35) {
            out.surfaceTop = BiomeMapper.defaultTop(biome);
            out.surfaceFiller = BiomeMapper.defaultFiller(biome);
            out.soilDepth = 1;
        } else {
            out.surfaceTop = BiomeMapper.defaultTop(biome);
            out.surfaceFiller = BiomeMapper.defaultFiller(biome);
            out.soilDepth = 3;
        }
    }
}
