# Iron's Spells 'n Spellbooks 魔法、流派与魔法书创建指南

> **同步基线（2026-04-24）**：本指南主体是"怎么从 0 做一个新法术/流派"，代码演进对它影响不大。以下快照用于帮助新开发者把章节示例映射到当前 `corpse_campus` 真实状态：
>
> - 流派已注册 5 个：`xujing / rizhao / dongyue / yuzhe / shengqi`（[`ModSchools`](src/main/java/com/mifan/registry/ModSchools.java)）
> - 法术已注册 **33 个**（缺 `impermanence_monk` 的一行注册，见 [代码修复清单.md](./代码修复清单.md) P0）
> - 异常法术书类 `AnomalySpellBookItem`（继承 ISS `SpellBook`）已实装于 [`anomaly/AnomalySpellBookItem.java`](src/main/java/com/mifan/anomaly/AnomalySpellBookItem.java)，通过 Curios `spellbook` 槽佩戴
> - 新增法术时必须**一并更新** [`AnomalyBookService.SPELL_SPECS`](src/main/java/com/mifan/anomaly/AnomalyBookService.java) 的 `(id, 中文名, rank, schoolId)` 登记，否则法力加成 / 流派强化 / 命令 lookup 都会漏这条

本指南用于 `corpse_campus` 模组开发，参考了以下项目的实际结构与实现方式：

- `irons-spells-n-spellbooks-1.20.1`
- `Twice_Block_Fight`

目标是说明如何在 `corpse_campus` 中：

- 配置 Iron's Spellbooks 开发环境
- 创建新的魔法流派（School）
- 创建新的魔法（Spell）
- 创建新的魔法书（SpellBook）

---

## 0. 先配置前置模组开发环境

在开始写法术前，必须先把 `corpse_campus` 的开发环境配置好。  
否则常见问题会包括：

- `AbstractSpell`、`SpellBook`、`SchoolType` 等类无法导入
- 编译期找不到依赖
- 运行时缺前置模组崩溃
- Curios 渲染、GeckoLib、动画类缺失

你当前工程采用的是和 `Twice_Block_Fight` 一样的思路：

**把前置模组 jar 放在 `libs/` 目录中，再通过 `flatDir + fg.deobf(...)` 引入。**

---

### 0.1 需要的前置模组

结合当前项目内容，最少需要以下依赖：

1. Iron's Spells 'n Spellbooks
2. Curios API
3. GeckoLib
4. Player Animation Lib

你当前项目里已经有这些 jar：

```text
corpse_campus/corpse_campus/libs/
├─ curios-forge-5.9.1+1.20.1.jar
├─ geckolib-forge-1.20.1-4.4.9.jar
├─ irons_spellbooks-1.20.1-3.4.0.11.jar
├─ player-animation-lib-forge-1.0.2-rc1+1.20.jar
└─ corpse-forge-1.20.1-1.0.23.jar
```

其中前四个是 Iron's Spellbooks 体系最关键的开发依赖。

> `corpse-forge-1.20.1-1.0.23.jar` 是否必须，取决于你自己的设计是否要依赖它。  
> 如果你后续的技能逻辑需要用到这个模组的类，也要一并写进 `build.gradle`。

---

### 0.2 build.gradle 的基础配置

你的 `corpse_campus/corpse_campus/build.gradle` 至少需要两部分关键配置。

---

#### 0.2.1 配置本地 libs 仓库

```groovy
repositories {
    flatDir {
        dir 'libs'
    }
}
```

这一步的作用是：让 Gradle 能从 `libs/` 目录识别本地 jar 依赖。

---

#### 0.2.2 配置 dependencies

参考 `Twice_Block_Fight/build.gradle`，推荐写法如下：

```groovy
dependencies {
    minecraft "net.minecraftforge:forge:${minecraft_version}-${forge_version}"

    // Iron's Spells 'n Spellbooks
    implementation fg.deobf("blank:irons_spellbooks:1.20.1-3.4.0.11")

    // Player Animation Lib
    implementation fg.deobf("blank:player-animation-lib-forge:1.0.2-rc1+1.20")

    // GeckoLib
    implementation fg.deobf("blank:geckolib-forge:1.20.1-4.4.9")

    // Curios API
    implementation fg.deobf("blank:curios-forge:5.9.1+1.20.1")
}
```

如果你还要使用 `corpse-forge-1.20.1-1.0.23.jar`，可额外加：

```groovy
implementation fg.deobf("blank:corpse-forge:1.20.1-1.0.23")
```

---

### 0.3 这些依赖为什么这样写

#### `flatDir`
因为你不是从远程 Maven 仓库拉取依赖，而是直接使用本地 jar。

