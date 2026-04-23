package com.mifan.admin;

import java.util.HashMap;
import java.util.Map;

/**
 * 指令元数据表:把 /magic 下每条指令映射到 (category, description)。
 *
 * 分类与说明严格遵循 {@code 管理员指令手册.md}——手册分类作为权威来源。
 * 未在此表出现的指令 fallback 为 ("other", "")。
 *
 * 新增指令时在这里加一行,GUI 和 /magic rules 相关提示会自动拾取。
 */
public final class CommandMetadata {

    public record Meta(String category, String description) {
    }

    private static final Map<String, Meta> TABLE = new HashMap<>();

    static {
        // 1. 书管理
        put("magic givebook", "book", "发放异常法术书并自动绑定到 Curios 书槽");
        put("magic forceequip", "book", "强制把异常书放回 Curios 书槽并同步客户端");
        put("magic fixbook", "book", "自动定位/绑定/补绑/放回书槽(givebook + forceequip 合并)");

        // 2. 法术管理
        put("magic add", "spell", "往玩家书上装载指定法术(按等级、数量)");
        put("magic spells", "spell", "列出玩家异常书上所有已装载的法术");
        put("magic clear", "spell", "从玩家书上清除指定法术(全部槽位)");
        put("magic clearall", "spell", "清空玩家异常书上所有法术");
        put("magic remove", "spell", "扣减玩家书上指定法术的等级(按次数)");

        // 3. 觉醒状态
        put("magic unawaken", "awaken", "取消玩家觉醒(清法术+释放名额,保留书)");
        put("magic setsequence", "awaken", "直接设置玩家主序列(xujing/rizhao/dongyue/yuzhe/shengqi/none=清除)");
        put("magic setrank", "awaken", "直接设置玩家最高位阶(B/A/S/none)");

        // 4. 状态与重算
        put("magic state", "state", "查询玩家异常状态(序列/位阶/属性/法力)");
        put("magic recalc", "state", "重算玩家异常属性加成(依据当前书内法术)");
        put("magic config", "state", "查看模组运行时配置(上限、代数等)");

        // 5. 法力值
        put("magic mana set", "mana", "设置玩家当前法力值(瞬时覆盖)");
        put("magic mana fill", "mana", "把玩家法力值填到上限");
        put("magic mana bonus", "mana", "覆盖玩家的法力上限加成(字段级设置)");

        // 6. 流派强化
        put("magic schoolbonus", "mana", "覆盖玩家书上指定流派的强化百分比");

        // 7. 异常特性吞噬物
        put("magic trait give", "trait", "发放异常特性吞噬物(5 序列 × B/A/S,共 15 种)");

        // 8. 上限系统
        put("magic limit info", "limit", "查看全服 40 人上限状态(开关/当前数/已觉醒玩家)");
        put("magic limit set", "limit", "运行时修改上限数值(持久化)");
        put("magic limit enable", "limit", "启用全服上限限制");
        put("magic limit disable", "limit", "关闭全服上限限制");
        put("magic limit recount", "limit", "重新扫描所有在线玩家,重建已觉醒集合");

        // 9. 生生不息封锁
        put("magic seal endless_life", "seal", "查看/设置生生不息的全局封锁状态");

        // 10. 回溯之虫镜像备份
        put("magic rewind backup create", "rewind", "启动玩家三阶段镜像备份(为回溯之虫准备)");
        put("magic rewind backup status", "rewind", "查询当前镜像备份扫描阶段/进度");
        put("magic rewind backup cancel", "rewind", "取消进行中的镜像备份任务");

        // 11. 每日刷新
        put("magic refresh all", "refresh", "全服刷新每次施放即锁的法术(日轮金乌等),generation +1");

        // 12. 查询类
        put("magic list schools", "list", "列出所有 5 个自定义流派及其 ResourceLocation");
        put("magic list spells", "list", "列出全部 35 个异常法术(可按流派过滤)");
        put("magic lookup", "list", "按中文名或英文 ID 反查法术元信息");
        put("magic who", "list", "列出当前全服已觉醒玩家清单");
        put("magic top", "list", "按指定维度(等级/法力)排行玩家");
        put("magic dump", "list", "导出玩家异常书全量 NBT 到聊天框");

        // 13. 帮助
        put("magic help", "help", "游戏内打印所有指令的完整说明");

        // 规则(本次新增)
        put("magic rules get", "rules", "列出所有规则配置项的当前值(不带参数)/查单条(带 key)");
        put("magic rules set", "rules", "修改单条规则配置(落盘)");
        put("magic rules reset", "rules", "把所有规则配置项恢复到默认值");
    }

    private static void put(String path, String cat, String desc) {
        TABLE.put(path, new Meta(cat, desc));
    }

    public static Meta lookup(String fullPath) {
        return TABLE.getOrDefault(fullPath, new Meta("other", ""));
    }

    private CommandMetadata() {
    }
}
