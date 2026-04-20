# `AbilityRuntime` 拆分续作计划

## 1. 文档目标

本文档用于交接 [`AbilityRuntime.java`](src/main/java/com/mifan/spell/AbilityRuntime.java) 的后续拆分工作，供下一位开发者在**不改变当前技能效果**、**不修改对外调用方式**、**可分阶段提交**的前提下继续推进重构。

当前已完成的拆分：

- [`RecorderOfficerRuntime.java`](src/main/java/com/mifan/spell/runtime/RecorderOfficerRuntime.java)
- [`DominanceRuntime.java`](src/main/java/com/mifan/spell/runtime/DominanceRuntime.java)

当前策略是：

1. 保留 [`AbilityRuntime.java`](src/main/java/com/mifan/spell/AbilityRuntime.java) 作为兼容门面。
2. 将技能相关逻辑迁入独立 runtime 类。
3. 由 [`AbilityRuntime.java`](src/main/java/com/mifan/spell/AbilityRuntime.java) 继续提供原有静态方法，对外仅做委托。
4. 每次只拆一个或一组高度内聚的技能，拆完立即编译验证。

---

## 2. 已确认的重构原则

### 2.1 不改行为

以下内容默认**不在拆分阶段修改**：

- 技能数值
- 技能持续时间
- 冷却时间
- 网络包协议字段
- NBT 键名
- 客户端表现
- 音效/粒子表现
- 现有法术类对 [`AbilityRuntime`](src/main/java/com/mifan/spell/AbilityRuntime.java) 的调用方式

### 2.2 允许的改动范围

拆分阶段允许进行以下低风险调整：

- 把方法迁移到新的 runtime 类
- 将 [`AbilityRuntime.java`](src/main/java/com/mifan/spell/AbilityRuntime.java) 中的方法改成委托调用
- 抽取仅被单一技能使用的私有辅助方法
- 用等价的数据结构替代低效实现，例如 `List` → `Set`
- 去除重复逻辑，但不能改变语义

### 2.3 每次提交的验收条件

每个拆分提交至少满足：

1. [`gradlew.bat`](gradlew.bat) `compileJava` 通过
2. 原有调用点无需整体改写
3. 运行时 NBT key 不变
4. 同一技能相关方法尽量在一个 runtime 文件内闭环

---

## 3. 建议目录结构

建议继续沿用当前目录：[`src/main/java/com/mifan/spell/runtime/`](src/main/java/com/mifan/spell/runtime/)

目标结构建议如下：

```text
com/mifan/spell/
  AbilityRuntime.java                  // 兼容门面，逐步瘦身
  runtime/
    DominanceRuntime.java              // 已完成
    RecorderOfficerRuntime.java        // 已完成
    TelekinesisRuntime.java            // 待拆
    NecroticRuntime.java               // 待拆
    MarkRuntime.java                   // 待拆
    ExecutionerRuntime.java            // 待拆
    DaiyueRuntime.java                 // 待拆
    ElementalDomainRuntime.java        // 待拆
    runtime/common/
      AbilityTags.java                 // 最后阶段再做
      EntityQueries.java               // 最后阶段再做
```

说明：

- 不建议一开始就抽 [`AbilityTags.java`](src/main/java/com/mifan/spell/AbilityRuntime.java) 或 [`EntityQueries.java`](src/main/java/com/mifan/spell/AbilityRuntime.java)，因为当前拆分仍处于“稳定迁移优先”阶段。
- 先按技能闭环拆分，最后再处理公共层抽象，风险更低。

---

## 4. 下一阶段推荐拆分顺序

按“低耦合优先、风险递增”的顺序建议如下。

### Phase 1：低风险继续拆分

#### 4.1 `Telekinesis`

建议新建：[`TelekinesisRuntime.java`](src/main/java/com/mifan/spell/runtime/TelekinesisRuntime.java)

预计迁移内容：

- 与 `TAG_TELEKINESIS_*` 相关的方法
- 目标抓取、悬空、释放、视线向量存取
- 仅服务于念力的私有辅助方法

优先原因：

- NBT 键集中
- 行为闭环清晰
- 与其它技能的共享逻辑较少

