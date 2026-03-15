package com.kilocade.bubbleshooter;

import javafx.geometry.Point2D;

/**
 * Player launcher model: aim state plus projectile creation.
 */
public final class Cannon {

    private static final double MIN_ANGLE_DEG = 10.0;
    private static final double MAX_ANGLE_DEG = 170.0;

    private final double originX;
    private final double originY;
    private double angleDegrees = 90.0;

    public Cannon(double originX, double originY) {
        this.originX = originX;
        this.originY = originY;
    }

    public void adjustAim(double deltaDegrees) {
        angleDegrees = clampAngle(angleDegrees + deltaDegrees);
    }

    public void aimAt(double x, double y) {
        double dx = x - originX;
        double dy = originY - y;
        double angleRad = Math.atan2(dy, dx);
        angleDegrees = clampAngle(Math.toDegrees(angleRad));
    }

    public Bubble fire(BubbleAmmo ammo, double radius, double speed) {
        return Bubble.fired(ammo, originX, originY, radius, speed, Math.toRadians(angleDegrees));
    }

    public double angleDegrees() {
        return angleDegrees;
    }

    public void setAngleDegrees(double angleDegrees) {
        this.angleDegrees = clampAngle(angleDegrees);
    }

    public Point2D origin() {
        return new Point2D(originX, originY);
    }

    private double clampAngle(double candidate) {
        return Math.max(MIN_ANGLE_DEG, Math.min(MAX_ANGLE_DEG, candidate));
    }
}
