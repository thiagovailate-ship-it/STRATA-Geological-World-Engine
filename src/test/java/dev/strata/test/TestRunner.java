package dev.strata.test;

/**
 * Runs the STRATA suite (Tests A-J). Exit code 0 = all green.
 * {@code --smoke} runs a fast pipeline subset (used for jar verification).
 */
public final class TestRunner {

    private TestRunner() {
    }

    public static void main(String[] args) {
        boolean smoke = args.length > 0 && "--smoke".equals(args[0]);
        if (smoke) {
            System.out.println("STRATA smoke check (jar load + one chunk pipeline)");
            long t0 = System.nanoTime();
            IntegrationTest.runSmoke();
            System.out.println("SMOKE OK in " + ((System.nanoTime() - t0) / 1000000) + " ms");
            return;
        }
        System.out.println("STRATA test suite (core engine, deterministic)");
        System.out.println("==============================================");
        int passed = 0;
        int failed = 0;
        passed += runOne("A Reproducibility  ", new Runnable() {
            public void run() {
                ReproTest.run();
            }
        }) ? 1 : 0;
        failed += lastFailed;
        passed += runOne("B Seed variation   ", new Runnable() {
            public void run() {
                SeedVariationTest.run();
            }
        }) ? 1 : 0;
        failed += lastFailed;
        passed += runOne("C Bounds           ", new Runnable() {
            public void run() {
                BoundsTest.run();
            }
        }) ? 1 : 0;
        failed += lastFailed;
        passed += runOne("D Continuity       ", new Runnable() {
            public void run() {
                ContinuityTest.run();
            }
        }) ? 1 : 0;
        failed += lastFailed;
        passed += runOne("E Numeric safety   ", new Runnable() {
            public void run() {
                NumericSafetyTest.run();
            }
        }) ? 1 : 0;
        failed += lastFailed;
        passed += runOne("F Coupling         ", new Runnable() {
            public void run() {
                CouplingTest.run();
            }
        }) ? 1 : 0;
        failed += lastFailed;
        passed += runOne("G Integration      ", new Runnable() {
            public void run() {
                IntegrationTest.run();
            }
        }) ? 1 : 0;
        failed += lastFailed;
        passed += runOne("H Performance      ", new Runnable() {
            public void run() {
                PerfTest.run();
            }
        }) ? 1 : 0;
        failed += lastFailed;
        passed += runOne("I Hydro coordinates", new Runnable() {
            public void run() {
                HydrologyGridAlignmentTest.run();
            }
        }) ? 1 : 0;
        failed += lastFailed;
        passed += runOne("J Tectonic rank continuity", new Runnable() {
            public void run() {
                TectonicRankContinuityTest.run();
            }
        }) ? 1 : 0;
        failed += lastFailed;

        System.out.println("==============================================");
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static int lastFailed;

    private static boolean runOne(String name, Runnable test) {
        System.out.println("[TEST " + name + "]");
        long t0 = System.nanoTime();
        try {
            test.run();
            System.out.println("[PASS " + name + "] in " + ((System.nanoTime() - t0) / 1000000)
                    + " ms");
            lastFailed = 0;
            return true;
        } catch (Throwable t) {
            System.out.println("[FAIL " + name + "] " + t);
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < Math.min(st.length, 12); i++) {
                System.out.println("    at " + st[i]);
            }
            lastFailed = 1;
            return false;
        }
    }
}