风险点：

- 和 [`AbilityEventHandler.java`](src/main/java/com/mifan/spell/AbilityEventHandler.java) 的 tick / release 逻辑绑定较深
- 拆分时需特别检查“持续施法心跳刷新”是否被破坏

#### 4.2 `Mark`

建议新建：[`MarkRuntime.java`](src/main/java/com/mifan/spell/runtime/MarkRuntime.java)

预计迁移内容：

- `TAG_MARK_*` 相关方法
- 标记布置、触发、位置读取、清理

优先原因：

- 逻辑规模中等
- 单技能内聚程度高
- 改动范围可控

### Phase 2：中风险拆分

#### 4.3 `Executioner`

建议新建：[`ExecutionerRuntime.java`](src/main/java/com/mifan/spell/runtime/ExecutionerRuntime.java)

预计迁移内容：

- 武器校验
- 斩波伤害计算
- 地波/横斩判定
- 护盾压力积累相关逻辑

风险点：

- 粒子、音效、伤害流程较多
- 方法之间调用链相对复杂

#### 4.4 `Daiyue`

建议新建：[`DaiyueRuntime.java`](src/main/java/com/mifan/spell/runtime/DaiyueRuntime.java)

预计迁移内容：

- 冲刺轨迹
- 命中判定
- 粒子与 slash 特效
- 路径采样相关辅助方法

风险点：

- 位置推进与命中判定耦合较高
- 对视觉效果和打击感敏感

说明：

- 本模块已经做过一次 `Set` 去重优化，后续拆分时不要回退。

### Phase 3：高风险模块

#### 4.5 `Necrotic`

建议新建：[`NecroticRuntime.java`](src/main/java/com/mifan/spell/runtime/NecroticRuntime.java)

预计迁移内容：

- `TAG_NECROTIC_*` 相关方法
- 丧尸化状态维护
- 治疗、回血、挑衅目标、最大生命值调整辅助逻辑

风险点：

- 与 [`AbilityEventHandler.java`](src/main/java/com/mifan/spell/AbilityEventHandler.java) 的死亡、受伤、击杀等事件强绑定
- 很容易在拆分时打断状态机连续性

#### 4.6 `ElementalDomain`

建议新建：[`ElementalDomainRuntime.java`](src/main/java/com/mifan/spell/runtime/ElementalDomainRuntime.java)

预计迁移内容：

- `TAG_ELEMENTAL_DOMAIN_*`
- `ElementalDomainState` 内部类
- 领域展开/恢复/方块替换
- 静态缓存映射与领域 tick 逻辑

风险点：

- 这是目前最重的模块之一
- 包含全局状态、三重循环、方块替换恢复、维度级恢复 tick
- 最容易出现性能与一致性问题

建议：

- 该模块拆分时单独一个提交
- 拆分前先补一轮注释，避免迁移时丢上下文

---

## 5. 具体执行模板

后续接手者每拆一个模块，可按下面流程执行。

### Step 1：锁定模块边界

先在 [`AbilityRuntime.java`](src/main/java/com/mifan/spell/AbilityRuntime.java) 中搜对应前缀：

- `TAG_TELEKINESIS_`
- `TAG_MARK_`
- `TAG_EXECUTIONER_`
- `TAG_NECROTIC_`
- `TAG_ELEMENTAL_DOMAIN_`

把以下内容一起打包迁移：

- NBT 相关入口方法
- tick 方法
- 清理方法
- 只被该技能使用的私有辅助方法

### Step 2：新建 runtime 类

新类保持规则：

- `final class`
- 私有构造函数
- 对外提供 `public static` 方法
- 不直接改外部调用者，只先保证能被 [`AbilityRuntime.java`](src/main/java/com/mifan/spell/AbilityRuntime.java) 委托

### Step 3：保留门面兼容层

在 [`AbilityRuntime.java`](src/main/java/com/mifan/spell/AbilityRuntime.java) 中：

- 保留原方法名
- 保留原参数签名
- 方法体仅委托给新 runtime

示例模式：

```java
public static void tickDominance(Player player) {
    DominanceRuntime.tick(player);
}
```

### Step 4：做仅限等价的局部优化

