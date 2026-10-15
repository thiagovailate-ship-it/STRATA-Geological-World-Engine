package dev.strata.forge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.MapGenBase;
import net.minecraft.world.gen.MapGenCaves;
import net.minecraft.world.gen.MapGenRavine;
import net.minecraft.world.gen.feature.WorldGenDungeons;
import net.minecraft.world.gen.feature.WorldGenLakes;
import net.minecraft.world.gen.structure.MapGenMineshaft;
import net.minecraft.world.gen.structure.MapGenScatteredFeature;
import net.minecraft.world.gen.structure.MapGenStronghold;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.MapGenVillage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.InitMapGenEvent;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.event.terraingen.TerrainGen;
import cpw.mods.fml.common.eventhandler.EventBus;
import dev.strata.blocks.StrataBlocks;
import dev.strata.core.ColumnData;
import dev.strata.core.StrataWorldModel;
import dev.strata.core.config.StrataWorldConfig;
import dev.strata.core.stratigraphy.GeoMaterial;
import dev.strata.core.terrain.BiomeKind;
import dev.strata.core.util.SeedMixer;

/**
 * Chunk generator for STRATA worlds: one engine sample per column, written
 * straight into raw chunk arrays, then vanilla caves, ravines, mineshafts,
 * villages, strongholds and scattered features on top.
 *
 * <p>Structure: {@link #provideChunk} builds terrain (plus registers structure
 * starts, exactly like vanilla); {@link #populate} places structures,
 * lakes, dungeons, biome decoration, animals and ice/snow. The ore generator
 * ({@link StrataOreGenerator}) runs afterwards as an {@code IWorldGenerator}.
 * All randomness that affects blocks is a pure function of
 * {@code (seed, x, z)}; the shared {@link Random} only drives vanilla
 * populate steps that vanilla itself seeds per chunk.</p>
 */
public class StrataChunkProvider implements IChunkProvider {

    private World worldObj;
    private Random rand;
    private final long worldSeed;
    private final boolean mapFeaturesEnabled;
    private final int seaLevel;
    private final ThreadLocal<StrataWorldModel> models = new ThreadLocal<StrataWorldModel>();
    private final ThreadLocal<ColumnData> columns = new ThreadLocal<ColumnData>();

    private MapGenBase caveGenerator;
    private MapGenBase ravineGenerator;
    private MapGenStructure mineshaftGenerator;
    private MapGenStructure villageGenerator;
    private MapGenStructure strongholdGenerator;
    private MapGenStructure scatteredFeatureGenerator;

    private final boolean genCaves;
    private final boolean genRavines;
    private final boolean genMineshafts;
    private final boolean genVillages;
    private final boolean genStrongholds;
    private final boolean genScattered;

    public StrataChunkProvider(World world, long seed, boolean mapFeatures) {
        this.worldObj = world;
        this.rand = new Random(seed);
        this.worldSeed = seed;
        this.mapFeaturesEnabled = mapFeatures;
        StrataWorldConfig cfg = StrataForgeConfig.snapshot();
        this.seaLevel = cfg.seaLevel;
        this.genCaves = StrataForgeConfig.enableCaves;
        this.genRavines = StrataForgeConfig.enableRavines;
        this.genMineshafts = StrataForgeConfig.enableMineshafts;
        this.genVillages = StrataForgeConfig.enableVillages;
        this.genStrongholds = StrataForgeConfig.enableStrongholds;
        this.genScattered = StrataForgeConfig.enableScatteredFeatures;

        this.caveGenerator = TerrainGen.getModdedMapGen(
                new MapGenCaves(), InitMapGenEvent.EventType.CAVE);
        this.ravineGenerator = TerrainGen.getModdedMapGen(
                new MapGenRavine(), InitMapGenEvent.EventType.RAVINE);
        Map<String, String> villageMap = new HashMap<String, String>();
        villageMap.put("size", "0");
        villageMap.put("distance", "32");
        this.villageGenerator = (MapGenVillage) TerrainGen.getModdedMapGen(
                new MapGenVillage(villageMap), InitMapGenEvent.EventType.VILLAGE);
        this.strongholdGenerator = (MapGenStronghold) TerrainGen.getModdedMapGen(
                new MapGenStronghold(), InitMapGenEvent.EventType.STRONGHOLD);
        this.mineshaftGenerator = (MapGenMineshaft) TerrainGen.getModdedMapGen(
                new MapGenMineshaft(), InitMapGenEvent.EventType.MINESHAFT);
        this.scatteredFeatureGenerator = (MapGenScatteredFeature) TerrainGen.getModdedMapGen(
                new MapGenScatteredFeature(), InitMapGenEvent.EventType.SCATTERED_FEATURE);
    }

    /** Engine model for the calling thread (shared with the ore step). */
    StrataWorldModel modelForThread() {
        StrataWorldModel m = models.get();
        if (m == null) {
            m = new StrataWorldModel(worldSeed, StrataForgeConfig.snapshot());
            models.set(m);
        }
        return m;
    }

