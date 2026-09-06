package com.kilocade.bubbleshooter;

import com.kilocade.bubbleshooter.Bubble.GridPosition;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

/**
 * Deterministic hex-grid model for anchored bubbles.
 */
public final class BubbleGrid {

    private static final double SQRT_THREE = Math.sqrt(3.0);
    private final int columns;
    private final double bubbleRadius;
    private final double horizontalSpacing;
    private final double verticalSpacing;

    private double leftPadding;
    private double topPadding;

    private final List<List<Bubble>> rows = new ArrayList<>();

    public BubbleGrid(int columns, double bubbleRadius, double leftPadding, double topPadding) {
        this.columns = columns;
        this.bubbleRadius = bubbleRadius;
        this.leftPadding = leftPadding;
        this.topPadding = topPadding;
        this.horizontalSpacing = bubbleRadius * 2.0;
        this.verticalSpacing = bubbleRadius * SQRT_THREE;
    }

    public void clear() {
        rows.clear();
    }

    public void setLayout(double newLeftPadding, double newTopPadding) {
        leftPadding = newLeftPadding;
        topPadding = newTopPadding;
        refreshCoordinates();
    }

    public List<List<Bubble>> rows() {
        return Collections.unmodifiableList(rows);
    }

    public List<Bubble> anchoredBubbles() {
        return rows.stream()
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .toList();
    }

    public boolean isEmpty() {
        return anchoredBubbles().isEmpty();
    }

    public Set<BubbleColor> activeColors() {
        EnumSet<BubbleColor> colors = EnumSet.noneOf(BubbleColor.class);
        for (Bubble bubble : anchoredBubbles()) {
            colors.add(bubble.color());
        }
        return colors;
    }

    public void loadLayout(StageGenerator.StageLayout layout) {
        clear();
        for (int row = 0; row < layout.rows().size(); row++) {
            ensureRowExists(row);
            List<BubbleAmmo> sourceRow = layout.rows().get(row);
            for (int col = 0; col < Math.min(columnsInRow(row), sourceRow.size()); col++) {
                BubbleAmmo ammo = sourceRow.get(col);
                if (ammo == null) {
                    continue;
                }
                rows.get(row).set(col, Bubble.anchored(
                        ammo,
                        cellCenterX(row, col),
                        cellCenterY(row),
                        bubbleRadius,
                        new GridPosition(row, col)
                ));
            }
        }
        trimBottomRows(0);
    }

    public Optional<Bubble> snapBubble(Bubble projectile, GridPosition anchorHint) {
        Optional<GridPosition> cell = attachmentCell(projectile, anchorHint);
        if (cell.isEmpty()) {
            return Optional.empty();
        }

        GridPosition position = cell.orElseThrow();
        ensureRowExists(position.row());
        Bubble anchored = projectile.snapTo(position, cellCenterX(position.row(), position.column()), cellCenterY(position.row()));
        rows.get(position.row()).set(position.column(), anchored);
        return Optional.of(anchored);
    }

    /** Read-only placement query used by the trajectory preview and actual shot. */
    public Optional<GridPosition> attachmentCell(Bubble projectile, GridPosition anchorHint) {
        if (projectile == null) {
            return Optional.empty();
        }

        return anchorHint == null
                ? findTopImpactCell(projectile)
                : findAttachmentCell(projectile, anchorHint);
    }

    ProjectilePhysics.Bounds projectileBounds() {
        return new ProjectilePhysics.Bounds(leftPadding,
                leftPadding + (columns - 1) * horizontalSpacing + bubbleRadius, topPadding);
    }

    public Set<Bubble> collectColorCluster(Bubble origin, BubbleColor targetColor) {
        if (origin == null || targetColor == null) {
            return Set.of();
        }
        Set<Bubble> cluster = Collections.newSetFromMap(new IdentityHashMap<>());
        Queue<Bubble> queue = new ArrayDeque<>();
        queue.add(origin);

        while (!queue.isEmpty()) {
            Bubble bubble = queue.poll();
            if (bubble == null || !bubble.matchesClusterColor(targetColor) || !cluster.add(bubble)) {
                continue;
            }
            queue.addAll(neighbors(bubble));
        }
        return cluster;
    }

