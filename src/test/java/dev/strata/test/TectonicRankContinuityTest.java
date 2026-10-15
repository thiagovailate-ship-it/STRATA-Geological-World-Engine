package dev.strata.test;

import dev.strata.core.StrataWorldModel;
import dev.strata.core.config.StrataWorldConfig;
import dev.strata.core.terrain.ElevationModel;
import dev.strata.core.tectonics.BoundarySample;

/** Regression samples for the two observed rank-swap cliffs in the base relief. */
public final class TectonicRankContinuityTest {

    private TectonicRankContinuityTest() {
    }

    public static void run() {
        StrataWorldConfig cfg = new StrataWorldConfig();
        cfg.validate();
        StrataWorldModel model = new StrataWorldModel(12345L, cfg);
        checkAdjacent(model.getElevationModel(), -273, 68, -272, 68, "F1/F2 boundary swap");
        checkAdjacent(model.getElevationModel(), 191, -216, 192, -216, "F3/F4 cutoff swap");
        checkSignedSide(model.getElevationModel(), -273, 68, -272, 68);
        System.out.println("  both former rank-swap cliffs are now continuous in base height, continent and regime fields");
    }

    private static void checkSignedSide(ElevationModel elevation, int x1, int z1, int x2, int z2) {
        ElevationModel.BaseSample a = new ElevationModel.BaseSample();
        ElevationModel.BaseSample b = new ElevationModel.BaseSample();
        elevation.sampleBase(x1, z1, a);
        elevation.sampleBase(x2, z2, b);
        Assert.check(a.boundary.sideW * b.boundary.sideW < 0.0,
                "signed side field did not cross the nearest-pair boundary");
        Assert.check(Math.abs(a.boundary.sideW - b.boundary.sideW) < 0.05,
                "signed side field has a discontinuity: "
                        + Math.abs(a.boundary.sideW - b.boundary.sideW));
    }

    private static void checkAdjacent(ElevationModel elevation, int x1, int z1,
                                      int x2, int z2, String what) {
        ElevationModel.BaseSample a = new ElevationModel.BaseSample();
        ElevationModel.BaseSample b = new ElevationModel.BaseSample();
        elevation.sampleBase(x1, z1, a);
        elevation.sampleBase(x2, z2, b);
        double dHeight = Math.abs(a.height - b.height);
        double dContinent = Math.abs(a.continent - b.continent);
        BoundarySample ba = a.boundary;
        BoundarySample bb = b.boundary;
        double dRegime = Math.max(Math.abs(ba.convergent01 - bb.convergent01),
                Math.max(Math.abs(ba.divergent01 - bb.divergent01),
                        Math.abs(ba.transform01 - bb.transform01)));
        Assert.check(dHeight < 4.0, what + " base-height jump: " + dHeight);
        Assert.check(dContinent < 0.05, what + " continent-field jump: " + dContinent);
        // The existing continuity suite permits <=0.60 one-step changes for
        // bounded hydrology masks; use that same broad gross-seam guard here.
        Assert.check(dRegime < 0.60, what + " boundary-regime jump: " + dRegime);
    }
}
