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
 * <p>Boundary classification is geometric, not random: for each plate pair,
 * relative velocity projected onto the inter-center axis measures approach
 * speed (positive = converging); the perpendicular component measures shear.
 * The current field blends these pair contributions symmetrically.</p>
 *
 * <p>Continuity design (the subtle part): truncating a weighted field at F3
 * leaves F3 with nonzero weight but drops F4 at a rank swap. Plate properties
 * therefore blend over all nine centers in the 3x3 search stencil. Relative
 * motion uses symmetric weighted pair covariances, evaluated algebraically
 * over the same stencil; these regional sums do not depend on F-rank order
 * while the stencil is fixed. The signed fault-side field blends all oriented
 * pair bisectors, rather than using a signless sorted-distance ratio or one
 * rank-dependent pair. Diagnostic nearest-pair fields (strike,
 * axis and plate identity) remain rank-dependent.</p>
 *
 * <p>Thread-safe: immutable after construction.</p>
 */
public final class PlateField {

    /** Matches the transform-scarp ramp width used by ElevationModel. */
    private static final double SIDE_SCALE = 60.0;

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
        double biasWeightSum = 0.0;
        double biasWeightedSum = 0.0;
        double baseWeightedSum = 0.0;
        double weightSum = 0.0;
        double weightSqSum = 0.0;
        double velocityXSum = 0.0;
        double velocityZSum = 0.0;
        double centerXSum = 0.0;
        double centerZSum = 0.0;
        double velocityDotCenterSum = 0.0;
        double velocitySqSum = 0.0;
        double centerSqSum = 0.0;
        double[] candidateDistance = new double[9];
        double[] candidateCenterX = new double[9];
        double[] candidateCenterZ = new double[9];
        double[] candidateWeight = new double[9];
        int candidateCount = 0;

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
                // Blend plate lithosphere properties over every center in the
                // search stencil, not only F1/F2/F3. Truncating at F3 makes
                // the field jump when F3/F4 exchange rank (their weights are
                // still nonzero at the cutoff).
                double d4 = d2 * d2;
                double propertyWeight = 1.0 / (d4 + 1.0);
                boolean oceanic = isOceanic(gx, gz);
                biasWeightSum += propertyWeight;
                biasWeightedSum += propertyWeight * plateBias(gx, gz, oceanic);
                baseWeightedSum += propertyWeight * plateBase(gx, gz, oceanic);
                double angle = velAngle(gx, gz);
                double speed = velSpeed(gx, gz);
                double vx = Math.cos(angle) * speed;
                double vz = Math.sin(angle) * speed;
                double rx = cx - px;
                double rz = cz - pz;
                weightSum += propertyWeight;
                weightSqSum += propertyWeight * propertyWeight;
                velocityXSum += propertyWeight * vx;
                velocityZSum += propertyWeight * vz;
                centerXSum += propertyWeight * rx;
                centerZSum += propertyWeight * rz;
                velocityDotCenterSum += propertyWeight * (vx * rx + vz * rz);
                velocitySqSum += propertyWeight * (vx * vx + vz * vz);
                centerSqSum += propertyWeight * (rx * rx + rz * rz);
                candidateDistance[candidateCount] = Math.sqrt(d2);
                candidateCenterX[candidateCount] = cx;
                candidateCenterZ[candidateCount] = cz;
                candidateWeight[candidateCount] = propertyWeight;
                candidateCount++;
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

        // 4. Plate properties for nearest-pair diagnostics.
        boolean oc1 = isOceanic(c1x, c1z);
        boolean oc2 = isOceanic(c2x, c2z);
        // 5. Smooth plate-property blends over the full search stencil. The
        // prior F1/F2/F3 truncation gave F3 a nonzero weight but dropped F4,
        // creating a step whenever F3/F4 swapped ranks.
        double blendBias = biasWeightSum > 0.0 ? biasWeightedSum / biasWeightSum : 0.0;
        double blendBase = biasWeightSum > 0.0 ? baseWeightedSum / biasWeightSum : 0.0;

