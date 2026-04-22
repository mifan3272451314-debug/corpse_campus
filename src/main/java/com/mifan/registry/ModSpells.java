package com.mifan.registry;

import com.mifan.corpsecampus;
import com.mifan.spell.dongyue.DaiyueSpell;
import com.mifan.spell.dongyue.ExecutionerSpell;
import com.mifan.spell.dongyue.ImpermanenceMonkSpell;
import com.mifan.spell.dongyue.InstinctSpell;
import com.mifan.spell.dongyue.ManiaSpell;
import com.mifan.spell.dongyue.NecroticRebirthSpell;
import com.mifan.spell.rizhao.AffinitySpell;
import com.mifan.spell.rizhao.MidasTouchSpell;
import com.mifan.spell.rizhao.NingheSpell;
import com.mifan.spell.rizhao.SunlightSpell;
import com.mifan.spell.shengqi.ApothecarySpell;
import com.mifan.spell.shengqi.FerrymanSpell;
import com.mifan.spell.shengqi.GrafterSpell;
import com.mifan.spell.shengqi.HealingSpell;
import com.mifan.spell.shengqi.HuihunSpell;
import com.mifan.spell.shengqi.StaminaSpell;
import com.mifan.spell.xujing.ElementalistSpell;
import com.mifan.spell.xujing.MarkSpell;
import com.mifan.spell.xujing.RecorderOfficerSpell;
import com.mifan.spell.xujing.DangerSenseSpell;
import com.mifan.spell.xujing.OlfactionSpell;
import com.mifan.spell.xujing.SonicSenseSpell;
import com.mifan.spell.yuzhe.DominanceSpell;
import com.mifan.spell.yuzhe.LifeThiefSpell;
import com.mifan.spell.yuzhe.MagneticClingSpell;
import com.mifan.spell.yuzhe.MimicSpell;
import com.mifan.spell.yuzhe.TelekinesisSpell;
import com.mifan.spell.yuzhe.WanxiangSpell;
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
        public static final RegistryObject<AbstractSpell> OLFACTION = SPELLS.register("olfaction",
                        OlfactionSpell::new);
        public static final RegistryObject<AbstractSpell> ELEMENTALIST = SPELLS.register("elementalist",
                        ElementalistSpell::new);
        public static final RegistryObject<AbstractSpell> RECORDER_OFFICER = SPELLS.register("recorder_officer",
                        RecorderOfficerSpell::new);
        public static final RegistryObject<AbstractSpell> MIDAS_TOUCH = SPELLS.register("midas_touch",
                        MidasTouchSpell::new);
        public static final RegistryObject<AbstractSpell> AFFINITY = SPELLS.register("affinity",
                        AffinitySpell::new);
        public static final RegistryObject<AbstractSpell> NINGHE = SPELLS.register("ninghe",
                        NingheSpell::new);
        public static final RegistryObject<AbstractSpell> SUNLIGHT = SPELLS.register("sunlight",
                        SunlightSpell::new);
        public static final RegistryObject<AbstractSpell> TELEKINESIS = SPELLS.register("telekinesis",
                        TelekinesisSpell::new);
        public static final RegistryObject<AbstractSpell> DOMINANCE = SPELLS.register("dominance", DominanceSpell::new);
        public static final RegistryObject<AbstractSpell> MAGNETIC_CLING = SPELLS.register("magnetic_cling",
                        MagneticClingSpell::new);
        public static final RegistryObject<AbstractSpell> LIFE_THIEF = SPELLS.register("life_thief",
                        LifeThiefSpell::new);
        public static final RegistryObject<AbstractSpell> WANXIANG = SPELLS.register("wanxiang",
                        WanxiangSpell::new);
        public static final RegistryObject<AbstractSpell> DAIYUE = SPELLS.register("daiyue", DaiyueSpell::new);
        public static final RegistryObject<AbstractSpell> INSTINCT = SPELLS.register("instinct", InstinctSpell::new);
        public static final RegistryObject<AbstractSpell> MANIA = SPELLS.register("mania", ManiaSpell::new);
        public static final RegistryObject<AbstractSpell> NECROTIC_REBIRTH = SPELLS.register("necrotic_rebirth",
                        NecroticRebirthSpell::new);
        public static final RegistryObject<AbstractSpell> EXECUTIONER = SPELLS.register("executioner",
                        ExecutionerSpell::new);
        public static final RegistryObject<AbstractSpell> MARK = SPELLS.register("mark", MarkSpell::new);
        public static final RegistryObject<AbstractSpell> HEALING = SPELLS.register("healing", HealingSpell::new);
        public static final RegistryObject<AbstractSpell> STAMINA = SPELLS.register("stamina", StaminaSpell::new);
        public static final RegistryObject<AbstractSpell> HUIHUN = SPELLS.register("huihun", HuihunSpell::new);
        public static final RegistryObject<AbstractSpell> APOTHECARY = SPELLS.register("apothecary",
                        ApothecarySpell::new);
        public static final RegistryObject<AbstractSpell> MIMIC = SPELLS.register("mimic",
                        MimicSpell::new);
        public static final RegistryObject<AbstractSpell> IMPERMANENCE_MONK = SPELLS.register("impermanence_monk",
                        ImpermanenceMonkSpell::new);
        public static final RegistryObject<AbstractSpell> GRAFTER = SPELLS.register("grafter",
                        GrafterSpell::new);
        public static final RegistryObject<AbstractSpell> FERRYMAN = SPELLS.register("ferryman",
                        FerrymanSpell::new);

        private ModSpells() {
        }

        public static void register(IEventBus eventBus) {
                SPELLS.register(eventBus);
        }
}
