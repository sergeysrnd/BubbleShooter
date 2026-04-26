package com.kilocade.bubbleshooter;

import com.kilocade.bubbleshooter.Bubble.GridPosition;
import java.util.List;

/**
 * Continuous projectile stepping with wall ricochet support.
 */
final class ProjectilePhysics {

    private static final double MIN_TRAVEL_RATIO = 1e-6;
    private static final double MIN_TIME_SLICE = 1e-6;
    private static final double WALL_NUDGE_DISTANCE = 0.08;
    private static final double COLLISION_PADDING = 0.75;

    private ProjectilePhysics() {
    }

    static AdvanceResult advance(
            Bubble projectile,
            double deltaSeconds,
            double playfieldWidth,
            double bubbleRadius,
            List<Bubble> anchoredBubbles
    ) {
        Bubble current = projectile;
        boolean bounced = false;
        double remaining = deltaSeconds;
        int guard = 0;

        while (remaining > MIN_TIME_SLICE && guard++ < 6) {
            Collision collision = detectCollision(current, remaining, playfieldWidth, bubbleRadius, anchoredBubbles);
            switch (collision) {
                case Collision.Wall wall -> {
                    double timeSpent = remaining * wall.travelRatio();
                    double timeLeft = remaining - timeSpent;
                    current = current.withPosition(wall.impactX(), wall.impactY())
                            .withVelocity(wall.reflectedVx(), current.vy());
                    bounced = true;

                    if (timeLeft <= MIN_TIME_SLICE) {
                        return AdvanceResult.inFlight(current, bounced);
                    }

                    double speed = Math.hypot(current.vx(), current.vy());
                    if (speed > MIN_TIME_SLICE) {
                        double nudgeSeconds = Math.min(timeLeft, WALL_NUDGE_DISTANCE / speed);
                        current = current.withPosition(
                                current.x() + current.vx() * nudgeSeconds,
                                current.y() + current.vy() * nudgeSeconds
                        );
                        timeLeft -= nudgeSeconds;
                    }
                    remaining = timeLeft;
                }
                case Collision.Grid grid -> {
                    Bubble impactBubble = current.withPosition(grid.impactX(), grid.impactY());
                    return AdvanceResult.anchored(impactBubble, grid.anchorHint(), bounced);
                }
                case null, default -> {
                    Bubble advanced = current.withPosition(
                            current.x() + current.vx() * remaining,
                            current.y() + current.vy() * remaining
                    );
                    return AdvanceResult.inFlight(advanced, bounced);
                }
            }
        }
        return AdvanceResult.inFlight(current, bounced);
    }

    private static Collision detectCollision(
            Bubble projectile,
            double deltaSeconds,
            double playfieldWidth,
            double bubbleRadius,
            List<Bubble> anchoredBubbles
    ) {
        double startX = projectile.x();
        double startY = projectile.y();
        double deltaX = projectile.vx() * deltaSeconds;
        double deltaY = projectile.vy() * deltaSeconds;
        double bestT = Double.POSITIVE_INFINITY;
        Collision bestCollision = null;

        if (Math.abs(deltaX) > MIN_TRAVEL_RATIO) {
            if (deltaX < 0) {
                double t = (bubbleRadius - startX) / deltaX;
                if (t > MIN_TRAVEL_RATIO && t <= 1.0) {
                    double impactY = startY + deltaY * t;
                    bestT = t;
                    bestCollision = new Collision.Wall(bubbleRadius, impactY, Math.abs(projectile.vx()), t);
                }
            } else {
                double wallX = playfieldWidth - bubbleRadius;
                double t = (wallX - startX) / deltaX;
                if (t > MIN_TRAVEL_RATIO && t <= 1.0) {
                    double impactY = startY + deltaY * t;
                    bestT = t;
                    bestCollision = new Collision.Wall(wallX, impactY, -Math.abs(projectile.vx()), t);
                }
            }
        }

        if (Math.abs(deltaY) > MIN_TRAVEL_RATIO && deltaY < 0) {
            double t = (bubbleRadius - startY) / deltaY;
            if (t > MIN_TRAVEL_RATIO && t <= 1.0 && t < bestT) {
                double impactX = startX + deltaX * t;
                bestT = t;
                bestCollision = new Collision.Grid(impactX, bubbleRadius, null, t);
            }
        }

        double a = deltaX * deltaX + deltaY * deltaY;
        if (a <= MIN_TRAVEL_RATIO) {
            return bestCollision;
        }

        double collisionRadius = (bubbleRadius * 2.0) + COLLISION_PADDING;
        double bestOverlapEntryT = Double.POSITIVE_INFINITY;
        for (Bubble other : anchoredBubbles) {
            double relX = startX - other.x();
            double relY = startY - other.y();
            double startDistanceSquared = (relX * relX) + (relY * relY);
            double b = 2.0 * ((relX * deltaX) + (relY * deltaY));
            double c = startDistanceSquared - (collisionRadius * collisionRadius);
            double discriminant = (b * b) - (4.0 * a * c);
            if (discriminant < 0) {
                continue;
            }

            double sqrtDiscriminant = Math.sqrt(discriminant);
            double entryT = (-b - sqrtDiscriminant) / (2.0 * a);
            if (startDistanceSquared <= collisionRadius * collisionRadius) {
                if (entryT < bestOverlapEntryT) {
                    bestOverlapEntryT = entryT;
                    bestT = 0.0;
                    bestCollision = new Collision.Grid(startX, startY, other.gridPosition(), 0.0);
                }
                continue;
            }

            double t = entryT;
            if (t <= MIN_TRAVEL_RATIO || t > 1.0 || t >= bestT) {
                continue;
            }

            double impactX = startX + deltaX * t;
            double impactY = startY + deltaY * t;
            bestT = t;
            bestCollision = new Collision.Grid(impactX, impactY, other.gridPosition(), t);
        }

        return bestCollision;
    }

    record AdvanceResult(Bubble projectile, GridPosition anchorHint, boolean anchored, boolean bounced) {

        static AdvanceResult inFlight(Bubble projectile, boolean bounced) {
            return new AdvanceResult(projectile, null, false, bounced);
        }

        static AdvanceResult anchored(Bubble projectile, GridPosition anchorHint, boolean bounced) {
            return new AdvanceResult(projectile, anchorHint, true, bounced);
        }
    }

    private sealed interface Collision permits Collision.Wall, Collision.Grid {
        double travelRatio();

        record Wall(double impactX, double impactY, double reflectedVx, double travelRatio) implements Collision {
        }

        record Grid(double impactX, double impactY, GridPosition anchorHint, double travelRatio) implements Collision {
        }
    }
}
