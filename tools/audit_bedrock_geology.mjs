import {
  GeologyModel,
  HYDRO_CELL_SIZE,
  HYDRO_TILE_CELLS,
  PLATE_CELL_SIZE,
  SEA_LEVEL,
} from "../bedrock/STRATA_BehaviorPack/scripts/geology_model.js";

const seed = "12345";
const sampleStep = 8;
const sampleHalfExtent = 1024;
const model = new GeologyModel(seed);
const heights = [];
const baseHeights = [];
const slopes = [];
const formations = Object.create(null);
let oceanColumns = 0;
let riverColumns = 0;
let lakeColumns = 0;
let changedColumns = 0;
let oceanicFractionSum = 0;
let strongConvergentColumns = 0;
let strongDivergentColumns = 0;
let strongTransformColumns = 0;
let absoluteDeltaSum = 0;
let signedDeltaSum = 0;
let maxAbsoluteDelta = 0;
const hydroTileBlocks = HYDRO_CELL_SIZE * HYDRO_TILE_CELLS;
const continuityStats = () => ({ pairs: 0, confluenceAbsDelta: 0, riverMaskToggles: 0 });
const hydrologyTileContinuity = Object.fromEntries(
  ["x", "z"].map((axis) => [axis, { edge: continuityStats(), interior: continuityStats() }]),
);
const samplesPerAxis = (sampleHalfExtent * 2) / sampleStep;
const previousRowConfluence = new Float32Array(samplesPerAxis);
const previousRowRiverMask = new Uint8Array(samplesPerAxis);
let hasPreviousRow = false;
const started = Date.now();

function recordContinuity(axis, isEdge, previous, current) {
  const stats = hydrologyTileContinuity[axis][isEdge ? "edge" : "interior"];
  stats.pairs++;
  stats.confluenceAbsDelta += Math.abs(current.confluence01 - previous.confluence01);
  if ((current.river01 > 0.45) !== (previous.river01 > 0.45)) stats.riverMaskToggles++;
}

for (let z = -sampleHalfExtent; z < sampleHalfExtent; z += sampleStep) {
  let previousColumn = undefined;
  for (let x = -sampleHalfExtent; x < sampleHalfExtent; x += sampleStep) {
    const column = model.sampleColumn(x, z);
    const sampleIndex = (x + sampleHalfExtent) / sampleStep;
    if (previousColumn) recordContinuity("x", x % hydroTileBlocks === 0, previousColumn, column);
    if (hasPreviousRow) {
      const previous = {
        confluence01: previousRowConfluence[sampleIndex],
        river01: previousRowRiverMask[sampleIndex] ? 1 : 0,
      };
      recordContinuity("z", z % hydroTileBlocks === 0, previous, column);
    }
    previousRowConfluence[sampleIndex] = column.confluence01;
    previousRowRiverMask[sampleIndex] = column.river01 > 0.45 ? 1 : 0;

    heights.push(column.finalHeight);
    baseHeights.push(column.baseHeight);
    slopes.push(column.slope);
    formations[column.formation] = (formations[column.formation] ?? 0) + 1;
    oceanicFractionSum += column.boundary.oceanicFraction;
    if (column.boundary.convergent01 > 0.5) strongConvergentColumns++;
    if (column.boundary.divergent01 > 0.5) strongDivergentColumns++;
    if (column.boundary.transform01 > 0.5) strongTransformColumns++;
    if (column.finalHeight < SEA_LEVEL) oceanColumns++;
    if (column.river01 > 0.45) riverColumns++;
    if (column.lake01 > 0.45) lakeColumns++;
    const delta = column.finalHeight - column.baseHeight;
    if (delta !== 0) changedColumns++;
    absoluteDeltaSum += Math.abs(delta);
    signedDeltaSum += delta;
    maxAbsoluteDelta = Math.max(maxAbsoluteDelta, Math.abs(delta));
    previousColumn = column;
  }
  hasPreviousRow = true;
}

const baseModel = new GeologyModel(seed);
let regionalBaseOcean = 0;
let regionalSamples = 0;
for (let z = -4096; z < 4096; z += 32) {
  for (let x = -4096; x < 4096; x += 32) {
    if (baseModel.sampleBase(x, z).height < SEA_LEVEL) regionalBaseOcean++;
    regionalSamples++;
  }
}

function sorted(values) {
  return values.sort((a, b) => a - b);
}
function quantile(values, p) {
  return values[Math.floor((values.length - 1) * p)];
}
function mean(values) {
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}
function round(value, places = 4) {
  const scale = 10 ** places;
  return Math.round(value * scale) / scale;
}
function summarizeContinuity(stats) {
  return {
    pairs: stats.pairs,
    meanAbsoluteConfluenceDelta: round(stats.confluenceAbsDelta / stats.pairs),
    riverMaskTogglePercent: round(100 * stats.riverMaskToggles / stats.pairs),
  };
}

