package com.kilocade.bubbleshooter;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Boundary and frame-partition regressions, including the real offset arena. */
final class RicochetRegressionTest {
    private static final double R = 18;
    private static final ProjectilePhysics.Bounds BOUNDS = new ProjectilePhysics.Bounds(180, 738, 74);

    private Bubble shot(double x, double y, double vx, double vy) {
        return new Bubble(BubbleColor.AZURE, BubbleKind.NORMAL, x, y, R, vx, vy, null);
    }

    @Test
    void outwardContactAtEitherWallReflectsImmediately() {
        for (boolean left : List.of(true, false)) {
            double x = left ? BOUNDS.minX() : BOUNDS.maxX();
            double vx = left ? -400 : 400;
            var result = ProjectilePhysics.advance(shot(x, 400, vx, -100), .1, BOUNDS, R, List.of());
            assertTrue(result.bounced());
            assertEquals(x - vx * .1, result.impactX(), 1e-9);
            assertEquals(-vx, result.projectile().vx());
            assertEquals(390, result.impactY(), 1e-9);
        }
    }

    @Test
    void aContactInfinitesimallyAfterFrameStartIsNotDiscarded() {
        var result = ProjectilePhysics.advance(shot(180 + 1e-9, 400, -400, -100), .1, BOUNDS, R, List.of());
        assertTrue(result.bounced());
        assertEquals(220, result.impactX(), 1e-7);
    }

    @Test
    void inwardMotionAtWallDoesNotReflectAgain() {
        var result = ProjectilePhysics.advance(shot(180, 400, 400, -100), .1, BOUNDS, R, List.of());
        assertFalse(result.bounced());
        assertEquals(220, result.impactX(), 1e-9);
    }

    @Test
    void ceilingAtFrameStartAndBothExactCornersStick() {
        for (double x : new double[]{180, 459, 738}) {
            var result = ProjectilePhysics.advance(shot(x, 74, x < 459 ? -100 : 100, -100), .1, BOUNDS, R, List.of());
            assertTrue(result.anchored());
            assertFalse(result.bounced());
            assertEquals(ProjectilePhysics.ImpactKind.CEILING, result.impactKind());
            assertEquals(74, result.impactY());
        }
        for (boolean left : List.of(true, false)) {
            var result = ProjectilePhysics.advance(shot(left ? 280 : 638, 174, left ? -100 : 100, -100), 2, BOUNDS, R, List.of());
            assertTrue(result.anchored());
            assertFalse(result.bounced());
            assertEquals(left ? 180 : 738, result.impactX(), 1e-9);
            assertEquals(74, result.impactY(), 1e-9);
        }
    }

    @Test
    void tinyTimeStepsStillMoveAndOutsideStartsRecover() {
        var tiny = ProjectilePhysics.advance(shot(400, 300, 400, -100), 1e-7, BOUNDS, R, List.of());
        assertEquals(400.00004, tiny.impactX(), 1e-10);
        var outside = ProjectilePhysics.advance(shot(179.9, 300, -400, -100), .1, BOUNDS, R, List.of());
        assertEquals(220, outside.impactX(), 1e-9);
        assertTrue(outside.bounced());
    }

    @Test
    void thousandEmptyArenaShotsAgreeWithUnfoldedRayOracle() {
        Random random = new Random(20260906);
        double width = BOUNDS.maxX() - BOUNDS.minX();
        for (int i = 0; i < 1000; i++) {
            double angle = Math.toRadians(10 + random.nextDouble() * 160);
            Bubble shot = Bubble.fired(BubbleColor.AZURE, 180 + random.nextDouble() * width,
                    200 + random.nextDouble() * 600, R, 720, angle);
            double time = (shot.y() - BOUNDS.ceilingY()) / -shot.vy();
            double unfolded = shot.x() - BOUNDS.minX() + shot.vx() * time;
            double wrapped = ((unfolded % (2 * width)) + 2 * width) % (2 * width);
            double expected = BOUNDS.minX() + (wrapped <= width ? wrapped : 2 * width - wrapped);
            var result = ProjectilePhysics.advance(shot, time + .01, BOUNDS, R, List.of());
            assertTrue(result.anchored(), "Shot " + i);
            assertEquals(expected, result.impactX(), 1e-7, "Shot " + i);
            assertEquals(74, result.impactY(), 1e-7);
        }
    }

