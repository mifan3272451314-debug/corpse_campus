# corpse_campus 代码质量修复清单

> 生成来源：5 个 Explore Agent 并行审查 `src/main/java` 全量 114 个 Java 文件（约 21 000 行）。
> 约束：**不改变行为 / 功能效果**，仅做 P0 缺陷修复 + 可读性/一致性重构。
> 状态字段：`待验证`（Agent 报告可能误报，需读码核实）/ `待批准`（问题真实，等待用户同意修）/ `已修`/ `驳回`（经核实是误报）。

---

## 数量汇总

| 模块 | P0 | P1 | P2 | 合计 |
|------|----|----|----|------|
| Q1 spell 根 + runtime | 6 | 14 | 10 | 30 |
| Q2 xujing + rizhao | 5 | 8 | 18 | 31 |
| Q3 dongyue + yuzhe + shengqi | 10 | 27 | 18 | 55 |
| Q4 registry + item + entity + anomaly | 13 | 24 | 18 | 55 |
| Q5 client + network + command + compat | 4 | 6 | 11 | 21 |
| **合计** | **38** | **79** | **75** | **192** |

**误报风险提示**：Agent 在未完整读全所有上下文时有时会给出"疑似"问题。本清单每条在修复前都需要二次核实源代码。下文用 `⚠️ 需验证` 标记高度可疑项。

---

## 目录导航

