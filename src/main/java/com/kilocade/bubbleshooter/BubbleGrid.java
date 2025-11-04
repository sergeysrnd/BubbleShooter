package com.kilocade.bubbleshooter;

import com.kilocade.bubbleshooter.Bubble.GridPosition;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

/**
 * Manages the staggered (hexagonal) grid of anchored bubbles: creation, placement,
 * neighborhood discovery, popping logic, floating detection, and row insertion.
 */
public final class BubbleGrid {

    private static final Random RNG = new SecureRandom();
    private static final double SQRT_THREE = Math.sqrt(3.0);

    private final int columns;
    private final double bubbleRadius;
    private final double leftPadding;
    private final double topPadding;
    private final double horizontalSpacing;
    private final double verticalSpacing;
    private final int initialRows;
    private int colorCount;

    private final List<List<Bubble>> rows = new ArrayList<>();

    /**
     * Constructs a hex grid with fixed column count and staggered layout.
     *
     * @param columns      number of bubble slots per row.
     * @param bubbleRadius radius for each bubble (in scene units).
     * @param leftPadding  horizontal offset for the first column.
     * @param topPadding   vertical offset for the first row.
     */
    public BubbleGrid(int columns, double bubbleRadius, double leftPadding, double topPadding,
                      int initialRows, int colorCount) {
        this.columns = columns;
        this.bubbleRadius = bubbleRadius;
        this.leftPadding = leftPadding;
        this.topPadding = topPadding;
        this.initialRows = initialRows;
        this.colorCount = colorCount;
        this.horizontalSpacing = bubbleRadius * 2.0;
        this.verticalSpacing = bubbleRadius * SQRT_THREE;
        seedInitialRows();
    }

    private void seedInitialRows() {
        rows.clear();
        for (int row = 0; row < initialRows; row++) {
            rows.add(createRandomRow(row));
        }
    }

    /**
     * @return immutable snapshot of the logical rows for introspection/testing.
     */
    public List<List<Bubble>> rows() {
        return Collections.unmodifiableList(rows);
    }

