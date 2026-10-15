package dev.strata.core.tectonics;

import dev.strata.core.config.StrataWorldConfig;
import dev.strata.core.util.MathUtil;
import dev.strata.core.util.SeedMixer;
import dev.strata.core.util.ValueNoise2D;

/**
 * Regional tectonic model: jittered-grid Voronoi plates with organic,
 * domain-warped boundaries.
 *
 * <p>Each grid cell owns one plate whose center is jittered inside the cell.
 * A sample finds the three nearest centers (F1/F2/F3 search over 3x3 cells);
 * the approximate distance to the shared Voronoi edge is {@code (F2-F1)/2}.
 * Before lookup, the query point is warped by low-frequency noise, which bends
 * plate boundaries into organic curves without changing the topology.</p>
 *
 * <p>Boundary classification is geometric, not random: with {@code a} the unit
 * vector from center 1 to center k and {@code v1, vk} the plate velocities,
 * {@code conv = (v1-vk).a} measures approach speed (positive = converging)
 * and the perpendicular remainder measures shear (transform motion).</p>
 *
 * <p>Continuity design (the subtle part): the 2nd/3rd plates swap ranks along
 * order-2 Voronoi lines, so anything that reads "plate 2's" identity alone
 * would jump there — most visibly near triple junctions. Every relief-driving
 * quantity is therefore a <em>symmetric blend</em>: per-plate properties use
 * inverse-distance weights over all three plates, and classification blends
 * the (1,2) and (1,3) pairs by their edge proximity. At a rank swap the
 * weights are equal, so blends are exactly continuous. Only the diagnostic
 * nearest-pair fields (strike, axis, plate-2 identity) may change off
 * boundaries — and relief never reads them.</p>
 *
 * <p>Thread-safe: immutable after construction.</p>
 */
public final class PlateField {

    private final long seed;
    private final StrataWorldConfig cfg;
    private final ValueNoise2D warpNoise;
    private final double classifyWidth;

    public PlateField(long seed, StrataWorldConfig cfg) {
        if (cfg == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.seed = seed;
        this.cfg = cfg;
        this.warpNoise = new ValueNoise2D(seed ^ 0x9A401L);
        // Boundaries influence classification out to ~28% of a plate cell.
        this.classifyWidth = MathUtil.clamp(cfg.plateCellSize * 0.28, 120.0, 460.0);
    }

    /** Classification falloff width in blocks (for tests and docs). */
    public double getClassifyWidth() {
        return classifyWidth;
    }

    /**
     * Samples the tectonic field at a block coordinate.
     * Pure function of {@code (seed, x, z)}: order- and chunk-independent.
     */
    public void sample(double x, double z, BoundarySample out) {
        // 1. Domain warp: organic boundaries, same topology.
        double wx = 0.0;
        double wz = 0.0;
        if (cfg.warpAmplitude > 0.0) {
            double wq = cfg.warpWavelength;
            wx = cfg.warpAmplitude * warpNoise.fbm(x / wq + 13.7, z / wq + 7.1, 3, 2.0, 0.5);
            wz = cfg.warpAmplitude * warpNoise.fbm(x / wq - 3.2, z / wq + 11.9, 3, 2.0, 0.5);
        }
        double px = x + wx;
        double pz = z + wz;

        // 2. Nearest three jittered cell centers (3x3 search).
        int cell = cfg.plateCellSize;
        int ccx = MathUtil.floorDiv((int) Math.floor(px), cell);
        int ccz = MathUtil.floorDiv((int) Math.floor(pz), cell);

        double best1 = Double.MAX_VALUE;
        double best2 = Double.MAX_VALUE;
        double best3 = Double.MAX_VALUE;
        int c1x = ccx;
        int c1z = ccz;
        int c2x = ccx;
        int c2z = ccz;
        int c3x = ccx;
        int c3z = ccz;
        double c1cx = 0.0;
        double c1cz = 0.0;
        double c2cx = 0.0;
        double c2cz = 0.0;
        double c3cx = 0.0;
        double c3cz = 0.0;

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int gx = ccx + dx;
                int gz = ccz + dz;
                long h = SeedMixer.hashSalted(SeedMixer.SALT_PLATE_CENTER, seed, gx, gz);
                double jx = 0.15 + 0.70 * SeedMixer.toUnit(h);
                double jz = 0.15 + 0.70 * SeedMixer.toUnit(SeedMixer.mix64(h));
                double cx = (gx + jx) * cell;
                double cz = (gz + jz) * cell;
                double ddx = px - cx;
                double ddz = pz - cz;
                double d2 = ddx * ddx + ddz * ddz;
                if (d2 < best1) {
                    best3 = best2;
                    c3x = c2x;
                    c3z = c2z;
                    c3cx = c2cx;
                    c3cz = c2cz;
                    best2 = best1;
                    c2x = c1x;
                    c2z = c1z;
                    c2cx = c1cx;
                    c2cz = c1cz;
                    best1 = d2;
                    c1x = gx;
                    c1z = gz;
                    c1cx = cx;
                    c1cz = cz;
                } else if (d2 < best2) {
                    best3 = best2;
                    c3x = c2x;
                    c3z = c2z;
                    c3cx = c2cx;
                    c3cz = c2cz;
                    best2 = d2;
                    c2x = gx;
                    c2z = gz;
                    c2cx = cx;
                    c2cz = cz;
                } else if (d2 < best3) {
                    best3 = d2;
                    c3x = gx;
                    c3z = gz;
                    c3cx = cx;
                    c3cz = cz;
                }
            }
        }

