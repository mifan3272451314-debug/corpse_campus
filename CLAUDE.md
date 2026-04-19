# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

`corpse_campus`（终末：尸骸校园）是一个基于 **Minecraft 1.20.1 Forge** 的模组，使用 Java 17 开发。模组构建在 **Iron's Spells 'n Spellbooks**（以下简称 ISS）之上，扩展了五大自定义流派和一整套以技能为核心的法术（Spell）。ISS 是硬依赖，几乎所有玩法类都继承或引用 ISS 的类型。

## 构建与运行

项目自带 Gradle Wrapper。`gradle.properties` 中设置了 `org.gradle.daemon=false`，每次调用都会启动新的守护进程。

- `./gradlew build` — 编译并重混淆 jar
- `./gradlew runClient` — 在开发环境启动客户端（工作目录 `./run`）
- `./gradlew runServer` — 启动开发用专用服务端（`--nogui`）
- `./gradlew runData` — 运行数据生成，输出到 `src/generated/resources/`
- `./gradlew runGameTestServer` — 执行已注册的 gametest 后退出

项目没有独立的单元测试体系，只有 Forge 自带的 gametest 钩子（命名空间 `corpse_campus`），也没有配置 lint。

## 前置依赖（关键）

ISS 及其配套模组不从远程 Maven 拉取，jar 包直接放在 `libs/` 下，通过 `build.gradle` 中的 `flatDir` + `fg.deobf(...)` 引入：

- `irons_spellbooks-1.20.1-*.jar`
- `curios-forge-*.jar`
- `geckolib-forge-*.jar`
- `player-animation-lib-forge-*.jar`

如果 IDE 里 ISS 相关类全红，或启动时报找不到 ISS 符号，首先检查 `libs/` 目录内容是否完整，然后刷新 Gradle。`src/main/resources/META-INF/mods.toml` 也必须声明这些运行时依赖（已声明）。

`CREATE_SPELL_AND_SPELLBOOK_GUIDE.md` 是仓库内的完整指引文档，涵盖如何新建流派、法术和魔法书。接入新的 ISS 内容前请先阅读。

## 代码结构

根包：`com.mifan`；模组 ID：`corpse_campus`；主类：`com.mifan.corpsecampus`。

### 注册入口（`com.mifan.registry`）

- `ModSchools` — 在 ISS 的 `SchoolRegistry.SCHOOL_REGISTRY_KEY` 下注册 5 个自定义 `SchoolType`。每个流派都有一个 `*_RESOURCE` 的 `ResourceLocation` 常量，法术通过 `defaultConfig.setSchoolResource(...)` 引用。五个流派：`xujing`（虚境）、`rizhao`（日兆）、`dongyue`（东岳）、`yuzhe`（愚者）、`shengqi`（圣祈）。
- `ModSpells` — 把所有法术注册到 ISS 的 `irons_spellbooks:spells` 注册表。新增法术都写在这里。
- `ModMobEffects` — 注册"标记型" `MobEffect`。这些效果不是玩法 Buff，而是用来标识某个切换式技能是否在生效（见下方"切换模式"）。

上述三个 Register 都在 `corpsecampus` 构造方法里挂到 mod 事件总线上。

### 法术（`com.mifan.spell.<school>`）

法术按流派分子包：`dongyue/`、`rizhao/`、`xujing/`、`yuzhe/`。每个法术继承 `AbstractSpell`，带 `@AutoSpellConfig` 注解。通用结构：

1. 在 `DefaultConfig` 中设定 `setSchoolResource(ModSchools.<SCHOOL>_RESOURCE)`、冷却、稀有度。
2. 重写 `getCastType()`、`getSpellResource()`、`onCast(...)`、`getSchoolType()`。
3. 切换型技能（如 `SonicSenseSpell`）：在 `onCast` 里判断施法者是否已有对应的 `ModMobEffects` 标记；没有就施加 `AbilityRuntime.TOGGLE_DURATION_TICKS` 时长的效果，已有则移除，从而实现开关切换。

### 运行时状态（`AbilityRuntime`、`AbilityEventHandler`、`AbilityClientHandler`）

玩家/实体的持久状态**不走 Capability**，而是直接写在 `entity.getPersistentData()` 的 NBT 上。`AbilityRuntime` 是核心工具类，承担：

- 把所有 NBT 键集中声明为 `TAG_*` 常量（危机、记录官、念动、磁吸、本能、躁狂、冥化、印记、处刑者、夺取、嗅觉、元素领域……）。
- 定义玩法常量（`TOGGLE_DURATION_TICKS`、耐久消耗、血量上限等）。
- 提供被各法术与事件处理器复用的共享逻辑。

改动技能相关代码时，请优先在 `AbilityRuntime` 里添加辅助方法与常量，避免 NBT 键散落在各 Spell 类里。

`AbilityEventHandler`（`@Mod.EventBusSubscriber(modid = MODID)`）挂载所有服务端事件：`PlayerTickEvent` 负责每 tick 推进技能逻辑，另有 `LivingAttackEvent`、`LivingHurtEvent`、`LivingDeathEvent`、`LivingHealEvent`、`AttackEntityEvent`、`PlayerInteractEvent`、`PlayerLoggedOutEvent` 等。tick 内部通过判断玩家是否带有 `ModMobEffects` 的标记效果来分发处理。

`AbilityClientHandler` 只做客户端表现：HUD 叠加、世界空间标记、嗅觉的声音屏蔽、收到数据包后的视觉特效等。

### 网络（`com.mifan.network`）

`ModNetwork` 注册一条 `SimpleChannel`，频道 ID 为 `corpse_campus:main`，数据包 ID 按顺序递增。按方向分两类：

- `clientbound/` — 服务端 → 客户端：打开交互界面（`OpenDominanceScreenPacket`、`OpenMidasTouchScreenPacket`、`OpenRecorderOfficerScreenPacket`）、推送视觉事件（`DangerSensePingPacket`、`InstinctProcPacket`）。
- `serverbound/` — 客户端 → 服务端：用户在这些界面上做完选择后回传（`SetDominanceTargetPacket`、`SetMidasTouchTimerPacket`、`SetRecorderOfficerTimerPacket`）。

每个新包必须实现 `encode`/`decode`/`handle`，并在 `ModNetwork.register()` 里补一行。**顺序很重要**，因为 packet ID 是递增分配的。

### 客户端界面（`com.mifan.client.screen`）

无菜单结构的 `Screen` 子类，由客户端收到对应的 "open" 数据包后弹出。收集用户输入后，再发送对应的 serverbound 包回服务端。

### 资源文件

- `assets/corpse_campus/lang/{en_us,zh_cn}.json` — 所有面向玩家的文本。新增法术、流派、tooltip 时必须**同时补充中英文**。法术 tooltip 键统一走 `tooltip.corpse_campus.*`，聊天提示走 `message.corpse_campus.*`。
- `data/corpse_campus/tags/items/focus/<school>.json` — 各流派的 focus 物品标签（当前都是空占位）。

## 命名约定

- 法术命名：类名 `FooBarSpell` → 注册名 `foo_bar` → `ResourceLocation` `corpse_campus:foo_bar` → 翻译键 `spell.corpse_campus.foo_bar`。
- 核心玩法逻辑放服务端；`AbilityClientHandler` 与 screen 只负责渲染或收集输入。
- 设计文档是中文的：`能力列表.md`（能力规格）与 `项目进度.md`（进度记录）。遇到某个法术名字已列出但实现仍是占位时，先看这两份文档确认预期行为。
