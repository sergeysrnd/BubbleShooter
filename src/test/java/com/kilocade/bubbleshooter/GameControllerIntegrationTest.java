package com.kilocade.bubbleshooter;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.animation.AnimationTimer;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Real JavaFX scene/input smoke test. Requires the Windows desktop used by this app. */
@EnabledOnOs(OS.WINDOWS)
final class GameControllerIntegrationTest {
    @TempDir Path temporary;
    @BeforeAll
    static void startToolkit() {
        Platform.startup(() -> Platform.setImplicitExit(false));
    }

    @AfterAll
    static void stopToolkit() {
        Platform.exit();
    }

    @Test
    void swapPauseResizeResumeAndRestartWorkThroughSceneInput() throws Exception {
        Fixture fixture = onFx(() -> {
            GameController controller = new GameController(new HighScoreStore(temporary.resolve("record.txt")));
            Stage stage = new Stage();
            Scene scene = controller.createScene();
            stage.setScene(scene);
            stage.show();
            ((java.util.Random) field(controller, "random")).setSeed(42);
            controller.start();
            return new Fixture(controller, stage, scene);
        });
        GameController controller = fixture.controller();
        Stage stage = fixture.stage();
        Scene scene = fixture.scene();
        try {
            awaitLayoutPulse();
            Bubble moving = onFx(() -> {
                layout(scene, controller);
                capture(scene, "initial");

                BubbleAmmo loaded = field(controller, "currentAmmo");
                BubbleAmmo queued = field(controller, "nextAmmo");
                assertNotEquals(loaded, queued, "Fixture needs different colors to exercise swap");
                key(scene, KeyCode.X);
                assertEquals(queued, field(controller, "currentAmmo"));
                assertEquals(loaded, field(controller, "nextAmmo"));
                for (int i = 0; i < 24; i++) key(scene, KeyCode.D);
                capture(scene, "ricochet-guide");
                key(scene, KeyCode.SPACE);
                Bubble fired = field(controller, "projectile");
                assertNotNull(fired);
                advance(controller, .02);
                Bubble inFlight = field(controller, "projectile");
                assertNotEquals(fired.x(), inFlight.x());

                key(scene, KeyCode.P);
                advance(controller, 1);
                assertEquals(inFlight, field(controller, "projectile"), "Pause must freeze the shot");
                key(scene, KeyCode.X);
                assertEquals(queued, field(controller, "currentAmmo"), "Cannot swap during a shot");
                return inFlight;
            });
            double oldLeft = onFx(() -> ((BubbleGrid) field(controller, "grid")).projectileBounds().minX());
            onFx(() -> {
                stage.setWidth(stage.getWidth() + 180);
                stage.setHeight(stage.getHeight() + 50);
                return null;
            });
            awaitLayoutPulse();
            onFx(() -> {
                layout(scene, controller);
                BubbleGrid grid = field(controller, "grid");
                assertTrue(grid.projectileBounds().minX() - oldLeft > 50, "Window must actually resize");
                Bubble resized = field(controller, "projectile");
                assertEquals(moving.x() + grid.projectileBounds().minX() - oldLeft, resized.x(), 1e-7);
                assertEquals(moving.y(), resized.y(), 1e-7);
                assertEquals(moving.vx(), resized.vx());
                capture(scene, "paused-resized");

                key(scene, KeyCode.ESCAPE);
                advance(controller, 10);
                assertNull(field(controller, "projectile"), "Resumed shot should land");
                assertTrue((Boolean) field(controller, "shotRicochet"), "Bank flag must survive all frames of the shot");
                key(scene, KeyCode.R);
                assertEquals(0, (Integer) field(controller, "score"));
                assertEquals(1, (Integer) field(controller, "stageNumber"));
                assertNull(field(controller, "projectile"));
                assertFalse((Boolean) field(controller, "shotRicochet"));
                key(scene, KeyCode.SPACE);
                assertNotNull(field(controller, "projectile"), "Restart must enable shooting");
                key(scene, KeyCode.R);
                stage.setWidth(760);
                stage.setHeight(760);
                return null;
            });
            awaitLayoutPulse();
            onFx(() -> {
                layout(scene, controller);
                assertTrue(scene.getWidth() <= 760, "Minimum-size snapshot must be at the smaller size");
                capture(scene, "minimum-size");
                javafx.scene.Node previous = null;
                for (String name : new String[]{"scoreLabel", "comboLabel", "pressureLabel", "currentPreview", "nextPreview"}) {
                    javafx.scene.Node node = field(controller, name);
                    javafx.scene.Node card = node.getParent();
                    if (previous != null) {
                        assertTrue(previous.localToScene(previous.getBoundsInLocal()).getMaxX()
                                <= card.localToScene(card.getBoundsInLocal()).getMinX(), "HUD cards overlap at " + name);
                    }
                    previous = card;
                }
                javafx.scene.Node button = field(controller, "restartButton");
                assertTrue(previous.localToScene(previous.getBoundsInLocal()).getMaxX()
                        <= button.localToScene(button.getBoundsInLocal()).getMinX());
                assertTrue(button.localToScene(button.getBoundsInLocal()).getMaxX() <= scene.getWidth());
                stage.setWidth(994);
                stage.setHeight(897);
                return null;
            });
            awaitLayoutPulse();
            onFx(() -> {
                // A two-orb ceiling bridge supports five falling orbs. The third rose breaks it.
                BubbleGrid grid = field(controller, "grid");
                var rows = new java.util.ArrayList<java.util.List<BubbleAmmo>>();
                for (int i = 0; i < 4; i++) rows.add(new java.util.ArrayList<>(java.util.Collections.nCopies(16, null)));
                rows.get(0).set(0, BubbleAmmo.normal(BubbleColor.AZURE));
                rows.get(0).set(7, BubbleAmmo.normal(BubbleColor.ROSE));
                rows.get(0).set(8, BubbleAmmo.normal(BubbleColor.ROSE));
                rows.get(1).set(7, BubbleAmmo.normal(BubbleColor.AZURE));
                rows.get(2).set(7, BubbleAmmo.normal(BubbleColor.GOLD));
                rows.get(2).set(8, BubbleAmmo.normal(BubbleColor.GOLD));
                rows.get(3).set(7, BubbleAmmo.normal(BubbleColor.VIOLET));
                rows.get(3).set(8, BubbleAmmo.normal(BubbleColor.VIOLET));
                grid.loadLayout(new StageGenerator.StageLayout(1, "Test", "Bridge", 6, java.util.List.of(BubbleColor.ROSE), rows));
                Bubble landed = grid.snapBubble(Bubble.fired(BubbleColor.ROSE, grid.cellCenterX(0, 6),
                        grid.cellCenterY(0), 18, 0, 0), null).orElseThrow();
                var flag = GameController.class.getDeclaredField("shotRicochet");
                flag.setAccessible(true);
                flag.setBoolean(controller, true);
                var resolve = GameController.class.getDeclaredMethod("resolveShot", Bubble.class);
                resolve.setAccessible(true);
                resolve.invoke(controller, landed);
                assertEquals(1305, (Integer) field(controller, "score"));
                assertEquals(1, grid.anchoredBubbles().size());
                assertEquals(1305, new HighScoreStore(temporary.resolve("record.txt")).best());
                GameEffects effects = field(controller, "effects");
                assertTrue(effects.size() > 20);
                advance(controller, .15);
                capture(scene, "burst-and-avalanche");
                key(scene, KeyCode.P);
                var particle = effects.layer().getChildren().getFirst();
                double y = particle.getTranslateY();
                advance(controller, .5);
                assertEquals(y, particle.getTranslateY(), "Pause must freeze effects too");
                key(scene, KeyCode.R);
                assertEquals(0, effects.size());
                assertEquals(1305, ((HighScoreStore) field(controller, "records")).best());
                return null;
            });
        } finally {
            onFx(() -> {
                controller.stop();
                stage.close();
                return null;
            });
        }
    }

