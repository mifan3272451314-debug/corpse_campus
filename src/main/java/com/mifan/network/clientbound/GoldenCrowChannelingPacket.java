package com.mifan.network.clientbound;

import com.mifan.spell.AbilityClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 金乌太阳引导期视觉：服务端每 tick 发一条，客户端本地生成向心粒子漩涡 + 地面日轮。
 */
public class GoldenCrowChannelingPacket {
    private final double centerX;
    private final double centerY;
    private final double centerZ;
    private final double groundY;
    private final float progress;
    private final double targetOrb;
    private final float particleScale;
    private final int elapsed;

    public GoldenCrowChannelingPacket(double centerX, double centerY, double centerZ, double groundY,
            float progress, double targetOrb, float particleScale, int elapsed) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.groundY = groundY;
        this.progress = progress;
        this.targetOrb = targetOrb;
        this.particleScale = particleScale;
        this.elapsed = elapsed;
    }

    public static void encode(GoldenCrowChannelingPacket packet, FriendlyByteBuf buffer) {
        buffer.writeDouble(packet.centerX);
        buffer.writeDouble(packet.centerY);
        buffer.writeDouble(packet.centerZ);
        buffer.writeDouble(packet.groundY);
        buffer.writeFloat(packet.progress);
        buffer.writeDouble(packet.targetOrb);
        buffer.writeFloat(packet.particleScale);
        buffer.writeVarInt(packet.elapsed);
    }

    public static GoldenCrowChannelingPacket decode(FriendlyByteBuf buffer) {
        return new GoldenCrowChannelingPacket(
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readFloat(), buffer.readDouble(), buffer.readFloat(), buffer.readVarInt());
    }

    public static void handle(GoldenCrowChannelingPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> AbilityClientHandler.handleGoldenCrowChanneling(packet)));
        context.setPacketHandled(true);
    }

    public double getCenterX() { return centerX; }
    public double getCenterY() { return centerY; }
    public double getCenterZ() { return centerZ; }
    public double getGroundY() { return groundY; }
    public float getProgress() { return progress; }
    public double getTargetOrb() { return targetOrb; }
    public float getParticleScale() { return particleScale; }
    public int getElapsed() { return elapsed; }
}
