package com.mifan.spell.shengqi;

import com.mifan.corpsecampus;
import com.mifan.registry.ModSchools;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.AutoSpellConfig;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@AutoSpellConfig
public class ApothecarySpell extends AbstractSpell {
    private static final TagKey<Item> CROPS_TAG = ItemTags
            .create(ResourceLocation.fromNamespaceAndPath(corpsecampus.MODID, "apothecary/crops"));
    private static final TagKey<Item> MINERALS_TAG = ItemTags
            .create(ResourceLocation.fromNamespaceAndPath(corpsecampus.MODID, "apothecary/minerals"));
    private static final TagKey<Item> BIOLOGICAL_MATERIALS_TAG = ItemTags
            .create(ResourceLocation.fromNamespaceAndPath(corpsecampus.MODID, "apothecary/biological_materials"));

    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "apothecary");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.UNCOMMON)
            .setSchoolResource(ModSchools.SHENGQI_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(25)
            .build();

    public ApothecarySpell() {
        this.manaCostPerLevel = 8;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
        this.baseManaCost = 20;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.apothecary_inventory_consume"),
                Component.translatable("tooltip.corpse_campus.apothecary_crop_result"),
                Component.translatable("tooltip.corpse_campus.apothecary_mineral_result"),
                Component.translatable("tooltip.corpse_campus.apothecary_bio_result"));
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
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return entity instanceof Player player
                && findPreferredIngredient(player) != null
                && super.checkPreCastConditions(level, spellLevel, entity, playerMagicData);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource,
            MagicData playerMagicData) {
        if (!level.isClientSide && entity instanceof Player player) {
            ItemStack ingredient = findPreferredIngredient(player);
            if (ingredient == null) {
                player.displayClientMessage(Component.translatable("message.corpse_campus.apothecary_no_material"), true);
                super.onCast(level, spellLevel, entity, castSource, playerMagicData);
                return;
            }

            BrewResult result = brewPotion(ingredient, spellLevel);
            if (result == null) {
                player.displayClientMessage(Component.translatable("message.corpse_campus.apothecary_no_material"), true);
                super.onCast(level, spellLevel, entity, castSource, playerMagicData);
                return;
            }

            Component ingredientName = ingredient.getHoverName().copy();
            ingredient.shrink(1);

            if (!player.getInventory().add(result.potion())) {
                player.drop(result.potion(), false);
            }

            level.playSound(null,
                    player.blockPosition(),
                    SoundEvents.BREWING_STAND_BREW,
                    SoundSource.PLAYERS,
                    0.55F,
                    1.0F + spellLevel * 0.03F);
            player.displayClientMessage(Component.translatable(result.messageKey(), ingredientName), true);
        }

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.SHENGQI.get();
    }

    private static ItemStack findPreferredIngredient(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        if (isValidIngredient(mainHand)) {
            return mainHand;
        }

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isValidIngredient(stack)) {
                return stack;
            }
        }
        return null;
    }

    private static boolean isValidIngredient(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(CROPS_TAG) || stack.is(MINERALS_TAG) || stack.is(BIOLOGICAL_MATERIALS_TAG));
    }

    private static BrewResult brewPotion(ItemStack ingredient, int spellLevel) {
        if (ingredient.is(CROPS_TAG)) {
            return new BrewResult(createRecoveryPotion(ingredient, spellLevel),
                    "message.corpse_campus.apothecary_brewed_recovery");
        }
        if (ingredient.is(MINERALS_TAG)) {
            return new BrewResult(createBuffPotion(ingredient, spellLevel),
                    "message.corpse_campus.apothecary_brewed_buff");
        }
        if (ingredient.is(BIOLOGICAL_MATERIALS_TAG)) {
            return new BrewResult(createDamagePotion(ingredient, spellLevel),
                    "message.corpse_campus.apothecary_brewed_damage");
        }
        return null;
    }

    private static ItemStack createBuffPotion(ItemStack ingredient, int spellLevel) {
        ItemStack stack = new ItemStack(Items.POTION);
        String path = getItemPath(ingredient);
        Potion potion = switch (path) {
            case "coal", "charcoal", "redstone", "lapis_lazuli" -> spellLevel >= 4
                    ? Potions.STRONG_SWIFTNESS
                    : Potions.SWIFTNESS;
            case "iron_ingot", "iron_nugget", "copper_ingot", "copper_nugget", "quartz" -> spellLevel >= 4
                    ? Potions.STRONG_STRENGTH
                    : Potions.STRENGTH;
            case "gold_ingot", "gold_nugget", "diamond", "emerald", "amethyst_shard", "netherite_scrap" -> Potions.LONG_FIRE_RESISTANCE;
            case "prismarine_crystals", "prismarine_shard", "nautilus_shell", "heart_of_the_sea" -> Potions.LONG_WATER_BREATHING;
            default -> spellLevel >= 4 ? Potions.STRONG_LEAPING : Potions.LEAPING;
        };
        PotionUtils.setPotion(stack, potion);
        return stack;
    }

    private static ItemStack createDamagePotion(ItemStack ingredient, int spellLevel) {
        String path = getItemPath(ingredient);
        boolean lingering = switch (path) {
            case "ghast_tear", "phantom_membrane", "glow_ink_sac", "echo_shard" -> true;
            default -> false;
        };
        ItemStack stack = new ItemStack(lingering ? Items.LINGERING_POTION : Items.SPLASH_POTION);
        Potion potion = switch (path) {
            case "spider_eye", "cave_spider_spawn_egg", "poisonous_potato" -> spellLevel >= 4
                    ? Potions.STRONG_POISON
                    : Potions.POISON;
            case "fermented_spider_eye", "rotten_flesh", "bone", "bone_meal" -> Potions.LONG_WEAKNESS;
            case "magma_cream", "blaze_powder", "blaze_rod", "fire_charge", "gunpowder" -> spellLevel >= 4
                    ? Potions.STRONG_HARMING
                    : Potions.HARMING;
            case "slime_ball", "honeycomb", "leather", "rabbit_hide", "scute", "turtle_scute" -> Potions.LONG_SLOWNESS;
            default -> spellLevel >= 4 ? Potions.STRONG_HARMING : Potions.HARMING;
        };
        PotionUtils.setPotion(stack, potion);
        return stack;
    }

    private static ItemStack createRecoveryPotion(ItemStack ingredient, int spellLevel) {
        ItemStack stack = new ItemStack(Items.POTION);
        String path = getItemPath(ingredient);
        Potion potion = switch (path) {
            case "wheat", "wheat_seeds", "pumpkin", "pumpkin_seeds", "melon_slice", "melon_seeds" -> spellLevel >= 4
                    ? Potions.STRONG_HEALING
                    : Potions.HEALING;
            case "carrot", "golden_carrot", "sweet_berries", "glow_berries", "apple" -> Potions.LONG_REGENERATION;
            case "potato", "baked_potato", "beetroot", "beetroot_seeds", "sugar_cane", "dried_kelp" -> Potions.LONG_SWIFTNESS;
            case "chorus_fruit", "nether_wart" -> spellLevel >= 4 ? Potions.STRONG_LEAPING : Potions.LEAPING;
            default -> spellLevel >= 4 ? Potions.STRONG_HEALING : spellLevel >= 2 ? Potions.REGENERATION : Potions.HEALING;
        };
        PotionUtils.setPotion(stack, potion);
        return stack;
    }

    private static String getItemPath(ItemStack ingredient) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(ingredient.getItem());
        return itemId == null ? "" : itemId.getPath().toLowerCase(Locale.ROOT);
    }

    private record BrewResult(ItemStack potion, String messageKey) {
    }
}