    /**
     * @return iterable collection of all anchored bubbles.
     */
    public List<Bubble> anchoredBubbles() {
        return rows.stream()
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Inserts the projectile bubble into the nearest available cell and returns
     * the anchored instance (with updated coordinates and grid position).
     *
     * @param projectile bubble in motion.
     * @return anchored bubble if a valid cell was found.
     */
    public Optional<Bubble> snapBubble(Bubble projectile) {
        if (projectile == null) {
            return Optional.empty();
        }
        Optional<GridPosition> cell = findNearestEmpty(projectile.x(), projectile.y());
        if (cell.isEmpty()) {
            return Optional.empty();
        }
        GridPosition position = cell.orElseThrow();
        ensureRowExists(position.row());
        double snapX = cellCenterX(position.row(), position.column());
        double snapY = cellCenterY(position.row());
        Bubble anchored = projectile.snapTo(position, snapX, snapY);
        rows.get(position.row()).set(position.column(), anchored);
        return Optional.of(anchored);
    }

    /**
     * Performs breadth-first search to gather all bubbles matching the origin color.
     *
     * @param origin starting bubble.
     * @return connected cluster sharing the same color.
     */
    public Set<Bubble> collectCluster(Bubble origin) {
        if (origin == null) {
            return Set.of();
        }
        BubbleColor targetColor = origin.color();
        Set<Bubble> cluster = Collections.newSetFromMap(new IdentityHashMap<>());
        Queue<Bubble> queue = new ArrayDeque<>();
        queue.add(origin);
        while (!queue.isEmpty()) {
            Bubble current = queue.poll();
            if (current == null || current.color() != targetColor || !cluster.add(current)) {
                continue;
            }
            neighbors(current).stream()
                    .filter(neighbor -> neighbor.color() == targetColor)
                    .forEach(queue::add);
        }
        return cluster;
    }

    /**
     * Identifies bubbles that are no longer connected to the ceiling after a pop.
     *
     * @return set of floating bubbles that should fall/vanish.
     */
    public Set<Bubble> collectFloatingBubbles() {
        if (rows.isEmpty()) {
            return Set.of();
        }
        Set<Bubble> attached = Collections.newSetFromMap(new IdentityHashMap<>());
        Queue<GridPosition> queue = new ArrayDeque<>();
        List<Bubble> topRow = rows.get(0);
        for (int col = 0; col < topRow.size(); col++) {
            Bubble bubble = topRow.get(col);
            if (bubble != null && attached.add(bubble)) {
                queue.add(new GridPosition(0, col));
            }
        }
        while (!queue.isEmpty()) {
            GridPosition position = queue.poll();
            for (GridPosition neighborPos : neighborPositions(position)) {
                Bubble neighbor = getBubble(neighborPos);
                if (neighbor != null && attached.add(neighbor)) {
                    queue.add(neighborPos);
                }
            }
        }
        Set<Bubble> floating = Collections.newSetFromMap(new IdentityHashMap<>());
        anchoredBubbles().forEach(floating::add);
        floating.removeAll(attached);
        return floating;
    }

    /**
     * Removes the supplied bubbles from the logical grid.
     */
    public void removeBubbles(Set<Bubble> toRemove) {
        if (toRemove == null || toRemove.isEmpty()) {
            return;
        }
        for (int row = 0; row < rows.size(); row++) {
            List<Bubble> rowData = rows.get(row);
            for (int col = 0; col < rowData.size(); col++) {
                Bubble bubble = rowData.get(col);
                if (bubble != null && toRemove.contains(bubble)) {
                    rowData.set(col, null);
                }
            }
        }
        trimBottomRows();
    }

    /**
     * Inserts a new random row at the top and shifts every existing bubble down.
     */
    public void pushNewRow(int colors) {
        this.colorCount = colors;
        rows.add(0, createRandomRow(0));
        for (int row = 0; row < rows.size(); row++) {
            List<Bubble> rowData = rows.get(row);
            for (int col = 0; col < rowData.size(); col++) {
                Bubble bubble = rowData.get(col);
                if (bubble == null) {
                    continue;
                }
                GridPosition position = new GridPosition(row, col);
                double x = cellCenterX(row, col);
                double y = cellCenterY(row);
                rowData.set(col, bubble.snapTo(position, x, y));
            }
        }
    }

    /**
     * Checks whether any anchored bubble has reached or crossed the provided bottom boundary.
     */
    public boolean hasReachedBottom(double bottomY) {
        return anchoredBubbles().stream()
                .anyMatch(bubble -> bubble.y() + bubble.radius() >= bottomY);
    }

    /**
     * Provides neighboring bubbles by grid adjacency (up to six).
     */
    public List<Bubble> neighbors(Bubble bubble) {
        if (bubble == null || bubble.gridPosition() == null) {
            return List.of();
        }
        List<Bubble> neighbors = new ArrayList<>(6);
        for (GridPosition position : neighborPositions(bubble.gridPosition())) {
            Bubble candidate = getBubble(position);
            if (candidate != null) {
                neighbors.add(candidate);
            }
        }
        return neighbors;
    }

    private Optional<GridPosition> findNearestEmpty(double x, double y) {
        int estimatedRow = Math.max(0, (int) Math.round((y - topPadding) / verticalSpacing));
        int minRow = Math.max(0, estimatedRow - 2);
        int maxRow = Math.max(rows.size(), estimatedRow + 2);
        GridPosition best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int row = minRow; row <= maxRow; row++) {
            int cols = columnsInRow(row);
            for (int col = 0; col < cols; col++) {
                if (isOccupied(row, col)) {
                    continue;
                }
                double centerX = cellCenterX(row, col);
                double centerY = cellCenterY(row);
                double distance = Math.hypot(centerX - x, centerY - y);
                if (best == null || distance < bestDistance) {
                    bestDistance = distance;
                    best = new GridPosition(row, col);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private boolean isOccupied(int row, int col) {
        if (row < 0 || col < 0) {
            return false;
        }
        if (row >= rows.size()) {
            return false;
        }
        List<Bubble> rowData = rows.get(row);
        if (col >= rowData.size()) {
            return false;
        }
        return rowData.get(col) != null;
    }

    private Bubble getBubble(GridPosition position) {
        return getBubble(position.row(), position.column());
    }

    private Bubble getBubble(int row, int col) {
        if (row < 0 || row >= rows.size()) {
            return null;
        }
        List<Bubble> rowData = rows.get(row);
        if (col < 0 || col >= rowData.size()) {
            return null;
        }
        return rowData.get(col);
    }

    private List<GridPosition> neighborPositions(GridPosition position) {
        int row = position.row();
        int col = position.column();
        int[][] deltasEven = {
                {-1, -1}, {-1, 0},
                {0, -1}, {0, 1},
                {1, -1}, {1, 0}
        };
        int[][] deltasOdd = {
                {-1, 0}, {-1, 1},
                {0, -1}, {0, 1},
                {1, 0}, {1, 1}
        };
        int[][] deltas = (row % 2 == 0) ? deltasEven : deltasOdd;
        List<GridPosition> neighbors = new ArrayList<>(deltas.length);
        for (int[] delta : deltas) {
            int nr = row + delta[0];
            int nc = col + delta[1];
            if (nr < 0 || nc < 0) {
                continue;
            }
            if (nc >= columnsInRow(nr)) {
                continue;
            }
            neighbors.add(new GridPosition(nr, nc));
        }
        return neighbors;
    }

    private void ensureRowExists(int row) {
        while (rows.size() <= row) {
            rows.add(createEmptyRow(rows.size()));
        }
    }

    private List<Bubble> createEmptyRow(int index) {
        int cols = columnsInRow(index);
        List<Bubble> row = new ArrayList<>(cols);
        for (int col = 0; col < cols; col++) {
            row.add(null);
        }
        return row;
    }

    private List<Bubble> createRandomRow(int index) {
        List<Bubble> row = createEmptyRow(index);
        for (int col = 0; col < row.size(); col++) {
            BubbleColor color = BubbleColor.fromIndex(RNG.nextInt(colorCount));
            double x = cellCenterX(index, col);
            double y = cellCenterY(index);
            row.set(col, Bubble.anchored(color, x, y, bubbleRadius, new GridPosition(index, col)));
        }
        return row;
    }

    private int columnsInRow(int row) {
        return columns;
    }

    private double cellCenterX(int row, int col) {
        double offset = (row % 2 == 0) ? 0.0 : bubbleRadius;
        return leftPadding + offset + col * horizontalSpacing;
    }

    private double cellCenterY(int row) {
        return topPadding + row * verticalSpacing;
    }

    private void trimBottomRows() {
        for (int row = rows.size() - 1; row >= initialRows; row--) {
            List<Bubble> rowData = rows.get(row);
            boolean hasBubble = rowData.stream().anyMatch(Objects::nonNull);
            if (hasBubble) {
                break;
            }
            rows.remove(row);
        }
    }
}