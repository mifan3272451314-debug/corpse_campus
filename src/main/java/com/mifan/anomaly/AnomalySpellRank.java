package com.mifan.anomaly;

public enum AnomalySpellRank {
    B(75, 25),
    A(150, 50),
    S(250, 50);

    private final int manaBonus;
    private final double schoolBonusPercent;

    AnomalySpellRank(int manaBonus, double schoolBonusPercent) {
        this.manaBonus = manaBonus;
        this.schoolBonusPercent = schoolBonusPercent;
    }

    public int getManaBonus() {
        return manaBonus;
    }

    public double getSchoolBonusPercent() {
        return schoolBonusPercent;
    }
}
