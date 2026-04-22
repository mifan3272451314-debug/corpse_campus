package com.mifan.anomaly;

public enum AnomalySpellRank {
    B(75, 25, 10.0D),
    A(150, 50, 20.0D),
    S(250, 50, 40.0D);

    private final int manaBonus;
    private final double schoolBonusPercent;
    private final double healthBonus;

    AnomalySpellRank(int manaBonus, double schoolBonusPercent, double healthBonus) {
        this.manaBonus = manaBonus;
        this.schoolBonusPercent = schoolBonusPercent;
        this.healthBonus = healthBonus;
    }

    public int getManaBonus() {
        return manaBonus;
    }

    public double getSchoolBonusPercent() {
        return schoolBonusPercent;
    }

    public double getHealthBonus() {
        return healthBonus;
    }
}
