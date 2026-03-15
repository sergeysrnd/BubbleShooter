package com.kilocade.bubbleshooter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kilocade.bubbleshooter.Bubble.GridPosition;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProjectilePhysicsTest {

    @Test
    void wallBouncePreservesRemainingTravelWithinStep() {
        Bubble projectile = new Bubble(BubbleColor.AZURE, BubbleKind.NORMAL, 20.0, 100.0, 10.0, -100.0, -50.0, null);

        ProjectilePhysics.AdvanceResult result = ProjectilePhysics.advance(
                projectile,
                0.30,
                200.0,
                10.0,
                List.of()
        );

        assertFalse(result.anchored());
        assertTrue(result.bounced());
        assertEquals(30.0, result.projectile().x(), 0.02);
        assertEquals(85.0, result.projectile().y(), 0.02);
        assertEquals(100.0, result.projectile().vx(), 0.001);
        assertEquals(-50.0, result.projectile().vy(), 0.001);
    }

    @Test
    void wallBounceCanStillAnchorInsideSameStep() {
        Bubble projectile = new Bubble(BubbleColor.ROSE, BubbleKind.NORMAL, 20.0, 100.0, 10.0, -140.0, -80.0, null);
        Bubble anchor = Bubble.anchored(BubbleColor.ROSE, 38.0, 73.0, 10.0, new GridPosition(2, 1));

        ProjectilePhysics.AdvanceResult result = ProjectilePhysics.advance(
                projectile,
                0.35,
                200.0,
                10.0,
                List.of(anchor)
        );

        assertTrue(result.anchored());
        assertTrue(result.bounced());
        assertEquals(new GridPosition(2, 1), result.anchorHint());
    }
}
