/*
 * Deterministic, geology-informed relief for STRATA's optional Bedrock
 * scripted dimension. Pure terrain functions live here so they can be tested
 * under Node without importing Minecraft's runtime module.
 *
 * This is a reduced, game-scale port of STRATA's existing plate/elevation/
 * drainage relationships. It is not a calibrated geophysical simulation.
 */

export const SEA_LEVEL = 63;
export const MIN_Y = -64;
export const MAX_SURFACE_Y = 248;
export const PLATE_CELL_SIZE = 1536;
export const HYDRO_CELL_SIZE = 8;
// A 512-block core spaces out, but does not eliminate, artificial tile edges.
export const HYDRO_TILE_CELLS = 64;
export const HYDRO_RADIUS_CELLS = 12;

const SEARCH_RADIUS = 1;
const OCEANIC_PLATE_FRACTION = 0.70;
// Calibrated on the documented seed-12345 audit window so the projected
// ocean/land share is close to Earth's observed global surface split.
const CONTINENTAL_OFFSET = -0.40;
const WARP_AMPLITUDE = 320;
const WARP_WAVELENGTH = 2800;
const RANGE_WIDTH = 150;
const FOOT_WIDTH = 330;
const TRENCH_WIDTH = 45;
const RIFT_WIDTH = 70;
const RIDGE_WIDTH = 220;
const SCARP_WIDTH = 60;
const HYDRO_CACHE_LIMIT = 8;

const SALT = Object.freeze({
  plateCenter: 0x1a7e,
  plateType: 0x0be5,
  plateMotion: 0xf00d,
  plateBase: 0x8a5e,
  warpX: 0x9a401,
  warpZ: 0x9a402,
  continent: 0xc0111e,
  regional: 0x9e9101,
  local1: 0x10ca1,
  local2: 0x10ca2,
  abyss: 0xa8e55,
  crest: 0xc4e57,
  hydroTie: 0x0d40,
  temperature: 0x7e9a1,
  moisture: 0x90157,
  formation: 0xf047,
  volcanic: 0xb016,
  thickness: 0x771c,
  surface: 0x5eed,
  alluvium: 0x1e15,
});

const GRADIENTS = [
  [1, 0], [-1, 0], [0, 1], [0, -1],
  [0.7071067811865476, 0.7071067811865476],
  [-0.7071067811865476, 0.7071067811865476],
  [0.7071067811865476, -0.7071067811865476],
  [-0.7071067811865476, -0.7071067811865476],
];
const FLOW_DX = [1, 1, 0, -1, -1, -1, 0, 1];
const FLOW_DZ = [0, 1, 1, 1, 0, -1, -1, -1];
const NO_FLOW = -1;

export function clamp(value, low, high) {
  return value < low ? low : value > high ? high : value;
}

export function clamp01(value) {
  return clamp(value, 0, 1);
}

export function lerp(a, b, t) {
  return a + (b - a) * t;
}

export function smoothstep(edge0, edge1, value) {
  const t = clamp01((value - edge0) / (edge1 - edge0));
  return t * t * (3 - 2 * t);
}

export function smoothstepDown(edgeHigh, edgeLow, value) {
  return 1 - smoothstep(edgeLow, edgeHigh, value);
}

function mix32(input) {
  let value = input >>> 0;
  value = Math.imul(value ^ (value >>> 16), 0x7feb352d);
  value = Math.imul(value ^ (value >>> 15), 0x846ca68b);
  return (value ^ (value >>> 16)) >>> 0;
}

export function seedToUint32(seed) {
  const text = String(seed);
  let hash = 0x811c9dc5;
  for (let i = 0; i < text.length; i++) {
    hash = Math.imul(hash ^ text.charCodeAt(i), 0x01000193);
  }
  return mix32(hash);
}

function hashCell(seed, x, z, salt) {
  const a = Math.imul(x | 0, 0x9e3779b1);
  const b = Math.imul(z | 0, 0x85ebca77);
  return mix32((seed ^ salt ^ a ^ b) >>> 0);
}

function unit(hash) {
  return (hash >>> 0) / 4294967296;
}

function fade(value) {
  return value * value * value * (value * (value * 6 - 15) + 10);
}

function gradientNoise(seed, x, z, salt) {
  const ix = Math.floor(x);
  const iz = Math.floor(z);
  const fx = x - ix;
  const fz = z - iz;
  const ux = fade(fx);
  const uz = fade(fz);

  const dot = (gx, gz, dx, dz) => {
    const gradient = GRADIENTS[hashCell(seed, gx, gz, salt) & 7];
    return gradient[0] * dx + gradient[1] * dz;
  };

  const d00 = dot(ix, iz, fx, fz);
  const d10 = dot(ix + 1, iz, fx - 1, fz);
  const d01 = dot(ix, iz + 1, fx, fz - 1);
  const d11 = dot(ix + 1, iz + 1, fx - 1, fz - 1);
  const nx0 = lerp(d00, d10, ux);
  const nx1 = lerp(d01, d11, ux);
  return lerp(nx0, nx1, uz) * 1.4142135623730951;
}

function fbm(seed, x, z, octaves, lacunarity, gain, salt) {
  let sum = 0;
  let amplitude = 1;
  let norm = 0;
  let fx = x;
  let fz = z;
  for (let octave = 0; octave < octaves; octave++) {
    sum += gradientNoise(seed, fx, fz, salt) * amplitude;
    norm += amplitude;
    amplitude *= gain;
    fx *= lacunarity;
    fz *= lacunarity;
  }
  return norm > 0 ? sum / norm : 0;
}

