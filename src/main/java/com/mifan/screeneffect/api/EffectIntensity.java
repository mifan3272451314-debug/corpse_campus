package com.mifan.screeneffect.api;

public enum EffectIntensity {
    OFF(false, false, false, false, false, 0.0F),
    LOW(false, false, true, true, false, 0.6F),
    MEDIUM(false, true, true, true, true, 1.0F),
    HIGH(true, true, true, true, true, 1.25F);

    public final boolean postShader;
    public final boolean midground;
    public final boolean particles;
    public final boolean edgeGlow;
    public final boolean uiDistortion;
    public final float particleDensity;

    EffectIntensity(boolean postShader, boolean midground, boolean particles,
            boolean edgeGlow, boolean uiDistortion, float particleDensity) {
        this.postShader = postShader;
        this.midground = midground;
        this.particles = particles;
        this.edgeGlow = edgeGlow;
        this.uiDistortion = uiDistortion;
        this.particleDensity = particleDensity;
    }

    public boolean isEnabled() {
        return this != OFF;
    }
}
