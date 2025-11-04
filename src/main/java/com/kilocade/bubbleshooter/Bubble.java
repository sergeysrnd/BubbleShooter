package com.kilocade.bubbleshooter;

import static java.lang.Math.cos;
import static java.lang.Math.sin;

import javafx.scene.shape.Circle;

/**
 * Immutable representation of a bubble within the game.
 * Stores spatial information, velocity, color, and optional grid coordinates.
 */
public record Bubble(
        BubbleColor color,
        double x,
        double y,
        double radius,
        double vx,
        double vy,
        GridPosition gridPosition
) {

    /**
     * Factory helper for static (grid-bound) bubbles.
     */
    public static Bubble anchored(BubbleColor color, double x, double y, double radius, GridPosition position) {
        return new Bubble(color, x, y, radius, 0, 0, position);
    }

    /**
     * Factory helper for dynamic (shot) bubbles derived from speed and angle.
     */
    public static Bubble fired(BubbleColor color, double x, double y, double radius, double speed, double angleRadians) {
        double vx = speed * cos(angleRadians);
        double vy = -speed * sin(angleRadians);
        return new Bubble(color, x, y, radius, vx, vy, null);
    }

    /**
     * Returns a new bubble with updated cartesian coordinates.
     */
    public Bubble withPosition(double newX, double newY) {
        return new Bubble(color, newX, newY, radius, vx, vy, gridPosition);
    }

    /**
     * Returns a new bubble with updated velocity components.
     */
    public Bubble withVelocity(double newVx, double newVy) {
        return new Bubble(color, x, y, radius, newVx, newVy, gridPosition);
    }

    /**
     * Returns a new bubble bound to a grid coordinate and cleared velocity.
     */
    public Bubble snapTo(GridPosition position, double snapX, double snapY) {
        return new Bubble(color, snapX, snapY, radius, 0, 0, position);
    }

    /**
     * Creates a JavaFX {@link Circle} view used for rendering.
     */
    public Circle asCircle() {
        Circle circle = new Circle(x, y, radius);
        circle.setFill(color.toFxColor());
        circle.setStrokeWidth(1.5);
        circle.setStroke(color.toFxColor().darker());
        return circle;
    }

    /**
     * Simple identifier for a cell within the staggered grid.
     *
     * @param row    zero-based row index.
     * @param column zero-based column index.
     */
    public record GridPosition(int row, int column) {
    }
}