    private ColumnData scratch() {
        ColumnData c = columns.get();
        if (c == null) {
            c = new ColumnData();
            columns.set(c);
        }
        return c;
    }

    @Override
    public Chunk provideChunk(int cx, int cz) {
        Block[] blocks = new Block[65536];
        byte[] meta = new byte[65536];
        StrataWorldModel model = modelForThread();
        ColumnData col = scratch();
        byte[] biomeIds = new byte[256];

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = cx * 16 + x;
                int wz = cz * 16 + z;
                model.sampleColumn(wx, wz, col);
                int top = col.finalHeight;
                int base = (x * 16 + z) * 256;

                blocks[base] = Blocks.bedrock;
                meta[base] = 0;
                long bh = SeedMixer.hashSalted(SeedMixer.SALT_BEDROCK, worldSeed, wx, wz);
                int rockFrom = 1;
                if ((bh & 1L) == 0L) {
                    blocks[base + 1] = Blocks.bedrock;
                    meta[base + 1] = 0;
                    rockFrom = 2;
                }

                int soilBottom = top - col.soilDepth;
                int sedBottom = soilBottom - col.sedimentCapForColumn;
                for (int y = rockFrom; y <= top; y++) {
                    GeoMaterial m;
                    if (y == top) {
                        m = col.surfaceTop;
                    } else if (y >= soilBottom) {
                        m = col.surfaceFiller;
                    } else if (y >= sedBottom) {
                        m = col.sedimentTopForColumn;
                    } else {
                        m = col.layers.rockAt(y);
                    }
                    StrataBlocks.put(blocks, meta, base + y, m);
                }

                int waterTop = -1;
                if (col.ocean) {
                    waterTop = seaLevel;
                } else if (col.lakeWaterTop > top) {
                    waterTop = col.lakeWaterTop;
                } else if (col.riverWaterTop > top) {
                    waterTop = col.riverWaterTop;
                }
                for (int y = top + 1; y <= waterTop; y++) {
                    blocks[base + y] = Blocks.water;
                    meta[base + y] = 0;
                }
                if (col.ocean && col.biome == BiomeKind.FROZEN_OCEAN && waterTop > top) {
                    blocks[base + waterTop] = Blocks.ice;
                    meta[base + waterTop] = 0;
                }

                biomeIds[x + z * 16] =
                        (byte) StrataBiomeMap.toVanilla(col.biome).biomeID;
            }
        }

        if (genCaves) {
            caveGenerator.func_151539_a(this, worldObj, cx, cz, blocks);
        }
        if (genRavines) {
            ravineGenerator.func_151539_a(this, worldObj, cx, cz, blocks);
        }
        if (mapFeaturesEnabled) {
            if (genMineshafts) {
                mineshaftGenerator.func_151539_a(this, worldObj, cx, cz, blocks);
            }
            if (genVillages) {
                villageGenerator.func_151539_a(this, worldObj, cx, cz, blocks);
            }
            if (genStrongholds) {
                strongholdGenerator.func_151539_a(this, worldObj, cx, cz, blocks);
            }
            if (genScattered) {
                scatteredFeatureGenerator.func_151539_a(this, worldObj, cx, cz, blocks);
            }
        }

        Chunk chunk = new Chunk(worldObj, blocks, meta, cx, cz);
        byte[] biomeArray = chunk.getBiomeArray();
        for (int i = 0; i < 256; i++) {
            biomeArray[i] = biomeIds[i];
        }
        chunk.setBiomeArray(biomeArray);
        chunk.generateSkylightMap();
        return chunk;
    }

    @Override
    public Chunk loadChunk(int x, int z) {
        return provideChunk(x, z);
    }

    @Override
    public boolean chunkExists(int x, int z) {
        return true;
    }

    @Override
    public void populate(IChunkProvider ichunkprovider, int chunkX, int chunkZ) {
        BlockFalling.fallInstantly = true;
        EventBus bus = MinecraftForge.EVENT_BUS;
        int worldX = chunkX * 16;
        int worldZ = chunkZ * 16;
        BiomeGenBase biome = worldObj.getBiomeGenForCoords(worldX + 16, worldZ + 16);
        rand.setSeed(worldObj.getSeed());
        long k = rand.nextLong() / 2L * 2L + 1L;
        long l = rand.nextLong() / 2L * 2L + 1L;
        rand.setSeed((long) chunkX * k + (long) chunkZ * l ^ worldObj.getSeed());
        boolean village = false;
        bus.post(new PopulateChunkEvent.Pre(
                ichunkprovider, worldObj, rand, chunkX, chunkZ, village));

        if (mapFeaturesEnabled) {
            if (genMineshafts) {
                mineshaftGenerator.generateStructuresInChunk(worldObj, rand, chunkX, chunkZ);
            }
            if (genVillages) {
                village = villageGenerator.generateStructuresInChunk(
                        worldObj, rand, chunkX, chunkZ);
            }
            if (genStrongholds) {
                strongholdGenerator.generateStructuresInChunk(worldObj, rand, chunkX, chunkZ);
            }
            if (genScattered) {
                scatteredFeatureGenerator.generateStructuresInChunk(
                        worldObj, rand, chunkX, chunkZ);
            }
        }

        int x1;
        int y1;
        int z1;
        if (!village && StrataForgeConfig.enableWaterLakes
                && biome != BiomeGenBase.desert && biome != BiomeGenBase.desertHills
                && rand.nextInt(4) == 0
                && TerrainGen.populate(this, worldObj, rand, chunkX, chunkZ,
                        village, PopulateChunkEvent.Populate.EventType.LAKE)) {
            x1 = worldX + rand.nextInt(16) + 8;
            y1 = rand.nextInt(256);
            z1 = worldZ + rand.nextInt(16) + 8;
            new WorldGenLakes(Blocks.water).generate(worldObj, rand, x1, y1, z1);
        }
        if (!village && StrataForgeConfig.enableLavaLakes && rand.nextInt(80) == 0
                && TerrainGen.populate(this, worldObj, rand, chunkX, chunkZ,
                        village, PopulateChunkEvent.Populate.EventType.LAVA)) {
            x1 = worldX + rand.nextInt(16) + 8;
            y1 = rand.nextInt(rand.nextInt(248) + 8);
            z1 = worldZ + rand.nextInt(16) + 8;
            new WorldGenLakes(Blocks.lava).generate(worldObj, rand, x1, y1, z1);
        }
        if (StrataForgeConfig.enableDungeons
                && TerrainGen.populate(this, worldObj, rand, chunkX, chunkZ,
                        village, PopulateChunkEvent.Populate.EventType.DUNGEON)) {
            for (int i = 0; i < 8; i++) {
                x1 = worldX + rand.nextInt(16) + 8;
                y1 = rand.nextInt(256);
                z1 = worldZ + rand.nextInt(16) + 8;
                new WorldGenDungeons().generate(worldObj, rand, x1, y1, z1);
            }
        }

        biome.decorate(worldObj, rand, worldX, worldZ);

        if (TerrainGen.populate(this, worldObj, rand, chunkX, chunkZ,
                village, PopulateChunkEvent.Populate.EventType.ANIMALS)) {
            SpawnerAnimals.performWorldGenSpawning(
                    worldObj, biome, worldX + 8, worldZ + 8, 16, 16, rand);
        }
        if (TerrainGen.populate(this, worldObj, rand, chunkX, chunkZ,
                village, PopulateChunkEvent.Populate.EventType.ICE)) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int px = worldX + x;
                    int pz = worldZ + z;
                    int py = worldObj.getPrecipitationHeight(px, pz);
                    if (worldObj.isBlockFreezable(px, py - 1, pz)) {
                        worldObj.setBlock(px, py - 1, pz, Blocks.ice, 0, 2);
                    }
                    if (StrataForgeConfig.enableSnowLayers
                            && worldObj.func_147478_e(px, py, pz, true)) {
                        worldObj.setBlock(px, py, pz, Blocks.snow_layer, 0, 2);
                    }
                }
            }
        }

        bus.post(new PopulateChunkEvent.Post(
                ichunkprovider, worldObj, rand, chunkX, chunkZ, village));
        BlockFalling.fallInstantly = false;
    }

    @Override
    public boolean saveChunks(boolean flag, IProgressUpdate progress) {
        return true;
    }

    @Override
    public boolean unloadQueuedChunks() {
        return false;
    }

    @Override
    public boolean canSave() {
        return true;
    }

    @Override
    public String makeString() {
        return "StrataLevelSource";
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List getPossibleCreatures(EnumCreatureType type, int x, int y, int z) {
        BiomeGenBase biome = worldObj.getBiomeGenForCoords(x, z);
        return biome == null ? null : biome.getSpawnableList(type);
    }

    @Override
    public ChunkPosition func_147416_a(World world, String name, int x, int y, int z) {
        if ("Stronghold".equals(name) && genStrongholds && strongholdGenerator != null) {
            return strongholdGenerator.func_151545_a(world, x, y, z);
        }
        return null;
    }

    @Override
    public int getLoadedChunkCount() {
        return 0;
    }

    @Override
    public void recreateStructures(int x, int z) {
        if (!mapFeaturesEnabled) {
            return;
        }
        if (genMineshafts) {
            mineshaftGenerator.func_151539_a(this, worldObj, x, z, (Block[]) null);
        }
        if (genVillages) {
            villageGenerator.func_151539_a(this, worldObj, x, z, (Block[]) null);
        }
        if (genStrongholds) {
            strongholdGenerator.func_151539_a(this, worldObj, x, z, (Block[]) null);
        }
        if (genScattered) {
            scatteredFeatureGenerator.func_151539_a(this, worldObj, x, z, (Block[]) null);
        }
    }

    @Override
    public void saveExtraData() {
    }
}
