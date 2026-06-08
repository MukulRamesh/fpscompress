package com.mukulramesh.fpscompress;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.mukulramesh.fpscompress.component.FPSDataComponents;
import com.mukulramesh.fpscompress.debug.Dev2TestCommands;
import com.mukulramesh.fpscompress.portal.DimensionTeleportListener;
import com.mukulramesh.fpscompress.portal.FPSDataAttachments;
import com.mukulramesh.fpscompress.portal.ExporterBlock;
import com.mukulramesh.fpscompress.portal.ExporterBlockEntity;
import com.mukulramesh.fpscompress.portal.ImporterBlock;
import com.mukulramesh.fpscompress.portal.ImporterBlockEntity;
import com.mukulramesh.fpscompress.portal.PrefabBlock;
import com.mukulramesh.fpscompress.portal.PrefabBlockEntity;
import com.mukulramesh.fpscompress.portal.PrefabBlockItem;
import com.mukulramesh.fpscompress.portal.PrefabRoomBlockListener;
import com.mukulramesh.fpscompress.portal.PSDExitListener;
import com.mukulramesh.fpscompress.portal.SimulationWrenchItem;
import com.mukulramesh.fpscompress.portal.TpsCacheUpgradeItem;
import com.mukulramesh.fpscompress.blueprint.PreFabBlueprintItem;
import com.mukulramesh.fpscompress.blueprint.FabricatorBlock;
import com.mukulramesh.fpscompress.blueprint.FabricatorBlockEntity;
import com.mukulramesh.fpscompress.blueprint.NbtRequirementRegistry;

