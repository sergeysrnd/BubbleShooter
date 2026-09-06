package com.kilocade.bubbleshooter;

import static java.lang.Math.cos;
import static java.lang.Math.sin;

import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * Immutable representation of an orb in flight or anchored to the grid.
 */
public record Bubble(
        BubbleColor color,
        BubbleKind kind,
        double x,
        double y,
        double radius,
        double vx,
        double vy,
        GridPosition gridPosition
) {

    public static Bubble anchored(BubbleAmmo ammo, double x, double y, double radius, GridPosition position) {
        return new Bubble(ammo.color(), ammo.kind(), x, y, radius, 0, 0, position);
    }

    public static Bubble anchored(BubbleColor color, double x, double y, double radius, GridPosition position) {
        return anchored(BubbleAmmo.normal(color), x, y, radius, position);
    }

    public static Bubble fired(BubbleAmmo ammo, double x, double y, double radius, double speed, double angleRadians) {
        double vx = speed * cos(angleRadians);
        double vy = -speed * sin(angleRadians);
        return new Bubble(ammo.color(), ammo.kind(), x, y, radius, vx, vy, null);
    }

    public static Bubble fired(BubbleColor color, double x, double y, double radius, double speed, double angleRadians) {
        return fired(BubbleAmmo.normal(color), x, y, radius, speed, angleRadians);
    }

    public BubbleAmmo ammo() {
        return new BubbleAmmo(color, kind);
    }

    public Bubble withPosition(double newX, double newY) {
        return new Bubble(color, kind, newX, newY, radius, vx, vy, gridPosition);
    }

    public Bubble withVelocity(double newVx, double newVy) {
        return new Bubble(color, kind, x, y, radius, newVx, newVy, gridPosition);
    }

    public Bubble snapTo(GridPosition position, double snapX, double snapY) {
        return new Bubble(color, kind, snapX, snapY, radius, 0, 0, position);
    }

    public boolean matchesClusterColor(BubbleColor targetColor) {
        return kind == BubbleKind.PRISM || color == targetColor;
    }

    public Circle asCircle() {
        Circle shell = new Circle(x, y, radius);
        shell.setFill(kind.fill(color));
        shell.setStroke(kind.stroke(color));
        shell.setStrokeWidth(kind == BubbleKind.NORMAL ? 1.0 : 3.0);
        shell.setEffect(new DropShadow(radius * 0.22, kind.glow(color)));
        return shell;
    }

    public record GridPosition(int row, int column) {
    }
}