function ridged(seed, x, z, octaves, lacunarity, gain, salt) {
  let sum = 0;
  let amplitude = 1;
  let norm = 0;
  let fx = x;
  let fz = z;
  for (let octave = 0; octave < octaves; octave++) {
    let ridge = 1 - Math.abs(gradientNoise(seed, fx, fz, salt));
    ridge *= ridge;
    sum += ridge * amplitude;
    norm += amplitude;
    amplitude *= gain;
    fx *= lacunarity;
    fz *= lacunarity;
  }
  return norm > 0 ? sum / norm : 0;
}

function floorDiv(value, divisor) {
  return Math.floor(value / divisor);
}

function floorMod(value, divisor) {
  return ((value % divisor) + divisor) % divisor;
}

function pairKey(a, b) {
  return `${a},${b}`;
}

class MinHeap {
  constructor(priority) {
    this.values = [];
    this.priority = priority;
  }

  get length() {
    return this.values.length;
  }

  less(a, b) {
    const diff = this.priority(a) - this.priority(b);
    return diff < 0 || (diff === 0 && a < b);
  }

  push(value) {
    const values = this.values;
    let index = values.length;
    values.push(value);
    while (index > 0) {
      const parent = (index - 1) >> 1;
      if (!this.less(values[index], values[parent])) break;
      [values[index], values[parent]] = [values[parent], values[index]];
      index = parent;
    }
  }

  pop() {
    const values = this.values;
    if (values.length === 0) return undefined;
    const first = values[0];
    const last = values.pop();
    if (values.length > 0) {
      values[0] = last;
      let index = 0;
      while (true) {
        const left = index * 2 + 1;
        const right = left + 1;
        let smallest = index;
        if (left < values.length && this.less(values[left], values[smallest])) smallest = left;
        if (right < values.length && this.less(values[right], values[smallest])) smallest = right;
        if (smallest === index) break;
        [values[index], values[smallest]] = [values[smallest], values[index]];
        index = smallest;
      }
    }
    return first;
  }
}

export class GeologyModel {
  constructor(worldSeed) {
    this.seed = seedToUint32(worldSeed);
    this.hydroTiles = new Map();
    this.baseCache = new Map();
  }