如果在迁移时发现明显低效点，只允许做**语义等价优化**，例如：

- 命中去重改 `Set`
- 重复字符串比较逻辑复用
- 把重复 `clamp` 抽成公共静态方法

禁止在这个步骤顺便做：

- 数值平衡调整
- 网络协议修改
- 客户端 UI 行为变更
- 文档口径修正

### Step 5：编译验证

每次拆分后执行：

[`gradlew.bat`](gradlew.bat) `compileJava`

如果通过，再继续下一模块。

---

## 6. 推荐优先搜索的调用点

继续拆分前，优先检查以下文件中的直接调用：

- [`AbilityEventHandler.java`](src/main/java/com/mifan/spell/AbilityEventHandler.java)
- [`DominanceSpell.java`](src/main/java/com/mifan/spell/yuzhe/DominanceSpell.java)
- [`TelekinesisSpell.java`](src/main/java/com/mifan/spell/yuzhe/TelekinesisSpell.java)
- [`RecorderOfficerSpell.java`](src/main/java/com/mifan/spell/xujing/RecorderOfficerSpell.java)
- [`SetDominanceTargetPacket.java`](src/main/java/com/mifan/network/serverbound/SetDominanceTargetPacket.java)
- [`SetRecorderOfficerTimerPacket.java`](src/main/java/com/mifan/network/serverbound/SetRecorderOfficerTimerPacket.java)

目的不是修改这些文件，而是确认：

1. 原签名仍被兼容
2. 没有遗漏的静态方法入口
3. 拆分后不会出现死代码或循环依赖

---

## 7. 当前不建议立即做的事情

以下事项建议等拆分稳定后再处理：

### 7.1 抽公共 `AbilityTags`

虽然从结构上合理，但目前 NBT 常量还分散在 [`AbilityRuntime.java`](src/main/java/com/mifan/spell/AbilityRuntime.java) 可降低迁移成本。太早统一抽取会导致：

- 修改面过大
- 冲突增多
- 接手者更难判断行为变化来自“拆分”还是“常量迁移”

### 7.2 抽公共 `EntityQueries`

目前 [`findTargetInSight()`](src/main/java/com/mifan/spell/AbilityRuntime.java) 与 [`findLivingEntityById()`](src/main/java/com/mifan/spell/AbilityRuntime.java) 仍放在 [`AbilityRuntime.java`](src/main/java/com/mifan/spell/AbilityRuntime.java) 中是可接受的。

建议等 3~4 个 runtime 类稳定后，再统一抽公共查询工具。

### 7.3 改网络安全校验

这是重要工作，但属于“功能正确性/安全性修复”，不是“纯拆分”。

如果交给另一个人继续拆分，建议不要和以下改动混在同一个提交：

- serverbound 参数校验
- 距离检查
- 权限检查
- 包 ID 兼容策略调整

---

## 8. 建议提交粒度

推荐按如下粒度提交：

1. `refactor: extract Telekinesis runtime from AbilityRuntime`
2. `refactor: extract Mark runtime from AbilityRuntime`
3. `refactor: extract Executioner runtime from AbilityRuntime`
4. `refactor: extract Daiyue runtime from AbilityRuntime`
5. `refactor: extract Necrotic runtime from AbilityRuntime`
6. `refactor: extract ElementalDomain runtime from AbilityRuntime`
7. `refactor: move shared tags and entity queries to common runtime utils`

好处：

- 每次回滚范围小
- 审查成本低
- 一旦某模块出现行为偏差，定位更快

---

## 9. 交接结论

下一位开发者继续拆分时，优先目标不是“做大重构”，而是：

1. 持续缩小 [`AbilityRuntime.java`](src/main/java/com/mifan/spell/AbilityRuntime.java) 的职责
2. 每次只迁移一个技能闭环
3. 保持门面兼容
4. 只做等价优化
5. 每步都用编译通过作为最低验收标准

如果严格按本文档推进，可以在不破坏当前玩法效果的情况下，把 [`AbilityRuntime.java`](src/main/java/com/mifan/spell/AbilityRuntime.java) 逐步拆成可维护的按技能运行时结构。