    private record Fixture(GameController controller, Stage stage, Scene scene) {}

    private static <T> T onFx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(20, TimeUnit.SECONDS);
    }

    private static void awaitLayoutPulse() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        onFx(() -> {
            new AnimationTimer() {
                int pulses;
                @Override public void handle(long now) {
                    if (++pulses == 3) {
                        stop();
                        ready.countDown();
                    }
                }
            }.start();
            return null;
        });
        assertTrue(ready.await(5, TimeUnit.SECONDS), "JavaFX layout pulse timed out");
    }

    private static void layout(Scene scene, GameController controller) {
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        controller.refreshLayout();
    }

    private static void key(Scene scene, KeyCode code) {
        Event.fireEvent(scene, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }

    private static void advance(GameController controller, double seconds) throws Exception {
        var method = GameController.class.getDeclaredMethod("update", double.class);
        method.setAccessible(true);
        method.invoke(controller, seconds);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(GameController controller, String name) throws Exception {
        var field = GameController.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(controller);
    }

    private static void capture(Scene scene, String name) throws Exception {
        WritableImage snapshot = scene.snapshot(null);
        BufferedImage png = new BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < png.getHeight(); y++) {
            for (int x = 0; x < png.getWidth(); x++) {
                png.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
            }
        }
        Path path = Path.of("build", "reports", "ui-smoke", name + ".png");
        Files.createDirectories(path.getParent());
        ImageIO.write(png, "png", path.toFile());
    }
}