  sampleTectonics(x, z) {
    const warpX = WARP_AMPLITUDE * fbm(
      this.seed,
      x / WARP_WAVELENGTH + 13.7,
      z / WARP_WAVELENGTH + 7.1,
      3, 2, 0.5, SALT.warpX,
    );
    const warpZ = WARP_AMPLITUDE * fbm(
      this.seed,
      x / WARP_WAVELENGTH - 3.2,
      z / WARP_WAVELENGTH + 11.9,
      3, 2, 0.5, SALT.warpZ,
    );
    const px = x + warpX;
    const pz = z + warpZ;
    const cellSize = PLATE_CELL_SIZE;
    const centerCellX = floorDiv(Math.floor(px), cellSize);
    const centerCellZ = floorDiv(Math.floor(pz), cellSize);

    const candidates = [];
    let weightSum = 0;
    let weightSqSum = 0;
    let biasSum = 0;
    let baseSum = 0;
    let velocityXSum = 0;
    let velocityZSum = 0;
    let centerXSum = 0;
    let centerZSum = 0;
    let velocityDotCenterSum = 0;
    let velocitySqSum = 0;
    let centerSqSum = 0;

    for (let dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
      for (let dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
        const cellX = centerCellX + dx;
        const cellZ = centerCellZ + dz;
        const centerHash = hashCell(this.seed, cellX, cellZ, SALT.plateCenter);
        const jitterX = 0.15 + 0.70 * unit(centerHash);
        const jitterZ = 0.15 + 0.70 * unit(mix32(centerHash));
        const centerX = (cellX + jitterX) * cellSize;
        const centerZ = (cellZ + jitterZ) * cellSize;
        const rx = centerX - px;
        const rz = centerZ - pz;
        const distanceSquared = rx * rx + rz * rz;
        const weight = 1 / (distanceSquared * distanceSquared + 1);
        const oceanic = unit(hashCell(this.seed, cellX, cellZ, SALT.plateType)) < OCEANIC_PLATE_FRACTION;
        const baseUnit = unit(hashCell(this.seed, cellX, cellZ, SALT.plateBase));
        const plateBase = oceanic ? -(4 + baseUnit * 10) : (baseUnit - 0.5) * 12;
        const angle = unit(hashCell(this.seed, cellX, cellZ, SALT.plateMotion)) * Math.PI * 2;
        const speedUnit = unit(hashCell(this.seed, cellX, -cellZ - 1, SALT.plateMotion));
        const speed = 0.5 + speedUnit;
        const velocityX = Math.cos(angle) * speed;
        const velocityZ = Math.sin(angle) * speed;
        const distance = Math.sqrt(distanceSquared);
        const plateBias = oceanic ? -0.55 : 0.55;
        const candidate = {
          cellX, cellZ, centerX, centerZ, distanceSquared, distance, weight,
          oceanic, plateBase, plateBias, velocityX, velocityZ,
        };
        candidates.push(candidate);

        weightSum += weight;
        weightSqSum += weight * weight;
        biasSum += weight * plateBias;
        baseSum += weight * plateBase;
        velocityXSum += weight * velocityX;
        velocityZSum += weight * velocityZ;
        centerXSum += weight * rx;
        centerZSum += weight * rz;
        velocityDotCenterSum += weight * (velocityX * rx + velocityZ * rz);
        velocitySqSum += weight * (velocityX * velocityX + velocityZ * velocityZ);
        centerSqSum += weight * (rx * rx + rz * rz);
      }
    }

    candidates.sort((a, b) => a.distanceSquared - b.distanceSquared);
    const nearest = candidates[0];
    const second = candidates[1];
    const third = candidates[2];
    const edgeDistance = Math.max(0, (second.distance - nearest.distance) * 0.5);
    const pairWeightSum = 0.5 * (weightSum * weightSum - weightSqSum);
    let centerVariance = weightSum * centerSqSum - centerXSum * centerXSum - centerZSum * centerZSum;
    let velocityVariance = weightSum * velocitySqSum - velocityXSum * velocityXSum - velocityZSum * velocityZSum;
    centerVariance = Math.max(0, centerVariance);
    velocityVariance = Math.max(0, velocityVariance);
    const meanPairDistance = pairWeightSum > 0 ? Math.sqrt(centerVariance / pairWeightSum) : 0;
    const covariance = velocityXSum * centerXSum + velocityZSum * centerZSum
      - weightSum * velocityDotCenterSum;
    const convergenceN = pairWeightSum > 0 && meanPairDistance > 0
      ? covariance / (pairWeightSum * meanPairDistance * 3) : 0;
    const relativeSpeedSquared = pairWeightSum > 0 ? velocityVariance / pairWeightSum : 0;
    const shearSquared = Math.max(0, relativeSpeedSquared / 9 - convergenceN * convergenceN);
    const shearN = Math.sqrt(shearSquared);

    let sideWeighted = 0;
    let sideWeightSum = 0;
    for (let i = 0; i < candidates.length; i++) {
      for (let j = i + 1; j < candidates.length; j++) {
        const a = candidates[i];
        const b = candidates[j];
        const pairEdge = Math.abs(b.distance - a.distance) * 0.5;
        const proximity = Math.exp(-((pairEdge / 220) ** 2));
        const weight = a.weight * b.weight * proximity;
        if (!(weight > 0)) continue;
        let axisX = b.centerX - a.centerX;
        let axisZ = b.centerZ - a.centerZ;
        const axisLength = Math.hypot(axisX, axisZ);
        if (!(axisLength > 0)) continue;
        axisX /= axisLength;
        axisZ /= axisLength;
        let signedSide = (px - (a.centerX + b.centerX) * 0.5) * axisX
          + (pz - (a.centerZ + b.centerZ) * 0.5) * axisZ;
        if (a.centerX > b.centerX || (a.centerX === b.centerX && a.centerZ > b.centerZ)) {
          signedSide = -signedSide;
        }
        sideWeighted += weight * clamp(signedSide / SCARP_WIDTH, -1, 1);
        sideWeightSum += weight;
      }
    }

    const classifyWidth = clamp(cellSize * 0.28, 120, 460);
    const proximity = clamp01(1 - edgeDistance / classifyWidth);
    const convergent01 = smoothstep(0.06, 0.32, convergenceN) * proximity;
    const divergent01 = smoothstep(0.06, 0.32, -convergenceN) * proximity;
    const transform01 = smoothstep(0.05, 0.28, shearN) * proximity;
    const edgeAxisX = second.centerX - nearest.centerX;
    const edgeAxisZ = second.centerZ - nearest.centerZ;
    const edgeAxisLength = Math.max(1, Math.hypot(edgeAxisX, edgeAxisZ));

    return {
      edgeDistance,
      f1: nearest.distance,
      f2: second.distance,
      f3: third.distance,
      sideW: sideWeightSum > 0 ? sideWeighted / sideWeightSum : 0,
      plateBias: weightSum > 0 ? biasSum / weightSum : 0,
      plateBase: weightSum > 0 ? baseSum / weightSum : 0,
      boundary01: proximity,
      convergent01,
      divergent01,
      transform01,
      oceanic1: nearest.oceanic,
      oceanic2: second.oceanic,
      plateId1: hashCell(this.seed, nearest.cellX, nearest.cellZ, SALT.plateType) & 0x7fffffff,
      plateId2: hashCell(this.seed, second.cellX, second.cellZ, SALT.plateType) & 0x7fffffff,
      axisX: edgeAxisX / edgeAxisLength,
      axisZ: edgeAxisZ / edgeAxisLength,
    };
  }

