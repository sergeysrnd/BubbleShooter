package com.kilocade.bubbleshooter;

import javafx.scene.paint.Color;

/**
 * Supported bubble colors and their JavaFX {@link Color} mapping.
 */
public enum BubbleColor {
    ROSE(Color.web("#ff6b8a")),
    LIME(Color.web("#7ae582")),
    AZURE(Color.web("#5bc0ff")),
    GOLD(Color.web("#ffd166")),
    VIOLET(Color.web("#c77dff")),
    TEAL(Color.web("#4fffd8"));

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
        return values()[Math.floorMod(index, values().length)];
    }
}
