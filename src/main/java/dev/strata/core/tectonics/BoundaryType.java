package dev.strata.core.tectonics;

/**
 * Dominant tectonic regime at a sample point, derived from the relative
 * velocity of the two nearest plates projected onto their inter-center axis
 * (see {@link PlateField} and docs/ALGORITHMS.md).
 */
public enum BoundaryType {
    /** Plate interior: far from any boundary, no tectonic overprint. */
    STABLE,
    /** Plates move toward each other: ranges, trenches, metamorphism. */
    CONVERGENT,
    /** Plates move apart: rifts on land, ridges under oceans. */
    DIVERGENT,
    /** Plates slide past each other: fault scarps, linear valleys. */
    TRANSFORM
}
