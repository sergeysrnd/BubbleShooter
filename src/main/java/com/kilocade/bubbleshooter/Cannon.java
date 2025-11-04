package com.kilocade.bubbleshooter;

import java.util.Random;
import javafx.geometry.Point2D;

/**
 * Models the player's launcher, storing aim direction and providing new projectiles.
 */
public final class Cannon {

    private static final double MIN_ANGLE_DEG = 10.0;
    private static final double MAX_ANGLE_DEG = 170.0;
    private static final double DEFAULT_SPEED = 520.0;
    private static final Random RNG = new Random();

    private final double originX;
    private final double originY;
    private double angleDegrees = 90.0;
    private int colorCount = 5;

    private BubbleColor nextColor = BubbleColor.RED;

    public Cannon(double originX, double originY) {
        this.originX = originX;
        this.originY = originY;
    }

    public void setColorCount(int count) {
        this.colorCount = Math.min(count, BubbleColor.values().length);
        nextColor = BubbleColor.fromIndex(RNG.nextInt(colorCount));
    }

    /**
     * Adjusts the aim angle clamped within reasonable bounds.
     */
    public void adjustAim(double deltaDegrees) {
        angleDegrees = clampAngle(angleDegrees + deltaDegrees);
    }

    /**
     * Sets the aim angle directly based on a cursor position.
     */
    public void aimAt(double x, double y) {
        double dx = x - originX;
        double dy = originY - y; // invert Y so positive angles aim upward
        double angleRad = Math.atan2(dy, dx);
        angleDegrees = clampAngle(Math.toDegrees(angleRad));
    }

    private double clampAngle(double candidate) {
        return Math.max(MIN_ANGLE_DEG, Math.min(MAX_ANGLE_DEG, candidate));
    }

    /**
     * Creates a new projectile bubble using the currently queued color.
     */
    public Bubble shoot() {
        double angleRad = Math.toRadians(angleDegrees);
        Bubble bubble = Bubble.fired(nextColor, originX, originY, 18.0, DEFAULT_SPEED, angleRad);
        nextColor = BubbleColor.fromIndex(RNG.nextInt(colorCount));
        return bubble;
    }

    /**
     * @return current aim angle in degrees.
     */
    public double angleDegrees() {
        return angleDegrees;
    }

    /**
     * @return the color of the bubble that will be shot next (for UI preview).
     */
    public BubbleColor previewColor() {
        return nextColor;
    }

    /**
     * @return immutable point representing the cannon origin.
     */
    public Point2D origin() {
        return new Point2D(originX, originY);
    }
}