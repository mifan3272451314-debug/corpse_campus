package com.mifan.anomaly.evolution;

import com.mifan.anomaly.EvolutionAwakeningService;
import com.mifan.anomaly.EvolutionCorpseBridge;
import com.mifan.corpsecampus;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 圣祈进化条件触发层：嫁接师 / 摆渡人。
 *
 * <h3>嫁接师（grafter）</h3>
 * 背包持嫁接师胚胎时，ISS 法力值 == 0 的那一 tick 触发转化。每 20 tick 检查一次。
 * 用 {@code MagicData.getPlayerMagicData(player).getMana() <= 0.0F} 判定。
 *
 * <h3>摆渡人（ferryman）</h3>
 * 背包持摆渡人胚胎时，半径 10 格内存在 ≥ 5 具 {@code CorpseEntity}（Corpse mod）即转化。
 * Corpse mod 未加载时降级为"永远不触发"（反射桥为 null）。每 20 tick 扫一次。
 */
@Mod.EventBusSubscriber(modid = corpsecampus.MODID)
public final class ShengqiEvolutionTrigger {

    public static final ResourceLocation GRAFTER =
            ResourceLocation.fromNamespaceAndPath(corpsecampus.MODID, "grafter");
    public static final ResourceLocation FERRYMAN =
            ResourceLocation.fromNamespaceAndPath(corpsecampus.MODID, "ferryman");

    public static final double FERRYMAN_RADIUS = 10.0D;
    public static final int FERRYMAN_REQUIRED_CORPSES = 5;

    private static final int CHECK_INTERVAL = 20;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % CHECK_INTERVAL != 0) {
            return;
        }

        // 嫁接师：检查 mana == 0
        if (XujingEvolutionTrigger.hasEmbryoFor(player, GRAFTER)) {
            MagicData data = MagicData.getPlayerMagicData(player);
            if (data != null && data.getMana() <= 0.0F) {
                EvolutionAwakeningService.transformEmbryoToCore(player, GRAFTER);
            }
        }

        // 摆渡人：扫描 10 格内 CorpseEntity 实例
        if (XujingEvolutionTrigger.hasEmbryoFor(player, FERRYMAN)) {
            Class<?> corpseClass = EvolutionCorpseBridge.CORPSE_ENTITY_CLASS;
            if (corpseClass != null) {
                AABB box = new AABB(player.blockPosition()).inflate(FERRYMAN_RADIUS);
                List<Entity> corpses = player.level().getEntities((Entity) null, box,
                        e -> corpseClass.isInstance(e) && e.isAlive());
                if (corpses.size() >= FERRYMAN_REQUIRED_CORPSES) {
                    EvolutionAwakeningService.transformEmbryoToCore(player, FERRYMAN);
                }
            }
        }
    }

    private ShengqiEvolutionTrigger() {
    }
}