  sampleBase(x, z) {
    const key = `${x},${z}`;
    const cached = this.baseCache.get(key);
    if (cached) return cached;

    const boundary = this.sampleTectonics(x, z);
    const continentNoise = fbm(this.seed, x / 2400, z / 2400, 3, 2.03, 0.5, SALT.continent);
    const continent = boundary.plateBias + continentNoise * 1.25 + CONTINENTAL_OFFSET;
    const sea = SEA_LEVEL;

    const depth01 = clamp01(-continent / 1.2);
    let oceanHeight = sea - 6 - 26 * depth01;
    oceanHeight += fbm(this.seed, x / 420, z / 420, 2, 2, 0.5, SALT.abyss) * 4;
    if (boundary.divergent01 > 0.001) {
      const ridgeGate = smoothstepDown(0.2, -0.4, continent);
      const ridge = Math.exp(-((boundary.edgeDistance / RIDGE_WIDTH) ** 2));
      oceanHeight += 16 * ridge * boundary.divergent01 * ridgeGate;
    }

    const land01 = clamp01(continent / 1.2);
    let landHeight = sea + 3 + 24 * land01 + boundary.plateBase;
    landHeight += fbm(this.seed, x / 600, z / 600, 3, 2.02, 0.5, SALT.regional) * 13;
    if (boundary.divergent01 > 0.001) {
      const riftGate = smoothstep(-0.2, 0.4, continent);
      landHeight -= 22 * Math.exp(-((boundary.edgeDistance / RIFT_WIDTH) ** 2))
        * boundary.divergent01 * riftGate;
      landHeight += 18 * Math.exp(-(((boundary.edgeDistance - 150) / 100) ** 2))
        * boundary.divergent01 * riftGate;
    }

    let height = lerp(oceanHeight, landHeight, smoothstep(-0.08, 0.08, continent));
    let orogen = 0;
    if (boundary.convergent01 > 0.001) {
      const continentalWidth = smoothstep(-0.3, 0.5, continent);
      const effectiveWidth = RANGE_WIDTH * (1 + 0.25 * continentalWidth);
      const profile = Math.exp(-((boundary.edgeDistance / effectiveWidth) ** 2));
      const foothill = Math.exp(-((boundary.edgeDistance / FOOT_WIDTH) ** 2));
      const maxHeight = 55 + 55 * smoothstep(-0.9, 0.5, continent);
      const crestNoise = ridged(this.seed, x / 160, z / 160, 4, 2.05, 0.52, SALT.crest);
      const peak = 0.45 + 0.55 * crestNoise;
      orogen = maxHeight * profile * peak * boundary.convergent01
        + maxHeight * 0.18 * foothill * boundary.convergent01;
      const trench = Math.exp(-(((boundary.edgeDistance - 50) / TRENCH_WIDTH) ** 2));
      const trenchDepth = 12 + 18 * smoothstepDown(0.3, -0.3, continent);
      orogen -= trenchDepth * trench * boundary.convergent01;
      height += orogen;
    }

    if (boundary.transform01 > 0.001) {
      const ramp = smoothstep(0, SCARP_WIDTH, boundary.edgeDistance);
      height += 8 * ramp * clamp(boundary.sideW, -1, 1) * boundary.transform01;
    }

    const roughness = continent < 0 ? 0.5 : (1 + 0.6 * clamp01(orogen / 60));
    height += fbm(this.seed, x / 95, z / 95, 3, 2, 0.5, SALT.local1) * 5 * roughness;
    height += fbm(this.seed, x / 23, z / 23, 2, 2, 0.5, SALT.local2) * 1.6 * roughness;
    height = sea + (height - sea);

    const result = { height, continent, orogen01: clamp01(orogen / 60), boundary };
    this.baseCache.set(key, result);
    if (this.baseCache.size > 8192) this.baseCache.delete(this.baseCache.keys().next().value);
    return result;
  }

  sampleHydrology(x, z) {
    const cell = HYDRO_CELL_SIZE;
    const fractionalX = x / cell - 0.5;
    const fractionalZ = z / cell - 0.5;
    const cellX = Math.floor(fractionalX);
    const cellZ = Math.floor(fractionalZ);
    const fx = fractionalX - cellX;
    const fz = fractionalZ - cellZ;

    const c00 = this.hydroAt(cellX, cellZ);
    const c10 = this.hydroAt(cellX + 1, cellZ);
    const c01 = this.hydroAt(cellX, cellZ + 1);
    const c11 = this.hydroAt(cellX + 1, cellZ + 1);
    const blend = (key) => lerp(lerp(c00[key], c10[key], fx), lerp(c01[key], c11[key], fx), fz);
    const confluence01 = blend('confluence01');
    const river01 = smoothstep(0.20, 0.55, confluence01);
    const nearestPit = this.hydroAt(
      cellX + (fx < 0.5 ? 0 : 1),
      cellZ + (fz < 0.5 ? 0 : 1),
    ).pit;
    return {
      confluence01,
      river01,
      lake01: blend('lake01'),
      wet01: blend('wet01'),
      pit: nearestPit,
    };
  }

  async sampleHydrologyAsync(x, z, yieldOneTick) {
    const cell = HYDRO_CELL_SIZE;
    const fractionalX = x / cell - 0.5;
    const fractionalZ = z / cell - 0.5;
    const cellX = Math.floor(fractionalX);
    const cellZ = Math.floor(fractionalZ);
    const fx = fractionalX - cellX;
    const fz = fractionalZ - cellZ;
    const c00 = await this.hydroAtAsync(cellX, cellZ, yieldOneTick);
    const c10 = await this.hydroAtAsync(cellX + 1, cellZ, yieldOneTick);
    const c01 = await this.hydroAtAsync(cellX, cellZ + 1, yieldOneTick);
    const c11 = await this.hydroAtAsync(cellX + 1, cellZ + 1, yieldOneTick);
    const blend = (key) => lerp(lerp(c00[key], c10[key], fx), lerp(c01[key], c11[key], fx), fz);
    const confluence01 = blend('confluence01');
    const pitSample = await this.hydroAtAsync(
      cellX + (fx < 0.5 ? 0 : 1),
      cellZ + (fz < 0.5 ? 0 : 1),
      yieldOneTick,
    );
    return {
      confluence01,
      river01: smoothstep(0.20, 0.55, confluence01),
      lake01: blend('lake01'),
      wet01: blend('wet01'),
      pit: pitSample.pit,
    };
  }