    @Test
    void realBoardsLandInSameCellRegardlessOfPreviewOrFrameDuration() {
        StageGenerator generator = new StageGenerator();
        Random random = new Random(1782);
        for (int i = 0; i < 500; i++) {
            BubbleGrid grid = new BubbleGrid(16, R, 180, 74);
            grid.loadLayout(generator.generate(1 + i % 12, 16, i));
            for (int wave = 0; wave < i % 4; wave++) {
                grid.pushPressureRow(List.copyOf(grid.activeColors()), 1 + i % 12);
            }
            Bubble shot = Bubble.fired(BubbleColor.AZURE, 459, 620, R, 720,
                    Math.toRadians(10 + random.nextDouble() * 160));
            var whole = ProjectilePhysics.advance(shot, 10, grid.projectileBounds(), R, grid.anchoredBubbles());
            var frames = fly(shot, grid, i % 2 == 0 ? .012 : 1.0 / 144);
            assertTrue(whole.anchored(), "Shot " + i);
            assertEquals(whole.anchorHint(), frames.anchorHint(), "Shot " + i);
            assertEquals(whole.impactX(), frames.impactX(), 1e-6);
            assertEquals(whole.impactY(), frames.impactY(), 1e-6);
            int before = grid.anchoredBubbles().size();
            var predicted = grid.attachmentCell(frames.projectile(), frames.anchorHint());
            assertTrue(predicted.isPresent(), "No slot for shot " + i);
            assertEquals(before, grid.anchoredBubbles().size(), "Preview changed the board");
            Bubble landed = grid.snapBubble(whole.projectile(), whole.anchorHint()).orElseThrow();
            assertEquals(predicted.orElseThrow(), landed.gridPosition());
            assertEquals(before + 1, grid.anchoredBubbles().size());
            assertTrue(landed.x() >= grid.projectileBounds().minX());
            assertTrue(landed.x() <= grid.projectileBounds().maxX());
            for (Bubble other : grid.anchoredBubbles()) {
                if (other != landed) assertTrue(Math.hypot(other.x() - landed.x(), other.y() - landed.y()) >= 2 * R - 1e-7);
            }
        }
    }

    @Test
    void translatingBoardAndShotTogetherPreservesCollision() {
        BubbleGrid grid = new BubbleGrid(16, R, 180, 74);
        grid.loadLayout(new StageGenerator().generate(1, 16, 42));
        Bubble shot = shot(459, 600, -600, -200);
        var before = ProjectilePhysics.advance(shot, 5, grid.projectileBounds(), R, grid.anchoredBubbles());
        grid.setLayout(330, 74);
        var after = ProjectilePhysics.advance(shot.withPosition(609, 600), 5, grid.projectileBounds(), R, grid.anchoredBubbles());
        assertEquals(before.anchorHint(), after.anchorHint());
        assertEquals(before.impactX() + 150, after.impactX(), 1e-7);
        assertEquals(before.impactY(), after.impactY(), 1e-7);
    }

    private ProjectilePhysics.AdvanceResult fly(Bubble shot, BubbleGrid grid, double dt) {
        var obstacles = grid.anchoredBubbles();
        for (int i = 0; i < 2000; i++) {
            var step = ProjectilePhysics.advance(shot, dt, grid.projectileBounds(), R, obstacles);
            if (step.anchored()) return step;
            assertTrue(step.impactX() >= grid.projectileBounds().minX() - 1e-8);
            assertTrue(step.impactX() <= grid.projectileBounds().maxX() + 1e-8);
            shot = step.projectile();
        }
        throw new AssertionError("Shot never landed");
    }
}
