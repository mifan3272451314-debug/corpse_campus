package com.mifan.registry;

import com.mifan.anomaly.AnomalySpellBookItem;
import com.mifan.corpsecampus;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS,
            corpsecampus.MODID);

    public static final RegistryObject<Item> ANOMALY_TRAIT_SPELLBOOK = ITEMS.register("anomaly_trait_spellbook",
            AnomalySpellBookItem::new);

    private ModItems() {
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
