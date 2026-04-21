package com.mifan.registry;

import com.mifan.anomaly.AnomalySpellRank;
import com.mifan.anomaly.AnomalySpellBookItem;
import com.mifan.corpsecampus;
import com.mifan.item.AnomalyDetectorItem;
import com.mifan.item.AnomalyTraitItem;
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
    public static final RegistryObject<Item> TRAIT_XUJING_B = registerTraitItem("trait_xujing_b");
    public static final RegistryObject<Item> TRAIT_XUJING_A = registerTraitItem("trait_xujing_a");
    public static final RegistryObject<Item> TRAIT_XUJING_S = registerTraitItem("trait_xujing_s");
    public static final RegistryObject<Item> TRAIT_RIZHAO_B = registerTraitItem("trait_rizhao_b");
    public static final RegistryObject<Item> TRAIT_RIZHAO_A = registerTraitItem("trait_rizhao_a");
    public static final RegistryObject<Item> TRAIT_RIZHAO_S = registerTraitItem("trait_rizhao_s");
    public static final RegistryObject<Item> TRAIT_DONGYUE_B = registerTraitItem("trait_dongyue_b");
    public static final RegistryObject<Item> TRAIT_DONGYUE_A = registerTraitItem("trait_dongyue_a");
    public static final RegistryObject<Item> TRAIT_DONGYUE_S = registerTraitItem("trait_dongyue_s");
    public static final RegistryObject<Item> TRAIT_YUZHE_B = registerTraitItem("trait_yuzhe_b");
    public static final RegistryObject<Item> TRAIT_YUZHE_A = registerTraitItem("trait_yuzhe_a");
    public static final RegistryObject<Item> TRAIT_YUZHE_S = registerTraitItem("trait_yuzhe_s");
    public static final RegistryObject<Item> TRAIT_SHENGQI_B = registerTraitItem("trait_shengqi_b");
    public static final RegistryObject<Item> TRAIT_SHENGQI_A = registerTraitItem("trait_shengqi_a");
    public static final RegistryObject<Item> TRAIT_SHENGQI_S = registerTraitItem("trait_shengqi_s");

    private ModItems() {
    }

    private static RegistryObject<Item> registerTraitItem(String id) {
        return ITEMS.register(id, AnomalyTraitItem::new);
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
}