import com.mukulramesh.fpscompress.gui.FabricatorMenu;
import com.mukulramesh.fpscompress.gui.PreFabConfigMenu;
import com.mukulramesh.fpscompress.network.FaceConfigPacket;
import com.mukulramesh.fpscompress.network.PrintRequestPacket;
import com.mukulramesh.fpscompress.network.ScanRequestPacket;
import com.mukulramesh.fpscompress.network.SimulationControlPacket;
import com.mukulramesh.fpscompress.datagen.ModRecipeProvider;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.concurrent.CompletableFuture;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
    // Create a Deferred Register to hold BlockEntityTypes
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MODID);
    // Create a Deferred Register to hold MenuTypes
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(BuiltInRegistries.MENU, MODID);

    // ===== Blocks =====

    /**
     * PreFab Machine block - An upgraded Compact Machine that stores factory state.
     */
    public static final DeferredBlock<PrefabBlock> PREFAB_BLOCK =
        BLOCKS.register("prefab_machine", PrefabBlock::new);

    /**
     * Importer Block - CM dimension input gate for PreFab resource transport.
     */
    public static final DeferredBlock<ImporterBlock> IMPORTER_BLOCK =
        BLOCKS.register("importer", ImporterBlock::new);

    /**
     * Exporter Block - CM dimension output gate for PreFab resource transport.
     */
    public static final DeferredBlock<ExporterBlock> EXPORTER_BLOCK =
        BLOCKS.register("exporter", ExporterBlock::new);

    /**
     * Fabricator Block - Scans PreFabs to create Blueprints, prints PreFabs from Blueprints.
     */
    public static final DeferredBlock<FabricatorBlock> FABRICATOR_BLOCK =
        BLOCKS.register("fabricator", FabricatorBlock::new);

    /**
     * PreFab Machine BlockEntity type.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PrefabBlockEntity>> PREFAB_BE =
        BLOCK_ENTITIES.register("prefab_machine", () ->
            BlockEntityType.Builder.of(PrefabBlockEntity::new, PREFAB_BLOCK.get()).build(null));

    /**
     * Importer BlockEntity type.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ImporterBlockEntity>> IMPORTER_BE =
        BLOCK_ENTITIES.register("importer", () ->
            BlockEntityType.Builder.of(ImporterBlockEntity::new, IMPORTER_BLOCK.get()).build(null));

    /**
     * Exporter BlockEntity type.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExporterBlockEntity>> EXPORTER_BE =
        BLOCK_ENTITIES.register("exporter", () ->
            BlockEntityType.Builder.of(ExporterBlockEntity::new, EXPORTER_BLOCK.get()).build(null));

    /**
     * Fabricator BlockEntity type.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FabricatorBlockEntity>>
        FABRICATOR_BE = BLOCK_ENTITIES.register("fabricator", () ->
            BlockEntityType.Builder.of(FabricatorBlockEntity::new, FABRICATOR_BLOCK.get()).build(null));

    // ===== Menu Types =====

    /**
     * PreFab Configuration Menu - GUI for configuring face modes and filters.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<PreFabConfigMenu>> PREFAB_CONFIG_MENU =
        MENU_TYPES.register("prefab_config", () ->
            IMenuTypeExtension.create(PreFabConfigMenu::new));

    /**
     * PreFab Status Menu - GUI for displaying state and controlling simulation.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<com.mukulramesh.fpscompress.gui.PreFabStatusMenu>>
        PREFAB_STATUS_MENU = MENU_TYPES.register("prefab_status", () ->
            IMenuTypeExtension.create(com.mukulramesh.fpscompress.gui.PreFabStatusMenu::new));

    /**
     * Fabricator Menu - GUI for scanning PreFabs and printing Blueprints.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<FabricatorMenu>> FABRICATOR_MENU =
        MENU_TYPES.register("fabricator", () ->
            IMenuTypeExtension.create(FabricatorMenu::new));

    // ===== Items =====

    /**
     * PreFab Machine item (block item with tooltip support).
     * Immune to fire/lava damage to preserve factory data.
     */
    public static final DeferredItem<PrefabBlockItem> PREFAB_ITEM =
        ITEMS.register("prefab_machine", () -> new PrefabBlockItem(PREFAB_BLOCK.get(),
            new Item.Properties().fireResistant()));

    /**
     * Importer Block item.
     * Immune to fire/lava damage to preserve UUID data.
     */
    public static final DeferredItem<BlockItem> IMPORTER_ITEM =
        ITEMS.register("importer", () -> new BlockItem(IMPORTER_BLOCK.get(),
            new Item.Properties().fireResistant()));

    /**
     * Exporter Block item.
     * Immune to fire/lava damage to preserve UUID data.
     */
    public static final DeferredItem<BlockItem> EXPORTER_ITEM =
        ITEMS.register("exporter", () -> new BlockItem(EXPORTER_BLOCK.get(),
            new Item.Properties().fireResistant()));

    /**
     * PreFab Upgrade Template item - Right-click a Compact Machine to convert it to a PreFab.
     */
    public static final DeferredItem<TpsCacheUpgradeItem> TPS_CACHE_UPGRADE =
        ITEMS.register("prefab_upgrade_template", () -> new TpsCacheUpgradeItem(new Item.Properties()));

    /**
     * Simulation Wrench - Control tool for managing factory simulation states.
     */
    public static final DeferredItem<SimulationWrenchItem> SIMULATION_WRENCH =
        ITEMS.register("simulation_wrench", () -> new SimulationWrenchItem(new Item.Properties()));

    /**
     * PreFab Blueprint item - Stores scanned factory configuration.
     * Stackable to 1, immune to fire/lava to preserve blueprint data.
     */
    public static final DeferredItem<PreFabBlueprintItem> PREFAB_BLUEPRINT =
        ITEMS.register("prefab_blueprint", () -> new PreFabBlueprintItem(
            new Item.Properties().stacksTo(1).fireResistant()));

    /**
     * Fabricator Block item.
     */
    public static final DeferredItem<BlockItem> FABRICATOR_ITEM =
        ITEMS.register("fabricator", () -> new BlockItem(FABRICATOR_BLOCK.get(),
            new Item.Properties()));

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
                output.accept(PREFAB_ITEM.get());
                output.accept(IMPORTER_ITEM.get());
                output.accept(EXPORTER_ITEM.get());
                output.accept(TPS_CACHE_UPGRADE.get());
                output.accept(SIMULATION_WRENCH.get());
                output.accept(PREFAB_BLUEPRINT.get());
                output.accept(FABRICATOR_ITEM.get());
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
        // Register data attachments (stub for backwards compatibility)
        FPSDataAttachments.ATTACHMENT_TYPES.register(modEventBus);
        // Register BlockEntity types
        BLOCK_ENTITIES.register(modEventBus);
        // Register Menu types
        MENU_TYPES.register(modEventBus);
        // Register network packets
        modEventBus.addListener(this::registerPackets);
        // Register data generation
        modEventBus.addListener(this::gatherData);

        // FIXED: DimensionTeleportListener now captures exact block on click (no more 3,087 block search!)
        NeoForge.EVENT_BUS.register(new DimensionTeleportListener());

        // Register PSD exit listener for PreFab room exit
        NeoForge.EVENT_BUS.register(new PSDExitListener());

        // Register PreFab room block blacklist listener
        NeoForge.EVENT_BUS.register(new PrefabRoomBlockListener());

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (FPSCompress) to respond
        // directly to events. Do not add this line if there are no @SubscribeEvent-annotated
        // functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        // Using SERVER type to allow runtime config changes without restart
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("FPSCompress common setup complete");
    }

    private void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        // Register recipe provider
        generator.addProvider(
                event.includeServer(),
                new ModRecipeProvider(output, lookupProvider)
        );
    }

    private void registerPackets(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
            FaceConfigPacket.TYPE,
            FaceConfigPacket.STREAM_CODEC,
            FaceConfigPacket::handle
        );
        LOGGER.info("Registered network packet: FaceConfigPacket");

        registrar.playToServer(
            SimulationControlPacket.TYPE,
            SimulationControlPacket.STREAM_CODEC,
            SimulationControlPacket::handle
        );
        LOGGER.info("Registered network packet: SimulationControlPacket");

        registrar.playToClient(
            com.mukulramesh.fpscompress.network.StatusGuiSyncPacket.TYPE,
            com.mukulramesh.fpscompress.network.StatusGuiSyncPacket.STREAM_CODEC,
            com.mukulramesh.fpscompress.network.StatusGuiSyncPacket::handle
        );
        LOGGER.info("Registered network packet: StatusGuiSyncPacket");

        registrar.playToServer(
            com.mukulramesh.fpscompress.network.RateDisplayPreferencePacket.TYPE,
            com.mukulramesh.fpscompress.network.RateDisplayPreferencePacket.STREAM_CODEC,
            com.mukulramesh.fpscompress.network.RateDisplayPreferencePacket::handle
        );
        LOGGER.info("Registered network packet: RateDisplayPreferencePacket");

        registrar.playToServer(
            com.mukulramesh.fpscompress.network.PrefabNamePacket.TYPE,
            com.mukulramesh.fpscompress.network.PrefabNamePacket.STREAM_CODEC,
            com.mukulramesh.fpscompress.network.PrefabNamePacket::handle
        );
        LOGGER.info("Registered network packet: PrefabNamePacket");

        registrar.playToServer(
            ScanRequestPacket.TYPE,
            ScanRequestPacket.STREAM_CODEC,
            ScanRequestPacket::handle
        );
        LOGGER.info("Registered network packet: ScanRequestPacket");

        registrar.playToServer(
            PrintRequestPacket.TYPE,
            PrintRequestPacket.STREAM_CODEC,
            PrintRequestPacket::handle
        );
        LOGGER.info("Registered network packet: PrintRequestPacket");
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");

        // Migrate stale config files when Java defaults change
        migrateConfigIfNeeded(event);
    }

    /**
     * Check the config file version and auto-update changed defaults
     * so players don't need to manually delete stale TOML files.
     *
     * <p>NeoForge never overwrites existing TOML files, so when we change
     * a Java default (e.g., blueprintPrintTicks from 1 → 20), the old value
     * persists in the file. This method detects that and fixes it.
     */
    private void migrateConfigIfNeeded(ServerStartingEvent event) {
        int loadedVersion = Config.SERVER.getConfigVersion();
        if (loadedVersion >= Config.CURRENT_CONFIG_VERSION) {
            return; // Already up to date
        }

        LOGGER.warn("Config version is {} (expected {}) — migrating stale TOML...",
            loadedVersion, Config.CURRENT_CONFIG_VERSION);

        // Per-world config takes precedence; also fix the default template
        Path worldConfig = event.getServer().getServerDirectory()
            .resolve("serverconfig").resolve("fpscompress-server.toml");
        Path defaultConfig = Path.of("config", "fpscompress-server.toml");

        for (Path configPath : new Path[]{worldConfig, defaultConfig}) {
            try {
                if (Files.exists(configPath)) {
                    migrateConfigFile(configPath, loadedVersion);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to migrate config file: {}", configPath, e);
            }
        }
    }

    /**
     * Apply migrations to a single TOML config file.
     */
    private void migrateConfigFile(Path configPath, int fromVersion) throws IOException {
        String content = Files.readString(configPath);
        String original = content;

        // Version 0 → 1: blueprintPrintTicks default changed from 1 to 20
        if (fromVersion < 1) {
            // Only fix if it's still at the old default — don't touch user-customized values
            content = content.replaceFirst(
                "blueprintPrintTicks = 1(\\r?\\n)",
                "blueprintPrintTicks = 20$1");
            fromVersion = 1;
        }

        // Version 1 → 2: prefabConsumedOnScan default changed from true to false
        if (fromVersion < 2) {
            content = content.replaceFirst(
                "prefabConsumedOnScan = true(\\r?\\n)",
                "prefabConsumedOnScan = false$1");
            fromVersion = 2;
        }

        // Version 2 → 3: Added prefabRoomBlacklistedBlocks (new field, no value migration)
        if (fromVersion < 3) {
            fromVersion = 3;
        }

        // Version 3 → 4: prefabRoomBlacklistedBlocks default changed from [] to ["minecraft:bedrock"]
        if (fromVersion < 4) {
            content = content.replaceFirst(
                "prefabRoomBlacklistedBlocks = \\[\\](\\r?\\n)",
                "prefabRoomBlacklistedBlocks = [\"minecraft:bedrock\"]$1");
            fromVersion = 4;
        }

        // Bump the config version in the file
        if (content.contains("configVersion = ")) {
            content = content.replaceFirst(
                "configVersion = \\d+",
                "configVersion = " + Config.CURRENT_CONFIG_VERSION);
        } else {
            // Old file without version field — insert it after blueprintPrintTicks line
            content = content.replaceFirst(
                "(blueprintPrintTicks = \\d+)(\\r?\\n)",
                "$1$2\tconfigVersion = " + Config.CURRENT_CONFIG_VERSION + "$2");
        }

        if (!content.equals(original)) {
            Files.writeString(configPath, content);
            LOGGER.info("Migrated config file {} to v{}",
                configPath, Config.CURRENT_CONFIG_VERSION);
        }
    }

    // Register debug commands
    @SubscribeEvent
    public void onCommandsRegister(RegisterCommandsEvent event) {
        Dev2TestCommands.register(event.getDispatcher());
        com.mukulramesh.fpscompress.commands.RoomDebugCommands.register(event.getDispatcher());
        LOGGER.info("Registered debug commands: /fps_dev2, /fpscompress room");
    }

    // Register NBT requirement registry as reload listener
    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(NbtRequirementRegistry.getInstance());
        LOGGER.info("Registered NBT requirement registry as reload listener");
    }
}
