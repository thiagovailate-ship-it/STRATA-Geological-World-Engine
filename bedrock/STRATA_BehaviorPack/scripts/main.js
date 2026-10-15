import { BlockVolume, system, world } from "@minecraft/server";
import {
  GeologyModel,
  MIN_Y,
  SEA_LEVEL,
} from "./geology_model.js";

const DIMENSION_ID = "strata:geology";
const PORTAL_ITEM_ID = "strata:geology_compass";
const CHUNK_SIZE = 16;
const WORLD_TOP = MIN_Y + 384 - 1;
const TICKING_AREA_ID = "strata_terrain_builder";
const RETURN_TICKING_AREA_PREFIX = "strata_overworld_return";
const GENERATION_BATCH_COLUMNS = 16;
const MAX_COMPLETED_CHUNKS = 4096;
const SPAWN_SEARCH_RADIUS_TILES = 12;
let returnTickingAreaSequence = 0;

let geologyModel;
let generationWorkerRunning = false;
let generationWorkerScheduled = false;
const generationQueue = [];
const queuedChunks = new Map();
const completedChunks = new Map();
const activePlayers = new Set();

system.beforeEvents.startup.subscribe((event) => {
  // The stable 2.8+ DimensionRegistry creates custom dimensions with the void
  // generator. STRATA writes its deterministic terrain after chunks are loaded.
  event.dimensionRegistry.registerCustomDimension(DIMENSION_ID);
});

world.afterEvents.itemUse.subscribe((event) => {
  if (event.itemStack.typeId !== PORTAL_ITEM_ID) return;
  const player = event.source;
  if (!player || activePlayers.has(player.id)) return;

  activePlayers.add(player.id);
  const task = player.dimension.id === DIMENSION_ID
    ? returnToOverworld(player)
    : enterGeologyDimension(player);
  task.catch((error) => {
    player.sendMessage(`§cSTRATA: falha ao gerar/transferir: ${safeError(error)}`);
  }).finally(() => activePlayers.delete(player.id));
});

system.runInterval(() => {
  const dimension = world.getDimension(DIMENSION_ID);
  for (const player of world.getAllPlayers()) {
    if (player.dimension.id !== DIMENSION_ID) continue;
    const chunkX = Math.floor(player.location.x / CHUNK_SIZE);
    const chunkZ = Math.floor(player.location.z / CHUNK_SIZE);
    for (let dz = -1; dz <= 1; dz++) {
      for (let dx = -1; dx <= 1; dx++) {
        queueChunk(dimension, chunkX + dx, chunkZ + dz)
          .catch((error) => player.sendMessage(`§cSTRATA: chunk ${chunkX + dx},${chunkZ + dz}: ${safeError(error)}`));
      }
    }
  }
}, 20);

function getModel() {
  if (!geologyModel) geologyModel = new GeologyModel(world.seed);
  return geologyModel;
}

async function enterGeologyDimension(player) {
  const model = getModel();
  const location = player.location;
  player.sendMessage("§6STRATA: procurando terreno seguro e preparando a dimensão…");
  const spawn = await findSpawn(model, location.x, location.z);
  const dimension = world.getDimension(DIMENSION_ID);
  const centerChunkX = Math.floor(spawn.x / CHUNK_SIZE);
  const centerChunkZ = Math.floor(spawn.z / CHUNK_SIZE);
  const nearby = [];
  for (let dz = -1; dz <= 1; dz++) {
    for (let dx = -1; dx <= 1; dx++) {
      nearby.push({ x: centerChunkX + dx, z: centerChunkZ + dz, distance: dx * dx + dz * dz });
    }
  }
  nearby.sort((a, b) => a.distance - b.distance || a.z - b.z || a.x - b.x);

  await Promise.all(nearby.map((chunk) => queueChunk(dimension, chunk.x, chunk.z)));

  const landing = model.sampleColumn(spawn.x, spawn.z);
  const landingY = spawn.needsPlatform
    ? buildSpawnPlatform(dimension, spawn.x, spawn.z, landing.finalHeight)
    : landing.finalHeight;
  player.teleport(
    { x: spawn.x + 0.5, y: landingY + 1.05, z: spawn.z + 0.5 },
    { dimension },
  );
  player.sendMessage("§aSTRATA: dimensão geológica pronta. Use a bússola novamente para voltar ao spawn do Overworld.");
}

