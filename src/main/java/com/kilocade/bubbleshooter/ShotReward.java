package com.kilocade.bubbleshooter;

/** Bonuses are awarded once per successful shot, never for empty ricochets. */
record ShotReward(int total, int ricochetBonus, int avalancheBonus) {
    static ShotReward calculate(int popped, int dropped, int combo, boolean ricochet) {
        if (popped < 0 || dropped < 0 || combo < 0) throw new IllegalArgumentException("Negative shot count");
        if (popped < 3) return new ShotReward(0, 0, 0);
        int bank = ricochet ? 150 : 0;
        int avalanche = dropped >= 5 ? 250 : 0;
        return new ShotReward(popped * 90 + dropped * 120 + combo * 35 + bank + avalanche, bank, avalanche);
    }
}
