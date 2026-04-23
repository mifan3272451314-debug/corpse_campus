package com.mifan;

import com.mifan.registry.ModAttributes;
import com.mifan.network.ModNetwork;
import com.mifan.registry.ModEntities;
import com.mifan.registry.ModMobEffects;
import com.mifan.registry.ModItems;
import com.mifan.registry.ModSchools;
import com.mifan.registry.ModSpells;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(corpsecampus.MODID)
public class corpsecampus {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "corpse_campus";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under
    // the "corpse_campus" namespace
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    // Create a Deferred Register to hold Items which will all be registered under
    // the "corpse_campus" namespace
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be
    // registered under the "corpse_campus" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister
            .create(Registries.CREATIVE_MODE_TAB, MODID);

    // Creates a new Block with the id "corpse_campus:example_block", combining the
    // namespace and path
    public static final RegistryObject<Block> EXAMPLE_BLOCK = BLOCKS.register("example_block",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)));
    // Creates a new BlockItem with the id "corpse_campus:example_block", combining
    // the namespace and path
    public static final RegistryObject<Item> EXAMPLE_BLOCK_ITEM = ITEMS.register("example_block",
            () -> new BlockItem(EXAMPLE_BLOCK.get(), new Item.Properties()));

    // Creates a new food item with the id "corpse_campus:example_id", nutrition 1
    // and saturation 2
    public static final RegistryObject<Item> EXAMPLE_ITEM = ITEMS.register("example_item",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .alwaysEat().nutrition(1).saturationMod(2f).build())));

    // Creates a creative tab with the id "corpse_campus:example_tab" for the
    // example item, that is placed after the combat tab
    public static final RegistryObject<CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab",
            () -> CreativeModeTab.builder()
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .title(net.minecraft.network.chat.Component.translatable("itemGroup.corpse_campus.main"))
                    .icon(() -> ModItems.ANOMALY_TRAIT_SPELLBOOK.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(EXAMPLE_BLOCK_ITEM.get());
                        output.accept(EXAMPLE_ITEM.get());
                        output.accept(ModItems.ANOMALY_TRAIT_SPELLBOOK.get());
                        output.accept(ModItems.ANOMALY_DETECTOR.get());
                        output.accept(ModItems.TRAIT_XUJING_B.get());
                        output.accept(ModItems.TRAIT_XUJING_A.get());
                        output.accept(ModItems.TRAIT_XUJING_S.get());
                        output.accept(ModItems.TRAIT_RIZHAO_B.get());
                        output.accept(ModItems.TRAIT_RIZHAO_A.get());
                        output.accept(ModItems.TRAIT_RIZHAO_S.get());
                        output.accept(ModItems.TRAIT_DONGYUE_B.get());
                        output.accept(ModItems.TRAIT_DONGYUE_A.get());
                        output.accept(ModItems.TRAIT_DONGYUE_S.get());
                        output.accept(ModItems.TRAIT_YUZHE_B.get());
                        output.accept(ModItems.TRAIT_YUZHE_A.get());
                        output.accept(ModItems.TRAIT_YUZHE_S.get());
                        output.accept(ModItems.TRAIT_SHENGQI_B.get());
                        output.accept(ModItems.TRAIT_SHENGQI_A.get());
                        output.accept(ModItems.TRAIT_SHENGQI_S.get());
                        // 异常法术卷轴：一个 Item + NBT 区分 35 个法术，循环输出
                        for (com.mifan.anomaly.AnomalyBookService.SpellSpec spec
                                : com.mifan.anomaly.AnomalyBookService.getAllSpellSpecs()) {
                            output.accept(com.mifan.item.SpellScrollItem.createFor(spec.spellId()));
                        }
                        // 异能胚胎：同构,35 张,无任何效果,仅用作判断与视觉
                        for (com.mifan.anomaly.AnomalyBookService.SpellSpec spec
                                : com.mifan.anomaly.AnomalyBookService.getAllSpellSpecs()) {
                            output.accept(com.mifan.item.SpellEmbryoItem.createFor(spec.spellId()));
                        }
                        // 管理员调试核心物品
                        output.accept(ModItems.DESIGNATED_ABILITY.get());
                        output.accept(ModItems.RANK_CORE_B.get());
                        output.accept(ModItems.RANK_CORE_A.get());
                        output.accept(ModItems.RANK_CORE_S.get());
                        output.accept(ModItems.ABILITY_CLEAR_CORE.get());
                        output.accept(ModItems.LOOT_REFRESH_CORE.get());
                    }).build());

    public corpsecampus() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);
        ModAttributes.register(modEventBus);
        ModItems.register(modEventBus);
        // Register custom spell schools for Iron's Spells 'n Spellbooks
        ModSchools.register(modEventBus);
        // Register custom marker effects for toggle abilities
        ModMobEffects.register(modEventBus);
        // Register custom spells for Iron's Spells 'n Spellbooks
        ModSpells.register(modEventBus);
        // Register custom entity types
        ModEntities.register(modEventBus);
        // Register custom network packets
        ModNetwork.register();

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the
        // config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        // Anomaly system config (corpse_campus-anomaly.toml)
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, com.mifan.anomaly.AnomalyConfig.SPEC,
                "corpse_campus-anomaly.toml");
        // Spell screen-effect client config (corpse_campus-screen_effect.toml)
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT,
                com.mifan.screeneffect.config.ScreenEffectConfig.SPEC,
                "corpse_campus-screen_effect.toml");
        com.mifan.screeneffect.registry.ModScreenEffects.bootstrap();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");
        LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));

        if (Config.logDirtBlock)
            LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));

        LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);

        Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS)
            event.accept(EXAMPLE_BLOCK_ITEM);
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    // You can use EventBusSubscriber to automatically register all static methods
    // in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());

            event.enqueueWork(() -> {
                // 异能法术卷轴：按 NBT 里存的 spellId → 所属流派，给出 0-4 浮点索引，
                // spell_scroll.json 用 overrides 映射到 5 张 scroll_<school>.png
                net.minecraft.client.renderer.item.ItemProperties.register(
                        ModItems.SPELL_SCROLL.get(),
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "school"),
                        (stack, level, entity, seed) -> {
                            net.minecraft.resources.ResourceLocation spellId =
                                    com.mifan.item.SpellScrollItem.getSpellId(stack);
                            if (spellId == null) {
                                return 0.0F;
                            }
                            com.mifan.anomaly.AnomalyBookService.SpellSpec spec =
                                    com.mifan.anomaly.AnomalyBookService.getSpellSpec(spellId);
                            if (spec == null) {
                                return 0.0F;
                            }
                            return switch (spec.schoolId().getPath()) {
                                case "xujing" -> 0.0F;
                                case "rizhao" -> 1.0F;
                                case "dongyue" -> 2.0F;
                                case "yuzhe" -> 3.0F;
                                case "shengqi" -> 4.0F;
                                default -> 0.0F;
                            };
                        });

                // 异能胚胎：同卷轴的 school 索引机制,复用到 spell_embryo.json 的 overrides
                net.minecraft.client.renderer.item.ItemProperties.register(
                        ModItems.SPELL_EMBRYO.get(),
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "school"),
                        (stack, level, entity, seed) -> {
                            net.minecraft.resources.ResourceLocation spellId =
                                    com.mifan.item.SpellEmbryoItem.getSpellId(stack);
                            if (spellId == null) {
                                return 0.0F;
                            }
                            com.mifan.anomaly.AnomalyBookService.SpellSpec spec =
                                    com.mifan.anomaly.AnomalyBookService.getSpellSpec(spellId);
                            if (spec == null) {
                                return 0.0F;
                            }
                            return switch (spec.schoolId().getPath()) {
                                case "xujing" -> 0.0F;
                                case "rizhao" -> 1.0F;
                                case "dongyue" -> 2.0F;
                                case "yuzhe" -> 3.0F;
                                case "shengqi" -> 4.0F;
                                default -> 0.0F;
                            };
                        });
            });
        }

        @SubscribeEvent
        public static void onRegisterRenderers(
                net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.GOLDEN_CROW_SUN.get(),
                    com.mifan.client.renderer.GoldenCrowSunRenderer::new);
            event.registerEntityRenderer(ModEntities.SPIRIT_WORM.get(),
                    com.mifan.client.renderer.SpiritWormRenderer::new);
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class CommonModEvents {
        @SubscribeEvent
        public static void onEntityAttributeCreation(
                net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
            event.put(ModEntities.SPIRIT_WORM.get(),
                    com.mifan.entity.SpiritWormEntity.createAttributes().build());
        }
    }
}
