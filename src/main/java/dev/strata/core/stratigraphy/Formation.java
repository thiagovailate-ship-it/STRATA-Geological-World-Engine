package dev.strata.core.stratigraphy;

/**
 * Regional rock formation: the template that decides which layer stack a
 * column gets. Formations derive from plate type, boundary regime, elevation
 * and deposition — never from an isolated random roll.
 */
public enum Formation {
    /** Granite/gneiss continental interior (stable shields). */
    SHIELD,
    /** Convergent belts: schist/gneiss with granite and marble lenses. */
    OROGEN,
    /** Basalt/andesite volcanic provinces. */
    VOLCANIC,
    /** Warm shallow-basin limestone platforms. */
    CARBONATE,
    /** Sandstone/shale/conglomerate clastic basins (default lowland). */
    CLASTIC_BASIN,
    /** Continental rifts: volcanics interbedded with clastics. */
    RIFT,
    /** Oceanic crust: sediment cap over basalt. */
    OCEANIC,
    /** Thick recent alluvium over the local formation (cap, not replacement). */
    ALLUVIAL
}
