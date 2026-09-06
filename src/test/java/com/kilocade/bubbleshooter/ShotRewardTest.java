package com.kilocade.bubbleshooter;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class ShotRewardTest {
    @Test void bankingOnlyRewardsSuccessfulShots() {
        assertEquals(0, ShotReward.calculate(2, 0, 0, true).total());
        var direct = ShotReward.calculate(3, 0, 1, false);
        var bank = ShotReward.calculate(3, 0, 1, true);
        assertEquals(305, direct.total());
        assertEquals(150, bank.total() - direct.total());
        assertEquals(150, bank.ricochetBonus());
    }

    @Test void avalancheStartsAtFiveAndStacksWithBankBonus() {
        assertEquals(0, ShotReward.calculate(3, 4, 1, true).avalancheBonus());
        var avalanche = ShotReward.calculate(3, 5, 1, true);
        assertEquals(250, avalanche.avalancheBonus());
        assertEquals(1305, avalanche.total());
        assertTrue(ShotReward.calculate(3, 5, 2, true).total() > avalanche.total());
    }
}
