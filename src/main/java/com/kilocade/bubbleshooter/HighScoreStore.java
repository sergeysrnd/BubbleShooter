package com.kilocade.bubbleshooter;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Small, atomic local record file. Storage failure never interrupts a game. */
final class HighScoreStore {
    private final Path file;
    private int best;
    private boolean saved = true;

    HighScoreStore(Path file) {
        this.file = file.toAbsolutePath();
        try {
            if (Files.exists(file)) best = Math.max(0, Integer.parseInt(Files.readString(file).trim()));
        } catch (IOException | IllegalArgumentException | SecurityException ignored) {
            saved = false;
        }
    }

    static HighScoreStore local() {
        return new HighScoreStore(Path.of(System.getProperty("user.home"), ".comet-bloom", "best-score.txt"));
    }

    int best() { return best; }
    boolean saved() { return saved; }

    void record(int score) {
        if (score <= best && saved) return;
        best = Math.max(best, score);
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "record-", ".tmp");
            Files.writeString(temporary, Integer.toString(best));
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            saved = true;
        } catch (IOException | SecurityException ignored) {
            saved = false;
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException | SecurityException ignored) { }
            }
        }
    }
}
