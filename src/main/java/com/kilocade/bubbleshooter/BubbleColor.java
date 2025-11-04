package com.kilocade.bubbleshooter;

import javafx.scene.paint.Color;

/**
 * Supported bubble colors and their JavaFX {@link Color} mapping.
 */
public enum BubbleColor {
    RED(Color.web("#e74c3c")),
    GREEN(Color.web("#27ae60")),
    BLUE(Color.web("#2980b9")),
    YELLOW(Color.web("#f1c40f")),
    PURPLE(Color.web("#8e44ad"));

    private final Color fxColor;

    BubbleColor(Color fxColor) {
        this.fxColor = fxColor;
    }

    /**
     * Provides the JavaFX color representation.
     *
     * @return Color used for rendering.
     */
    public Color toFxColor() {
        return fxColor;
    }

    /**
     * Simple helper to cycle colors, leveraging a modern switch expression.
     *
     * @param index index that will be wrapped across available colors.
     * @return bubble color at the provided index.
     */
    public static BubbleColor fromIndex(int index) {
        return switch (Math.floorMod(index, values().length)) {
            case 0 -> RED;
            case 1 -> GREEN;
            case 2 -> BLUE;
            case 3 -> YELLOW;
            case 4 -> PURPLE;
            default -> throw new IllegalStateException("Unexpected index");
        };
    }
}