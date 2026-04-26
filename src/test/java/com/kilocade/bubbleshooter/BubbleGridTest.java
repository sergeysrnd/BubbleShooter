package com.kilocade.bubbleshooter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kilocade.bubbleshooter.Bubble.GridPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class BubbleGridTest {

    @Test
    void emptyGridReportsNoBubblesOrColors() {
        BubbleGrid grid = new BubbleGrid(4, 10.0, 100.0, 50.0);

        assertTrue(grid.isEmpty());
        assertTrue(grid.activeColors().isEmpty());
    }

    @Test
    void snapBubbleAnchorsIntoTopRowAndTracksItsColor() {
        BubbleGrid grid = new BubbleGrid(4, 10.0, 100.0, 50.0);
        Bubble projectile = Bubble.fired(BubbleColor.AZURE, 119.0, 52.0, 10.0, 0.0, 0.0);

        Bubble anchored = grid.snapBubble(projectile, null).orElseThrow();

        assertEquals(new GridPosition(0, 1), anchored.gridPosition());
        assertFalse(grid.isEmpty());
        assertEquals(1, grid.anchoredBubbles().size());
        assertEquals(Set.of(BubbleColor.AZURE), grid.activeColors());
    }

    @Test
    void prismConnectsSameColorCluster() {
        BubbleGrid grid = new BubbleGrid(5, 10.0, 100.0, 50.0);
        StageGenerator.StageLayout layout = new StageGenerator.StageLayout(
                1,
                "Test",
                "Test layout",
                5,
                List.of(BubbleColor.ROSE, BubbleColor.AZURE),
                List.of(
                        new ArrayList<>(List.of(BubbleAmmo.normal(BubbleColor.ROSE), BubbleAmmo.normal(BubbleColor.ROSE), BubbleAmmo.normal(BubbleColor.AZURE), BubbleAmmo.normal(BubbleColor.AZURE), BubbleAmmo.normal(BubbleColor.AZURE))),
                        new ArrayList<>(List.of(BubbleAmmo.normal(BubbleColor.AZURE), BubbleAmmo.normal(BubbleColor.ROSE), BubbleAmmo.normal(BubbleColor.AZURE), BubbleAmmo.normal(BubbleColor.AZURE), BubbleAmmo.normal(BubbleColor.AZURE)))
                )
        );
        layout.rows().get(0).set(2, null);
        layout.rows().get(0).set(3, null);
        layout.rows().get(0).set(4, null);
        layout.rows().get(1).set(0, null);
        layout.rows().get(1).set(2, null);
        layout.rows().get(1).set(3, null);
        layout.rows().get(1).set(4, null);
        grid.loadLayout(layout);

        Bubble prism = grid.snapBubble(
                        Bubble.fired(new BubbleAmmo(BubbleColor.AZURE, BubbleKind.PRISM), 140.0, 84.0, 10.0, 0.0, 0.0),
                        new GridPosition(0, 1))
                .orElseThrow();

        Set<Bubble> cluster = grid.collectColorCluster(prism, BubbleColor.ROSE);

        assertEquals(4, cluster.size());
    }

    @Test
    void hintedSnapPrefersImpactSideAndMatchingNeighbors() {
        BubbleGrid grid = new BubbleGrid(5, 10.0, 100.0, 50.0);
        StageGenerator.StageLayout layout = new StageGenerator.StageLayout(
                1,
                "Test",
                "Guided snap",
                5,
                List.of(BubbleColor.ROSE, BubbleColor.AZURE),
                List.of(
                        new ArrayList<>(java.util.Arrays.asList(BubbleAmmo.normal(BubbleColor.AZURE), BubbleAmmo.normal(BubbleColor.ROSE), null, null, null)),
                        new ArrayList<>(java.util.Arrays.asList(BubbleAmmo.normal(BubbleColor.ROSE), null, null, null, null))
                )
        );
        grid.loadLayout(layout);

        double targetX = grid.cellCenterX(1, 1) + 0.8;
        double targetY = grid.cellCenterY(1) + 0.6;
        Bubble anchored = grid.snapBubble(
                        Bubble.fired(BubbleColor.ROSE, targetX, targetY, 10.0, 0.0, 0.0),
                        new GridPosition(0, 1))
                .orElseThrow();

        assertEquals(new GridPosition(1, 1), anchored.gridPosition());
    }

    @Test
    void topImpactSnapsIntoLocalTopCellWhenItIsFree() {
        BubbleGrid grid = new BubbleGrid(5, 10.0, 100.0, 50.0);

        Bubble anchored = grid.snapBubble(
                        Bubble.fired(BubbleColor.AZURE, 139.4, 10.0, 10.0, 0.0, 0.0),
                        null)
                .orElseThrow();

        assertEquals(new GridPosition(0, 2), anchored.gridPosition());
    }

    @Test
    void topImpactDoesNotJumpToDistantFreeTopSlot() {
        BubbleGrid grid = new BubbleGrid(5, 10.0, 100.0, 50.0);
        StageGenerator.StageLayout layout = new StageGenerator.StageLayout(
                1,
                "Test",
                "Top impact",
                5,
                List.of(BubbleColor.ROSE, BubbleColor.AZURE),
                List.of(
                        new ArrayList<>(java.util.Arrays.asList(null, BubbleAmmo.normal(BubbleColor.ROSE), BubbleAmmo.normal(BubbleColor.ROSE), null, null))
                )
        );
        grid.loadLayout(layout);

        Bubble anchored = grid.snapBubble(
                        Bubble.fired(BubbleColor.ROSE, 130.0, 10.0, 10.0, 0.0, 0.0),
                        null)
                .orElseThrow();

        assertEquals(new GridPosition(1, 1), anchored.gridPosition());
    }

    @Test
    void ricochetSnapPrefersIncomingLaneOverSideMass() {
        BubbleGrid grid = new BubbleGrid(6, 10.0, 100.0, 50.0);
        StageGenerator.StageLayout layout = new StageGenerator.StageLayout(
                1,
                "Test",
                "Ricochet geometry",
                5,
                List.of(BubbleColor.ROSE, BubbleColor.AZURE),
                List.of(
                        new ArrayList<>(java.util.Arrays.asList(BubbleAmmo.normal(BubbleColor.ROSE), BubbleAmmo.normal(BubbleColor.ROSE), null, null, null, null)),
                        new ArrayList<>(java.util.Arrays.asList(null, null, null, null, null, null)),
                        new ArrayList<>(java.util.Arrays.asList(null, BubbleAmmo.normal(BubbleColor.ROSE), null, null, null, null))
                )
        );
        grid.loadLayout(layout);

        Bubble projectile = Bubble.fired(BubbleColor.ROSE, 0.0, 0.0, 10.0, 220.0, Math.toRadians(42.0))
                .withPosition(105.5, 70.9);

        Bubble anchored = grid.snapBubble(projectile, new GridPosition(2, 1)).orElseThrow();

        assertEquals(new GridPosition(1, 0), anchored.gridPosition());
    }

    @Test
    void ricochetSnapStaysOnOuterContactInsteadOfDeeperColorMass() {
        BubbleGrid grid = new BubbleGrid(6, 10.0, 100.0, 50.0);
        StageGenerator.StageLayout layout = new StageGenerator.StageLayout(
                1,
                "Test",
                "Dense cloud",
                5,
                List.of(BubbleColor.ROSE, BubbleColor.AZURE),
                List.of(
                        new ArrayList<>(java.util.Arrays.asList(null, BubbleAmmo.normal(BubbleColor.ROSE), BubbleAmmo.normal(BubbleColor.ROSE), null, null, null)),
                        new ArrayList<>(java.util.Arrays.asList(BubbleAmmo.normal(BubbleColor.ROSE), null, BubbleAmmo.normal(BubbleColor.ROSE), null, null, null)),
                        new ArrayList<>(java.util.Arrays.asList(null, null, BubbleAmmo.normal(BubbleColor.ROSE), null, null, null))
                )
        );
        grid.loadLayout(layout);

        Bubble projectile = Bubble.fired(BubbleColor.ROSE, 0.0, 0.0, 10.0, 220.0, Math.toRadians(42.0))
                .withPosition(121.5, 77.6);

        Bubble anchored = grid.snapBubble(projectile, new GridPosition(2, 2)).orElseThrow();

        assertEquals(new GridPosition(2, 1), anchored.gridPosition());
    }

    @Test
    void hintedSnapUsesActualPhysicsContactInsteadOfNearbyDenseMass() {
        BubbleGrid grid = new BubbleGrid(6, 10.0, 100.0, 50.0);
        StageGenerator.StageLayout layout = new StageGenerator.StageLayout(
                1,
                "Test",
                "Hinted ricochet",
                5,
                List.of(BubbleColor.ROSE, BubbleColor.AZURE),
                List.of(
                        new ArrayList<>(java.util.Arrays.asList(BubbleAmmo.normal(BubbleColor.ROSE), BubbleAmmo.normal(BubbleColor.AZURE), BubbleAmmo.normal(BubbleColor.AZURE), null, null, null)),
                        new ArrayList<>(java.util.Arrays.asList(null, BubbleAmmo.normal(BubbleColor.AZURE), BubbleAmmo.normal(BubbleColor.AZURE), null, null, null)),
                        new ArrayList<>(java.util.Arrays.asList(null, null, BubbleAmmo.normal(BubbleColor.AZURE), null, null, null))
                )
        );
        grid.loadLayout(layout);

        Bubble projectile = Bubble.fired(BubbleColor.ROSE, 0.0, 0.0, 10.0, 220.0, Math.toRadians(42.0))
                .withPosition(grid.cellCenterX(0, 0) - 2.0, grid.cellCenterY(0) + 12.0);

        Bubble anchored = grid.snapBubble(projectile, new GridPosition(0, 0)).orElseThrow();

        assertTrue(grid.neighbors(anchored).stream()
                .anyMatch(neighbor -> new GridPosition(0, 0).equals(neighbor.gridPosition())));
    }

    @Test
    void hintedDenseInteriorImpactDoesNotTeleportToSurfaceCell() {
        BubbleGrid grid = new BubbleGrid(7, 10.0, 100.0, 50.0);
        List<List<BubbleAmmo>> rows = new ArrayList<>();
        for (int row = 0; row < 7; row++) {
            ArrayList<BubbleAmmo> line = new ArrayList<>();
            for (int col = 0; col < 7; col++) {
                line.add(BubbleAmmo.normal(BubbleColor.ROSE));
            }
            rows.add(line);
        }
        StageGenerator.StageLayout layout = new StageGenerator.StageLayout(
                1,
                "Test",
                "Dense block",
                7,
                List.of(BubbleColor.ROSE),
                rows
        );
        grid.loadLayout(layout);

        Bubble projectile = Bubble.fired(
                        BubbleColor.ROSE,
                        grid.cellCenterX(3, 3),
                        grid.cellCenterY(3),
                        10.0,
                        0.0,
                        0.0)
                .withPosition(grid.cellCenterX(3, 3), grid.cellCenterY(3));

        assertTrue(grid.snapBubble(projectile, new GridPosition(3, 3)).isEmpty());
    }

    @Test
    void collisionBubblesExcludeDenseInteriorCells() {
        BubbleGrid grid = new BubbleGrid(7, 10.0, 100.0, 50.0);
        List<List<BubbleAmmo>> rows = new ArrayList<>();
        for (int row = 0; row < 7; row++) {
            ArrayList<BubbleAmmo> line = new ArrayList<>();
            for (int col = 0; col < 7; col++) {
                line.add(BubbleAmmo.normal(BubbleColor.ROSE));
            }
            rows.add(line);
        }
        StageGenerator.StageLayout layout = new StageGenerator.StageLayout(
                1,
                "Test",
                "Dense block",
                7,
                List.of(BubbleColor.ROSE),
                rows
        );
        grid.loadLayout(layout);

        assertTrue(grid.collisionBubbles().stream()
                .noneMatch(bubble -> new GridPosition(3, 3).equals(bubble.gridPosition())));
        assertTrue(grid.collisionBubbles().stream()
                .anyMatch(bubble -> new GridPosition(6, 3).equals(bubble.gridPosition())));
    }
}