        double f1 = Math.sqrt(best1);
        double f2 = Math.sqrt(best2);
        double f3 = Math.sqrt(best3);
        double edge = (f2 - f1) * 0.5;

        // Smooth side coordinate from symmetric inverse-distance weights
        // (power 4): sharp provinces, no crease at the boundary, invariant
        // under F1/F2 swaps. Relief blends MUST use this (or edge-based
        // flat-top profiles), never the V-shaped signed distance.
        double q1 = f1 * f1;
        double q2 = f2 * f2;
        q1 *= q1;
        q2 *= q2;
        double qden = q1 + q2;
        double sideW = qden > 0.0 ? (q1 - q2) / qden : 0.0;

        // 3. Inter-center axis (across) and strike (along). Centers of distinct
        // cells always differ (jitter stays inside its cell), so dist12 > 0.
        double ax = c2cx - c1cx;
        double az = c2cz - c1cz;
        double dist12 = Math.sqrt(ax * ax + az * az);
        if (!(dist12 > 0.0)) {
            dist12 = 1.0;
        }
        ax /= dist12;
        az /= dist12;
        double signed = (px - c1cx) * ax + (pz - c1cz) * az - dist12 * 0.5;

        // 4. Plate properties from cell hashes (deterministic provinces).
        boolean oc1 = isOceanic(c1x, c1z);
        boolean oc2 = isOceanic(c2x, c2z);
        boolean oc3 = isOceanic(c3x, c3z);
        double v1x = velX(c1x, c1z);
        double v1z = velZ(c1x, c1z);
        double v2x = velX(c2x, c2z);
        double v2z = velZ(c2x, c2z);
        double v3x = velX(c3x, c3z);
        double v3z = velZ(c3x, c3z);
        double b1s = plateBias(c1x, c1z, oc1);
        double b2s = plateBias(c2x, c2z, oc2);
        double b3s = plateBias(c3x, c3z, oc3);
        double e1 = plateBase(c1x, c1z, oc1);
        double e2 = plateBase(c2x, c2z, oc2);
        double e3 = plateBase(c3x, c3z, oc3);

        // 5. Symmetric three-plate property blends (rank-swap continuous).
        double i1 = 1.0 / (q1 + 1.0);
        double q3 = f3 * f3;
        q3 *= q3;
        double i2 = 1.0 / (q2 + 1.0);
        double i3 = 1.0 / (q3 + 1.0);
        double isum = i1 + i2 + i3;
        double w1 = i1 / isum;
        double w2 = i2 / isum;
        double w3 = i3 / isum;
        double blendBias = w1 * b1s + w2 * b2s + w3 * b3s;
        double blendBase = w1 * e1 + w2 * e2 + w3 * e3;

        // 6. Relative motion classification, blended over pairs (1,2)+(1,3).
        // Pair weights are flat-top in pair-edge proximity; at an F2/F3 rank
        // swap the pairs exchange equal weights, so the blend is continuous.
        double conv12 = pairConv(v1x, v1z, v2x, v2z, c1cx, c1cz, c2cx, c2cz);
        double conv13 = pairConv(v1x, v1z, v3x, v3z, c1cx, c1cz, c3cx, c3cz);
        double shear12 = pairShear(v1x, v1z, v2x, v2z, c1cx, c1cz, c2cx, c2cz);
        double shear13 = pairShear(v1x, v1z, v3x, v3z, c1cx, c1cz, c3cx, c3cz);
        double p12 = Math.exp(-MathUtil.sq(((f2 - f1) * 0.5) / 220.0));
        double p13 = Math.exp(-MathUtil.sq(((f3 - f1) * 0.5) / 220.0));
        double psum = p12 + p13 + 1e-9;
        double convN = (p12 * conv12 + p13 * conv13) / psum;
        double shearN = (p12 * shear12 + p13 * shear13) / psum;