const crustStep = 256;
const crustGrid = 32;
const plateLagCells = PLATE_CELL_SIZE / crustStep;
const crustField = new Float32Array(crustGrid * crustGrid);
const crustModel = new GeologyModel(seed);
let crustFractionSum = 0;
for (let j = 0; j < crustGrid; j++) {
  for (let i = 0; i < crustGrid; i++) {
    const tectonics = crustModel.sampleTectonics(
      -4096 + i * crustStep,
      -4096 + j * crustStep,
    );
    const index = j * crustGrid + i;
    crustField[index] = tectonics.oceanicFraction;
    crustFractionSum += tectonics.oceanicFraction;
  }
}
function crustCorrelation(dx, dz) {
  let count = 0, sumA = 0, sumB = 0;
  for (let j = 0; j < crustGrid; j++) {
    for (let i = 0; i < crustGrid; i++) {
      const nextX = i + dx, nextZ = j + dz;
      if (nextX >= crustGrid || nextZ >= crustGrid) continue;
      sumA += crustField[j * crustGrid + i];
      sumB += crustField[nextZ * crustGrid + nextX];
      count++;
    }
  }
  const meanA = sumA / count, meanB = sumB / count;
  let covariance = 0, varianceA = 0, varianceB = 0;
  for (let j = 0; j < crustGrid; j++) {
    for (let i = 0; i < crustGrid; i++) {
      const nextX = i + dx, nextZ = j + dz;
      if (nextX >= crustGrid || nextZ >= crustGrid) continue;
      const a = crustField[j * crustGrid + i] - meanA;
      const b = crustField[nextZ * crustGrid + nextX] - meanB;
      covariance += a * b;
      varianceA += a * a;
      varianceB += b * b;
    }
  }
  return covariance / Math.sqrt(varianceA * varianceB);
}

sorted(heights);
sorted(baseHeights);
sorted(slopes);
const count = heights.length;
const output = {
  seed,
  localWindow: {
    widthBlocks: sampleHalfExtent * 2,
    stepBlocks: sampleStep,
    samples: count,
    finalHeight: {
      min: heights[0],
      p10: quantile(heights, 0.10),
      p25: quantile(heights, 0.25),
      median: quantile(heights, 0.50),
      p75: quantile(heights, 0.75),
      p90: quantile(heights, 0.90),
      max: heights.at(-1),
      mean: round(mean(heights)),
    },
    baseHeight: {
      min: baseHeights[0],
      max: baseHeights.at(-1),
      mean: round(mean(baseHeights)),
    },
    oceanPercent: round(100 * oceanColumns / count),
    riverMaskPercent: round(100 * riverColumns / count),
    lakeMaskPercent: round(100 * lakeColumns / count),
    slopeMean: round(mean(slopes)),
    slopeP95: round(quantile(slopes, 0.95)),
    formationPercent: Object.fromEntries(
      Object.entries(formations).sort(([a], [b]) => a.localeCompare(b))
        .map(([name, total]) => [name, round(100 * total / count)]),
    ),
    sameCoordinateBaseToFinal: {
      changedPercent: round(100 * changedColumns / count),
      meanAbsoluteHeightDelta: round(absoluteDeltaSum / count),
      meanSignedHeightDelta: round(signedDeltaSum / count),
      maxAbsoluteHeightDelta: maxAbsoluteDelta,
    },
  },
  tectonicStructure: {
    localWindowMeanOceanicFraction: round(oceanicFractionSum / count),
    strongBoundaryRegimePercent: {
      convergent: round(100 * strongConvergentColumns / count),
      divergent: round(100 * strongDivergentColumns / count),
      transform: round(100 * strongTransformColumns / count),
    },
    crustFieldSpatialCoherence: {
      widthBlocks: crustGrid * crustStep,
      sampleStepBlocks: crustStep,
      plateCellLagBlocks: PLATE_CELL_SIZE,
      meanOceanicFraction: round(crustFractionSum / crustField.length),
      correlationAtPlateCellLag: {
        x: round(crustCorrelation(plateLagCells, 0)),
        z: round(crustCorrelation(0, plateLagCells)),
      },
    },
  },
  hydrologyTileContinuity: {
    tileWidthBlocks: hydroTileBlocks,
    axes: Object.fromEntries(
      Object.entries(hydrologyTileContinuity).map(([axis, stats]) => [axis, {
        tileEdge: summarizeContinuity(stats.edge),
        interior: summarizeContinuity(stats.interior),
      }]),
    ),
  },
  regionalBaseOceanCalibration: {
    widthBlocks: 8192,
    stepBlocks: 32,
    samples: regionalSamples,
    oceanPercent: round(100 * regionalBaseOcean / regionalSamples),
  },
  elapsedMilliseconds: Date.now() - started,
};
console.log(JSON.stringify(output, null, 2));
