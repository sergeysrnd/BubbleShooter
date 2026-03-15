package com.kilocade.bubbleshooter;

/**
 * Describes a queued orb before it is fired.
 */
public record BubbleAmmo(BubbleColor color, BubbleKind kind) {

    public static BubbleAmmo normal(BubbleColor color) {
        return new BubbleAmmo(color, BubbleKind.NORMAL);
    }

    public boolean isSpecial() {
        return kind != BubbleKind.NORMAL;
    }

    public String displayName() {
        return switch (kind) {
            case NORMAL -> color.name();
            case PRISM -> "PRISM";
            case PULSE -> "PULSE";
        };
    }
}
