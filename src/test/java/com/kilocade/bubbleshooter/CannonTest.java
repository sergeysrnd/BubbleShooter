package com.kilocade.bubbleshooter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CannonTest {

    @Test
    void fireUsesAmmoKindAndColor() {
        Cannon cannon = new Cannon(100.0, 200.0);
        BubbleAmmo ammo = new BubbleAmmo(BubbleColor.GOLD, BubbleKind.PULSE);

        Bubble bubble = cannon.fire(ammo, 18.0, 500.0);

        assertEquals(BubbleColor.GOLD, bubble.color());
        assertEquals(BubbleKind.PULSE, bubble.kind());
        assertEquals(100.0, bubble.x());
        assertEquals(200.0, bubble.y());
    }

    @Test
    void aimIsClampedWithinPlayableArc() {
        Cannon cannon = new Cannon(100.0, 200.0);

        cannon.aimAt(400.0, 260.0);
        assertTrue(cannon.angleDegrees() >= 10.0);

        cannon.aimAt(-200.0, 260.0);
        assertTrue(cannon.angleDegrees() <= 170.0);
    }
}
