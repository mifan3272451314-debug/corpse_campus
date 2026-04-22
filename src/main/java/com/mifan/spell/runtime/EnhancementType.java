package com.mifan.spell.runtime;

public enum EnhancementType {
    NONE,
    SPEED,
    ATTACK,
    DEFENSE,
    HEALTH;

    public boolean isEnhanced() {
        return this != NONE;
    }

    public byte toByte() {
        return (byte) ordinal();
    }

    public static EnhancementType fromByte(byte b) {
        int idx = b & 0xFF;
        EnhancementType[] values = values();
        if (idx < 0 || idx >= values.length) {
            return NONE;
        }
        return values[idx];
    }

    public static EnhancementType fromOrdinalSafe(int ordinal) {
        EnhancementType[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return NONE;
        }
        return values[ordinal];
    }
}
