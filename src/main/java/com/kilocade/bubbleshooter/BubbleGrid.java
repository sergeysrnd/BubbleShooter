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
    private static final double CONTACT_EPSILON = 1.1;
    private static final double CONTACT_RING_EPSILON = 1.4;

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

    public List<Bubble> collisionBubbles() {
        return anchoredBubbles().stream()
                .filter(bubble -> hasAttachmentSlot(bubble.gridPosition()))
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
        if (projectile == null) {
            return Optional.empty();
        }

        Optional<GridPosition> cell = anchorHint == null
                ? findTopImpactCell(projectile)
                : findAttachmentCell(projectile, anchorHint);
        if (cell.isEmpty() && anchorHint == null) {
            cell = findNearestSupportedCell(projectile.x(), projectile.y());
        }
        if (cell.isEmpty()) {
            return Optional.empty();
        }

        GridPosition position = cell.orElseThrow();
        ensureRowExists(position.row());
        Bubble anchored = projectile.snapTo(position, cellCenterX(position.row(), position.column()), cellCenterY(position.row()));
        rows.get(position.row()).set(position.column(), anchored);
        return Optional.of(anchored);
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
        List<Bubble> row = createEmptyRow(0);
        int cadence = 4 + Math.floorMod(stage, 3);
        for (int col = 0; col < row.size(); col++) {
            if ((col + stage) % cadence == 0) {
                continue;
            }
            BubbleColor color = palette.get(Math.floorMod(col + stage, palette.size()));
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

    private Optional<GridPosition> findAttachmentCell(Bubble projectile, GridPosition anchorHint) {
        List<ImpactContact> contacts = impactContacts(projectile, anchorHint);
        if (contacts.isEmpty()) {
            return Optional.empty();
        }

        List<AttachmentCandidate> candidates = new ArrayList<>();
        List<GridPosition> candidateCells = anchorHint == null
                ? candidateCellsAroundContacts(contacts)
                : immediateCandidateCellsAroundContacts(contacts);

        for (GridPosition candidate : candidateCells) {
            double candidateX = cellCenterX(candidate.row(), candidate.column());
            double candidateY = cellCenterY(candidate.row());
            double impactDistance = Math.hypot(candidateX - projectile.x(), candidateY - projectile.y());
            double idealDistance = contacts.stream()
                    .mapToDouble(contact -> Math.hypot(candidateX - contact.idealX(), candidateY - contact.idealY()))
                    .min()
                    .orElse(impactDistance);
            int occupiedNeighbors = occupiedNeighborCount(candidate);
            candidates.add(new AttachmentCandidate(candidate, impactDistance, idealDistance, occupiedNeighbors));
        }

        return candidates.stream()
                .sorted((left, right) -> {
                    int byImpact = Double.compare(left.impactDistance(), right.impactDistance());
                    if (byImpact != 0) {
                        return byImpact;
                    }
                    int byIdeal = Double.compare(left.idealDistance(), right.idealDistance());
                    if (byIdeal != 0) {
                        return byIdeal;
                    }
                    int byNeighbors = Integer.compare(left.occupiedNeighbors(), right.occupiedNeighbors());
                    if (byNeighbors != 0) {
                        return byNeighbors;
                    }
                    int byRow = Integer.compare(left.position().row(), right.position().row());
                    if (byRow != 0) {
                        return byRow;
                    }
                    return Integer.compare(left.position().column(), right.position().column());
                })
                .map(AttachmentCandidate::position)
                .findFirst();
    }

    private List<ImpactContact> impactContacts(Bubble projectile, GridPosition anchorHint) {
        if (anchorHint != null) {
            Bubble primaryAnchor = getBubble(anchorHint);
            if (primaryAnchor == null) {
                return List.of();
            }
            return List.of(contactFromBubble(projectile, primaryAnchor));
        }

        double contactThreshold = (bubbleRadius * 2.0) + CONTACT_EPSILON;
        List<Bubble> nearbyContacts = new ArrayList<>();
        for (Bubble bubble : anchoredBubbles()) {
            if (Math.hypot(projectile.x() - bubble.x(), projectile.y() - bubble.y()) <= contactThreshold) {
                nearbyContacts.add(bubble);
            }
        }

        if (nearbyContacts.isEmpty()) {
            return List.of();
        }

        double nearestDistance = nearbyContacts.stream()
                .mapToDouble(contact -> Math.hypot(projectile.x() - contact.x(), projectile.y() - contact.y()))
                .filter(distance -> distance > 1e-6)
                .min()
                .orElse(Double.POSITIVE_INFINITY);

        List<ImpactContact> impactContacts = new ArrayList<>();
        for (Bubble bubble : nearbyContacts) {
            double distance = Math.hypot(projectile.x() - bubble.x(), projectile.y() - bubble.y());
            if (distance > nearestDistance + CONTACT_RING_EPSILON) {
                continue;
            }
            impactContacts.add(contactFromBubble(projectile, bubble));
        }
        return impactContacts;
    }

    private ImpactContact contactFromBubble(Bubble projectile, Bubble anchor) {
        double dx = projectile.x() - anchor.x();
        double dy = projectile.y() - anchor.y();
        double length = Math.hypot(dx, dy);
        if (length <= 1e-6) {
            return new ImpactContact(anchor.gridPosition(), anchor.x(), anchor.y());
        }
        double idealX = anchor.x() + dx / length * horizontalSpacing;
        double idealY = anchor.y() + dy / length * horizontalSpacing;
        return new ImpactContact(anchor.gridPosition(), idealX, idealY);
    }

    private List<GridPosition> candidateCellsAroundContacts(List<ImpactContact> contacts) {
        Queue<GridPosition> queue = new ArrayDeque<>();
        LinkedHashSet<GridPosition> visited = new LinkedHashSet<>();

        for (ImpactContact contact : contacts) {
            GridPosition position = contact.position();
            if (position != null && isOccupied(position.row(), position.column()) && visited.add(position)) {
                queue.add(position);
            }
        }

        while (!queue.isEmpty()) {
            int frontierSize = queue.size();
            LinkedHashSet<GridPosition> candidates = new LinkedHashSet<>();

            for (int index = 0; index < frontierSize; index++) {
                GridPosition anchor = queue.poll();
                for (GridPosition neighbor : neighborPositions(anchor)) {
                    if (isOccupied(neighbor.row(), neighbor.column())) {
                        if (visited.add(neighbor)) {
                            queue.add(neighbor);
                        }
                        continue;
                    }
                    if (isSupportedCandidate(neighbor)) {
                        candidates.add(neighbor);
                    }
                }
            }

            if (!candidates.isEmpty()) {
                return new ArrayList<>(candidates);
            }
        }

        return List.of();
    }

    private List<GridPosition> immediateCandidateCellsAroundContacts(List<ImpactContact> contacts) {
        LinkedHashSet<GridPosition> candidates = new LinkedHashSet<>();
        for (ImpactContact contact : contacts) {
            GridPosition position = contact.position();
            if (position == null || !isOccupied(position.row(), position.column())) {
                continue;
            }
            for (GridPosition neighbor : neighborPositions(position)) {
                if (!isOccupied(neighbor.row(), neighbor.column()) && isSupportedCandidate(neighbor)) {
                    candidates.add(neighbor);
                }
            }
        }
        return new ArrayList<>(candidates);
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

    private Optional<GridPosition> findNearestSupportedCell(double x, double y) {
        int estimatedRow = Math.max(0, (int) Math.round((y - topPadding) / verticalSpacing));
        GridPosition best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int row = Math.max(0, estimatedRow - 2); row <= Math.max(rows.size(), estimatedRow + 3); row++) {
            for (int col = 0; col < columnsInRow(row); col++) {
                GridPosition candidate = new GridPosition(row, col);
                if (isOccupied(row, col) || !isSupportedCandidate(candidate)) {
                    continue;
                }
                double distance = Math.hypot(cellCenterX(row, col) - x, cellCenterY(row) - y);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
        }
        return Optional.ofNullable(best);
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

    private boolean hasAttachmentSlot(GridPosition position) {
        if (position == null) {
            return false;
        }
        for (GridPosition neighbor : neighborPositions(position)) {
            if (!isOccupied(neighbor.row(), neighbor.column()) && isSupportedCandidate(neighbor)) {
                return true;
            }
        }
        return false;
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

    private record ImpactContact(GridPosition position, double idealX, double idealY) {
    }

    private record AttachmentCandidate(
            GridPosition position,
            double impactDistance,
            double idealDistance,
            int occupiedNeighbors
    ) {
    }
}
