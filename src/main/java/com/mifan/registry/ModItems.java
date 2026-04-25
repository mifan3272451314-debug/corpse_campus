package com.mifan.registry;

import com.mifan.anomaly.AnomalySpellRank;
import com.mifan.anomaly.AnomalySpellBookItem;
import com.mifan.corpsecampus;
import com.mifan.item.AbilityClearItem;
import com.mifan.item.AnomalyDetectorItem;
import com.mifan.item.AnomalyTraitItem;
import com.mifan.item.DesignatedAbilityItem;
import com.mifan.item.EvolutionCoreItem;
import com.mifan.item.LootRefreshItem;
import com.mifan.item.RankBlessingItem;
import com.mifan.item.SlateItem;
import com.mifan.item.SpellScrollItem;
import com.mifan.item.SpellEmbryoItem;
import com.mifan.item.SunBowItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Locale;

public final class ModItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS,
            corpsecampus.MODID);

    public static final RegistryObject<Item> ANOMALY_TRAIT_SPELLBOOK = ITEMS.register("anomaly_trait_spellbook",
            AnomalySpellBookItem::new);
    public static final RegistryObject<Item> ANOMALY_DETECTOR = ITEMS.register("anomaly_detector",
            AnomalyDetectorItem::new);
    public static final RegistryObject<Item> TRAIT_XUJING_B = registerTraitItem("trait_xujing_b",
            ModSchools.XUJING_RESOURCE, AnomalySpellRank.B);
    public static final RegistryObject<Item> TRAIT_XUJING_A = registerTraitItem("trait_xujing_a",
            ModSchools.XUJING_RESOURCE, AnomalySpellRank.A);
    public static final RegistryObject<Item> TRAIT_XUJING_S = registerTraitItem("trait_xujing_s",
            ModSchools.XUJING_RESOURCE, AnomalySpellRank.S);
    public static final RegistryObject<Item> TRAIT_RIZHAO_B = registerTraitItem("trait_rizhao_b",
            ModSchools.RIZHAO_RESOURCE, AnomalySpellRank.B);
    public static final RegistryObject<Item> TRAIT_RIZHAO_A = registerTraitItem("trait_rizhao_a",
            ModSchools.RIZHAO_RESOURCE, AnomalySpellRank.A);
    public static final RegistryObject<Item> TRAIT_RIZHAO_S = registerTraitItem("trait_rizhao_s",
            ModSchools.RIZHAO_RESOURCE, AnomalySpellRank.S);
    public static final RegistryObject<Item> TRAIT_DONGYUE_B = registerTraitItem("trait_dongyue_b",
            ModSchools.DONGYUE_RESOURCE, AnomalySpellRank.B);
    public static final RegistryObject<Item> TRAIT_DONGYUE_A = registerTraitItem("trait_dongyue_a",
            ModSchools.DONGYUE_RESOURCE, AnomalySpellRank.A);
    public static final RegistryObject<Item> TRAIT_DONGYUE_S = registerTraitItem("trait_dongyue_s",
            ModSchools.DONGYUE_RESOURCE, AnomalySpellRank.S);
    public static final RegistryObject<Item> TRAIT_YUZHE_B = registerTraitItem("trait_yuzhe_b",
            ModSchools.YUZHE_RESOURCE, AnomalySpellRank.B);
    public static final RegistryObject<Item> TRAIT_YUZHE_A = registerTraitItem("trait_yuzhe_a",
            ModSchools.YUZHE_RESOURCE, AnomalySpellRank.A);
    public static final RegistryObject<Item> TRAIT_YUZHE_S = registerTraitItem("trait_yuzhe_s",
            ModSchools.YUZHE_RESOURCE, AnomalySpellRank.S);
    public static final RegistryObject<Item> TRAIT_SHENGQI_B = registerTraitItem("trait_shengqi_b",
            ModSchools.SHENGQI_RESOURCE, AnomalySpellRank.B);
    public static final RegistryObject<Item> TRAIT_SHENGQI_A = registerTraitItem("trait_shengqi_a",
            ModSchools.SHENGQI_RESOURCE, AnomalySpellRank.A);
    public static final RegistryObject<Item> TRAIT_SHENGQI_S = registerTraitItem("trait_shengqi_s",
            ModSchools.SHENGQI_RESOURCE, AnomalySpellRank.S);

    // ─── 管理员调试核心物品（创造标签页可见，无 OP 权限校验） ─────────────────────────
    public static final RegistryObject<Item> RANK_CORE_B = ITEMS.register("rank_core_b",
            () -> new RankBlessingItem(AnomalySpellRank.B));
    public static final RegistryObject<Item> RANK_CORE_A = ITEMS.register("rank_core_a",
            () -> new RankBlessingItem(AnomalySpellRank.A));
    public static final RegistryObject<Item> RANK_CORE_S = ITEMS.register("rank_core_s",
            () -> new RankBlessingItem(AnomalySpellRank.S));
    public static final RegistryObject<Item> ABILITY_CLEAR_CORE = ITEMS.register("ability_clear_core",
            AbilityClearItem::new);
    public static final RegistryObject<Item> DESIGNATED_ABILITY = ITEMS.register("designated_ability",
            DesignatedAbilityItem::new);
    public static final RegistryObject<Item> SPELL_SCROLL = ITEMS.register("spell_scroll",
            SpellScrollItem::new);
    public static final RegistryObject<Item> SPELL_EMBRYO = ITEMS.register("spell_embryo",
            SpellEmbryoItem::new);
    public static final RegistryObject<Item> EVOLUTION_CORE = ITEMS.register("evolution_core",
            EvolutionCoreItem::new);
    public static final RegistryObject<Item> LOOT_REFRESH_CORE = ITEMS.register("loot_refresh_core",
            LootRefreshItem::new);
    public static final RegistryObject<Item> SLATE = ITEMS.register("slate",
            SlateItem::new);
    public static final RegistryObject<Item> SUN_BOW = ITEMS.register("ri_lun_jin_wu_bow",
            SunBowItem::new);

    private ModItems() {
    }

    private static RegistryObject<Item> registerTraitItem(String id, ResourceLocation schoolId,
            AnomalySpellRank rank) {
        return ITEMS.register(id, () -> new AnomalyTraitItem(schoolId, rank));
    }

    public static Item getTraitItem(ResourceLocation schoolId, AnomalySpellRank rank) {
        String path = schoolId.getPath().toLowerCase(Locale.ROOT);
        return switch (path) {
            case "xujing" -> switch (rank) {
                case B -> TRAIT_XUJING_B.get();
                case A -> TRAIT_XUJING_A.get();
                case S -> TRAIT_XUJING_S.get();
            };
            case "rizhao" -> switch (rank) {
                case B -> TRAIT_RIZHAO_B.get();
                case A -> TRAIT_RIZHAO_A.get();
                case S -> TRAIT_RIZHAO_S.get();
            };
            case "dongyue" -> switch (rank) {
                case B -> TRAIT_DONGYUE_B.get();
                case A -> TRAIT_DONGYUE_A.get();
                case S -> TRAIT_DONGYUE_S.get();
            };
            case "yuzhe" -> switch (rank) {
                case B -> TRAIT_YUZHE_B.get();
                case A -> TRAIT_YUZHE_A.get();
                case S -> TRAIT_YUZHE_S.get();
            };
            case "shengqi" -> switch (rank) {
                case B -> TRAIT_SHENGQI_B.get();
                case A -> TRAIT_SHENGQI_A.get();
                case S -> TRAIT_SHENGQI_S.get();
            };
            default -> throw new IllegalArgumentException("Unsupported anomaly school: " + schoolId);
        };
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

    public static boolean isBRankTraitItem(Item item) {
        return item == TRAIT_XUJING_B.get()
                || item == TRAIT_RIZHAO_B.get()
                || item == TRAIT_DONGYUE_B.get()
                || item == TRAIT_YUZHE_B.get()
                || item == TRAIT_SHENGQI_B.get();
    }
}
