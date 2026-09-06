package com.kilocade.bubbleshooter;

import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

/**
 * Orb types used both for ammo and anchored bubbles.
 */
public enum BubbleKind {
    NORMAL,
    PRISM,
    PULSE;

    public Paint fill(BubbleColor color) {
        return switch (this) {
            case NORMAL -> new RadialGradient(
                    0,
                    0,
                    0.32,
                    0.25,
                    0.78,
                    true,
                    CycleMethod.NO_CYCLE,
                    new Stop(0, color.toFxColor().interpolate(Color.WHITE, .88)),
                    new Stop(.13, color.toFxColor().interpolate(Color.WHITE, .5)),
                    new Stop(.34, color.toFxColor()),
                    new Stop(.76, color.toFxColor().deriveColor(0, 1.1, .65, 1)),
                    new Stop(1, color.toFxColor().deriveColor(0, 1.1, .28, 1))
            );
            case PRISM -> new LinearGradient(
                    0,
                    0,
                    1,
                    1,
                    true,
                    CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.web("#fcf6ff")),
                    new Stop(0.3, Color.web("#71d9ff")),
                    new Stop(0.65, Color.web("#ff7bc1")),
                    new Stop(1.0, Color.web("#ffe785"))
            );
            case PULSE -> new RadialGradient(
                    0,
                    0,
                    0.4,
                    0.35,
                    0.85,
                    true,
                    CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#fff0b3")),
                    new Stop(0.55, Color.web("#ff944d")),
                    new Stop(1, Color.web("#8f250c"))
            );
        };
    }

    public Color stroke(BubbleColor color) {
        return switch (this) {
            case NORMAL -> color.toFxColor().interpolate(Color.WHITE, .28);
            case PRISM -> Color.web("#ffffff");
            case PULSE -> Color.web("#ffe8cf");
        };
    }

    public Color glow(BubbleColor color) {
        return switch (this) {
            case NORMAL -> color.toFxColor().deriveColor(0, 1.0, 1.2, 0.7);
            case PRISM -> Color.web("#a2f6ff");
            case PULSE -> Color.web("#ff9b61");
        };
    }

    public String shortLabel() {
        return switch (this) {
            case NORMAL -> "";
            case PRISM -> "Prism";
            case PULSE -> "Pulse";
        };
    }
}