  hydroAt(cellX, cellZ) {
    const tileX = floorDiv(cellX, HYDRO_TILE_CELLS);
    const tileZ = floorDiv(cellZ, HYDRO_TILE_CELLS);
    const tile = this.hydroTile(tileX, tileZ);
    const localX = floorMod(cellX, HYDRO_TILE_CELLS);
    const localZ = floorMod(cellZ, HYDRO_TILE_CELLS);
    const index = localZ * HYDRO_TILE_CELLS + localX;
    return {
      confluence01: tile.confluence01[index],
      lake01: tile.lake01[index],
      wet01: tile.wet01[index],
      pit: tile.pit[index] !== 0,
    };
  }

  async hydroAtAsync(cellX, cellZ, yieldOneTick) {
    const tileX = floorDiv(cellX, HYDRO_TILE_CELLS);
    const tileZ = floorDiv(cellZ, HYDRO_TILE_CELLS);
    const tile = await this.hydroTileAsync(tileX, tileZ, yieldOneTick);
    const localX = floorMod(cellX, HYDRO_TILE_CELLS);
    const localZ = floorMod(cellZ, HYDRO_TILE_CELLS);
    const index = localZ * HYDRO_TILE_CELLS + localX;
    return {
      confluence01: tile.confluence01[index],
      lake01: tile.lake01[index],
      wet01: tile.wet01[index],
      pit: tile.pit[index] !== 0,
    };
  }

  async hydroTileAsync(tileX, tileZ, yieldOneTick) {
    const key = pairKey(tileX, tileZ);
    const cached = this.hydroTiles.get(key);
    if (cached) {
      this.hydroTiles.delete(key);
      this.hydroTiles.set(key, cached);
      return cached;
    }
    const iterator = this.buildHydroTileSteps(tileX, tileZ);
    let result = iterator.next();
    while (!result.done) {
      await yieldOneTick();
      result = iterator.next();
    }
    const tile = result.value;
    this.hydroTiles.set(key, tile);
    if (this.hydroTiles.size > HYDRO_CACHE_LIMIT) {
      this.hydroTiles.delete(this.hydroTiles.keys().next().value);
    }
    return tile;
  }

  hydroTile(tileX, tileZ) {
    const key = pairKey(tileX, tileZ);
    const cached = this.hydroTiles.get(key);
    if (cached) {
      this.hydroTiles.delete(key);
      this.hydroTiles.set(key, cached);
      return cached;
    }
    const tile = this.buildHydroTile(tileX, tileZ);
    this.hydroTiles.set(key, tile);
    if (this.hydroTiles.size > HYDRO_CACHE_LIMIT) {
      this.hydroTiles.delete(this.hydroTiles.keys().next().value);
    }
    return tile;
  }

  buildHydroTile(tileX, tileZ) {
    const iterator = this.buildHydroTileSteps(tileX, tileZ);
    let result = iterator.next();
    while (!result.done) result = iterator.next();
    return result.value;
  }