        double prox = MathUtil.clamp01(1.0 - edge / classifyWidth);
        double convI = MathUtil.smoothstep(0.06, 0.32, convN) * prox;
        double divI = MathUtil.smoothstep(0.06, 0.32, -convN) * prox;
        double transI = MathUtil.smoothstep(0.05, 0.28, shearN) * prox;

        BoundaryType dom = BoundaryType.STABLE;
        double best = 1.0 - prox; // interior weight
        if (convI > best) {
            dom = BoundaryType.CONVERGENT;
            best = convI;
        }
        if (divI > best) {
            dom = BoundaryType.DIVERGENT;
            best = divI;
        }
        if (transI > best) {
            dom = BoundaryType.TRANSFORM;
        }

        out.edgeDistance = edge;
        out.signedDistance = signed;
        out.f1 = f1;
        out.f2 = f2;
        out.f3 = f3;
        out.sideW = sideW;
        out.plateBias = blendBias;
        out.plateBase = blendBase;
        out.boundary01 = prox;
        out.dominant = dom;
        out.convergent01 = convI;
        out.divergent01 = divI;
        out.transform01 = transI;
        out.strikeX = -az;
        out.strikeZ = ax;
        out.axisX = ax;
        out.axisZ = az;
        out.oceanic1 = oc1;
        out.oceanic2 = oc2;
        out.plateId1 = plateId(c1x, c1z);
        out.plateId2 = plateId(c2x, c2z);
        out.plateBase1 = plateBase(c1x, c1z, oc1);
        out.plateBase2 = plateBase(c2x, c2z, oc2);
    }

    /**
     * Normalized approach speed of plate 1 toward plate k along their
     * inter-center axis (positive = converging). Typical range O(-1..1).
     */
    private static double pairConv(double v1x, double v1z, double vkx, double vkz,
                                   double c1x, double c1z, double ckx, double ckz) {
        double ax = ckx - c1x;
        double az = ckz - c1z;
        double len = Math.sqrt(ax * ax + az * az);
        if (!(len > 0.0)) {
            return 0.0;
        }
        return ((v1x - vkx) * ax + (v1z - vkz) * az) / (len * 3.0);
    }

    /** Normalized shear (transform) speed of pair (1,k). */
    private static double pairShear(double v1x, double v1z, double vkx, double vkz,
                                    double c1x, double c1z, double ckx, double ckz) {
        double ax = ckx - c1x;
        double az = ckz - c1z;
        double len = Math.sqrt(ax * ax + az * az);
        if (!(len > 0.0)) {
            return 0.0;
        }
        ax /= len;
        az /= len;
        double relX = v1x - vkx;
        double relZ = v1z - vkz;
        double conv = relX * ax + relZ * az;
        double px = relX - conv * ax;
        double pz = relZ - conv * az;
        return Math.sqrt(px * px + pz * pz) / 3.0;
    }

    /** Stable plate identifier for debug coloring and provinces. */
    public int plateId(int cellX, int cellZ) {
        long h = SeedMixer.hashSalted(SeedMixer.SALT_PLATE_TYPE, seed, cellX * 73856093L, cellZ * 19349663L);
        return (int) (h & 0x7FFFFFFFL);
    }

    private boolean isOceanic(int cellX, int cellZ) {
        long h = SeedMixer.hashSalted(SeedMixer.SALT_PLATE_TYPE, seed, cellX, cellZ);
        return SeedMixer.toUnit(h) < cfg.oceanFraction;
    }

    private double velAngle(int cellX, int cellZ) {
        long h = SeedMixer.hashSalted(SeedMixer.SALT_PLATE_MOTION, seed, cellX, cellZ);
        return SeedMixer.toUnit(h) * Math.PI * 2.0;
    }

    private double velSpeed(int cellX, int cellZ) {
        long h = SeedMixer.hashSalted(SeedMixer.SALT_PLATE_MOTION, seed, cellX, -cellZ - 1);
        return 0.5 + SeedMixer.toUnit(h);
    }

    private double velX(int cellX, int cellZ) {
        return Math.cos(velAngle(cellX, cellZ)) * velSpeed(cellX, cellZ);
    }

    private double velZ(int cellX, int cellZ) {
        return Math.sin(velAngle(cellX, cellZ)) * velSpeed(cellX, cellZ);
    }

    private double plateBase(int cellX, int cellZ, boolean oceanic) {
        long h = SeedMixer.hashSalted(SeedMixer.SALT_PLATE_BASE, seed, cellX, cellZ);
        double u = SeedMixer.toUnit(h);
        return oceanic ? -(4.0 + u * 10.0) : (u - 0.5) * 12.0;
    }

    private double plateBias(int cellX, int cellZ, boolean oceanic) {
        return oceanic ? -0.55 : 0.55;
    }
}
