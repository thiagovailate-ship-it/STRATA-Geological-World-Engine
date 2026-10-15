package dev.strata.core.stratigraphy;

import dev.strata.core.config.StrataWorldConfig;
import dev.strata.core.tectonics.BoundarySample;
import dev.strata.core.util.MathUtil;
import dev.strata.core.util.SeedMixer;
import dev.strata.core.util.ValueNoise2D;

/**
 * Chooses the {@link Formation} for a column from the same regional fields
 * that built the relief (plate type, boundary regime, continent mask, slope,
 * drainage, temperature). Volcanic provinces additionally vary per plate so
 * whole regions share a magmatic character.
 */
public final class FormationModel {

    public static final class FormationSample {
        public Formation formation = Formation.CLASTIC_BASIN;
        /** Low-frequency spatial variant hash (lenses, thickness noise). */
        public long variantHash;
        /** Alluvial-cap strength in [0,1] (veneer over any formation). */
        public double deposition01;
    }

    private final long seed;
    private final StrataWorldConfig cfg;
    private final ValueNoise2D provinceNoise;

    public FormationModel(long seed, StrataWorldConfig cfg) {
        if (cfg == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.seed = seed;
        this.cfg = cfg;
        this.provinceNoise = new ValueNoise2D(seed ^ 0xF0C4L);
    }

    public void sample(double x, double z, double continent, BoundarySample boundary,
                       double slope, double confluence01, double temp01, FormationSample out) {
        double dep = 0.0;
        if (slope < 0.12) {
            dep = MathUtil.smoothstep(0.20, 0.60, MathUtil.clamp01(confluence01))
                    * (1.0 - slope / 0.12);
        }
        out.deposition01 = dep;

        int vcx = MathUtil.floorDiv((int) Math.floor(x), 173);
        int vcz = MathUtil.floorDiv((int) Math.floor(z), 173);
        out.variantHash = SeedMixer.hashSalted(SeedMixer.SALT_FORM, seed, vcx, vcz);

        Formation f;
        if (continent < -0.15) {
            f = Formation.OCEANIC;
        } else if (boundary.convergent01 > 0.45) {
            f = Formation.OROGEN;
        } else if (boundary.divergent01 > 0.40 && continent > -0.10) {
            f = Formation.RIFT;
        } else if (isVolcanicProvince(x, z, boundary)) {
            f = Formation.VOLCANIC;
        } else if (continent >= -0.10 && continent <= 0.35 && slope < 0.30) {
            double carb = SeedMixer.toUnit(SeedMixer.hashSalted(SeedMixer.SALT_FORM, seed,
                    boundary.plateId1, 77L));
            f = (temp01 > 0.55 && carb < 0.45) ? Formation.CARBONATE : Formation.CLASTIC_BASIN;
        } else if (continent > 0.40 && boundary.boundary01 < 0.25) {
            f = Formation.SHIELD;
        } else {
            f = Formation.CLASTIC_BASIN;
        }
        // Strong recent deposition caps (not replaces) the local formation.
        out.formation = dep > 0.55 ? Formation.ALLUVIAL : f;
    }

    /**
     * Volcanic character: per-plate predisposition near convergent or rifted
     * margins, modulated by a smooth province field so provinces have soft
     * edges instead of per-column speckle.
     */
    private boolean isVolcanicProvince(double x, double z, BoundarySample b) {
        double margin = Math.max(b.convergent01, b.divergent01);
        if (margin < 0.28) {
            return false;
        }
        double plateVolc = SeedMixer.toUnit(SeedMixer.hashSalted(SeedMixer.SALT_VOLC, seed,
                b.plateId1, 0L));
        double local = provinceNoise.fbm01(x / 900.0, z / 900.0, 2, 2.0, 0.5);
        return plateVolc * 0.7 + local * 0.3 < 0.30;
    }

    /** Exposed surface rock for steep slopes (no soil), per formation. */
    public static GeoMaterial surfaceRock(Formation f, long variantHash) {
        double u = SeedMixer.toUnit(SeedMixer.mix64(variantHash + 0x5CA4L));
        switch (f) {
            case OROGEN:
                return u < 0.45 ? GeoMaterial.SCHIST : (u < 0.8 ? GeoMaterial.GNEISS : GeoMaterial.GRANITE);
            case VOLCANIC:
                return u < 0.7 ? GeoMaterial.BASALT : GeoMaterial.ANDESITE;
            case RIFT:
                return u < 0.5 ? GeoMaterial.BASALT : GeoMaterial.SANDSTONE;
            case CARBONATE:
                return GeoMaterial.LIMESTONE;
            case OCEANIC:
                return GeoMaterial.BASALT;
            case SHIELD:
                return u < 0.5 ? GeoMaterial.GRANITE : GeoMaterial.GNEISS;
            case ALLUVIAL:
                return GeoMaterial.CONGLOMERATE;
            case CLASTIC_BASIN:
            default:
                if (u < 0.4) {
                    return GeoMaterial.SANDSTONE;
                }
                return u < 0.7 ? GeoMaterial.SHALE : GeoMaterial.CONGLOMERATE;
        }
    }
}