#### `blank:xxx:version`
`flatDir` 模式下，group 名一般不重要，很多项目直接用 `blank` 作为占位。

#### `fg.deobf(...)`
Forge 模组开发环境里引用其他模组时，通常必须用它。  
否则很容易出现：

- 映射不匹配
- 运行正常但开发环境报错
- 或开发环境通过但运行期崩溃

---

### 0.4 Java、Forge、MC 版本要求

#### Java
Minecraft 1.20.1 Forge 模组开发通常使用：

- Java 17

你的项目已经配置为：

```groovy
java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}
```

这是正确的。

#### Minecraft
你当前目标版本是：

- Minecraft 1.20.1

因此所有前置模组版本必须都与 1.20.1 对齐。

#### Forge
Forge 版本也必须与这些依赖兼容。  
如果出现类签名变化、启动崩溃、映射异常，优先检查 Forge 版本是否过新或过旧。

---

### 0.5 mods.toml 中声明运行依赖

仅在 `build.gradle` 中写依赖还不够。  
为了保证游戏启动时能正确识别前置模组，建议在：

```text
src/main/resources/META-INF/mods.toml
```

中补充运行依赖。

示例：

```toml
[[dependencies.${mod_id}]]
modId="forge"
mandatory=true
versionRange="${forge_version_range}"
ordering="NONE"
side="BOTH"

[[dependencies.${mod_id}]]
modId="minecraft"
mandatory=true
versionRange="${minecraft_version_range}"
ordering="NONE"
side="BOTH"

[[dependencies.${mod_id}]]
modId="irons_spellbooks"
mandatory=true
versionRange="[3.4.0,)"
ordering="AFTER"
side="BOTH"

[[dependencies.${mod_id}]]
modId="curios"
mandatory=true
versionRange="[5.9.1,)"
ordering="AFTER"
side="BOTH"

[[dependencies.${mod_id}]]
modId="geckolib"
mandatory=true
versionRange="[4.4.9,)"
ordering="AFTER"
side="BOTH"

[[dependencies.${mod_id}]]
modId="playeranimator"
mandatory=true
versionRange="[1.0.2,)"
ordering="AFTER"
side="BOTH"
```

> 注意：  
> 实际 `modId` 应以这些模组自身的 `mods.toml` 为准。  
> 常见值通常是：
>
> - `irons_spellbooks`
> - `curios`
> - `geckolib`
> - `playeranimator`

---

### 0.6 开发环境检查清单

建议在开始写法术前，先确认下面这几项：

- `libs/` 中 jar 文件完整
- `build.gradle` 已启用 `flatDir`
- `dependencies` 已添加 Iron's Spellbooks / Curios / GeckoLib / Player Animation
- `mods.toml` 已声明依赖
- 工程能正常刷新 Gradle
- 能正常启动一次 client run

---

### 0.7 常见问题

#### 1）IDE 里类全红
一般是以下原因之一：

- `libs/` 里没放 jar
- `repositories` 没开 `flatDir`
- `dependencies` 写错
- Gradle 没刷新

#### 2）能编译但启动崩溃
一般是：

- 缺运行时依赖
- `mods.toml` 没声明
- 某个前置模组版本不兼容

#### 3）SpellBook 渲染类找不到
通常是 Curios 或 Iron's Spellbooks 没正确加载。

#### 4）动画相关类报错
优先检查：

- GeckoLib
- Player Animation Lib

---

## 1. 创建新的魔法流派（School）

流派决定一个法术属于哪个魔法体系。  
例如你在 `corpse_campus` 里可以设计：

- 尸骸系
- 亡语系
- 腐蚀系
- 瘟疫系

如果一个法术要和 Iron's Spellbooks 的流派系统对接，就要先注册 `SchoolType`。

---

### 1.1 注册流派的基本思路

参考 `Twice_Block_Fight` 的注册方式，你需要：

1. 定义 `SchoolType` 注册表 key
2. 创建 `DeferredRegister<SchoolType>`
3. 注册自定义流派对象
4. 在主模组类中绑定到事件总线

---

### 1.2 示例：注册一个“尸骸”流派

```java
package com.yourname.corpse_campus.registry;

import io.redspace.ironsspellbooks.api.spells.SchoolType;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModSchools {
    public static final String MOD_ID = "corpse_campus";

    public static final ResourceKey<Registry<SchoolType>> SCHOOL_REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "schools"));

    public static final DeferredRegister<SchoolType> SCHOOLS =
            DeferredRegister.create(SCHOOL_REGISTRY_KEY, MOD_ID);

    public static final ResourceLocation CORPSE_SCHOOL_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "corpse");

    public static final RegistryObject<SchoolType> CORPSE_SCHOOL =
            SCHOOLS.register("corpse", () -> new SchoolType(
                    CORPSE_SCHOOL_RESOURCE,
                    ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/school_corpse.png"),
                    Component.translatable("school.corpse_campus.corpse")
            ));

    public static void register(IEventBus eventBus) {
        SCHOOLS.register(eventBus);
    }
}
```

