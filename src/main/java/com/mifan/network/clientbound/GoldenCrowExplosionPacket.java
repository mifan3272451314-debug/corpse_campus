package com.mifan.network.clientbound;

import com.mifan.spell.AbilityClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 金乌太阳爆炸瞬间：7 圈震荡波 + 巨型冲击光环 + 冲天火柱 + 同心扩散全部由客户端本地生成。
 * 服务端只发一个包，替代原先 ~1900 个粒子 packet。
 */
public class GoldenCrowExplosionPacket {
    private final double x;
    private final double y;
    private final double z;
    private final double explosionRadius;
    private final double stunRadius;
    private final float particleScale;

    public GoldenCrowExplosionPacket(double x, double y, double z,
            double explosionRadius, double stunRadius, float particleScale) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.explosionRadius = explosionRadius;
        this.stunRadius = stunRadius;
        this.particleScale = particleScale;
    }

    public static void encode(GoldenCrowExplosionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeDouble(packet.x);
        buffer.writeDouble(packet.y);
        buffer.writeDouble(packet.z);
        buffer.writeDouble(packet.explosionRadius);
        buffer.writeDouble(packet.stunRadius);
        buffer.writeFloat(packet.particleScale);
    }

    public static GoldenCrowExplosionPacket decode(FriendlyByteBuf buffer) {
        return new GoldenCrowExplosionPacket(
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readFloat());
    }

    public static void handle(GoldenCrowExplosionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> AbilityClientHandler.handleGoldenCrowExplosion(packet)));
        context.setPacketHandled(true);
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public double getExplosionRadius() { return explosionRadius; }
    public double getStunRadius() { return stunRadius; }
    public float getParticleScale() { return particleScale; }
}
