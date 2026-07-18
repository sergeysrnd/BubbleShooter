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

    @Test
    void overlappingStartAnchorsBeforeProjectileCanTunnelIntoCloud() {
        Bubble projectile = new Bubble(BubbleColor.ROSE, BubbleKind.NORMAL, 39.0, 73.0, 10.0, 120.0, -40.0, null);
        Bubble surfaceAnchor = Bubble.anchored(BubbleColor.ROSE, 38.0, 73.0, 10.0, new GridPosition(2, 1));
        Bubble deeperAnchor = Bubble.anchored(BubbleColor.ROSE, 58.0, 67.0, 10.0, new GridPosition(2, 2));

        ProjectilePhysics.AdvanceResult result = ProjectilePhysics.advance(
                projectile,
                0.16,
                200.0,
                10.0,
                List.of(surfaceAnchor, deeperAnchor)
        );

        assertTrue(result.anchored());
        assertEquals(new GridPosition(2, 1), result.anchorHint());
        assertEquals(projectile.x(), result.projectile().x(), 0.001);
        assertEquals(projectile.y(), result.projectile().y(), 0.001);
    }

    @Test
    void overlappingStartKeepsSurfaceAnchorEvenWhenDeeperCenterIsCloser() {
        Bubble projectile = new Bubble(BubbleColor.ROSE, BubbleKind.NORMAL, 5.0, 50.0, 5.0, 100.0, 0.0, null);
        Bubble surfaceAnchor = Bubble.anchored(BubbleColor.ROSE, 0.0, 50.0, 5.0, new GridPosition(1, 0));
        Bubble deeperAnchor = Bubble.anchored(BubbleColor.ROSE, 9.0, 50.0, 5.0, new GridPosition(1, 1));

        ProjectilePhysics.AdvanceResult result = ProjectilePhysics.advance(
                projectile,
                0.10,
                120.0,
                5.0,
                List.of(surfaceAnchor, deeperAnchor)
        );

        assertTrue(result.anchored());
        assertEquals(new GridPosition(1, 0), result.anchorHint());
    }

    @Test
    void gridContactWinsWhenItOccursAtTheSameInstantAsWallContact() {
        Bubble projectile = new Bubble(BubbleColor.GOLD, BubbleKind.NORMAL, 50.0, 80.0, 10.0, -100.0, 0.0, null);
        Bubble anchor = Bubble.anchored(BubbleColor.GOLD, 30.0, 80.0, 10.0, new GridPosition(1, 0));

        ProjectilePhysics.AdvanceResult result = ProjectilePhysics.advance(
                projectile,
                0.5,
                200.0,
                10.0,
                List.of(anchor)
        );

        assertTrue(result.anchored());
        assertFalse(result.bounced());
        assertEquals(new GridPosition(1, 0), result.anchorHint());
    }

    @Test
    void denseCloudCannotBeSkippedDuringAHighSpeedStep() {
        Bubble projectile = new Bubble(BubbleColor.AZURE, BubbleKind.NORMAL, 10.0, 60.0, 10.0, 300.0, 0.0, null);
        Bubble front = Bubble.anchored(BubbleColor.AZURE, 70.0, 60.0, 10.0, new GridPosition(3, 0));
        Bubble behind = Bubble.anchored(BubbleColor.ROSE, 90.0, 60.0, 10.0, new GridPosition(3, 1));

        ProjectilePhysics.AdvanceResult result = ProjectilePhysics.advance(
                projectile,
                0.5,
                300.0,
                10.0,
                List.of(front, behind)
        );

        assertTrue(result.anchored());
        assertEquals(new GridPosition(3, 0), result.anchorHint());
        assertEquals(50.0, result.projectile().x(), 0.001);
    }

    @Test
    void seamCollisionUsesStableGridOrderRatherThanAnchoredListOrder() {
        Bubble projectile = new Bubble(BubbleColor.AZURE, BubbleKind.NORMAL, 50.0, 100.0, 10.0, 0.0, -100.0, null);
        Bubble left = Bubble.anchored(BubbleColor.ROSE, 40.0, 70.0, 10.0, new GridPosition(2, 4));
        Bubble right = Bubble.anchored(BubbleColor.ROSE, 60.0, 70.0, 10.0, new GridPosition(2, 5));

        ProjectilePhysics.AdvanceResult forward = ProjectilePhysics.advance(projectile, 0.5, 200.0, 10.0, List.of(left, right));
        ProjectilePhysics.AdvanceResult reverse = ProjectilePhysics.advance(projectile, 0.5, 200.0, 10.0, List.of(right, left));

        assertEquals(new GridPosition(2, 4), forward.anchorHint());
        assertEquals(forward.anchorHint(), reverse.anchorHint());
        assertEquals(ProjectilePhysics.ImpactKind.BUBBLE, forward.impactKind());
        assertEquals(forward.impactY(), reverse.impactY(), 0.000001);
    }

    @Test
    void anAdvanceCanResolveMoreThanSixWallBounces() {
        Bubble projectile = new Bubble(BubbleColor.GOLD, BubbleKind.NORMAL, 50.0, 100.0, 10.0, 1_000.0, 0.0, null);

        ProjectilePhysics.AdvanceResult result = ProjectilePhysics.advance(projectile, 1.0, 100.0, 10.0, List.of());

        assertFalse(result.anchored());
        assertTrue(result.bounced());
        assertEquals(90.0, result.projectile().x(), 0.000001);
        assertEquals(-1_000.0, result.projectile().vx(), 0.000001);
    }
}
