package com.mifan.registry;

import com.mifan.corpsecampus;
import com.mifan.spell.dongyue.ExecutionerSpell;
import com.mifan.spell.dongyue.InstinctSpell;
import com.mifan.spell.dongyue.ManiaSpell;
import com.mifan.spell.xujing.DangerSenseSpell;
import com.mifan.spell.xujing.SonicSenseSpell;
import com.mifan.spell.yuzhe.DominanceSpell;
import com.mifan.spell.yuzhe.MagneticClingSpell;
import com.mifan.spell.yuzhe.TelekinesisSpell;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModSpells {
    private static final DeferredRegister<AbstractSpell> SPELLS = DeferredRegister.create(
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spells"),
            corpsecampus.MODID);

    public static final RegistryObject<AbstractSpell> SONIC_SENSE = SPELLS.register("sonic_sense",
            SonicSenseSpell::new);
    public static final RegistryObject<AbstractSpell> DANGER_SENSE = SPELLS.register("danger_sense",
            DangerSenseSpell::new);
    public static final RegistryObject<AbstractSpell> TELEKINESIS = SPELLS.register("telekinesis",
            TelekinesisSpell::new);
    public static final RegistryObject<AbstractSpell> DOMINANCE = SPELLS.register("dominance", DominanceSpell::new);
    public static final RegistryObject<AbstractSpell> MAGNETIC_CLING = SPELLS.register("magnetic_cling",
            MagneticClingSpell::new);
    public static final RegistryObject<AbstractSpell> INSTINCT = SPELLS.register("instinct", InstinctSpell::new);
    public static final RegistryObject<AbstractSpell> MANIA = SPELLS.register("mania", ManiaSpell::new);
    public static final RegistryObject<AbstractSpell> EXECUTIONER = SPELLS.register("executioner",
            ExecutionerSpell::new);

    private ModSpells() {
    }

    public static void register(IEventBus eventBus) {
        SPELLS.register(eventBus);
    }
}