---

### 1.3 流派资源文件

#### 语言文件
`src/main/resources/assets/corpse_campus/lang/zh_cn.json`

```json
{
  "school.corpse_campus.corpse": "尸骸"
}
```

#### 图标
```text
src/main/resources/assets/corpse_campus/textures/gui/school_corpse.png
```

---

## 2. 创建新的魔法（Spell）

每个法术都要继承 `AbstractSpell`。

参考 `Twice_Block_Fight/src/main/java/.../spells/` 下的实现，一个法术通常包含以下内容：

- 法术唯一 ID
- 默认配置 `DefaultConfig`
- 施法方式 `CastType`
- 法术逻辑 `onCast / onServerCastTick / onServerCastComplete`
- 所属流派 `getSchoolType()`

---

### 2.1 基础法术模板

```java
package com.yourname.corpse_campus.spells;

import com.yourname.corpse_campus.registry.ModSchools;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.AutoSpellConfig;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

@AutoSpellConfig
public class CorpseExplosionSpell extends AbstractSpell {
    private final ResourceLocation spellId =
            ResourceLocation.fromNamespaceAndPath("corpse_campus", "corpse_explosion");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.COMMON)
            .setSchoolResource(ModSchools.CORPSE_SCHOOL_RESOURCE)
            .setMaxLevel(10)
            .setCooldownSeconds(5)
            .build();

    public CorpseExplosionSpell() {
        this.manaCostPerLevel = 5;
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 2;
        this.castTime = 20;
        this.baseManaCost = 20;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable(
                        "ui.irons_spellbooks.damage",
                        Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)
                )
        );
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
        return Optional.of(SoundEvents.ZOMBIE_AMBIENT);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.GENERIC_EXPLODE);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (!level.isClientSide) {
            float damage = getSpellPower(spellLevel, entity);
            // TODO: 在这里写你的实际法术逻辑
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.CORPSE_SCHOOL.get();
    }
}
```

---

### 2.2 常用字段说明

#### `spellId`
法术唯一资源位置：

```java
ResourceLocation.fromNamespaceAndPath("corpse_campus", "corpse_explosion")
```

通常建议与注册名保持一致。

#### `DefaultConfig`
主要控制：

- 最低稀有度
- 所属流派
- 最大等级
- 冷却时间

#### `CastType`
常见有三种：

- `INSTANT`：瞬发
- `CONTINUOUS`：持续施法
- `LONG`：长施法/引导型

#### `@AutoSpellConfig`
允许该法术通过配置自动生成配置项，后续更容易调数值。

---

### 2.3 更复杂法术怎么写

如果你想做类似 `Twice_Block_Fight` 里的持续技能，可以参考这些方法：

- `onCast(...)`
- `onServerCastTick(...)`
- `onServerCastComplete(...)`

例如：

- 持续绘制轨迹
- 持续召唤弹幕
- 引导结束后爆发
- 记录施法期间数据并在完成时结算

如果技能逻辑复杂，建议：

- 服务端处理伤害、实体生成、状态变化
- 客户端只做粒子与视觉表现
- 不要把核心逻辑只写在客户端

---

### 2.4 注册法术

和 `Twice_Block_Fight` 一样，需要单独做一个法术注册类。

```java
package com.yourname.corpse_campus.registry;

import com.yourname.corpse_campus.spells.CorpseExplosionSpell;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModSpells {
    public static final DeferredRegister<AbstractSpell> SPELLS =
            DeferredRegister.create(
                    ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spells"),
                    "corpse_campus"
            );

    public static final RegistryObject<AbstractSpell> CORPSE_EXPLOSION =
            SPELLS.register("corpse_explosion", CorpseExplosionSpell::new);

    public static void register(IEventBus eventBus) {
        SPELLS.register(eventBus);
    }
}
```

---

### 2.5 法术命名建议

建议统一一套规则：

- 类名：`CorpseExplosionSpell`
- 注册名：`corpse_explosion`
- 语言键：`spell.corpse_campus.corpse_explosion`
- 图标：`textures/mob_effect/corpse_explosion.png`

这样最不容易乱。

---

## 3. 创建新的魔法书（SpellBook）

魔法书本质上是一个物品，但不是普通物品。  
在 Iron's Spellbooks 体系里，它通常还是 Curios 装备物。

如果只是先做一个能用的基础魔法书，最简单的方式就是直接注册 `SpellBook`。

---

### 3.1 最基础的 SpellBook 注册方式

