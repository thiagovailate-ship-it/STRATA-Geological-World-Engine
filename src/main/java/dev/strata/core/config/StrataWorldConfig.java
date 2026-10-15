package dev.strata.core.config;

import dev.strata.core.util.MathUtil;

/**
 * Plain tuning parameters for world generation.
 *
 * <p>This is a validated POJO with no Forge dependency: the Forge config screen
 * / file ({@code dev.strata.config.StrataConfig}) loads values into a fresh
 * instance and the model keeps its own defensive copy. Every field is actually
 * read by the generator (no dead options); {@link #validate()} clamps hostile
 * values into ranges the math can handle.</p>
 */
public final class StrataWorldConfig {

    /** Scales all relief away from sea level. 0 = flat water world. */
    public double reliefIntensity = 1.0;
    /** Scales convergent mountain ranges and foothills only. */
    public double mountainIntensity = 1.0;
    /** Voronoi plate cell size in blocks (regional scale). */
    public int plateCellSize = 1536;
    /** Fraction of plates that are oceanic (0..1). */
    public double oceanFraction = 0.55;
    /** Wavelength in blocks of the continent/ocean mask noise. */
    public double continentWavelength = 2400.0;
    /** Domain-warp amplitude in blocks applied before plate lookup. */
    public double warpAmplitude = 320.0;
    /** Wavelength in blocks of the plate-boundary warp field. */
    public double warpWavelength = 2800.0;
    /** Scales erosion lowering and deposition. 0 disables erosion math. */
    public double erosionStrength = 1.0;
    /** Scales local roughness octaves. */
    public double roughnessIntensity = 1.0;
    /** Scales river thresholds (higher = more/wider rivers). */
    public double drainageIntensity = 1.0;
    /** Vanilla-parity sea level. */
    public int seaLevel = 63;
    /** Hard clamp for the terrain surface (must stay below 256). */
    public int maxSurfaceHeight = 248;

    public boolean enableOrogen = true;
    public boolean enableRivers = true;
    public boolean enableErosion = true;
    public boolean enableFaultScarps = true;

    /** Hydrology sampling cell in blocks. */
    public int hydroCellSize = 8;
    /** Inner cells per side of one hydrology tile. */
    public int hydroTileCells = 16;
    /** Uniform upstream confluence radius in cells (tile-margin size). */
    public int hydroRadiusCells = 12;

    /** Clamps every field into its documented safe range. */
    public void validate() {
        reliefIntensity = MathUtil.clamp(reliefIntensity, 0.0, 3.0);
        mountainIntensity = MathUtil.clamp(mountainIntensity, 0.0, 3.0);
        plateCellSize = MathUtil.clampInt(plateCellSize, 384, 8192);
        oceanFraction = MathUtil.clamp01(oceanFraction);
        continentWavelength = MathUtil.clamp(continentWavelength, 600.0, 8000.0);
        warpAmplitude = MathUtil.clamp(warpAmplitude, 0.0, 1200.0);
        warpWavelength = MathUtil.clamp(warpWavelength, 800.0, 12000.0);
        erosionStrength = MathUtil.clamp(erosionStrength, 0.0, 3.0);
        roughnessIntensity = MathUtil.clamp(roughnessIntensity, 0.0, 3.0);
        drainageIntensity = MathUtil.clamp(drainageIntensity, 0.0, 3.0);
        seaLevel = MathUtil.clampInt(seaLevel, 20, 120);
        maxSurfaceHeight = MathUtil.clampInt(maxSurfaceHeight, 128, 255);
        hydroCellSize = MathUtil.clampInt(hydroCellSize, 4, 16);
        hydroTileCells = MathUtil.clampInt(hydroTileCells, 8, 32);
        hydroRadiusCells = MathUtil.clampInt(hydroRadiusCells, 4, 24);
    }

    /** Defensive copy. */
    public StrataWorldConfig copy() {
        StrataWorldConfig c = new StrataWorldConfig();
        c.reliefIntensity = reliefIntensity;
        c.mountainIntensity = mountainIntensity;
        c.plateCellSize = plateCellSize;
        c.oceanFraction = oceanFraction;
        c.continentWavelength = continentWavelength;
        c.continentWavelength = continentWavelength;
        c.warpAmplitude = warpAmplitude;
        c.warpWavelength = warpWavelength;
        c.erosionStrength = erosionStrength;
        c.roughnessIntensity = roughnessIntensity;
        c.drainageIntensity = drainageIntensity;
        c.seaLevel = seaLevel;
        c.maxSurfaceHeight = maxSurfaceHeight;
        c.enableOrogen = enableOrogen;
        c.enableRivers = enableRivers;
        c.enableErosion = enableErosion;
        c.enableFaultScarps = enableFaultScarps;
        c.hydroCellSize = hydroCellSize;
        c.hydroTileCells = hydroTileCells;
        c.hydroRadiusCells = hydroRadiusCells;
        return c;
    }
}
