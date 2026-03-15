package com.kilocade.bubbleshooter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class StageGeneratorTest {

    @Test
    void generatedStageContainsPaletteAndFilledCells() {
        StageGenerator generator = new StageGenerator();
        StageGenerator.StageLayout layout = generator.generate(3, 18);

        assertTrue(layout.palette().size() >= 4);
        assertFalse(layout.rows().isEmpty());
        assertTrue(layout.rows().stream().flatMap(java.util.Collection::stream).anyMatch(cell -> cell != null));
    }

    @Test
    void differentVariantSeedsProduceDifferentStageLayouts() {
        StageGenerator generator = new StageGenerator();

        StageGenerator.StageLayout first = generator.generate(1, 16, 11L);
        StageGenerator.StageLayout second = generator.generate(1, 16, 29L);

        assertTrue(
                !first.palette().equals(second.palette()) || !first.rows().equals(second.rows())
        );
    }
}
