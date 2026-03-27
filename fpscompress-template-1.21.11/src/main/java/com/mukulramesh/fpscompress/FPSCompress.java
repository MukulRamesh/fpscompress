package com.mukulramesh.fpscompress;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.mukulramesh.fpscompress.component.FPSDataComponents;
import com.mukulramesh.fpscompress.portal.FPSDataAttachments;
import com.mukulramesh.fpscompress.portal.SimulationWrenchItem;
import com.mukulramesh.fpscompress.portal.TpsCacheUpgradeItem;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(FPSCompress.MODID)
public final class FPSCompress {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "fpscompress";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "fpscompress" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "fpscompress" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the
    // "fpscompress" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // ===== Items =====

    /**
     * TPS Cache Upgrade item - Right-click a Compact Machine to enable TPS caching.
     */
    public static final DeferredItem<TpsCacheUpgradeItem> TPS_CACHE_UPGRADE =
        ITEMS.register("tps_cache_upgrade", () -> new TpsCacheUpgradeItem(new Item.Properties()));

    /**
     * Simulation Wrench - Control tool for managing factory simulation states.
     */
    public static final DeferredItem<SimulationWrenchItem> SIMULATION_WRENCH =
        ITEMS.register("simulation_wrench", () -> new SimulationWrenchItem(new Item.Properties()));

    // ===== Creative Tab =====

    /**
     * FPSCompress creative tab containing all mod items.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FPS_COMPRESS_TAB =
        CREATIVE_MODE_TABS.register("fpscompress_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.fpscompress"))
            .withTabsBefore(CreativeModeTabs.TOOLS_AND_UTILITIES)
            .icon(() -> TPS_CACHE_UPGRADE.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(TPS_CACHE_UPGRADE.get());
                output.accept(SIMULATION_WRENCH.get());
            }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    @SuppressWarnings("this-escape") // Event bus doesn't fire until after constructor completes
    public FPSCompress(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);
        // Register data components for persistent upgrade tracking
        FPSDataComponents.DATA_COMPONENTS.register(modEventBus);
        // Register data attachments for storing VirtualMachineData
        FPSDataAttachments.ATTACHMENT_TYPES.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (FPSCompress) to respond
        // directly to events. Do not add this line if there are no @SubscribeEvent-annotated
        // functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());

        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }
}