    public Set<Bubble> collectRadius(Bubble origin, int depth) {
        if (origin == null || depth < 0) {
            return Set.of();
        }
        Set<Bubble> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Queue<BubbleStep> queue = new ArrayDeque<>();
        queue.add(new BubbleStep(origin, 0));
        while (!queue.isEmpty()) {
            BubbleStep step = queue.poll();
            if (!visited.add(step.bubble()) || step.depth() >= depth) {
                continue;
            }
            for (Bubble neighbor : neighbors(step.bubble())) {
                queue.add(new BubbleStep(neighbor, step.depth() + 1));
            }
        }
        return visited;
    }

    public Set<Bubble> collectFloatingBubbles() {
        if (rows.isEmpty()) {
            return Set.of();
        }

        Set<Bubble> attached = Collections.newSetFromMap(new IdentityHashMap<>());
        Queue<GridPosition> queue = new ArrayDeque<>();
        for (int col = 0; col < rows.get(0).size(); col++) {
            Bubble bubble = rows.get(0).get(col);
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
        for (Bubble bubble : anchoredBubbles()) {
            if (!attached.contains(bubble)) {
                floating.add(bubble);
            }
        }
        return floating;
    }

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
        trimBottomRows(0);
    }

    public void pushPressureRow(List<BubbleColor> palette, int stage) {
        List<BubbleColor> usablePalette = (palette == null || palette.isEmpty())
                ? List.of(BubbleColor.AZURE)
                : palette;
        List<Bubble> row = createEmptyRow(0);
        int cadence = 4 + Math.floorMod(stage, 3);
        for (int col = 0; col < row.size(); col++) {
            if ((col + stage) % cadence == 0) {
                continue;
            }
            BubbleColor color = usablePalette.get(Math.floorMod(col + stage, usablePalette.size()));
            row.set(col, Bubble.anchored(color, cellCenterX(0, col), cellCenterY(0), bubbleRadius, new GridPosition(0, col)));
        }
        rows.add(0, row);
        refreshCoordinates();
    }

    public boolean hasReachedBottom(double bottomY) {
        return anchoredBubbles().stream().anyMatch(bubble -> bubble.y() + bubble.radius() >= bottomY);
    }

    public List<Bubble> neighbors(Bubble bubble) {
        if (bubble == null || bubble.gridPosition() == null) {
            return List.of();
        }
        List<Bubble> neighbors = new ArrayList<>(6);
        for (GridPosition position : neighborPositions(bubble.gridPosition())) {
            Bubble neighbor = getBubble(position);
            if (neighbor != null) {
                neighbors.add(neighbor);
            }
        }
        return neighbors;
    }

    public double cellCenterX(int row, int col) {
        double offset = (row % 2 == 0) ? 0.0 : bubbleRadius;
        return leftPadding + offset + col * horizontalSpacing;
    }

    public double cellCenterY(int row) {
        return topPadding + row * verticalSpacing;
    }

