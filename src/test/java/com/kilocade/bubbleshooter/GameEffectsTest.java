package com.kilocade.bubbleshooter;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class GameEffectsTest {
    private final Bubble bubble = Bubble.anchored(BubbleColor.AZURE, 100, 100, 18, new Bubble.GridPosition(0, 0));

    @Test void fallingVisualAcceleratesAndExpiresWithoutChangingModel() {
        GameEffects effects = new GameEffects();
        effects.drop(bubble);
        var node = effects.layer().getChildren().getFirst();
        effects.advance(.1);
        double firstMove = node.getTranslateY();
        effects.advance(.1);
        assertTrue(node.getTranslateY() - firstMove > firstMove);
        assertEquals(100, bubble.y());
        effects.advance(2);
        assertEquals(0, effects.size());
        assertTrue(effects.layer().getChildren().isEmpty());
    }

    @Test void effectsAreBoundedAndRestartClearsAllNodes() {
        GameEffects effects = new GameEffects();
        for (int i = 0; i < 2000; i++) effects.trail(bubble);
        assertTrue(effects.size() <= 600);
        effects.clear();
        assertEquals(0, effects.size());
        assertTrue(effects.layer().getChildren().isEmpty());
    }
}