  *buildHydroTileSteps(tileX, tileZ) {
    const n = HYDRO_TILE_CELLS;
    const radius = HYDRO_RADIUS_CELLS;
    const patchSize = n + 2 * radius;
    const startCellX = tileX * n - radius;
    const startCellZ = tileZ * n - radius;
    const patchCount = patchSize * patchSize;
    const heights = new Float64Array(patchCount);
    const jittered = new Float64Array(patchCount);
    const filled = new Float64Array(patchCount);
    const closed = new Uint8Array(patchCount);

    for (let j = 0; j < patchSize; j++) {
      for (let i = 0; i < patchSize; i++) {
        const cellX = startCellX + i;
        const cellZ = startCellZ + j;
        const worldX = (cellX + 0.5) * HYDRO_CELL_SIZE;
        const worldZ = (cellZ + 0.5) * HYDRO_CELL_SIZE;
        const index = j * patchSize + i;
        const height = this.sampleBase(worldX, worldZ).height;
        heights[index] = height;
        const tie = unit(hashCell(this.seed, cellX, cellZ, SALT.hydroTie));
        jittered[index] = height + (tie - 0.5) * 0.02;
        filled[index] = height;
      }
      if ((j & 7) === 7) yield;
    }

    const open = new MinHeap((index) => filled[index]);
    for (let j = 0; j < patchSize; j++) {
      for (let i = 0; i < patchSize; i++) {
        const index = j * patchSize + i;
        if (i === 0 || j === 0 || i === patchSize - 1 || j === patchSize - 1
            || heights[index] < SEA_LEVEL) {
          closed[index] = 1;
          open.push(index);
        }
      }
    }

    let floodSteps = 0;
    while (open.length > 0) {
      const current = open.pop();
      const x = current % patchSize;
      const z = Math.floor(current / patchSize);
      for (let direction = 0; direction < 8; direction++) {
        const nx = x + FLOW_DX[direction];
        const nz = z + FLOW_DZ[direction];
        if (nx < 0 || nz < 0 || nx >= patchSize || nz >= patchSize) continue;
        const next = nz * patchSize + nx;
        if (closed[next]) continue;
        closed[next] = 1;
        filled[next] = Math.max(heights[next], filled[current] + 1e-3);
        open.push(next);
      }
      if ((++floodSteps & 511) === 0) yield;
    }

    const drained = new Float64Array(patchCount);
    const flow = new Int8Array(patchCount);
    flow.fill(NO_FLOW);
    for (let index = 0; index < patchCount; index++) {
      drained[index] = filled[index] - heights[index] > 1e-6 ? filled[index] : jittered[index];
    }
    for (let j = 0; j < patchSize; j++) {
      for (let i = 0; i < patchSize; i++) {
        const index = j * patchSize + i;
        if (heights[index] < SEA_LEVEL
            || i === 0 || j === 0 || i === patchSize - 1 || j === patchSize - 1) continue;
        let bestDirection = NO_FLOW;
        let bestHeight = drained[index];
        for (let direction = 0; direction < 8; direction++) {
          const nx = i + FLOW_DX[direction];
          const nz = j + FLOW_DZ[direction];
          if (nx < 0 || nz < 0 || nx >= patchSize || nz >= patchSize) continue;
          const neighbor = drained[nz * patchSize + nx];
          if (neighbor < bestHeight) {
            bestHeight = neighbor;
            bestDirection = direction;
          }
        }
        flow[index] = bestDirection;
      }
      if ((j & 7) === 7) yield;
    }

    // Each land cell contributes one unit of runoff. Since every flow edge
    // descends in `drained`, descending elevation order accumulates all
    // upstream area exactly once without truncating individual path walks.
    const accumulation = new Float32Array(patchCount);
    const flowOrder = new Array(patchCount);
    for (let index = 0; index < patchCount; index++) {
      accumulation[index] = heights[index] >= SEA_LEVEL ? 1 : 0;
      flowOrder[index] = index;
    }
    flowOrder.sort((a, b) => drained[b] - drained[a] || a - b);
    for (let orderIndex = 0; orderIndex < patchCount; orderIndex++) {
      const current = flowOrder[orderIndex];
      const direction = flow[current];
      if (direction >= 0) {
        const currentX = current % patchSize;
        const currentZ = Math.floor(current / patchSize);
        const nextX = currentX + FLOW_DX[direction];
        const nextZ = currentZ + FLOW_DZ[direction];
        const next = nextZ * patchSize + nextX;
        accumulation[next] += accumulation[current];
      }
      if ((orderIndex & 511) === 511) yield;
    }

    const count = n * n;
    const confluence01 = new Float32Array(count);
    const lake01 = new Float32Array(count);
    const wet01 = new Float32Array(count);
    const pit = new Uint8Array(count);
    for (let j = 0; j < n; j++) {
      for (let i = 0; i < n; i++) {
        const patchX = i + radius;
        const patchZ = j + radius;
        const patchIndex = patchZ * patchSize + patchX;
        const outputIndex = j * n + i;
        const upstream = accumulation[patchIndex];
        const confluence = clamp01((upstream - 1) / 48);
        confluence01[outputIndex] = confluence;
        const ocean = heights[patchIndex] < SEA_LEVEL;
        const fillDepth = filled[patchIndex] - heights[patchIndex];
        const isPit = !ocean && fillDepth > 1;
        pit[outputIndex] = isPit ? 1 : 0;
        if (isPit) {
          lake01[outputIndex] = clamp01((fillDepth - 1) / 3) * clamp01(upstream / 14);
        }
        const gx = (heights[patchIndex + 1] - heights[patchIndex - 1]) / (2 * HYDRO_CELL_SIZE);
        const gz = (heights[patchIndex + patchSize] - heights[patchIndex - patchSize])
          / (2 * HYDRO_CELL_SIZE);
        const cellSlope = Math.hypot(gx, gz);
        if (!ocean) {
          wet01[outputIndex] = smoothstep(0.55, 0.85, confluence)
            * smoothstepDown(0.07, 0.025, cellSlope);
        }
      }
      if ((j & 7) === 7) yield;
    }
    return { confluence01, lake01, wet01, pit };
  }

  sampleColumn(x, z) {
    const base = this.sampleBase(x, z);
    const plusX = this.sampleBase(x + 2, z).height;
    const plusZ = this.sampleBase(x, z + 2).height;
    const slope = Math.hypot((plusX - base.height) / 2, (plusZ - base.height) / 2);
    return this.finishColumn(x, z, base, slope, this.sampleHydrology(x, z));
  }

  async sampleColumnAsync(x, z, yieldOneTick) {
    const base = this.sampleBase(x, z);
    const plusX = this.sampleBase(x + 2, z).height;
    const plusZ = this.sampleBase(x, z + 2).height;
    const slope = Math.hypot((plusX - base.height) / 2, (plusZ - base.height) / 2);
    const hydro = await this.sampleHydrologyAsync(x, z, yieldOneTick);
    return this.finishColumn(x, z, base, slope, hydro);
  }

