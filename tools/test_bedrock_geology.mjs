import test from "node:test";
import assert from "node:assert/strict";
import {
  GeologyModel,
  HYDRO_CELL_SIZE,
  HYDRO_TILE_CELLS,
  MAX_SURFACE_Y,
  MIN_Y,
} from "../bedrock/STRATA_BehaviorPack/scripts/geology_model.js";

test("same world seed and absolute coordinates produce identical full columns", () => {
  const first = new GeologyModel("12345");
  const expected = first.sampleColumn(-273, 68);
  first.sampleColumn(1024, -512);
  first.sampleColumn(72, 72);
  assert.deepEqual(first.sampleColumn(-273, 68), expected);
  assert.deepEqual(new GeologyModel("12345").sampleColumn(-273, 68), expected);
});

test("world seed affects the procedural geology", () => {
  const a = new GeologyModel("12345").sampleColumn(-273, 68);
  const b = new GeologyModel("54321").sampleColumn(-273, 68);
  assert.notDeepEqual(b, a);
});

test("D8 drainage outputs are deterministic across the 512-block tile seam", () => {
  const tileBlocks = HYDRO_CELL_SIZE * HYDRO_TILE_CELLS;
  const coordinates = [];
  for (const x of [tileBlocks - 8, tileBlocks, tileBlocks + 8]) {
    for (const z of [tileBlocks - 8, tileBlocks, tileBlocks + 8]) coordinates.push([x, z]);
  }
  const forward = new GeologyModel("12345");
  const expected = coordinates.map(([x, z]) => forward.sampleHydrology(x, z));
  const reverse = new GeologyModel("12345");
  const actual = coordinates.slice().reverse().map(([x, z]) => reverse.sampleHydrology(x, z)).reverse();
  assert.deepEqual(actual, expected);
  for (const value of actual) {
    assert.ok(value.confluence01 >= 0 && value.confluence01 <= 1);
    assert.ok(value.river01 >= 0 && value.river01 <= 1);
    assert.ok(value.lake01 >= 0 && value.lake01 <= 1);
  }
});

test("time-sliced hydrology is bit-for-bit equivalent to synchronous sampling", async () => {
  const expected = new GeologyModel("12345").sampleColumn(512, 512);
  const model = new GeologyModel("12345");
  let yielded = 0;
  const actual = await model.sampleColumnAsync(512, 512, async () => { yielded++; });
  assert.deepEqual(actual, expected);
  assert.ok(yielded > 0, "uncached hydrology should yield so Bedrock can share work across ticks");
});

test("sampled columns stay within dimension bounds and have ordered rock contacts", () => {
  const model = new GeologyModel("12345");
  const formations = new Set([
    "alluvial", "carbonate", "clastic_basin", "oceanic", "orogen", "rift", "shield", "volcanic",
  ]);
  for (let z = -128; z <= 128; z += 32) {
    for (let x = -128; x <= 128; x += 32) {
      const column = model.sampleColumn(x, z);
      assert.ok(column.finalHeight >= 2 && column.finalHeight <= MAX_SURFACE_Y);
      assert.ok(column.layerBounds.deepTop < column.layerBounds.middleTop);
      assert.ok(column.layerBounds.middleTop < column.layerBounds.shallowTop);
      assert.ok(column.layerBounds.shallowTop < column.finalHeight);
      assert.ok(formations.has(column.formation));
      assert.equal(typeof column.surface.top, "string");
    }
  }
  assert.ok(MIN_Y < 0);
});

test("no large one-block height jump appears at chunk or hydrology-tile seams", () => {
  const model = new GeologyModel("12345");
  let maxDelta = 0;
  const fixedLines = [-135, -128, -17, -1, 0, 15, 16, 121, 127, 128, 135];
  for (const z of fixedLines) {
    for (let x = -140; x < 140; x++) {
      const a = model.sampleColumn(x, z).finalHeight;
      const b = model.sampleColumn(x + 1, z).finalHeight;
      maxDelta = Math.max(maxDelta, Math.abs(b - a));
    }
  }
  for (const x of fixedLines) {
    for (let z = -140; z < 140; z++) {
      const a = model.sampleColumn(x, z).finalHeight;
      const b = model.sampleColumn(x, z + 1).finalHeight;
      maxDelta = Math.max(maxDelta, Math.abs(b - a));
    }
  }
  for (const seam of [-512, 0, 512]) {
    for (let offset = -8; offset < 8; offset++) {
      const x = seam + offset;
      const z = seam + offset;
      for (const cross of [0, seam]) {
        const horizontalA = model.sampleColumn(x, cross).finalHeight;
        const horizontalB = model.sampleColumn(x + 1, cross).finalHeight;
        const verticalA = model.sampleColumn(cross, z).finalHeight;
        const verticalB = model.sampleColumn(cross, z + 1).finalHeight;
        maxDelta = Math.max(
          maxDelta,
          Math.abs(horizontalB - horizontalA),
          Math.abs(verticalB - verticalA),
        );
      }
    }
  }
  assert.ok(maxDelta <= 12, `observed ${maxDelta}-block adjacent height change`);
});
