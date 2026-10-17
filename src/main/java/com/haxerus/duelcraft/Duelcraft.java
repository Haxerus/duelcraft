package com.haxerus.duelcraft;

import com.haxerus.duelcraft.core.data.CardData;
import com.haxerus.duelcraft.collection.CollectionAttachments;
import com.haxerus.duelcraft.item.CardComponents;
import com.haxerus.duelcraft.item.CardItem;
import com.haxerus.duelcraft.server.DuelCommand;
import com.haxerus.duelcraft.server.DuelManager;
import com.haxerus.duelcraft.server.DuelNetworking;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Duelcraft.MODID)
public class Duelcraft {
    public static final String MODID = "duelcraft";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredItem<CardItem> CARD = ITEMS.registerItem("card", CardItem::new);
    public static final DeferredItem<com.haxerus.duelcraft.item.CardBinderItem> CARD_BINDER =
            ITEMS.registerItem("card_binder", com.haxerus.duelcraft.item.CardBinderItem::new);
    public static final DeferredBlock<com.haxerus.duelcraft.block.DuelMatBlock> DUEL_MAT = BLOCKS.register("duel_mat",
            () -> new com.haxerus.duelcraft.block.DuelMatBlock(BlockBehaviour.Properties.of().strength(0.1f).mapColor(MapColor.COLOR_GREEN)));
    public static final DeferredItem<BlockItem> DUEL_MAT_ITEM = ITEMS.registerSimpleBlockItem("duel_mat", DUEL_MAT);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DUELCRAFT_TAB = CREATIVE_MODE_TABS.register("duelcraft", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.duelcraft"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> CARD_BINDER.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(CARD_BINDER.get());
                output.accept(DUEL_MAT_ITEM.get());
            }).build());

    public Duelcraft(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(DuelNetworking::onRegisterPayloads);
        registerServerLifecycle(modEventBus, NeoForge.EVENT_BUS);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        CollectionAttachments.TYPES.register(modEventBus);
        CardComponents.TYPES.register(modEventBus);

        NeoForge.EVENT_BUS.addListener(DuelCommand::register);
        NeoForge.EVENT_BUS.addListener(com.haxerus.duelcraft.server.CardGrantCommand::onRegister);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
    }

    static void registerServerLifecycle(IEventBus modEventBus, IEventBus serverEventBus) {
        modEventBus.addListener(ServerConfig::onLoading);
        modEventBus.addListener(ServerConfig::onUnloading);
        serverEventBus.addListener(DuelManager::onServerStarting);
        serverEventBus.addListener(DuelManager::onServerStopped);
        serverEventBus.addListener(DuelManager::onServerTick);
        serverEventBus.addListener(DuelManager::onPlayerLoggedOut);
        serverEventBus.addListener(DuelManager::onPlayerLoggedIn);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        CardData.load();
    }
}