  finishColumn(x, z, base, slope, hydro) {
    const temperature = clamp01(
      0.60 + 0.40 * fbm(this.seed, x / 3000 + 11.3, z / 3000 - 5.2,
        2, 2, 0.5, SALT.temperature) - 0.0016 * (base.height - SEA_LEVEL),
    );
    const coast = clamp01(1 - Math.abs(base.continent) / 0.5);
    const moisture = clamp01(
      0.45 + 0.55 * fbm(this.seed, x / 2600 - 7.7, z / 2600 + 3.9,
        3, 2, 0.5, SALT.moisture)
      + 0.20 * coast + 0.30 * hydro.river01 + 0.25 * hydro.lake01 + 0.20 * hydro.wet01,
    );
    const variantX = floorDiv(Math.floor(x), 173);
    const variantZ = floorDiv(Math.floor(z), 173);
    const variantHash = hashCell(this.seed, variantX, variantZ, SALT.formation);
    const deposition01 = slope < 0.12
      ? smoothstep(0.20, 0.60, hydro.confluence01) * (1 - slope / 0.12) : 0;

    let formation = 'clastic_basin';
    if (base.continent < -0.15) {
      formation = 'oceanic';
    } else if (base.boundary.convergent01 > 0.45) {
      formation = 'orogen';
    } else if (base.boundary.divergent01 > 0.40 && base.continent > -0.10) {
      formation = 'rift';
    } else if (this.isVolcanicProvince(x, z, base.boundary)) {
      formation = 'volcanic';
    } else if (base.continent >= -0.10 && base.continent <= 0.35 && slope < 0.30) {
      formation = temperature > 0.55
        && unit(hashCell(this.seed, base.boundary.plateId1, 77, SALT.formation)) < 0.45
        ? 'carbonate' : 'clastic_basin';
    } else if (base.continent > 0.40 && base.boundary.boundary01 < 0.25) {
      formation = 'shield';
    }
    if (deposition01 > 0.55) formation = 'alluvial';

    const baseRock = this.formationRock(formation, variantHash);
    const hardness = slope > 0.5 ? this.rockHardness(baseRock) : 0.2;
    const area = 1 + 60 * clamp01(hydro.confluence01);
    const boundedSlope = Math.min(Math.max(slope, 0), 1.2);
    const fluvial = 1.6 * Math.sqrt(area) * boundedSlope;
    const hill = Math.min(3, 2.5 * boundedSlope * boundedSlope);
    const erosionLower = Math.min((fluvial + hill) * (1.3 - 0.6 * hardness), 16);
    const depositionRaise = slope > 0.10 ? 0
      : 2 * smoothstep(0.25, 0.70, clamp01(hydro.confluence01)) * (1 - slope / 0.10);
    const baseOcean = base.height < SEA_LEVEL;
    const riverEff = hydro.river01 * (baseOcean ? 0.3 : 1);
    let riverCarve = riverEff > 0 ? Math.pow(riverEff, 1.2) * 8
      * (1 - smoothstep(0.3, 0.7, hydro.lake01)) : 0;
    if (!baseOcean) riverCarve += hydro.wet01 * 1.5;
    const finalHeight = clamp(Math.round(base.height - erosionLower + depositionRaise - riverCarve), 2, MAX_SURFACE_Y);
    const ocean = finalHeight < SEA_LEVEL;
    const deepOcean = SEA_LEVEL - finalHeight > 12;
    const beach = !ocean && finalHeight <= SEA_LEVEL + 2 && slope < 0.4 && hydro.river01 < 0.3;

    let lakeWaterTop = -1;
    if (!ocean && hydro.pit && hydro.lake01 > 0.45) {
      lakeWaterTop = Math.min(finalHeight + 1 + Math.round(hydro.lake01 * 2), MAX_SURFACE_Y);
    }
    let riverWaterTop = -1;
    if (!ocean && hydro.river01 > 0.45 && riverCarve > 4.5) {
      const depth = Math.min(3, Math.trunc(riverCarve - 4));
      riverWaterTop = Math.min(finalHeight + depth, MAX_SURFACE_Y);
    }

    const surface = this.planSurface({
      x, z, finalHeight, slope, ocean, beach, hydro, temperature, moisture,
      formation, deposition01, variantHash, baseRock,
    });
    const layers = this.layerMaterials(formation, variantHash);
    const thicknessNoise = fbm(this.seed, x / 200, z / 200, 2, 2, 0.5, SALT.thickness);
    const shallowThickness = Math.max(6, Math.round(14 + thicknessNoise * 5));
    const middleThickness = Math.max(10, Math.round(24 + thicknessNoise * 8));
    const soilDepth = surface.soilDepth;
    const coverBelowTop = surface.sedimentCap > 0
      ? surface.sedimentCap : Math.max(0, soilDepth - 1);
    const shallowTop = finalHeight - coverBelowTop - 1;
    const middleTop = shallowTop - shallowThickness;
    const deepTop = middleTop - middleThickness;

    return {
      x, z,
      baseHeight: Math.round(base.height),
      finalHeight,
      continent: base.continent,
      slope,
      confluence01: hydro.confluence01,
      river01: hydro.river01,
      lake01: hydro.lake01,
      wet01: hydro.wet01,
      pit: hydro.pit,
      temperature,
      moisture,
      erosionLower,
      depositionRaise,
      riverCarve,
      ocean,
      deepOcean,
      beach,
      lakeWaterTop,
      riverWaterTop,
      formation,
      surface,
      layers: { deep: layers.deep, middle: layers.middle, shallow: layers.shallow },
      layerBounds: { deepTop, middleTop, shallowTop },
      boundary: base.boundary,
    };
  }

  isVolcanicProvince(x, z, boundary) {
    const margin = Math.max(boundary.convergent01, boundary.divergent01);
    if (margin < 0.28) return false;
    const plateVolcanism = unit(hashCell(this.seed, boundary.plateId1, 0, SALT.volcanic));
    const local = (fbm(this.seed, x / 900, z / 900, 2, 2, 0.5, SALT.volcanic) + 1) * 0.5;
    return plateVolcanism * 0.7 + local * 0.3 < 0.30;
  }