async function findSpawn(model, originX, originZ) {
  const tileSize = 128;
  const chunkCenterOffset = 72; // center of the middle chunk in a 3x3 prebuilt ring
  const originTileX = Math.floor((originX - chunkCenterOffset) / tileSize);
  const originTileZ = Math.floor((originZ - chunkCenterOffset) / tileSize);
  let best = undefined;
  let evaluated = 0;

  for (let radius = 0; radius <= SPAWN_SEARCH_RADIUS_TILES; radius++) {
    for (let dz = -radius; dz <= radius; dz++) {
      for (let dx = -radius; dx <= radius; dx++) {
        if (Math.max(Math.abs(dx), Math.abs(dz)) !== radius) continue;
        const x = (originTileX + dx) * tileSize + chunkCenterOffset;
        const z = (originTileZ + dz) * tileSize + chunkCenterOffset;
        const base = model.sampleBase(x, z);
        if ((++evaluated & 127) === 0) await waitOneTick();
        // Reserve enough relief for the capped erosion and channel cuts so a
        // chosen spawn remains above sea level after the final-height pass.
        if (base.height < SEA_LEVEL + 27) continue;
        const slopeX = model.sampleBase(x + 2, z).height - base.height;
        const slopeZ = model.sampleBase(x, z + 2).height - base.height;
        const slope = Math.hypot(slopeX, slopeZ) / 2;
        const distance = (x - originX) ** 2 + (z - originZ) ** 2;
        const score = distance + Math.abs(base.height - (SEA_LEVEL + 38)) * 80 + slope * 500;
        if (!best || score < best.score) best = { x, z, score, needsPlatform: false };
      }
    }
    // Prefer a nearby safe land point; only expand farther if this ring found none.
    if (best && radius >= 2) break;
  }

  if (best) return best;
  const fallbackX = Math.floor(originX / CHUNK_SIZE) * CHUNK_SIZE + 8;
  const fallbackZ = Math.floor(originZ / CHUNK_SIZE) * CHUNK_SIZE + 8;
  return { x: fallbackX, z: fallbackZ, score: Infinity, needsPlatform: true };
}

function buildSpawnPlatform(dimension, x, z, surfaceHeight) {
  const centerX = Math.floor(x);
  const centerZ = Math.floor(z);
  const platformY = Math.max(SEA_LEVEL + 1, surfaceHeight + 1);
  dimension.fillBlocks(
    new BlockVolume(
      { x: centerX - 2, y: platformY, z: centerZ - 2 },
      { x: centerX + 2, y: platformY, z: centerZ + 2 },
    ),
    "strata:basalt",
  );
  dimension.fillBlocks(
    new BlockVolume(
      { x: centerX - 1, y: platformY + 1, z: centerZ - 1 },
      { x: centerX + 1, y: platformY + 3, z: centerZ + 1 },
    ),
    "minecraft:air",
  );
  return platformY;
}

async function returnToOverworld(player) {
  const spawn = world.getDefaultSpawnLocation();
  const overworld = world.getDimension("minecraft:overworld");
  const blockX = Math.floor(spawn.x);
  const blockZ = Math.floor(spawn.z);
  const tickingAreaId = `${RETURN_TICKING_AREA_PREFIX}_${returnTickingAreaSequence++}`;
  const tickingAreas = world.tickingAreaManager;
  let areaCreated = false;

  try {
    await tickingAreas.createTickingArea(tickingAreaId, {
      dimension: overworld,
      from: { x: blockX, y: MIN_Y, z: blockZ },
      to: { x: blockX, y: WORLD_TOP, z: blockZ },
    });
    areaCreated = true;
    const top = overworld.getTopmostBlock({ x: blockX, z: blockZ });
    const safeY = top ? top.location.y + 1.05 : SEA_LEVEL + 4;
    player.teleport(
      { x: spawn.x + 0.5, y: safeY, z: spawn.z + 0.5 },
      { dimension: overworld },
    );
  } finally {
    if (areaCreated && tickingAreas.hasTickingArea(tickingAreaId)) {
      tickingAreas.removeTickingArea(tickingAreaId);
    }
  }
  player.sendMessage("§aSTRATA: retornou ao spawn padrão do Overworld.");
}

function queueChunk(dimension, chunkX, chunkZ) {
  const key = `${chunkX},${chunkZ}`;
  if (completedChunks.has(key)) {
    const value = completedChunks.get(key);
    completedChunks.delete(key);
    completedChunks.set(key, value);
    return Promise.resolve();
  }
  const existing = queuedChunks.get(key);
  if (existing) return existing;

  let resolveJob;
  let rejectJob;
  const promise = new Promise((resolve, reject) => {
    resolveJob = resolve;
    rejectJob = reject;
  });
  queuedChunks.set(key, promise);
  generationQueue.push({ dimension, chunkX, chunkZ, key, resolve: resolveJob, reject: rejectJob });
  scheduleGenerationWorker();
  return promise;
}

function scheduleGenerationWorker() {
  if (generationWorkerRunning || generationWorkerScheduled) return;
  generationWorkerScheduled = true;
  system.runTimeout(() => {
    generationWorkerScheduled = false;
    processGenerationQueue().catch((error) => {
      console.warn(`STRATA generation worker stopped: ${safeError(error)}`);
      generationWorkerRunning = false;
      if (generationQueue.length > 0) scheduleGenerationWorker();
    });
  }, 1);
}

