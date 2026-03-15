package com.kilocade.bubbleshooter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Builds handcrafted-looking procedural stages for the arcade campaign.
 */
public final class StageGenerator {

    public StageLayout generate(int stage, int columns) {
        return generate(stage, columns, 0L);
    }

    public StageLayout generate(int stage, int columns, long variantSeed) {
        int normalizedStage = Math.max(1, stage);
        int colorCount = Math.min(4 + (normalizedStage - 1) / 2, BubbleColor.values().length);
        int rows = Math.min(5 + normalizedStage, 10);
        int driftThreshold = Math.max(7 - (normalizedStage - 1) / 3, 3);
        Random variant = new Random(variantSeed ^ (normalizedStage * 0x9E3779B97F4A7C15L) ^ columns);
        Style style = Style.values()[Math.floorMod((normalizedStage - 1) + variant.nextInt(Style.values().length), Style.values().length)];
        int shapeShift = variant.nextInt(Math.max(1, columns));
        int stageShift = variant.nextInt(3);
        boolean mirrored = variant.nextBoolean();
        List<BubbleColor> palette = buildPalette(colorCount, variant);
        List<List<BubbleAmmo>> layoutRows = new ArrayList<>();

        for (int row = 0; row < rows; row++) {
            List<BubbleAmmo> rowData = new ArrayList<>(columns);
            for (int col = 0; col < columns; col++) {
                int sampleCol = mirrored ? columns - 1 - col : col;
                sampleCol = Math.floorMod(sampleCol + shapeShift, columns);
                int sampleStage = normalizedStage + stageShift;

                if (!style.isFilled(row, sampleCol, rows, columns, sampleStage)) {
                    rowData.add(null);
                    continue;
                }
                BubbleColor color = palette.get(Math.floorMod(colorBand(row, sampleCol, sampleStage, style), palette.size()));
                rowData.add(BubbleAmmo.normal(color));
            }
            layoutRows.add(rowData);
        }

        return new StageLayout(
                normalizedStage,
                style.title,
                style.subtitle,
                driftThreshold,
                palette,
                layoutRows
        );
    }

    private List<BubbleColor> buildPalette(int colorCount, Random variant) {
        List<BubbleColor> pool = new ArrayList<>(List.of(BubbleColor.values()));
        Collections.rotate(pool, variant.nextInt(pool.size()));
        return List.copyOf(pool.subList(0, colorCount));
    }

    private int colorBand(int row, int col, int stage, Style style) {
        return switch (style) {
            case RIBBON -> (col / 2) + row + stage;
            case CITADEL -> (row / 2) + col + stage * 2;
            case HARBOR -> (Math.abs(col - 4) / 2) + row + stage;
            case SHARDS -> row + (col / 3) + stage * 3;
            case COMET -> (col + row * 2) / 2 + stage;
        };
    }

    private enum Style {
        RIBBON("Ribbon Drift", "Diagonal braids with deliberate gaps") {
            @Override
            boolean isFilled(int row, int col, int rows, int columns, int stage) {
                return row < 2 || Math.floorMod(col + row + stage, 5) != 0;
            }
        },
        CITADEL("Solar Citadel", "Dense flanks around a bright core") {
            @Override
            boolean isFilled(int row, int col, int rows, int columns, int stage) {
                int center = columns / 2;
                int distance = Math.abs(col - center);
                return distance <= 2 + row / 3 || row < 2 || distance >= center - 2;
            }
        },
        HARBOR("Twin Harbor", "Two clusters guarding a central lane") {
            @Override
            boolean isFilled(int row, int col, int rows, int columns, int stage) {
                int lane = columns / 2;
                return Math.abs(col - lane) >= 2 || row < 2 || row % 3 == 0;
            }
        },
        SHARDS("Mirror Shards", "Broken diagonals mirrored across the board") {
            @Override
            boolean isFilled(int row, int col, int rows, int columns, int stage) {
                return Math.floorMod(col - row + stage, 4) != 0 || Math.floorMod(col + row, 5) == 0;
            }
        },
        COMET("Comet Wake", "A sweeping wave that curls toward the walls") {
            @Override
            boolean isFilled(int row, int col, int rows, int columns, int stage) {
                double curve = Math.sin((col + stage * 0.8) * 0.6) * 1.8;
                return Math.abs((row % 4) - 1.5 - curve) <= 1.8 || row < 2;
            }
        };

        private final String title;
        private final String subtitle;

        Style(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }

        abstract boolean isFilled(int row, int col, int rows, int columns, int stage);
    }

    public record StageLayout(
            int stageNumber,
            String title,
            String subtitle,
            int driftThreshold,
            List<BubbleColor> palette,
            List<List<BubbleAmmo>> rows
    ) {
    }
}