  formationRock(formation, variantHash) {
    const value = unit(mix32(variantHash ^ 0x5ca4));
    switch (formation) {
      case 'orogen': return value < 0.45 ? 'strata:schist' : value < 0.8 ? 'strata:gneiss' : 'strata:granite';
      case 'volcanic': return value < 0.7 ? 'strata:basalt' : 'strata:andesite';
      case 'rift': return value < 0.5 ? 'strata:basalt' : 'strata:sandstone';
      case 'carbonate': return 'strata:limestone';
      case 'oceanic': return 'strata:basalt';
      case 'shield': return value < 0.5 ? 'strata:granite' : 'strata:gneiss';
      case 'alluvial': return 'strata:conglomerate';
      default: return value < 0.4 ? 'strata:sandstone' : value < 0.7 ? 'strata:shale' : 'strata:conglomerate';
    }
  }

  rockHardness(blockId) {
    switch (blockId) {
      case 'strata:granite': case 'strata:quartzite': case 'strata:basalt': return 0.9;
      case 'strata:gneiss': return 0.8;
      case 'strata:andesite': return 0.75;
      case 'strata:schist': case 'strata:marble': case 'strata:slate': return 0.6;
      case 'strata:sandstone': case 'strata:conglomerate': case 'strata:limestone': return 0.5;
      default: return 0.35;
    }
  }

  layerMaterials(formation, variantHash) {
    const a = unit(mix32(variantHash ^ 0x0000000b));
    const b = unit(mix32(variantHash ^ 0x00000017));
    switch (formation) {
      case 'orogen':
        return { deep: 'strata:granite', middle: a < 0.35 ? 'strata:granite' : 'strata:gneiss', shallow: b < 0.30 ? 'strata:marble' : 'strata:quartzite' };
      case 'volcanic': return { deep: 'strata:basalt', middle: 'strata:andesite', shallow: 'strata:basalt' };
      case 'rift': return { deep: 'strata:basalt', middle: 'strata:shale', shallow: a < 0.5 ? 'strata:basalt' : 'strata:sandstone' };
      case 'carbonate': return { deep: 'strata:limestone', middle: 'strata:shale', shallow: 'strata:limestone' };
      case 'oceanic': return { deep: 'strata:basalt', middle: 'strata:andesite', shallow: 'strata:basalt' };
      case 'shield': return { deep: 'strata:granite', middle: 'strata:gneiss', shallow: a < 0.5 ? 'strata:granite' : 'strata:gneiss' };
      case 'alluvial': return { deep: 'strata:sandstone', middle: 'strata:shale', shallow: 'strata:conglomerate' };
      default: return { deep: 'strata:sandstone', middle: b < 0.6 ? 'strata:shale' : 'strata:slate', shallow: a < 0.5 ? 'strata:sandstone' : 'strata:conglomerate' };
    }
  }

  planSurface(sample) {
    const { x, z, finalHeight, slope, ocean, beach, hydro, temperature, moisture,
      formation, deposition01, variantHash, baseRock } = sample;
    if (ocean) {
      const depth = SEA_LEVEL - finalHeight;
      const sediment = fbm(this.seed, x / 130, z / 130, 2, 2, 0.5, SALT.surface);
      const top = depth > 14 ? (sediment < -0.10 ? 'minecraft:gravel' : 'minecraft:sand')
        : (sediment < 0.30 ? 'minecraft:sand' : 'minecraft:gravel');
      return { top, filler: top, soilDepth: 0, sedimentCap: 2 + (depth > 10 ? 2 : 0), sediment: top, baseRock };
    }
    if (beach) {
      const red = temperature > 0.6 && moisture < 0.3;
      return { top: red ? 'minecraft:red_sand' : 'minecraft:sand', filler: 'minecraft:sand', soilDepth: 3, sedimentCap: 0, sediment: 'minecraft:sand', baseRock };
    }
    if (hydro.river01 > 0.5) {
      return { top: 'minecraft:gravel', filler: 'minecraft:dirt', soilDepth: 2, sedimentCap: 0, sediment: 'minecraft:gravel', baseRock };
    }
    if (hydro.lake01 > 0.4 && hydro.pit) {
      const top = unit(hashCell(this.seed, x, z, SALT.surface)) < 0.5 ? 'minecraft:clay' : 'minecraft:gravel';
      return { top, filler: 'minecraft:dirt', soilDepth: 2, sedimentCap: 0, sediment: top, baseRock };
    }
    if (slope > 0.55) {
      return { top: baseRock, filler: baseRock, soilDepth: 0, sedimentCap: 0, sediment: baseRock, baseRock };
    }
    if (formation === 'alluvial' || deposition01 > 0.4) {
      const alluvium = fbm(this.seed, x / 90 + 31.7, z / 90 - 12.3, 2, 2, 0.5, SALT.alluvium);
      const top = alluvium < 0.10 ? 'minecraft:gravel' : alluvium < 0.55 ? 'minecraft:dirt' : 'minecraft:sand';
      return { top, filler: 'minecraft:dirt', soilDepth: 3, sedimentCap: 0, sediment: top, baseRock };
    }
    if (slope > 0.35) {
      return { top: temperature > 0.68 && moisture < 0.25 ? 'minecraft:sand' : 'minecraft:grass_block',
        filler: 'minecraft:dirt', soilDepth: 1, sedimentCap: 0, sediment: 'minecraft:dirt', baseRock };
    }
    if (temperature > 0.68 && moisture < 0.25) {
      return { top: 'minecraft:sand', filler: 'minecraft:sand', soilDepth: 3, sedimentCap: 0, sediment: 'minecraft:sand', baseRock };
    }
    return { top: 'minecraft:grass_block', filler: 'minecraft:dirt', soilDepth: 3, sedimentCap: 0, sediment: 'minecraft:dirt', baseRock };
  }
}
