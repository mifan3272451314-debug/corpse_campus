package com.mifan.spell.runtime;

public enum MinionMode {
    STAND,
    FOLLOW,
    PATROL,
    AGGRESSIVE,
    RETREAT;

    public byte toByte() {
        return (byte) ordinal();
    }

    public static MinionMode fromByte(byte b) {
        int idx = b & 0xFF;
        MinionMode[] values = values();
        if (idx < 0 || idx >= values.length) {
            return FOLLOW;
        }
        return values[idx];
    }

    public MinionMode cycleNext() {
        MinionMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public String translationKey() {
        return "mode.corpse_campus.necromancer." + name().toLowerCase();
    }
}