- [P0 清单](#p0-严重缺陷---38-条)
  - [Q1 spell 根 + runtime](#q1-p0)
  - [Q2 xujing + rizhao](#q2-p0)
  - [Q3 dongyue + yuzhe + shengqi](#q3-p0)
  - [Q4 registry + item + entity + anomaly](#q4-p0)
  - [Q5 client + network + command + compat](#q5-p0)
- [P1 清单](#p1-可读性--一致性---79-条)
- [P2 清单](#p2-清洁度---75-条)

---

## P0 严重缺陷 — 38 条

### Q1 P0

| # | 路径:行 | 问题 | 修复 | 行为不变论据 | 状态 |
|---|--------|------|------|-------------|------|
| Q101 | spell/AbilityEventHandler.java:1254 | `getEntity()` 返回可能 null，直接 `.isRemoved()` 有 NPE 风险 | `if (entity != null && !entity.isRemoved()) entity.discard();` | 只加防御，空实体本来也不能 discard | 待验证 |
| Q102 | spell/AbilityEventHandler.java:609-639 | `handleLifeThiefRedirect` 中 `findRandomNearbyTarget().get()` 第二次调用无 null 守卫 | 缓存首次结果到局部变量，统一 null 校验 | 纯等价提取 | 待验证 |
| Q104 | spell/AbilityEventHandler.java:730,876,903 | `syncOlfactionTrails` 在遍历 ListTag 时又调用 `pruneExpiredOlfactionTrail(trail)` 修改 trail，CME 风险 | 倒序索引遍历或先 collect 后修改 | 遍历-修改分离，结果集相同 | 待验证 |
| Q105 | spell/OlfactionClientHandler.java:104-109 | 客户端 FOOTPRINTS removeIf 与 render 遍历同在一个 static List，并发风险 | 倒序 index 遍历 remove | 逻辑等价 | 待验证 |
| Q106 | spell/AbilityEventHandler.java:1254-1270 | 同 Q101 重复条目（tickGoldenCrowSun 路径） | 同上 | 同上 | 待验证（与 Q101 合并） |
| Q119 | spell/AbilityEventHandler.java:113-124 | `BLOCK_BOMBS` 全局 static `Map<Level,...>`，多维度并发写入风险 | 以维度 UUID 为 key 或加 synchronized | 键不冲突则行为一致 | 待验证 |

<a id="q2-p0"></a>

### Q2 P0

| # | 路径:行 | 问题 | 修复 | 行为不变论据 | 状态 |
|---|--------|------|------|-------------|------|
| Q200 | spell/rizhao/MidasTouchSpell.java:108 | 调用 `SoundEvents.NOTE_BLOCK_BASS.value()` 疑似 API 过时 | 改为 `SoundEvents.NOTE_BLOCK_BASS` | ⚠️ 需验证：1.20.1 下 `SoundEvents` 字段是 `Holder<SoundEvent>` 还是直接 `SoundEvent`；可能是误报 | ⚠️ 需验证 |
| Q201 | spell/rizhao/GoldenCrowSunSpell.java:234 | 未 `instanceof ServerLevel` 就创建实体 | 上层已检查则下层无需重复；按实际读码判断 | ⚠️ 需验证 | ⚠️ 需验证 |
| Q202 | spell/rizhao/GoldenCrowSunSpell.java:220,228 | `playerMagicData` 可能 null，直接 `getMana()` NPE | 入口做 `if (magicData == null) return;` | 按 ISS 调用模式 magicData 理应 non-null，需读码确认 | ⚠️ 需验证 |
| Q203 | spell/rizhao/LightPrayerSpell.java:112-114 | `playerMagicData.setMana(0.0F)` 前无 null 检查 | 同上 | 同上 | ⚠️ 需验证 |
| Q204 | spell/rizhao/AffinitySpell.java:123-135 | `level.getRandom()` 多线程安全性 | `ServerLevel.random` 即可 | ⚠️ 需验证：MC 1.20 `RandomSource` 线程模型 | ⚠️ 需验证 |

<a id="q3-p0"></a>

### Q3 P0

| # | 路径:行 | 问题 | 修复 | 行为不变论据 | 状态 |
|---|--------|------|------|-------------|------|
| Q307 | spell/dongyue/NecroticRebirthSpell.java:94-99 | `clear()` 调用 `data.remove` 多个 tag，静默失败 | 逐项先 `contains` 判断再 remove；或统一入口加警告日志 | 结果相同 | 待验证 |
| Q313 | spell/yuzhe/DominanceSpell.java:89 | `findDominanceMobTarget` 结果两条路径重复 null 检查 | 引入局部 `final Mob target = ...` 共用 | 纯等价 | 待验证 |
| Q316 | spell/yuzhe/runtime/MimicRuntime.java:231-234 | `getOrCreateSlotList` 没有先 `contains(Tag.TAG_LIST)` | 加守卫：`if (!data.contains(TAG, Tag.TAG_LIST)) return new ListTag();` | 未创建时返回空 list 与创建新 list 语义等价 | 待验证 |
| Q321 | spell/yuzhe/AuthorityGraspSpell.java:106-109 | `playerMagicData.setMana(0)` 前无 null 守卫 | 加 `if (playerMagicData != null)` | 等价于 ISS 默认路径 | ⚠️ 需验证 |
| Q327 | spell/yuzhe/WanxiangSpell.java:82 | `level instanceof ServerLevel` 失败时无 fallback 日志 | 添加 else 分支 log | 行为不变，仅可观测性 | 待验证 |
| Q330 | spell/shengqi/runtime/GrafterRuntime.java:121-122 | `getItem().getTag()` 可能 null | `getTag() != null && getTag().contains(...)` | 防御等价 | 待验证 |
| Q331 | spell/shengqi/runtime/GrafterRuntime.java:34-44 | `collectTransferableSpells` 中 `slot.getSpell()` 可能 null 导致 NPE | `if (slot.getSpell() == null) continue;` | 等价 | 待验证 |
| Q334 | spell/shengqi/runtime/GrafterRuntime.java:158-170 | 解析 `TAG_STAGE_ABILITIES` CSV 失败时静默 skip 无日志 | 加 logger.warn | 行为不变，可观测性 | 待验证 |
| Q339 | spell/shengqi/HealingSpell.java:86-90 | `lookedTarget instanceof Player` 前已赋值给 Player 类型 | pattern matching：`targetPlayer = (lookedTarget instanceof Player p) ? p : null;` | 逻辑等价 | 待验证 |
| Q346 | spell/shengqi/EndlessLifeSpell.java:81-104 | `grantInheritedSpells` 等失败无反馈 | 检查返回值 → 错误提示 | 失败原本静默，改为显式 | 待验证 |
| Q348 | spell/shengqi/runtime/EndlessLifeRuntime.java:40-52 | `isSealed()` 在 overworld=null 时返回 false（多人激活风险） | 改返回 true 或缓存首次查询 | ⚠️ 语义变更风险，**可能改变行为** | ⚠️ 可能破坏行为，**驳回** |
| Q353 | spell/shengqi/runtime/EndlessLifeRuntime.java:75-91 | `grantInheritedSpells` 中多次调用 `addSpell`，granted 计数可能错 | 每次确认 book non-empty | 需验证真实性 | 待验证 |

<a id="q4-p0"></a>

### Q4 P0

| # | 路径:行 | 问题 | 修复 | 行为不变论据 | 状态 |
|---|--------|------|------|-------------|------|
| Q401 | registry/ModEntities.java:24 | `.toString()` 冗余调用 | 删除 | 字符串相等 | 待验证 |
| Q402 | anomaly/AnomalyBookService.java:1128 | `ownerMatches()` 语义不清 | 加注释说明 null 视作通用书 | 不改逻辑，只加注释 | 待验证 |
| Q403 | anomaly/AnomalyEventHandler.java:57,92,97,108 | 硬编码 `"AnomalyP0"` 字符串 4 处 | 抽 `AnomalyBookService.PLAYER_ROOT` 常量 | 纯重构 | 待验证 |
| Q404 | anomaly/AnomalyEventHandler.java:96 | `putInt(PENDING_TRAIT_DROPS, ...)` 后未 `setDirty()` | 补 `setDirty()` 链 | ⚠️ 需验证 Forge 的 PersistentData 是否自动脏标记 | ⚠️ 需验证 |
| Q405 | entity/GoldenCrowSunEntity.java:346-348 | 修改玩家 NBT 后未 `setDirty()` | 末尾补 `setDirty()` | 同上 | ⚠️ 需验证 |
| Q407 | registry/ModSpells.java:47 | `DeferredRegister.create()` 参数类型疑似错误 | ⚠️ 按 ISS 模式本是 `DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, MODID)`，需读码确认 | **极可能误报**，启动时如果错早就炸了 | ⚠️ 极可能误报 |
| Q410 | item/AnomalyTraitItem.java:45 | `use()` 中 `stack.shrink(1)` 直接修改 | 改 `InteractionResultHolder.consume(stack)` | 改变返回类型可能影响行为 | ⚠️ 需验证 |
| Q411 | anomaly/AnomalyBookService.java:345 | `ensureBookPresent()` 可能返回空栈，调用处未 `isEmpty()` 守卫 | 调用处补 `if (book.isEmpty()) return;` | 若原本进到空栈会 NPE，现在提前返回；这**可能改变行为**（原本会抛出现在不抛） | ⚠️ 行为是否可观察？需与用户确认 |
| Q412 | anomaly/AnomalyEventHandler.java:56-58 | `PlayerEvent.Clone` 整块 `copy()` PERSISTED_NBT | 选择性复制 | ⚠️ 此修会**改变行为**（某些 tag 不再复制）| ⚠️ 可能破坏行为，**驳回** |
| Q413 | registry/ModMobEffects.java:105-107 | 引用 `AbilityRuntime.LIGHT_PRAYER_SPELL_RESIST_UUID` / `_BONUS` | 核实这两个常量是否真实存在 | ⚠️ 若真不存在则编译就挂了，**极可能误报** | ⚠️ 极可能误报 |
| Q420 | item/AnomalyTraitItem.java:71-86 | tooltip `tag.contains(...)` 未带类型枚举 | `tag.contains(TAG_OWNER_NAME, Tag.TAG_STRING)` | 正常情况下结果相同；类型不匹配时原本可能异常取值，现在提前跳过（可能改变一个 edge case）| 待验证 |
| Q428 | anomaly/AnomalyBookService.java:236-241 | `getStoredSchoolBonusPercent()` 未检查 COMPOUND 类型 | 同上 | 同上 | 待验证 |
| Q429 | entity/GoldenCrowSunEntity.java:364-369 | `readAdditionalSaveData()` 未检查 tag 键存在 | 加 `tag.contains("LifeTicks")` 等 | 若键不存在，原本返回默认值（primitive 0）；带 contains 的行为需对齐 | 待验证 |

<a id="q5-p0"></a>

### Q5 P0

| # | 路径:行 | 问题 | 修复 | 行为不变论据 | 状态 |
|---|--------|------|------|-------------|------|
| Q500 | command/MagicCommand.java:57 | 主 `/magic` 节点未加 `.requires(...)`，只有子命令加了 | 顶层加 `.requires(src -> src.hasPermission(2))` | ⚠️ **改变行为**：无权限玩家原本能看到帮助菜单 | ⚠️ 可能破坏行为，**驳回** |
| Q501 | network/serverbound/SetMidasTouchTimerPacket.java:36-37 | ISS MagicData `.getMana()` 前无 null 检查 | 加 `if (magicData == null) return;` | 若原本直接 NPE，改后改为静默失败，可观察到的差异极小 | 待验证 |
| Q502 | network/ModNetwork.java:42-184 | `registered` 布尔值不严谨（但现实不会重载） | 保持现状或改 AtomicBoolean | 非问题；属于理论洁癖 | 驳回（P2 取向） |
| Q503 | compat/playerrevive/PlayerReviveCompat.java 全文 | 反射链长，若 IBleeding 接口变会 NPE | 每次 invoke 前 null-check MethodHandle | 防御增强 | 待验证 |

---

## P1 可读性 / 一致性 — 79 条

### Q1 P1（spell 根 + runtime）

| # | 路径:行 | 问题 | 修复 | 行为不变论据 | 状态 |
|---|--------|------|------|-------------|------|
| Q103 | spell/AbilityEventHandler.java:1087-1119 | `isTouchingClimbableWall` 重复 `hasSolidWall` 4 次 | 抽 helper `checkWallDirection(...)` | 纯提取 | 待批准 |
| Q107 | spell/AbilityEventHandler.java:76-110 | `onPlayerTick` 110 行 / 12 个 tickXxx 调用 | 分组抽 `tickAbilities(...)` | 纯提取 | 待批准 |
| Q108 | spell/AbilityEventHandler.java:465-498 | `tickSonicSense` + `tickStamina` 同模式（effect 不存在则清数据） | 抽 `clearEffectDataIfInactive(...)` | 纯提取 | 待批准 |
| Q109 | spell/AbilityEventHandler.java:661-705 | 魔法常量 `5.0D`（weapon damage threshold） | 抽 `DANGER_SENSE_WEAPON_THRESHOLD_DAMAGE = 5.0` | 常量复用 | 待批准 |
| Q110 | spell/AbilityRuntime.java:69 | `TOGGLE_DURATION_TICKS = 20*60*60*4` 无语义注释 | 加注释或拆成 `TOGGLE_HOURS = 4` | 等价表达 | 待批准 |
| Q111 | spell/AbilityEventHandler.java:1278-1340 | `tickNinghe` + `tickSunlight` 结构高度相似 | 抽 `tickAreaAuraEffect(caster, radius, Consumer<Player>)` | ⚠️ 需确认两个 tick 的分支细节完全等价 | ⚠️ 需细读后批准 |
| Q112 | spell/AbilityEventHandler.java:999-1042 | `isTouchingClimbableWall` 嵌套深 | stream + `anyMatch` | 等价 | 待批准 |
| Q113 | spell/AbilityEventHandler.java:1121-1150 | `emitShockwave` 3 处 `spellLevel * 浮点` | 抽数组常量 | 等价 | 待批准 |
| Q114 | spell/AbilityClientHandler.java:183-201 | `handleInstinctProc` 4 次 `packet.isLastStand()` | 缓存到局部 `boolean lastStand` | 等价 | 待批准 |
| Q115 | spell/ElementalDomainClientHandler.java:197-226 | `drawText` pushPose/popPose 冗余 | 单次包装循环 | 渲染结果等价 | 待批准 |
| Q116 | spell/AbilityEventHandler.java:556 | `slotState.getCompound(key)` 未 `contains` 守卫 | 加守卫 | 等价或更安全 | 待批准 |
| Q117 | spell/AbilityEventHandler.java:831-843 | `data.getList()` 修改后未 `put` 回 data | 补 `put` | ⚠️ 若原本返回的 ListTag 是引用，则修改已生效；需读码确认 | 待验证 |
| Q118 | spell/MidasBombRuntime.java:237-251 | `stack.getTag()` 可能 null，多处无统一空检查 | 入口统一 `if (stack.isEmpty() || !stack.hasTag()) return false;` | 等价 | 待批准 |
| Q120 | spell/OlfactionClientHandler.java:32,59 | static List FOOTPRINTS 无同步 | `synchronized` 或 `CopyOnWriteArrayList` | 行为等价 | 待批准 |

### Q2 P1（xujing + rizhao）

| # | 路径:行 | 问题 | 修复 | 行为不变论据 | 状态 |
|---|--------|------|------|-------------|------|
| Q205 | spell/xujing/{DangerSense,Olfaction,SonicSense}Spell.java | 三个 toggle 法术 onCast 完全相同 | 抽 `AbilityRuntime.toggleEffect(...)` | 纯提取 | 待批准 |
| Q206 | spell/rizhao/{Ninghe,Sunlight}Spell.java | 与虚境三个 toggle 相同模式 | 同上合并 | 纯提取 | 待批准 |
| Q207 | spell/xujing/RecorderOfficerSpell.java:119 | 强制转 ServerPlayer 前无 `instanceof` | pattern matching | 等价 | 待批准 |
| Q209 | spell/xujing/MarkSpell.java:31 vs rizhao/FertileLandSpell.java:25-26 | `spellId` 一个 private final、一个 public static final | 统一 private final | 封装加强 | 待批准 |
| Q211 | spell/xujing/ElementalistSpell.java:95-96,101-102 | ServerLevel instanceof 重复 2 次 | 缓存局部变量 | 等价 | 待批准 |
| Q212 | spell/rizhao/GoldenCrowSunSpell.java | 289 行 / `onCast` >100 行 | 拆 `resolveExistingSun/beginNewSun/validateDailyLimit/applyParticlesAndSounds` | 纯提取 | 待批准 |
| Q216 | spell/xujing/{Mark,RecorderOfficer}Spell.java | `findMarkedBlock` 重复 | 抽公共 helper | 等价 | 待批准 |
| Q217 | spell/xujing/* | 范围计算散落 | 统一 `calculateRangeByLevel(base, perLevel, level)` | ⚠️ 需对齐每处实际公式 | ⚠️ 需细读 |

### Q3 P1（dongyue + yuzhe + shengqi）

| # | 路径:行 | 问题 | 修复 | 行为不变论据 | 状态 |
|---|--------|------|------|-------------|------|
| Q301 | spell/dongyue/ImpermanenceMonkSpell.java:106 | onCast >120 行 | 拆 `infectTargetFlow/handleNoTarget/handleFull` | 纯提取 | 待批准 |
| Q303 | spell/dongyue/runtime/ImpermanenceMonkRuntime.java:90-103 | 离线感染目标无日志 | 加 WARN | 不改行为 | 待批准 |
| Q304 | spell/dongyue/runtime/ImpermanenceMonkRuntime.java:156 | `getList` 前未双保险 `contains(TAG_LIST)` | 加双保险 | 等价 | 待批准 |
| Q305 | spell/dongyue/GreatNecromancerSpell.java:122 | `findNearbyPlayer` null 无降级 | ⚠️ 加 fallback 会**改变行为** | 驳回 |
| Q308 | spell/dongyue/InstinctSpell.java:116-118 | 硬编码 0.15F 与 tooltip 的 100 倍换算未联动 | 抽 `IMMUNITY_CHANCE` 常量 | 等价 | 待批准 |
| Q309 | spell/dongyue/ManiaSpell.java:116-122 | getBonusDamagePercent / tooltip 未校验 | 加断言或单测 | 不改运行时 | 待批准 |
| Q311 | spell/dongyue/ImpermanenceMonkSpell.java:182-189 | 被遮挡目标静默失败 | ⚠️ 改为提示是**功能变更** | 驳回 |
| Q312 | spell/yuzhe/DominanceSpell.java:31,88-116 | `MAX_CONTROLLED=8` 与 AbilityRuntime 不联动 | 引用 `AbilityRuntime.DOMINANCE_MAX_CONTROLLED` | 等价 | 待批准 |
| Q314 | spell/yuzhe/MimicSpell.java:89-110 | `beginAbsorb/beginRelease` 无异常捕获 | ⚠️ try-catch + 错误提示属于**行为增强** | 驳回 |
| Q315 | spell/yuzhe/MimicSpell.java vs shengqi/GrafterSpell.java | `findNearbyPlayer` 重复 | 抽 `AbilityRuntime.findNearbyPlayerInBox` | 纯提取 | 待批准 |
| Q317 | spell/yuzhe/runtime/MimicRuntime.java:60-84 | `setSlot` 未校验 spellId | ⚠️ 增加校验是行为变更 | 驳回 |
| Q318 | spell/yuzhe/runtime/MimicRuntime.java:158-160 | MIMIC_ID/GRAFTER_ID/ENDLESS_LIFE_ID 分散 | 集中到 AbilityRuntime | 等价 | 待批准 |
| Q320 | spell/yuzhe/runtime/MimicRuntime.java:167-181 | `resolveSlotSpell` 无缓存 | ⚠️ 加缓存改变 invalidation 语义 | 驳回（非等价优化） |
| Q322 | spell/yuzhe/runtime/DominanceRuntime.java:35 | `containsUuid` 格式待验证 | 读码核实 | - | 待验证 |
| Q323 | spell/yuzhe/runtime/DominanceRuntime.java:70 | 死亡生物未及时清理 | 返回前 `removeIf(m -> !m.isAlive())` | ⚠️ 可能**提前**清理某些 tick 应保留的数据 | 待验证 |
| Q324 | spell/yuzhe/TelekinesisSpell.java:78-82 | `checkPreCastConditions` 与 findTarget 重复检查 | 整合 | ⚠️ 需对比两处细节 | 待验证 |
| Q325 | spell/yuzhe/TelekinesisSpell.java:88-92 | 目标被 kill 后无反馈 | ⚠️ 加提示是**功能变更** | 驳回 |
| Q326 | spell/yuzhe/LifeThiefSpell.java:31,100 | public static 常量作用域 | 改 private | 若无外部引用则等价 | 待批准 |
| Q328 | spell/yuzhe/MagneticClingSpell.java:92 | `TAG_MAGNETIC_LAST_GROUND` 过期清理 | ⚠️ 清理时机改动是行为变更 | 驳回 |
| Q332 | spell/shengqi/runtime/GrafterRuntime.java:172-181 | `writeSpellIntoBook` 错误分支合并 | 改 enum 返回 | ⚠️ 调用者需同步调整，属于 refactor | 待批准（范围大） |
| Q336 | spell/shengqi/ApothecarySpell.java:236-239 | `getItemPath` NPE 守卫显式化 | null 守卫 | 等价 | 待批准 |
| Q337 | spell/shengqi/ApothecarySpell.java:145-157 | `findPreferredIngredient` 无 early-return | 加 break | 功能等价 | 待批准 |
| Q338 | spell/shengqi/ApothecarySpell.java:164-178 | `brewPotion` 返回 null 分歧不可区分 | ⚠️ 改 Optional 是接口变更 | 待批准（范围大） |
| Q340 | spell/shengqi/HealingSpell.java:88 | fallback 未验 instanceof Player | 补 check | 等价 | 待批准 |
| Q342 | spell/shengqi/FerrymanSpell.java:81-84 | `instanceof ServerPlayer` 失败静默 | 加 log | 不改行为 | 待批准 |
| Q344 | spell/shengqi/HuihunSpell.java:102-108 | 权限跳过无日志 | 加 log | 不改行为 | 待批准 |
| Q345 | spell/shengqi/StaminaSpell.java:36-37 | `COOLDOWN_SECONDS` 配置是否真生效无验证 | 加断言 | 不改运行时 | 待批准 |
| Q347 | spell/shengqi/runtime/EndlessLifeRuntime.java:29-35 | INHERITED_SPELLS 硬编码 6 条 | ⚠️ 动态查询是行为变更 | 驳回 |
| Q349 | spell/shengqi/runtime/EndlessLifeRuntime.java:149-169 | SavedData 字段裸露 | 加 getter/setter | 封装，行为等价 | 待批准 |
| Q351 | spell/shengqi/runtime/GrafterRuntime.java:59-78 | absorb 原子性无单测 | 加单测 | - | 待批准（新增测试不影响代码） |
| Q352 | spell/shengqi/runtime/GrafterRuntime.java:184-202 | 失败原因 log 不分级 | 分 log | 不改行为 | 待批准 |
| Q354 | spell/shengqi/{Ferryman,EndlessLife}Spell.java | Beacon 音效硬编码两处 | 抽常量 | 等价 | 待批准 |

### Q4 P1（registry + item + entity + anomaly）

| # | 路径:行 | 问题 | 修复 | 行为不变论据 | 状态 |
|---|--------|------|------|-------------|------|
| Q414 | registry/ModItems.java:66-94 | 嵌套 switch >25 行 | 查表 map | 等价 | 待批准 |
| Q415 | registry/ModSchools.java:34-82 | 五流派 registerSchool 重复 | 配置数组+循环 | 等价 | 待批准 |
| Q416 | anomaly/AnomalyBookService.java:62-67 | `TRACKED_SCHOOLS` 可变 | `Collections.unmodifiableList(List.of(...))` | 防御 | 待批准 |
| Q417 | anomaly/AnomalyBookService.java:243-258 | `getCurrentTraitRanks()` 无缓存 | ⚠️ 加缓存 invalidation 复杂 | 驳回（非等价）|
| Q418 | anomaly/AnomalyEventHandler.java:24 | `PENDING_TRAIT_DROPS` 常量未共享 | 移到 AnomalyBookService | 等价 | 待批准 |
| Q419 | entity/GoldenCrowSunEntity.java:283-299 | `target == livingOwner` 在 livingOwner 可能为 null | 补 null 守卫 | 等价 | 待批准 |
| Q421 | anomaly/AnomalyLimitService.java:52-55 | catch IllegalArgumentException 吞错 | 加 log | 不改行为 | 待批准 |
| Q422 | anomaly/AnomalyBookService.java:769-795 | Curios 调用 try-catch 吞错 | 加 log | 不改行为 | 待批准 |
| Q423 | registry/ModAttributes.java:35-42 | switch default → null | ⚠️ 改 throw 是**行为变更** | 驳回 |
| Q424 | anomaly/AnomalyBookService.java:365-380 | `setMainSequence`+`setHighestRank` 相似 | 抽 `updateBookMetadata` | 等价 | 待批准 |
| Q425 | anomaly/AnomalySpellBookItem.java:63-73 | attribute 返回值判空 | 显式 null 守卫 | 等价（文档已做） | 待批准 |
| Q426 | anomaly/AnomalyBookService.java:1261-1303 | `createSpellSpecs` 多次 new ResourceLocation | switch/enum 预定义 | 等价 | 待批准 |
| Q427 | anomaly/AnomalyEventHandler.java:73-134 | onPlayerDeath vs onPlayerDrops 一致性注释 | 加注释 | 不改行为 | 待批准 |
| Q430 | registry/ModMobEffects.java:89-136 | 三个内部子类重复 | 抽基类 `AbstractAbilityMarkerEffect` | 等价 | 待批准 |
| Q431 | anomaly/AnomalyBookService.java:518-559 | `ensureBookPresent` 复杂 | 拆 `findOrCreateBook/bindBook/equipBook` | 纯提取 | 待批准 |
| Q432 | item/AnomalyDetectorItem.java:19-25 | 发包后无 catch | 加 catch | 不改行为 | 待批准 |
| Q433 | anomaly/AnomalyBookService.java:88-89 | public API 不在类顶部 | 调序 | 等价 | 待批准 |
| Q434 | entity/GoldenCrowSunEntity.java:160-216 | 粒子魔法数字 | 命名常量 | 等价 | 待批准 |
| Q435 | anomaly/AnomalyBookService.java:1164-1184 | `recalculateBookBonuses` 未 setDirty | ⚠️ 需验证是否真有问题 | 待验证 |

### Q5 P1（client + network + command + compat）

| # | 路径:行 | 问题 | 修复 | 行为不变论据 | 状态 |
|---|--------|------|------|-------------|------|
| Q504 | client/ClientKeyMappings.java:43-57 | tick 里 Minecraft 取法顺序不一致 | 统一前置 null-check | 等价 | 待批准 |
| Q505 | command/MagicCommand.java:262-296 | `addSpell` 中文字面量 | i18n | ⚠️ 若翻译文件未配，显示会变；需提前补 lang | 待批准（含 lang）|
| Q506 | network/clientbound/OlfactionTrailSyncPacket.java:32-44 | decode size 无 bounds check | 加 `if (size > MAX) throw` | ⚠️ 异常路径改变；属于防御 | 待批准 |
| Q507 | client/screen/NecromancerScreen.java:72-96 | 滚动 off-by-one 风险 | `Mth.clamp(scrollOffset, 0, max)` | 边界情况更稳 | 待批准 |
| Q508 | client/screen/PlayerStatusScreen.java:67-71 | render 中 player null 检查时机 | 提前 return | 等价 | 待批准 |
| Q509 | client/screen/AbilityUpgradeScreen.java:79-99 | 多次调用未锁定 book 对象 | 缓存 book 局部引用 | 等价 | 待批准 |

---

## P2 清洁度 — 75 条

（P2 主要是注释、命名、import、JavaDoc 补全，一般不是 review 第一批重点。此处仅列编号和一句话问题；详细见对应 Agent 报告。）

### Q1 P2
- Q121 `LIGHT_PRAYER_SPELL_RESIST_UUID` 用 String 存 UUID
- Q122 class visibility 冗余
- Q123 无主人 checksum 注释
- Q124 `getRevealRange` 单行方法可内联
- Q125 `Math.min(1, spellLevel-1)` spellLevel=1 时为负
- Q126 MarkRuntime tick 圈复杂度高
- Q127 tickRizhaoEnergy Math.min 抽 helper
- Q128 ElementalDomain 颜色魔法数字
- Q129 `>> 4` chunk 转换无注释
- Q130 Necromancer vs Dominance UUID 解析风格不一致

### Q2 P2
- Q213–Q230 基本是魔法数字命名、import 风格、构造器重复注释、tooltip 翻译键单位说明等

### Q3 P2
- Q302 ImpermanenceMonk 注释语气
- Q306 ResourceLocation 字面量集中化
- Q310 常量引用清单
- Q319 MimicRuntime `displayName` 降级格式
- Q329 Dominance tooltip vs 代码不一致（≥35 vs >35）
- Q333 GrafterSpell 注释澄清
- Q335 Apothecary createPotion 三方法合并
- Q341 HealingSpell duration 单位注释
- Q343 HuihunSpell 权限级别文档
- Q350 GrafterSpell 日志补充
- Q355 MimicSpell vs GrafterSpell findNearbyPlayer 细节差异

### Q4 P2
- Q436–Q453 几乎全部是 JavaDoc 补齐、record 改名、DecimalFormat locale、方法分拆等

### Q5 P2
- Q510–Q520 ISS 全限定名 import 化、SoulEntry 共享 record、权限常量命名、玩家列表上限、颜色 config 化、lang 键验证、翻译键一致性、encode/decode 注释

---

## 行为风险分级索引

**已识别会改变行为的条目（驳回或需用户特批）**：
- Q305 GreatNecromancerSpell 加 fallback
- Q311 ImpermanenceMonk 遮挡提示
- Q314 MimicSpell 异常 catch+反馈
- Q317 MimicRuntime setSlot 校验
- Q320 MimicRuntime 加缓存
- Q325 TelekinesisSpell 目标被 kill 反馈
- Q328 MagneticCling 清理时机
- Q347 EndlessLife INHERITED_SPELLS 动态化
- Q348 EndlessLife isSealed null-overworld 返回值变更
- Q412 AnomalyEventHandler Clone 选择性复制
- Q417 AnomalyBookService 缓存 invalidation
- Q423 ModAttributes default throw
- Q500 MagicCommand 主节点加权限

**极可能误报**（需读码再说）：
- Q200 NOTE_BLOCK_BASS.value()
- Q407 DeferredRegister 类型
- Q413 LIGHT_PRAYER_SPELL_RESIST_UUID 存在性

---

## 下一步

由用户逐项或逐批圈选 → AI 先读码核实 → 确认真实后修 → 单项 commit + push。
