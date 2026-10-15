package dev.strata.core;

import dev.strata.core.stratigraphy.Formation;
import dev.strata.core.stratigraphy.GeoMaterial;
import dev.strata.core.stratigraphy.LayerStack;
import dev.strata.core.tectonics.BoundarySample;
import dev.strata.core.terrain.BiomeKind;

/**
 * Complete geological answer for one block column: the single object the
 * chunk provider, the ore generator, the lab and the tests all read.
 *
 * <p>Instances are mutable and meant to be reused by the caller; the model
 * overwrites every field on each {@link StrataWorldModel#sampleColumn} call,
 * so there is no aliasing between samples.</p>
 */
public final class ColumnData {

    public int x;
    public int z;
    /** Rounded pre-erosion height. */
    public int baseHeight;
    /** Rounded final surface height (top solid block). */
    public int finalHeight;
    /** Continent mask (C &lt; 0 is oceanic). */
    public double continent;
    /** Local slope (rise over run) from base heights. */
    public double slope;
    /** Mountain-mask strength in [0,1]. */
    public double orogen01;
    public boolean ocean;
    public boolean deepOcean;
    public boolean beach;
    /** Bounded-radius drainage confluence in [0,1]. */
    public double confluence01;
    /** River mask in [0,1]. */
    public double river01;
    /** Lake mask in [0,1]. */
    public double lake01;
    /** Wet-flat mask in [0,1] (holds no deep water). */
    public double wet01;
    public boolean pit;
    /** Water top for contained tarns, or -1 when absent. */
    public int lakeWaterTop = -1;
    /** Water top held inside carved river channels, or -1 when dry. */
    public int riverWaterTop = -1;
    public double erosionLower;
    public double depositionRaise;
    /** Carve depth applied by rivers, in blocks. */
    public double riverCarve;
    public double temp01;
    public double moist01;
    public BiomeKind biome = BiomeKind.PLAINS;
    public Formation formation = Formation.CLASTIC_BASIN;
    public double deposition01;
    public GeoMaterial surfaceTop = GeoMaterial.GRASS;
    public GeoMaterial surfaceFiller = GeoMaterial.DIRT;
    public int soilDepth;
    /** Extra ocean-floor sediment layers (0 on land). */
    public int sedimentCapForColumn;
    /** Ocean-floor sediment material. */
    public GeoMaterial sedimentTopForColumn = GeoMaterial.SAND;
    public final LayerStack layers = new LayerStack();
    public final BoundarySample boundary = new BoundarySample();
    public int plateId;
    public boolean plateOceanic;
    /** Fault layer-lag in blocks (matches the surface scarp). */
    public double faultShift;

    /**
     * Exact field-by-field comparison for the reproducibility test.
     * Doubles compare with {@code ==} on purpose: the engine is bit
     * deterministic on one JVM, and any drift is a bug.
     */
    public boolean sameAs(ColumnData o) {
        if (o == null) {
            return false;
        }
        if (x != o.x || z != o.z || baseHeight != o.baseHeight || finalHeight != o.finalHeight) {
            return false;
        }
        if (continent != o.continent || slope != o.slope || orogen01 != o.orogen01) {
            return false;
        }
        if (ocean != o.ocean || deepOcean != o.deepOcean || beach != o.beach) {
            return false;
        }
        if (confluence01 != o.confluence01 || river01 != o.river01 || lake01 != o.lake01) {
            return false;
        }
        if (pit != o.pit || lakeWaterTop != o.lakeWaterTop
                || riverWaterTop != o.riverWaterTop) {
            return false;
        }
        if (erosionLower != o.erosionLower || depositionRaise != o.depositionRaise
                || riverCarve != o.riverCarve) {
            return false;
        }
        if (temp01 != o.temp01 || moist01 != o.moist01) {
            return false;
        }
        if (biome != o.biome || formation != o.formation || deposition01 != o.deposition01) {
            return false;
        }
        if (surfaceTop != o.surfaceTop || surfaceFiller != o.surfaceFiller || soilDepth != o.soilDepth) {
            return false;
        }
        if (sedimentCapForColumn != o.sedimentCapForColumn
                || sedimentTopForColumn != o.sedimentTopForColumn) {
            return false;
        }
        if (plateId != o.plateId || plateOceanic != o.plateOceanic || faultShift != o.faultShift) {
            return false;
        }
        if (layers.count != o.layers.count) {
            return false;
        }
        for (int i = 0; i < layers.count; i++) {
            if (layers.rock[i] != o.layers.rock[i] || layers.topY[i] != o.layers.topY[i]) {
                return false;
            }
        }
        BoundarySample a = boundary;
        BoundarySample b = o.boundary;
        return a.edgeDistance == b.edgeDistance
                && a.signedDistance == b.signedDistance
                && a.f1 == b.f1
                && a.f2 == b.f2
                && a.f3 == b.f3
                && a.sideW == b.sideW
                && a.plateBias == b.plateBias
                && a.plateBase == b.plateBase
                && a.boundary01 == b.boundary01
                && a.dominant == b.dominant
                && a.convergent01 == b.convergent01
                && a.divergent01 == b.divergent01
                && a.transform01 == b.transform01
                && a.strikeX == b.strikeX
                && a.strikeZ == b.strikeZ
                && a.axisX == b.axisX
                && a.axisZ == b.axisZ
                && a.oceanic1 == b.oceanic1
                && a.oceanic2 == b.oceanic2
                && a.plateId1 == b.plateId1
                && a.plateId2 == b.plateId2
                && a.plateBase1 == b.plateBase1
                && a.plateBase2 == b.plateBase2;
    }
}
