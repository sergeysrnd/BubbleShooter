package com.kilocade.bubbleshooter;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class HighScoreStoreTest {
    @TempDir Path directory;

    @Test void recordSurvivesReopeningAndLowerScoresCannotReplaceIt() {
        Path path = directory.resolve("profile/best.txt");
        HighScoreStore store = new HighScoreStore(path);
        store.record(1500);
        assertTrue(store.saved());
        HighScoreStore reopened = new HighScoreStore(path);
        assertEquals(1500, reopened.best());
        reopened.record(200);
        assertEquals(1500, new HighScoreStore(path).best());
        reopened.record(2000);
        assertEquals(2000, new HighScoreStore(path).best());
    }

    @Test void malformedRecordIsRecoverable() throws Exception {
        Path path = directory.resolve("best.txt");
        Files.writeString(path, "broken!");
        HighScoreStore store = new HighScoreStore(path);
        assertEquals(0, store.best());
        store.record(350);
        assertTrue(store.saved());
        assertEquals(350, new HighScoreStore(path).best());
    }

    @Test void unavailableStorageKeepsRecordInMemoryWithoutStoppingGame() throws Exception {
        Path parent = directory.resolve("not-a-directory");
        Files.writeString(parent, "occupied");
        HighScoreStore store = new HighScoreStore(parent.resolve("best.txt"));
        assertDoesNotThrow(() -> store.record(800));
        assertEquals(800, store.best());
        assertFalse(store.saved());
        Files.delete(parent);
        store.record(10);
        assertTrue(store.saved());
        assertEquals(800, new HighScoreStore(parent.resolve("best.txt")).best());
    }
}
