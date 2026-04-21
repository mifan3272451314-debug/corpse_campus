package com.mifan.anomaly;

import com.mifan.corpsecampus;
import com.mifan.registry.ModAttributes;
import com.mifan.registry.ModItems;
import com.mifan.registry.ModSchools;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainerMutable;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.item.SpellBook;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.IForgeRegistry;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AnomalyBookService {
    public static final String SPELLBOOK_SLOT = "spellbook";
    public static final int MAX_SPELL_SLOTS = 35;
    public static final int CHECK_INTERVAL_TICKS = 20;

    private static final String PLAYER_ROOT = "AnomalyP0";
    private static final String PLAYER_BOUND_BOOK_ID = "BoundBookId";
    private static final String PLAYER_BOOK_SNAPSHOT = "BookSnapshot";

    private static final String BOOK_ID = "AnomalyBookId";
    private static final String BOOK_OWNER_UUID = "AnomalyOwnerUuid";
    private static final String BOOK_OWNER_NAME = "AnomalyOwnerName";
    private static final String BOOK_MANA_BONUS = "AnomalyManaBonus";
    private static final String BOOK_SCHOOL_BONUSES = "AnomalySchoolBonuses";

    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.##");

    private static final List<ResourceLocation> TRACKED_SCHOOLS = List.of(
            ModSchools.XUJING_RESOURCE,
            ModSchools.RIZHAO_RESOURCE,
            ModSchools.DONGYUE_RESOURCE,
            ModSchools.YUZHE_RESOURCE,
            ModSchools.SHENGQI_RESOURCE);

    private static final Map<ResourceLocation, SpellSpec> SPELL_SPECS = createSpellSpecs();
    private static final Map<String, ResourceLocation> SPELL_ALIASES = createSpellAliases();

    private AnomalyBookService() {
    }

    public static List<ResourceLocation> getTrackedSchoolIds() {
        return TRACKED_SCHOOLS;
    }

    public static boolean isAnomalyBook(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.ANOMALY_TRAIT_SPELLBOOK.get());
    }

    @Nullable
    public static UUID getOwnerUuid(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(BOOK_OWNER_UUID) ? tag.getUUID(BOOK_OWNER_UUID) : null;
    }

    @Nullable
    public static UUID getBookId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(BOOK_ID) ? tag.getUUID(BOOK_ID) : null;
    }

    public static int getStoredManaBonus(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(BOOK_MANA_BONUS);
    }

    public static double getStoredSchoolBonusPercent(ItemStack stack, ResourceLocation schoolId) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(BOOK_SCHOOL_BONUSES, Tag.TAG_COMPOUND)) {
            return 0.0D;
        }
        return tag.getCompound(BOOK_SCHOOL_BONUSES).getDouble(schoolId.getPath());
    }

    @Nullable
    public static ResourceLocation resolveSpellId(String rawInput) {
        String normalized = rawInput.trim().toLowerCase(Locale.ROOT);
        ResourceLocation aliasMatch = SPELL_ALIASES.get(normalized);
        if (aliasMatch != null) {
            return aliasMatch;
        }

        ResourceLocation parsed = normalized.contains(":")
                ? ResourceLocation.tryParse(normalized)
                : ResourceLocation.fromNamespaceAndPath(corpsecampus.MODID, normalized);
        if (parsed != null && SPELL_SPECS.containsKey(parsed)) {
            return parsed;
        }
        return null;
    }

    @Nullable
    public static SpellSpec getSpellSpec(ResourceLocation spellId) {
        return SPELL_SPECS.get(spellId);
    }

    @Nullable
    public static AbstractSpell getRegisteredSpell(ResourceLocation spellId) {
        IForgeRegistry<AbstractSpell> registry = SpellRegistry.REGISTRY.get();
        AbstractSpell spell = registry.getValue(spellId);
        return spell == null || spell == SpellRegistry.none() ? null : spell;
    }

    public static ItemStack ensureBookPresent(ServerPlayer player) {
        RecoveryState state = new RecoveryState();
        state.expectedBookId = getBoundBookId(player);

        processCandidate(player, state, BookLocation.CURIO, 0, getCurioSpellbookStack(player));

        for (int i = 0; i < player.getInventory().items.size(); i++) {
            processCandidate(player, state, BookLocation.INVENTORY, i, player.getInventory().items.get(i));
        }
        for (int i = 0; i < player.getInventory().offhand.size(); i++) {
            processCandidate(player, state, BookLocation.OFFHAND, i, player.getInventory().offhand.get(i));
        }

        ItemStack primary = state.primaryStack;
        if (primary.isEmpty()) {
            primary = restoreOrCreateBook(player, state.expectedBookId);
            state.primaryStack = primary;
            state.primaryLocation = null;
            state.primaryIndex = -1;
            state.expectedBookId = getBookId(primary);
        }

        if (state.expectedBookId == null) {
            UUID bookId = getBookId(primary);
            if (bookId == null) {
                bookId = UUID.randomUUID();
            }
            bindBookToPlayer(player, primary, bookId);
            state.expectedBookId = bookId;
        }

        setBoundBookId(player, state.expectedBookId);
        ensureSpellContainer(primary);
        bindBookToPlayer(player, primary, state.expectedBookId);
        recalculateBookBonuses(primary);
        equipPrimaryBook(player, state.primaryLocation, state.primaryIndex, primary);
        updateBookSnapshot(player, primary);
        clampCurrentManaToMax(player);

        ItemStack equipped = getCurioSpellbookStack(player);
        return equipped.isEmpty() ? primary : equipped;
    }

    public static boolean addSpell(ServerPlayer player, ItemStack book, AbstractSpell spell, int requestedSpellLevel,
            int count) {
        ensureSpellContainer(book);
        int spellLevel = Mth.clamp(requestedSpellLevel, 1, spell.getMaxLevel());
        ISpellContainerMutable mutable = ISpellContainer.getOrCreate(book).mutableCopy();

        boolean changed = false;
        for (int i = 0; i < count; i++) {
            AddAttemptStatus status = addOrUpgradeSpell(mutable, spell, spellLevel);
            if (status == AddAttemptStatus.CHANGED) {
                changed = true;
            }
        }

        if (!changed) {
            return false;
        }

        ISpellContainer.set(book, mutable.toImmutable());
        recalculateBookBonuses(book);
        updateBookSnapshot(player, book);
        refreshCurioState(player);
        clampCurrentManaToMax(player);
        return true;
    }

    public static List<Component> buildStateLines(ServerPlayer player) {
        ItemStack book = ensureBookPresent(player);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("[异常法术书状态] " + player.getGameProfile().getName()));
        lines.add(Component.literal("- 绑定书 ID: " + stringifyUuid(getBookId(book))));
        lines.add(Component.literal("- 绑定主人: " + player.getGameProfile().getName() + " / " + player.getUUID()));
        lines.add(Component.literal("- 当前额外法力: +" + getStoredManaBonus(book)));
        lines.add(Component.literal("- 虚境强化: +" + formatPercent(getStoredSchoolBonusPercent(book, ModSchools.XUJING_RESOURCE)) + "%"));
        lines.add(Component.literal("- 日兆强化: +" + formatPercent(getStoredSchoolBonusPercent(book, ModSchools.RIZHAO_RESOURCE)) + "%"));
        lines.add(Component.literal("- 东岳强化: +" + formatPercent(getStoredSchoolBonusPercent(book, ModSchools.DONGYUE_RESOURCE)) + "%"));
        lines.add(Component.literal("- 愚者强化: +" + formatPercent(getStoredSchoolBonusPercent(book, ModSchools.YUZHE_RESOURCE)) + "%"));
        lines.add(Component.literal("- 圣祈强化: +" + formatPercent(getStoredSchoolBonusPercent(book, ModSchools.SHENGQI_RESOURCE)) + "%"));

        var activeSpells = ISpellContainer.getOrCreate(book).getActiveSpells();
        if (activeSpells.isEmpty()) {
            lines.add(Component.literal("- 已搭载法术: 无"));
        } else {
            lines.add(Component.literal("- 已搭载法术:"));
            for (SpellSlot slot : activeSpells) {
                SpellSpec spec = SPELL_SPECS.get(slot.getSpell().getSpellResource());
                String spellName = spec != null ? spec.zhName() : slot.getSpell().getSpellName();
                String rank = spec != null ? spec.rank().name() : "UNKNOWN";
                lines.add(Component.literal("  • " + spellName + " Lv." + slot.getLevel() + " [" + rank + "]"));
            }
        }
        return lines;
    }

    public static List<Component> buildLoadedSpellLines(ServerPlayer player) {
        ItemStack book = ensureBookPresent(player);
        List<Component> lines = new ArrayList<>();
        var activeSpells = ISpellContainer.getOrCreate(book).getActiveSpells();

        lines.add(Component.literal("[异常法术装载列表] " + player.getGameProfile().getName()));
        lines.add(Component.literal("- 当前装载数量: " + activeSpells.size()));
        if (activeSpells.isEmpty()) {
            lines.add(Component.literal("- 当前没有装载任何异常法术"));
            return lines;
        }

        for (SpellSlot slot : activeSpells) {
            SpellSpec spec = SPELL_SPECS.get(slot.getSpell().getSpellResource());
            String spellName = spec != null ? spec.zhName() : slot.getSpell().getSpellName();
            String rank = spec != null ? spec.rank().name() : "UNKNOWN";
            lines.add(Component.literal("  • " + spellName + " | ID=" + slot.getSpell().getSpellResource()
                    + " | Lv." + slot.getLevel() + " | " + rank));
        }
        return lines;
    }

    public static boolean clearSpell(ServerPlayer player, ItemStack book, AbstractSpell spell) {
        ensureSpellContainer(book);
        ISpellContainerMutable mutable = ISpellContainer.getOrCreate(book).mutableCopy();
        boolean removed = mutable.removeSpell(spell);
        if (!removed) {
            return false;
        }

        persistSpellContainerChange(player, book, mutable);
        return true;
    }

    public static int clearAllSpells(ServerPlayer player, ItemStack book) {
        ensureSpellContainer(book);
        ISpellContainerMutable mutable = ISpellContainer.getOrCreate(book).mutableCopy();
        List<Integer> indices = mutable.getActiveSpells().stream()
                .map(SpellSlot::index)
                .sorted(Comparator.reverseOrder())
                .toList();

        int removedCount = 0;
        for (int index : indices) {
            if (mutable.removeSpellAtIndex(index)) {
                removedCount++;
            }
        }

        if (removedCount <= 0) {
            return 0;
        }

        persistSpellContainerChange(player, book, mutable);
        return removedCount;
    }

    private static void processCandidate(ServerPlayer player, RecoveryState state, BookLocation location, int index,
            ItemStack stack) {
        if (!isAnomalyBook(stack)) {
            return;
        }

        UUID ownerUuid = getOwnerUuid(stack);
        if (ownerUuid != null && !ownerUuid.equals(player.getUUID())) {
            clearLocation(player, location, index);
            return;
        }

        if (state.expectedBookId == null) {
            UUID chosenBookId = getBookId(stack);
            if (chosenBookId == null) {
                chosenBookId = UUID.randomUUID();
            }
            bindBookToPlayer(player, stack, chosenBookId);
            setBoundBookId(player, chosenBookId);
            state.expectedBookId = chosenBookId;
            adoptPrimary(state, location, index, stack, player);
            return;
        }

        UUID bookId = getBookId(stack);
        if (bookId == null) {
            bindBookToPlayer(player, stack, state.expectedBookId);
            bookId = state.expectedBookId;
        }

        if (!state.expectedBookId.equals(bookId)) {
            clearLocation(player, location, index);
            return;
        }

        bindBookToPlayer(player, stack, state.expectedBookId);
        adoptPrimary(state, location, index, stack, player);
    }

    private static void adoptPrimary(RecoveryState state, BookLocation location, int index, ItemStack stack,
            ServerPlayer player) {
        if (state.primaryStack.isEmpty()) {
            state.primaryStack = stack;
            state.primaryLocation = location;
            state.primaryIndex = index;
            return;
        }
        clearLocation(player, location, index);
    }

    private static ItemStack restoreOrCreateBook(ServerPlayer player, @Nullable UUID expectedBookId) {
        CompoundTag data = getOrCreatePlayerData(player);
        ItemStack restored = ItemStack.EMPTY;
        if (data.contains(PLAYER_BOOK_SNAPSHOT, Tag.TAG_COMPOUND)) {
            restored = ItemStack.of(data.getCompound(PLAYER_BOOK_SNAPSHOT));
            if (!isAnomalyBook(restored)) {
                restored = ItemStack.EMPTY;
            }
        }

        if (restored.isEmpty()) {
            restored = new ItemStack(ModItems.ANOMALY_TRAIT_SPELLBOOK.get());
        }

        ensureSpellContainer(restored);
        UUID bookId = expectedBookId != null ? expectedBookId : getBookId(restored);
        if (bookId == null) {
            bookId = UUID.randomUUID();
        }
        bindBookToPlayer(player, restored, bookId);
        setBoundBookId(player, bookId);
        return restored;
    }

    private static void equipPrimaryBook(ServerPlayer player, @Nullable BookLocation primaryLocation, int primaryIndex,
            ItemStack primaryStack) {
        if (primaryLocation == BookLocation.CURIO) {
            refreshCurioState(player);
            return;
        }

        if (primaryLocation != null) {
            clearLocation(player, primaryLocation, primaryIndex);
        }

        var stacksHandler = getSpellbookStacksHandler(player);
        if (stacksHandler.isPresent() && stacksHandler.get().getStacks().getSlots() > 0) {
            IDynamicStackHandler stacks = stacksHandler.get().getStacks();
            ItemStack existing = stacks.getStackInSlot(0);
            if (!existing.isEmpty()) {
                moveToInventoryOrDrop(player, existing.copy());
            }
            stacks.setStackInSlot(0, primaryStack);
            stacksHandler.get().update();
        } else {
            moveToInventoryOrDrop(player, primaryStack.copy());
        }

        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static void clearLocation(ServerPlayer player, BookLocation location, int index) {
        switch (location) {
            case INVENTORY -> {
                player.getInventory().items.set(index, ItemStack.EMPTY);
                player.getInventory().setChanged();
            }
            case OFFHAND -> {
                player.getInventory().offhand.set(index, ItemStack.EMPTY);
                player.getInventory().setChanged();
            }
            case CURIO -> getSpellbookStacksHandler(player).ifPresent(handler -> {
                handler.getStacks().setStackInSlot(index, ItemStack.EMPTY);
                handler.update();
            });
        }
    }

    private static void moveToInventoryOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void refreshCurioState(ServerPlayer player) {
        getSpellbookStacksHandler(player).ifPresent(ICurioStacksHandler::update);
        player.containerMenu.broadcastChanges();
    }

    private static void clampCurrentManaToMax(ServerPlayer player) {
        MagicData magicData = MagicData.getPlayerMagicData(player);
        if (magicData == null) {
            return;
        }
        float maxMana = (float) player.getAttributeValue(AttributeRegistry.MAX_MANA.get());
        magicData.setMana(Math.min(magicData.getMana(), maxMana));
    }

    private static AddAttemptStatus addOrUpgradeSpell(ISpellContainerMutable mutable, AbstractSpell spell, int level) {
        int existingIndex = mutable.getIndexForSpell(spell);
        if (existingIndex >= 0) {
            SpellData existingData = mutable.getSpellAtIndex(existingIndex);
            if (existingData.getLevel() >= level) {
                return AddAttemptStatus.SKIPPED;
            }

            boolean removed = mutable.removeSpellAtIndex(existingIndex);
            boolean readded = removed && mutable.addSpellAtIndex(spell, level, existingIndex, existingData.isLocked());
            return readded ? AddAttemptStatus.CHANGED : AddAttemptStatus.FULL;
        }

        return mutable.addSpell(spell, level, false) ? AddAttemptStatus.CHANGED : AddAttemptStatus.FULL;
    }

    private static void ensureSpellContainer(ItemStack stack) {
        if (stack.getItem() instanceof SpellBook spellBook) {
            spellBook.initializeSpellContainer(stack);
        } else {
            ISpellContainer.getOrCreate(stack);
        }
    }

    private static void recalculateBookBonuses(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag schoolBonuses = new CompoundTag();
        int manaBonus = 0;

        ensureSpellContainer(stack);
        for (SpellSlot slot : ISpellContainer.getOrCreate(stack).getActiveSpells()) {
            SpellSpec spec = SPELL_SPECS.get(slot.getSpell().getSpellResource());
            if (spec == null) {
                continue;
            }

            manaBonus += spec.rank().getManaBonus();
            String schoolKey = spec.schoolId().getPath();
            schoolBonuses.putDouble(schoolKey,
                    schoolBonuses.getDouble(schoolKey) + spec.rank().getSchoolBonusPercent());
        }

        tag.putInt(BOOK_MANA_BONUS, manaBonus);
        tag.put(BOOK_SCHOOL_BONUSES, schoolBonuses);
    }

    private static void persistSpellContainerChange(ServerPlayer player, ItemStack book, ISpellContainerMutable mutable) {
        ISpellContainer.set(book, mutable.toImmutable());
        recalculateBookBonuses(book);
        updateBookSnapshot(player, book);
        refreshCurioState(player);
        clampCurrentManaToMax(player);
    }

    private static void bindBookToPlayer(ServerPlayer player, ItemStack stack, UUID bookId) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(BOOK_ID, bookId);
        tag.putUUID(BOOK_OWNER_UUID, player.getUUID());
        tag.putString(BOOK_OWNER_NAME, player.getGameProfile().getName());
    }

    private static void updateBookSnapshot(ServerPlayer player, ItemStack stack) {
        CompoundTag data = getOrCreatePlayerData(player);
        data.put(PLAYER_BOOK_SNAPSHOT, stack.save(new CompoundTag()));
    }

    private static CompoundTag getOrCreatePlayerData(Player player) {
        CompoundTag persistentData = player.getPersistentData();
        CompoundTag persisted = persistentData.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persistentData.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            persistentData.put(Player.PERSISTED_NBT_TAG, persisted);
        }

        CompoundTag anomalyData = persisted.getCompound(PLAYER_ROOT);
        if (!persisted.contains(PLAYER_ROOT, Tag.TAG_COMPOUND)) {
            persisted.put(PLAYER_ROOT, anomalyData);
        }
        return anomalyData;
    }

    @Nullable
    private static UUID getBoundBookId(Player player) {
        CompoundTag data = getOrCreatePlayerData(player);
        return data.hasUUID(PLAYER_BOUND_BOOK_ID) ? data.getUUID(PLAYER_BOUND_BOOK_ID) : null;
    }

    private static void setBoundBookId(Player player, UUID bookId) {
        getOrCreatePlayerData(player).putUUID(PLAYER_BOUND_BOOK_ID, bookId);
    }

    private static ItemStack getCurioSpellbookStack(ServerPlayer player) {
        return getSpellbookStacksHandler(player)
                .filter(handler -> handler.getStacks().getSlots() > 0)
                .map(handler -> handler.getStacks().getStackInSlot(0))
                .orElse(ItemStack.EMPTY);
    }

    private static java.util.Optional<ICurioStacksHandler> getSpellbookStacksHandler(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve().flatMap(inv -> inv.getStacksHandler(SPELLBOOK_SLOT));
    }

    private static String formatPercent(double value) {
        return PERCENT_FORMAT.format(value);
    }

    private static String stringifyUuid(@Nullable UUID uuid) {
        return uuid == null ? "未绑定" : uuid.toString();
    }

    private static Map<ResourceLocation, SpellSpec> createSpellSpecs() {
        LinkedHashMap<ResourceLocation, SpellSpec> map = new LinkedHashMap<>();
        register(map, "sonic_sense", "音波", AnomalySpellRank.B, ModSchools.XUJING_RESOURCE);
        register(map, "danger_sense", "危机", AnomalySpellRank.B, ModSchools.XUJING_RESOURCE);
        register(map, "olfaction", "嗅觉", AnomalySpellRank.B, ModSchools.XUJING_RESOURCE);
        register(map, "mark", "印记", AnomalySpellRank.B, ModSchools.XUJING_RESOURCE);
        register(map, "recorder_officer", "记录官", AnomalySpellRank.A, ModSchools.XUJING_RESOURCE);
        register(map, "elementalist", "元素使", AnomalySpellRank.A, ModSchools.XUJING_RESOURCE);

        register(map, "affinity", "亲和", AnomalySpellRank.B, ModSchools.RIZHAO_RESOURCE);
        register(map, "midas_touch", "点金客", AnomalySpellRank.A, ModSchools.RIZHAO_RESOURCE);

        register(map, "daiyue", "岱岳", AnomalySpellRank.B, ModSchools.DONGYUE_RESOURCE);
        register(map, "mania", "躁狂", AnomalySpellRank.B, ModSchools.DONGYUE_RESOURCE);
        register(map, "instinct", "本能", AnomalySpellRank.B, ModSchools.DONGYUE_RESOURCE);
        register(map, "necrotic_rebirth", "冥化", AnomalySpellRank.B, ModSchools.DONGYUE_RESOURCE);
        register(map, "executioner", "刽子手", AnomalySpellRank.A, ModSchools.DONGYUE_RESOURCE);

        register(map, "wanxiang", "万象", AnomalySpellRank.B, ModSchools.YUZHE_RESOURCE);
        register(map, "telekinesis", "念力", AnomalySpellRank.B, ModSchools.YUZHE_RESOURCE);
        register(map, "dominance", "支配", AnomalySpellRank.B, ModSchools.YUZHE_RESOURCE);
        register(map, "magnetic_cling", "磁吸", AnomalySpellRank.B, ModSchools.YUZHE_RESOURCE);
        register(map, "life_thief", "盗命客", AnomalySpellRank.A, ModSchools.YUZHE_RESOURCE);

        register(map, "huihun", "回魂", AnomalySpellRank.B, ModSchools.SHENGQI_RESOURCE);
        register(map, "healing", "愈合", AnomalySpellRank.B, ModSchools.SHENGQI_RESOURCE);
        register(map, "stamina", "耐力", AnomalySpellRank.B, ModSchools.SHENGQI_RESOURCE);
        register(map, "apothecary", "药师", AnomalySpellRank.B, ModSchools.SHENGQI_RESOURCE);
        return Map.copyOf(map);
    }

    private static Map<String, ResourceLocation> createSpellAliases() {
        LinkedHashMap<String, ResourceLocation> aliases = new LinkedHashMap<>();
        for (SpellSpec spec : SPELL_SPECS.values()) {
            aliases.put(spec.spellId().toString().toLowerCase(Locale.ROOT), spec.spellId());
            aliases.put(spec.spellId().getPath().toLowerCase(Locale.ROOT), spec.spellId());
            aliases.put(spec.zhName().toLowerCase(Locale.ROOT), spec.spellId());
        }
        return Map.copyOf(aliases);
    }

    private static void register(Map<ResourceLocation, SpellSpec> map, String spellPath, String zhName,
            AnomalySpellRank rank, ResourceLocation schoolId) {
        ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath(corpsecampus.MODID, spellPath);
        map.put(spellId, new SpellSpec(spellId, zhName, rank, schoolId));
    }

    public record SpellSpec(ResourceLocation spellId, String zhName, AnomalySpellRank rank, ResourceLocation schoolId) {
    }

    private enum BookLocation {
        CURIO,
        INVENTORY,
        OFFHAND
    }

    private enum AddAttemptStatus {
        CHANGED,
        SKIPPED,
        FULL
    }

    private static final class RecoveryState {
        @Nullable
        private UUID expectedBookId;
        private ItemStack primaryStack = ItemStack.EMPTY;
        @Nullable
        private BookLocation primaryLocation;
        private int primaryIndex = -1;
    }
}