    private Optional<GridPosition> findTopImpactCell(Bubble projectile) {
        int preferredColumn = clampColumn((int) Math.round((projectile.x() - leftPadding) / horizontalSpacing));
        GridPosition direct = new GridPosition(0, preferredColumn);
        if (!isOccupied(direct.row(), direct.column())) {
            return Optional.of(direct);
        }

        GridPosition best = null;
        double bestScore = Double.MAX_VALUE;
        for (int row = 0; row <= Math.max(2, Math.min(3, rows.size())); row++) {
            for (int col = preferredColumn - 2; col <= preferredColumn + 2; col++) {
                if (col < 0 || col >= columnsInRow(row)) {
                    continue;
                }
                GridPosition candidate = new GridPosition(row, col);
                if (isOccupied(row, col) || !isSupportedCandidate(candidate)) {
                    continue;
                }
                double horizontalDistance = Math.abs(cellCenterX(row, col) - projectile.x());
                double rowPenalty = row * bubbleRadius * 1.8;
                double score = horizontalDistance + rowPenalty;
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * A grid impact may only occupy an immediate empty neighbor of the bubble
     * that was hit. Looking for a globally nearest supported cell can place a
     * ricocheted projectile inside a dense formation.
     */
    private Optional<GridPosition> findAttachmentCell(Bubble projectile, GridPosition anchorHint) {
        if (!isOccupied(anchorHint.row(), anchorHint.column())) {
            return Optional.empty();
        }

        LinkedHashSet<GridPosition> candidates = new LinkedHashSet<>();
        for (GridPosition candidate : neighborPositions(anchorHint)) {
            if (!isOccupied(candidate.row(), candidate.column()) && isSupportedCandidate(candidate)) {
                candidates.add(candidate);
            }
        }

        return candidates.stream()
                .min((left, right) -> {
                    double leftDistance = distanceToCell(projectile.x(), projectile.y(), left);
                    double rightDistance = distanceToCell(projectile.x(), projectile.y(), right);
                    int byDistance = Double.compare(leftDistance, rightDistance);
                    if (byDistance != 0) {
                        return byDistance;
                    }
                    int byRow = Integer.compare(left.row(), right.row());
                    return byRow != 0 ? byRow : Integer.compare(left.column(), right.column());
                });
    }

    private double distanceToCell(double x, double y, GridPosition position) {
        return Math.hypot(cellCenterX(position.row(), position.column()) - x, cellCenterY(position.row()) - y);
    }

    private int occupiedNeighborCount(GridPosition candidate) {
        int occupied = 0;
        for (GridPosition neighbor : neighborPositions(candidate)) {
            if (getBubble(neighbor) != null) {
                occupied++;
            }
        }
        return occupied;
    }

    private boolean isSupportedCandidate(GridPosition candidate) {
        return candidate.row() == 0 || occupiedNeighborCount(candidate) > 0;
    }

    private boolean isOccupied(int row, int col) {
        if (row < 0 || col < 0 || row >= rows.size()) {
            return false;
        }
        List<Bubble> rowData = rows.get(row);
        return col < rowData.size() && rowData.get(col) != null;
    }

    private Bubble getBubble(GridPosition position) {
        if (position == null) {
            return null;
        }
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
        int[][] even = {
                {-1, -1}, {-1, 0},
                {0, -1}, {0, 1},
                {1, -1}, {1, 0}
        };
        int[][] odd = {
                {-1, 0}, {-1, 1},
                {0, -1}, {0, 1},
                {1, 0}, {1, 1}
        };
        int[][] deltas = (row % 2 == 0) ? even : odd;

        List<GridPosition> neighbors = new ArrayList<>(6);
        for (int[] delta : deltas) {
            int nr = row + delta[0];
            int nc = col + delta[1];
            if (nr < 0 || nc < 0 || nc >= columnsInRow(nr)) {
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

    private List<Bubble> createEmptyRow(int rowIndex) {
        List<Bubble> row = new ArrayList<>(columnsInRow(rowIndex));
        for (int col = 0; col < columnsInRow(rowIndex); col++) {
            row.add(null);
        }
        return row;
    }

    private void refreshCoordinates() {
        for (int row = 0; row < rows.size(); row++) {
            List<Bubble> rowData = rows.get(row);
            for (int col = 0; col < rowData.size(); col++) {
                Bubble bubble = rowData.get(col);
                if (bubble == null) {
                    continue;
                }
                rowData.set(col, bubble.snapTo(new GridPosition(row, col), cellCenterX(row, col), cellCenterY(row)));
            }
        }
    }

    private int columnsInRow(int row) {
        return columns;
    }

    private int clampColumn(int candidate) {
        return Math.max(0, Math.min(columns - 1, candidate));
    }

    private void trimBottomRows(int minRows) {
        for (int row = rows.size() - 1; row >= minRows; row--) {
            List<Bubble> rowData = rows.get(row);
            if (rowData.stream().anyMatch(Objects::nonNull)) {
                break;
            }
            rows.remove(row);
        }
    }

    private record BubbleStep(Bubble bubble, int depth) {
    }

}
