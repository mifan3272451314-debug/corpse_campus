package com.mifan.spell.rizhao;

import com.mifan.registry.ModSchools;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.AutoSpellConfig;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@AutoSpellConfig
public class AffinitySpell extends AbstractSpell {
    private static final List<ResourceLocation> SOLAR_SPELL_IDS = List.of(
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "sunbeam"),
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "starfall"),
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "divine_smite"));

    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "affinity");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.UNCOMMON)
            .setSchoolResource(ModSchools.RIZHAO_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(120)
            .build();

    public AffinitySpell() {
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
        this.baseManaCost = 100;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.affinity_random_scroll"),
                Component.translatable("tooltip.corpse_campus.affinity_solar_pool"),
                Component.translatable("tooltip.corpse_campus.affinity_scroll_level", spellLevel));
    }

    @Override
    public CastType getCastType() {
        return CastType.INSTANT;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return spellId;
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.empty();
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource,
            MagicData playerMagicData) {
        if (!level.isClientSide && entity instanceof Player player) {
            AbstractSpell randomSolarSpell = getRandomSolarSpell(level.getRandom());
            if (randomSolarSpell == null) {
                player.displayClientMessage(Component.translatable("message.corpse_campus.affinity_no_scroll"), true);
                super.onCast(level, spellLevel, entity, castSource, playerMagicData);
                return;
            }

            int scrollLevel = Math.max(1, Math.min(spellLevel, randomSolarSpell.getMaxLevel()));
            ItemStack scroll = new ItemStack(ItemRegistry.SCROLL.get());
            ISpellContainer.createScrollContainer(randomSolarSpell, scrollLevel, scroll);

            if (!player.getInventory().add(scroll)) {
                player.drop(scroll, false);
            }

            level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS,
                    0.45F, 1.2F);
            player.displayClientMessage(
                    Component.translatable("message.corpse_campus.affinity_created", randomSolarSpell.getSpellName()),
                    true);
        }

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.RIZHAO.get();
    }

    private static AbstractSpell getRandomSolarSpell(RandomSource random) {
        IForgeRegistry<AbstractSpell> registry = SpellRegistry.REGISTRY.get();
        List<AbstractSpell> availableSpells = new ArrayList<>();
        for (ResourceLocation spellId : SOLAR_SPELL_IDS) {
            AbstractSpell spell = registry.getValue(spellId);
            if (spell != null && spell != SpellRegistry.none()) {
                availableSpells.add(spell);
            }
        }
        if (availableSpells.isEmpty()) {
            return null;
        }
        return availableSpells.get(random.nextInt(availableSpells.size()));
    }
}