        // 6. Relative motion from weighted pair covariances accumulated in
        // the 3x3 scan. The symmetric identities equal the all-unordered-pair
        // sums at O(9) cost. Centers are relative to the warped query to avoid
        // cancellation at large Minecraft coordinates.
        double pairWeightSum = 0.5 * (weightSum * weightSum - weightSqSum);
        double centerVariance = weightSum * centerSqSum
                - centerXSum * centerXSum - centerZSum * centerZSum;
        double velocityVariance = weightSum * velocitySqSum
                - velocityXSum * velocityXSum - velocityZSum * velocityZSum;
        if (centerVariance < 0.0) {
            centerVariance = 0.0;
        }
        if (velocityVariance < 0.0) {
            velocityVariance = 0.0;
        }
        double meanPairDistance = pairWeightSum > 0.0
                ? Math.sqrt(centerVariance / pairWeightSum) : 0.0;
        double covariance = velocityXSum * centerXSum + velocityZSum * centerZSum
                - weightSum * velocityDotCenterSum;
        double convN = pairWeightSum > 0.0 && meanPairDistance > 0.0
                ? covariance / (pairWeightSum * meanPairDistance * 3.0) : 0.0;
        double relativeSpeedSq = pairWeightSum > 0.0
                ? velocityVariance / pairWeightSum : 0.0;
        double shearSq = relativeSpeedSq / 9.0 - convN * convN;
        if (shearSq < 0.0) {
            shearSq = 0.0;
        }
        double shearN = Math.sqrt(shearSq);

        // Signed side field: symmetric sum over all unordered pairs, with
        // pair orientation fixed by lattice-center coordinates. This avoids
        // both the signless F1/F2 distance ratio and a nearest-pair switch at
        // F2/F3 rank boundaries.
        double sideWeighted = 0.0;
        double sideWeightSum = 0.0;
        for (int i = 0; i < candidateCount; i++) {
            for (int j = i + 1; j < candidateCount; j++) {
                double pairEdge = Math.abs(candidateDistance[j] - candidateDistance[i]) * 0.5;
                double proximity = Math.exp(-MathUtil.sq(pairEdge / 220.0));
                double weight = candidateWeight[i] * candidateWeight[j] * proximity;
                if (!(weight > 0.0)) {
                    continue;
                }
                double axisX = candidateCenterX[j] - candidateCenterX[i];
                double axisZ = candidateCenterZ[j] - candidateCenterZ[i];
                double axisLength = Math.sqrt(axisX * axisX + axisZ * axisZ);
                if (!(axisLength > 0.0)) {
                    continue;
                }
                axisX /= axisLength;
                axisZ /= axisLength;
                double signedSide = (px - (candidateCenterX[i] + candidateCenterX[j]) * 0.5)
                        * axisX + (pz - (candidateCenterZ[i] + candidateCenterZ[j]) * 0.5)
                        * axisZ;
                if (candidateCenterX[i] > candidateCenterX[j]
                        || (candidateCenterX[i] == candidateCenterX[j]
                        && candidateCenterZ[i] > candidateCenterZ[j])) {
                    signedSide = -signedSide;
                }
                sideWeighted += weight * MathUtil.clamp(signedSide / SIDE_SCALE, -1.0, 1.0);
                sideWeightSum += weight;
            }
        }
        double sideW = sideWeightSum > 0.0 ? sideWeighted / sideWeightSum : 0.0;

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

    private double plateBase(int cellX, int cellZ, boolean oceanic) {
        long h = SeedMixer.hashSalted(SeedMixer.SALT_PLATE_BASE, seed, cellX, cellZ);
        double u = SeedMixer.toUnit(h);
        return oceanic ? -(4.0 + u * 10.0) : (u - 0.5) * 12.0;
    }

    private double plateBias(int cellX, int cellZ, boolean oceanic) {
        return oceanic ? -0.55 : 0.55;
    }
}
