package dev.strata.core.stratigraphy;

import dev.strata.core.config.StrataWorldConfig;
import dev.strata.core.util.MathUtil;
import dev.strata.core.util.SeedMixer;
import dev.strata.core.util.ValueNoise2D;

/**
 * Builds the per-column {@link LayerStack} from a formation template.
 *
 * <p>Layer thicknesses vary with smooth low-frequency noise (wavelength ~200
 * blocks), so contacts undulate instead of forming global flat bands, and stay
 * continuous across chunk (and region) borders. Fault throw shifts boundaries
 * relative to the surface, so the upthrown side of a scarp exposes deeper
 * rocks. Formation <em>contacts</em> are the one intended discontinuity: where
 * the geology changes, the stack changes — that is the model working, and it
 * is a smooth-field decision (no chunk dependence).</p>
 */
public final class StrataColumn {

    private final long seed;
    private final StrataWorldConfig cfg;
    private final ValueNoise2D thickNoise;
    private final ValueNoise2D lensNoise;

    public StrataColumn(long seed, StrataWorldConfig cfg) {
        if (cfg == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.seed = seed;
        this.cfg = cfg;
        this.thickNoise = new ValueNoise2D(seed ^ 0x771C);
        this.lensNoise = new ValueNoise2D(seed ^ 0x1E15);
    }

    /**
     * Builds the stack for one column.
     *
     * @param surfaceY   final surface height (top solid block)
     * @param soilDepth  veneer thickness (0 = exposed bedrock)
     * @param top        veneer top material (surface block)
     * @param filler     veneer filler material (below the top block)
     * @param formation  regional formation template
     * @param variantHash low-frequency spatial variant (lenses)
     * @param faultShift layer lag vs. the surface (fault exposure), in blocks
     * @param sedimentCap extra sediment layers for ocean floors (0 on land)
     * @param sedimentTop sediment veneer material for ocean floors
     */
    public void build(double x, double z, int surfaceY, int soilDepth,
                      GeoMaterial top, GeoMaterial filler, Formation formation,
                      long variantHash, double faultShift, int sedimentCap,
                      GeoMaterial sedimentTop, LayerStack out) {
        out.clear();
        if (surfaceY < 1) {
            out.push(GeoMaterial.BEDROCK, 1);
            return;
        }

        int y = surfaceY;
        // Veneer: 1 top block + (soilDepth-1) filler + ocean sediment cap.
        out.push(top, y);
        y -= 1;
        int under = Math.max(0, soilDepth - 1) + Math.max(0, sedimentCap);
        if (under > 0) {
            out.push(sedimentCap > 0 ? sedimentTop : filler, y);
            y -= under;
        }

        // Formation template below the veneer. Boundaries lag the surface by
        // faultShift so the upthrown fault block exposes deeper rocks (the
        // downthrown block stays conformable: negative shifts clamp to keep
        // tops strictly descending, which rockAt() requires).
        Template t = template(formation, variantHash);
        int prevTop = y + 1;
        for (int i = 0; i < t.rock.length && out.count < LayerStack.MAX_LAYERS - 1; i++) {
            double wobble = thickNoise.fbm01(x / 200.0 + i * 17.31, z / 200.0 - i * 9.17, 2, 2.0, 0.5);
            int thick = Math.max(2, (int) (t.thick[i] * (0.7 + 0.6 * wobble)));
            int topB = y - (int) Math.round(faultShift);
            if (topB > y) {
                topB = y;
            }
            if (topB >= prevTop) {
                topB = prevTop - 1;
            }
            out.push(t.rock[i], topB);
            prevTop = topB;
            y -= thick;
            if (y < 4) {
                break;
            }
        }
        // Deep basement: whatever the formation sits on.
        if (y >= prevTop) {
            y = prevTop - 1;
        }
        out.push(t.basement, y);
    }

    /** Formation template: sequence + mean thicknesses + basement. */
    private static final class Template {
        final GeoMaterial[] rock;
        final int[] thick;
        final GeoMaterial basement;

        Template(GeoMaterial[] rock, int[] thick, GeoMaterial basement) {
            this.rock = rock;
            this.thick = thick;
            this.basement = basement;
        }
    }

    private Template template(Formation f, long variantHash) {
        double u1 = SeedMixer.toUnit(SeedMixer.mix64(variantHash + 11L));
        double u2 = SeedMixer.toUnit(SeedMixer.mix64(variantHash + 23L));
        switch (f) {
            case OROGEN: {
                // Metamorphic pile with granite intrusions / marble lenses.
                GeoMaterial mid = u1 < 0.35 ? GeoMaterial.GRANITE : GeoMaterial.GNEISS;
                GeoMaterial lens = u2 < 0.30 ? GeoMaterial.MARBLE : GeoMaterial.QUARTZITE;
                return new Template(
                        new GeoMaterial[]{GeoMaterial.SCHIST, mid, lens, GeoMaterial.GNEISS},
                        new int[]{14, 24, 8, 30}, GeoMaterial.GRANITE);
            }
            case VOLCANIC:
                return new Template(
                        new GeoMaterial[]{GeoMaterial.BASALT, GeoMaterial.ANDESITE,
                                GeoMaterial.BASALT, GeoMaterial.CONGLOMERATE},
                        new int[]{20, 12, 18, 10}, GeoMaterial.BASALT);
            case RIFT:
                return new Template(
                        new GeoMaterial[]{GeoMaterial.BASALT, GeoMaterial.SANDSTONE,
                                GeoMaterial.SHALE, GeoMaterial.CONGLOMERATE},
                        new int[]{12, 14, 10, 12}, GeoMaterial.BASALT);
            case CARBONATE:
                return new Template(
                        new GeoMaterial[]{GeoMaterial.LIMESTONE, GeoMaterial.SHALE,
                                GeoMaterial.LIMESTONE},
                        new int[]{22, 4, 26}, GeoMaterial.LIMESTONE);
            case OCEANIC:
                return new Template(
                        new GeoMaterial[]{GeoMaterial.BASALT, GeoMaterial.ANDESITE},
                        new int[]{24, 30}, GeoMaterial.BASALT);
            case SHIELD: {
                GeoMaterial upper = u1 < 0.5 ? GeoMaterial.GRANITE : GeoMaterial.GNEISS;
                return new Template(
                        new GeoMaterial[]{upper, GeoMaterial.GNEISS, GeoMaterial.SCHIST},
                        new int[]{18, 30, 20}, GeoMaterial.GRANITE);
            }
            case ALLUVIAL:
                return new Template(
                        new GeoMaterial[]{GeoMaterial.CONGLOMERATE, GeoMaterial.SANDSTONE,
                                GeoMaterial.SHALE},
                        new int[]{10, 14, 18}, GeoMaterial.SANDSTONE);
            case CLASTIC_BASIN:
            default: {
                GeoMaterial cap = u1 < 0.5 ? GeoMaterial.SANDSTONE : GeoMaterial.CONGLOMERATE;
                GeoMaterial mid = u2 < 0.6 ? GeoMaterial.SHALE : GeoMaterial.SLATE;
                return new Template(
                        new GeoMaterial[]{cap, mid, GeoMaterial.SANDSTONE},
                        new int[]{16, 10, 24}, GeoMaterial.SANDSTONE);
            }
        }
    }

    /** Ocean-floor sediment choice: sand near shore, gravel/clay offshore. */
    public GeoMaterial oceanSediment(double x, double z, int depthBelowSea) {
        double n = lensNoise.fbm01(x / 130.0, z / 130.0, 2, 2.0, 0.5);
        if (depthBelowSea > 14) {
            return n < 0.45 ? GeoMaterial.GRAVEL : (n < 0.75 ? GeoMaterial.CLAY : GeoMaterial.SAND);
        }
        return n < 0.65 ? GeoMaterial.SAND : GeoMaterial.GRAVEL;
    }

    /** Alluvium choice for depositional flats. */
    public GeoMaterial alluvium(double x, double z) {
        double n = lensNoise.fbm01(x / 90.0 + 31.7, z / 90.0 - 12.3, 2, 2.0, 0.5);
        return n < 0.55 ? GeoMaterial.DIRT : (n < 0.8 ? GeoMaterial.GRAVEL : GeoMaterial.SAND);
    }
}
