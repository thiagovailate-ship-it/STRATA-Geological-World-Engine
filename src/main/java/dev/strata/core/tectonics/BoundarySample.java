package dev.strata.core.tectonics;

/**
 * Mutable result struct for one {@link PlateField} query.
 * Reuse instances where convenient; the model treats them as values.
 */
public final class BoundarySample {

    /** Approximate distance to the nearest plate boundary, in blocks. */
    public double edgeDistance;
    /**
     * Signed across-boundary coordinate: 0 at the bisector, negative on both
     * sides (V-shaped). Diagnostics ONLY — never drive relief blends from it
     * (its gradient flips at the boundary and would crease the terrain).
     */
    public double signedDistance;
    /** Distance to the three nearest plate centers (f1 <= f2 <= f3). */
    public double f1;
    public double f2;
    public double f3;
    /**
     * Smooth side coordinate in [-1, +1]: -1 deep in plate 1, +1 deep in
     * plate 2, 0 at the boundary. Symmetric inverse-distance weighting makes
     * it seamless across F1/F2 swaps (see PlateField).
     */
    public double sideW;
    /** 1 exactly at a boundary, fading to 0 in plate interiors. */
    public double boundary01;
    /** Regime with the strongest intensity (STABLE in interiors). */
    public BoundaryType dominant = BoundaryType.STABLE;
    /** Convergent intensity in [0,1] (already distance-weighted). */
    public double convergent01;
    /** Divergent intensity in [0,1] (already distance-weighted). */
    public double divergent01;
    /** Transform intensity in [0,1] (already distance-weighted). */
    public double transform01;
    /**
     * Blended continent/ocean bias and base elevation from the three nearest
     * plates (symmetric inverse-distance weights). THESE are what relief
     * blends use — they cannot jump when the 2nd/3rd plates swap ranks.
     */
    public double plateBias;
    public double plateBase;
    /**
     * Nearest-pair diagnostics (strike/axis/plate-2 identity). These CAN
     * change away from boundaries when plates swap rank — never drive relief
     * from them; they exist for debug coloring and province labels.
     */
    public double strikeX = 1.0;
    public double strikeZ;
    /** Unit vector ACROSS the boundary, from plate 1 center to plate 2 center. */
    public double axisX = 1.0;
    public double axisZ;
    /** True when plate 1 / plate 2 is oceanic. */
    public boolean oceanic1;
    public boolean oceanic2;
    /** Stable plate identifiers (for provinces and debug coloring). */
    public int plateId1;
    public int plateId2;
    /** Base elevation offsets of the two nearest plates (blocks). */
    public double plateBase1;
    public double plateBase2;

    public void copyFrom(BoundarySample o) {
        edgeDistance = o.edgeDistance;
        signedDistance = o.signedDistance;
        f1 = o.f1;
        f2 = o.f2;
        f3 = o.f3;
        sideW = o.sideW;
        plateBias = o.plateBias;
        plateBase = o.plateBase;
        boundary01 = o.boundary01;
        dominant = o.dominant;
        convergent01 = o.convergent01;
        divergent01 = o.divergent01;
        transform01 = o.transform01;
        strikeX = o.strikeX;
        strikeZ = o.strikeZ;
        axisX = o.axisX;
        axisZ = o.axisZ;
        oceanic1 = o.oceanic1;
        oceanic2 = o.oceanic2;
        plateId1 = o.plateId1;
        plateId2 = o.plateId2;
        plateBase1 = o.plateBase1;
        plateBase2 = o.plateBase2;
    }
}