```java
package com.yourname.corpse_campus.registry;

import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.item.SpellBook;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, "corpse_campus");

    public static final RegistryObject<Item> CORPSE_SPELL_BOOK =
            ITEMS.register("corpse_spell_book",
                    () -> new SpellBook(
                            SpellRarity.RARE,
                            new Item.Properties().stacksTo(1)
                    )
            );

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
```

---

### 3.2 如果你想做“带属性”的魔法书

在原版 Iron's Spellbooks 中可以参考：

- `SpellBook`
- `SimpleAttributeSpellBook`

如果你希望魔法书提供额外属性，比如：

- 法强
- 最大法力
- 冷却缩减
- 某一流派增伤

那么更适合：

1. 继承 `SpellBook`
2. 自定义属性修饰器
3. 做成自己的子类

例如思路如下：

```java
public class CorpseSpellBook extends SpellBook {
    public CorpseSpellBook() {
        super(SpellRarity.RARE, new Item.Properties().stacksTo(1));
    }
}
```

后续再逐步扩展。

---

### 3.3 魔法书 Curios 渲染

如果你希望玩家装备后能像 Iron's Spellbooks 那样在身上显示魔法书，需要在客户端注册 Curio 渲染器。

示例：

```java
import io.redspace.ironsspellbooks.render.SpellBookCurioRenderer;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

// 在客户端注册逻辑中调用
CuriosRendererRegistry.register(ModItems.CORPSE_SPELL_BOOK.get(), SpellBookCurioRenderer::new);
```

这通常放在客户端初始化或客户端事件订阅中。

---

## 4. 资源文件准备

不管是流派、法术还是魔法书，最后都离不开资源文件。

---

### 4.1 语言文件

`src/main/resources/assets/corpse_campus/lang/zh_cn.json`

示例：

```json
{
  "school.corpse_campus.corpse": "尸骸",
  "spell.corpse_campus.corpse_explosion": "尸爆术",
  "item.corpse_campus.corpse_spell_book": "尸骸魔导书"
}
```

如果你还有法术说明文本，也要继续加。

---

### 4.2 法术图标

Iron's Spellbooks 的法术图标通常可放在：

```text
assets/corpse_campus/textures/mob_effect/corpse_explosion.png
```

---

### 4.3 流派图标

```text
assets/corpse_campus/textures/gui/school_corpse.png
```

---

### 4.4 魔法书物品纹理

```text
assets/corpse_campus/textures/item/corpse_spell_book.png
```

---

### 4.5 魔法书模型

```text
assets/corpse_campus/models/item/corpse_spell_book.json
```

最基础模型写法：

```json
{
  "parent": "item/generated",
  "textures": {
    "layer0": "corpse_campus:item/corpse_spell_book"
  }
}
```

---

## 5. 在主类中注册

最后别忘了把这些注册类都接到主模组类上。

```java
public class CorpseCampusMod {
    public CorpseCampusMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModSchools.register(modEventBus);
        ModSpells.register(modEventBus);
        ModItems.register(modEventBus);
    }
}
```

---

## 6. 推荐的实际开发顺序

为了少踩坑，建议按照下面顺序来做：

### 第一步：先把开发环境配通
- `libs/` 放好依赖
- `build.gradle` 引入
- `mods.toml` 声明依赖
- 客户端 run 能启动

### 第二步：先做 School
- 注册一个自己的流派
- 确认图标和名称能正确显示

### 第三步：先做一个最简单的 Spell
- 用 `INSTANT` 类型
- 先只做简单伤害或粒子效果
- 确保法术能注册并显示

### 第四步：再做 SpellBook
- 先做基础 `SpellBook`
- 后续再扩展属性和渲染

### 第五步：最后再做复杂引导法术
- 持续施法
- 多段结算
- 特效与实体协同

---

## 7. 参考关系总结

### Iron's Spellbooks 提供的核心类
- `AbstractSpell`
- `SchoolType`
- `SpellBook`
- `SimpleAttributeSpellBook`

### Twice_Block_Fight 提供的参考点
- `TBFSpells`：法术注册
- `TBFSchoolRegistry`：流派注册
- 各 `xxxSpell`：复杂法术实现案例
- `build.gradle`：本地 libs 依赖写法

---

## 8. 最后建议

如果你接下来要在 `corpse_campus` 里真正开始做内容，建议先落一个最小可用组合：

1. 一个新流派：`corpse`
2. 一个基础法术：`corpse_explosion`
3. 一本基础魔法书：`corpse_spell_book`

先确保：

- 能编译
- 能进游戏
- 能显示名称/图标
- 法术能释放
- 魔法书能注册

在这个基础上，再继续扩展更复杂的技能树、流派机制和专属魔法书。

这样比一开始直接做完整体系更稳。