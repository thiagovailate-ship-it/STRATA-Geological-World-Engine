package dev.strata.forge;

import net.minecraft.world.WorldType;
import net.minecraftforge.common.config.Configuration;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import dev.strata.blocks.StrataBlocks;

/**
 * STRATA — Geological World Engine for Minecraft 1.7.10.
 *
 * <p>Registers the "STRATA" world type (plate tectonics, drainage, erosion,
 * stratigraphy), one metadata rock block with twelve geological variants,
 * and a strata-aware ore generator. Pick "STRATA" on the world-creation
 * screen; the Nether and the End keep their vanilla generators.</p>
 */
@Mod(modid = StrataMod.MODID, name = StrataMod.NAME, version = StrataMod.VERSION,
        dependencies = "required-after:Forge@[10.13.4.1614,)")
public class StrataMod {

    public static final String MODID = "strata";
    public static final String NAME = "STRATA Geological World Engine";
    public static final String VERSION = "1.0.0";

    /** Instantiating the WorldType registers it in the creation GUI. */
    public static final WorldType STRATA_WORLD_TYPE = new StrataWorldType();

    @Instance(MODID)
    public static StrataMod instance;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        StrataForgeConfig.load(new Configuration(event.getSuggestedConfigurationFile()));
        StrataBlocks.init();
        GameRegistry.registerWorldGenerator(new StrataOreGenerator(), 0);
    }
}