async function processGenerationQueue() {
  if (generationWorkerRunning) return;
  generationWorkerRunning = true;
  while (generationQueue.length > 0) {
    const job = generationQueue.shift();
    try {
      await generateChunk(job);
      rememberCompleted(job.key);
      job.resolve();
    } catch (error) {
      job.reject(error);
    } finally {
      queuedChunks.delete(job.key);
    }
  }
  generationWorkerRunning = false;
}

function rememberCompleted(key) {
  completedChunks.set(key, true);
  if (completedChunks.size > MAX_COMPLETED_CHUNKS) {
    completedChunks.delete(completedChunks.keys().next().value);
  }
}

async function generateChunk(job) {
  const dimension = job.dimension;
  const x0 = job.chunkX * CHUNK_SIZE;
  const z0 = job.chunkZ * CHUNK_SIZE;
  const x1 = x0 + CHUNK_SIZE - 1;
  const z1 = z0 + CHUNK_SIZE - 1;
  const tickingAreas = world.tickingAreaManager;
  let areaCreated = false;

  try {
    await tickingAreas.createTickingArea(TICKING_AREA_ID, {
      dimension,
      from: { x: x0, y: MIN_Y, z: z0 },
      to: { x: x1, y: WORLD_TOP, z: z1 },
    });
    areaCreated = true;

    const marker = dimension.getBlock({ x: x0, y: MIN_Y + 1, z: z0 });
    if (marker && marker.typeId === "minecraft:bedrock") return;

    const model = getModel();
    const columns = [];
    let processed = 0;
    for (let localZ = 0; localZ < CHUNK_SIZE; localZ++) {
      for (let localX = 0; localX < CHUNK_SIZE; localX++) {
        columns.push(await model.sampleColumnAsync(x0 + localX, z0 + localZ, waitOneTick));
        processed++;
        if (processed % GENERATION_BATCH_COLUMNS === 0) await waitOneTick();
      }
    }

    dimension.fillBlocks(
      new BlockVolume({ x: x0, y: MIN_Y, z: z0 }, { x: x1, y: MIN_Y, z: z1 }),
      "minecraft:bedrock",
    );

    for (let index = 0; index < columns.length; index++) {
      writeColumn(dimension, columns[index]);
      if ((index + 1) % GENERATION_BATCH_COLUMNS === 0) await waitOneTick();
    }

    // Completion marker lives just above the bedrock floor so interrupted
    // generation is retried, while finished chunks are never overwritten.
    dimension.fillBlocks(
      new BlockVolume(
        { x: x0, y: MIN_Y + 1, z: z0 },
        { x: x0, y: MIN_Y + 1, z: z0 },
      ),
      "minecraft:bedrock",
    );
  } finally {
    if (areaCreated && tickingAreas.hasTickingArea(TICKING_AREA_ID)) {
      tickingAreas.removeTickingArea(TICKING_AREA_ID);
    }
  }
}

function writeColumn(dimension, column) {
  const x = column.x;
  const z = column.z;
  const height = column.finalHeight;
  const { deepTop, middleTop, shallowTop } = column.layerBounds;
  const { deep, middle, shallow } = column.layers;
  const { surface } = column;
  const coverUnder = surface.sedimentCap > 0
    ? surface.sedimentCap
    : Math.max(0, surface.soilDepth - 1);

  fillVertical(dimension, x, z, MIN_Y + 1, deepTop, deep);
  fillVertical(dimension, x, z, deepTop + 1, middleTop, middle);
  fillVertical(dimension, x, z, middleTop + 1, shallowTop, shallow);
  if (coverUnder > 0) {
    fillVertical(dimension, x, z, height - coverUnder, height - 1,
      surface.sedimentCap > 0 ? surface.sediment : surface.filler);
  }
  fillVertical(dimension, x, z, height, height, surface.top);

  let waterTop = -1;
  if (column.ocean) waterTop = SEA_LEVEL;
  else waterTop = Math.max(column.lakeWaterTop, column.riverWaterTop);
  if (waterTop > height) fillVertical(dimension, x, z, height + 1, waterTop, "minecraft:water");
}

function fillVertical(dimension, x, z, lowY, highY, blockId) {
  if (lowY > highY || highY < MIN_Y || lowY > WORLD_TOP) return;
  const low = Math.max(lowY, MIN_Y);
  const high = Math.min(highY, WORLD_TOP);
  if (low > high) return;
  dimension.fillBlocks(
    new BlockVolume({ x, y: low, z }, { x, y: high, z }),
    blockId,
  );
}

function waitOneTick() {
  return new Promise((resolve) => system.runTimeout(() => resolve(), 1));
}

function safeError(error) {
  if (error && typeof error.message === "string") return error.message.slice(0, 160);
  return String(error).slice(0, 160);
}
