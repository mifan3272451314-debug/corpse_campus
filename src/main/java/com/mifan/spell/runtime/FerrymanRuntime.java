package com.mifan.spell.runtime;

import com.mifan.anomaly.AnomalyBookService;
import com.mifan.spell.AbilityRuntime;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public final class FerrymanRuntime {
    private static final String NECROMANCY_MOD = "irons_spellbooks";
    private static final String[] NECROMANCY_SPELL_PATHS = {
            "acupuncture",
            "blood_needles",
            "blood_slash",
            "blood_step",
            "devour",
            "heartstop",
            "raise_dead",
            "ray_of_siphoning",
            "sacrifice",
            "wither_skull"
    };

    private FerrymanRuntime() {
    }

    public static void setTargetPlayer(ServerPlayer caster, UUID targetPlayerId, int spellLevel) {
        if (targetPlayerId.equals(caster.getUUID())) {
            caster.displayClientMessage(
                    Component.translatable("message.corpse_campus.ferryman_cannot_self"), true);
            return;
        }

        Player target = caster.serverLevel().getPlayerByUUID(targetPlayerId);
        if (target == null) {
            caster.displayClientMessage(
                    Component.translatable("message.corpse_campus.ferryman_no_target"), true);
            return;
        }

        CompoundTag data = caster.getPersistentData();
        data.putUUID(AbilityRuntime.TAG_FERRYMAN_TARGET, targetPlayerId);
        data.putInt(AbilityRuntime.TAG_FERRYMAN_LEVEL, Math.max(1, spellLevel));
        caster.displayClientMessage(Component.translatable(
                "message.corpse_campus.ferryman_target_set", target.getDisplayName()), true);
    }

    public static void onPotentialTargetDeath(LivingEntity dead) {
        if (!(dead instanceof ServerPlayer deadPlayer)) {
            return;
        }
        if (deadPlayer.getServer() == null) {
            return;
        }

        UUID deadId = deadPlayer.getUUID();
        for (ServerPlayer caster : deadPlayer.getServer().getPlayerList().getPlayers()) {
            CompoundTag data = caster.getPersistentData();
            if (!data.hasUUID(AbilityRuntime.TAG_FERRYMAN_TARGET)) {
                continue;
            }
            if (!deadId.equals(data.getUUID(AbilityRuntime.TAG_FERRYMAN_TARGET))) {
                continue;
            }

            int level = Math.max(1, data.getInt(AbilityRuntime.TAG_FERRYMAN_LEVEL));
            data.remove(AbilityRuntime.TAG_FERRYMAN_TARGET);
            data.remove(AbilityRuntime.TAG_FERRYMAN_LEVEL);

            grantRandomNecromancySpell(caster, level, deadPlayer);
        }
    }

    public static void clearTarget(ServerPlayer caster) {
        CompoundTag data = caster.getPersistentData();
        data.remove(AbilityRuntime.TAG_FERRYMAN_TARGET);
        data.remove(AbilityRuntime.TAG_FERRYMAN_LEVEL);
    }

    private static void grantRandomNecromancySpell(ServerPlayer caster, int spellLevel, Player dead) {
        String path = NECROMANCY_SPELL_PATHS[caster.getRandom().nextInt(NECROMANCY_SPELL_PATHS.length)];
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NECROMANCY_MOD, path);
        AbstractSpell spell = SpellRegistry.getSpell(id);
        if (spell == null) {
            caster.displayClientMessage(Component.translatable(
                    "message.corpse_campus.ferryman_grant_missing", path), true);
            return;
        }

        ItemStack book = AnomalyBookService.ensureBookPresent(caster);
        boolean injected = !book.isEmpty()
                && AnomalyBookService.addSpell(caster, book, spell, spellLevel, 1);

        Component spellName = Component.literal(spell.getSpellName());
        if (injected) {
            caster.displayClientMessage(Component.translatable(
                    "message.corpse_campus.ferryman_granted",
                    dead.getDisplayName(),
                    spellName), false);
        } else {
            caster.displayClientMessage(Component.translatable(
                    "message.corpse_campus.ferryman_granted_failed",
                    dead.getDisplayName(),
                    spellName), true);
        }
    }
}
