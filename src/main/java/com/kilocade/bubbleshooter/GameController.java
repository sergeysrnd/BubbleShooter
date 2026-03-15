package com.kilocade.bubbleshooter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

/**
 * Clean rewrite of the game loop around a classic bubble-shooter structure.
 */
public final class GameController {

    private static final double INITIAL_SCENE_WIDTH = 980;
    private static final double INITIAL_SCENE_HEIGHT = 860;
    private static final double BUBBLE_RADIUS = 18.0;
    private static final double PROJECTILE_SPEED = 720.0;
    private static final double MAX_PROJECTILE_STEP = BUBBLE_RADIUS * 0.6;
    private static final double GRID_TOP_PADDING = 74.0;
    private static final double CANNON_BOTTOM_MARGIN = 78.0;
    private static final double DANGER_MARGIN = 108.0;
    private static final double GUIDE_SLICE_SECONDS = 0.012;
    private static final int GRID_COLUMNS = 16;
    private static final int MISSES_PER_DROP = 6;
    private static final int BACKDROP_STARS = 56;

    private enum Phase {
        PLAYING,
        LOST
    }

    private record Star(double nx, double ny, double radius, double opacity) {
    }

    private final BorderPane root = new BorderPane();
    private final StackPane centerPane = new StackPane();
    private final Pane playfield = new Pane();
    private final Group backdropLayer = new Group();
    private final Group bubbleLayer = new Group();
    private final Group guideLayer = new Group();
    private final Group cannonLayer = new Group();
    private final Group overlayLayer = new Group();

    private final Rectangle backdropRect = new Rectangle();
    private final Line dangerLine = new Line();
    private final Line cannonBarrel = new Line();
    private final Circle cannonBase = new Circle(BUBBLE_RADIUS + 16);
    private final Circle cannonCore = new Circle(BUBBLE_RADIUS + 4);
    private final Label bannerLabel = new Label();

    private final Label titleLabel = new Label("Comet Bloom");
    private final Label stageLabel = new Label();
    private final Label scoreLabel = new Label();
    private final Label comboLabel = new Label();
    private final Label pressureLabel = new Label();
    private final Label objectiveLabel = new Label();
    private final Label statusLabel = new Label();

    private final Circle currentPreview = new Circle(18);
    private final Circle nextPreview = new Circle(14);
    private final Label currentLabel = new Label();
    private final Label nextLabel = new Label();

    private final Button restartButton = new Button("Restart");
    private final Button soundButton = new Button("Sound: On");

    private final StageGenerator stageGenerator = new StageGenerator();
    private final SoundEngine soundEngine = new SoundEngine();
    private final List<Star> stars = new ArrayList<>();
    private final Random random = new Random();

    private Scene scene;
    private BubbleGrid grid;
    private StageGenerator.StageLayout stageLayout;
    private Cannon cannon;
    private AnimationTimer loop;

    private Phase phase = Phase.PLAYING;
    private Bubble projectile;
    private Circle projectileNode;
    private BubbleAmmo currentAmmo;
    private BubbleAmmo nextAmmo;

    private int stageNumber = 1;
    private int score;
    private int combo;
    private int misses;
    private boolean soundEnabled = true;

    private long lastTickNanos;
    private double lastPlayfieldWidth = INITIAL_SCENE_WIDTH;
    private double lastPlayfieldHeight = INITIAL_SCENE_HEIGHT - 180;

    public GameController() {
        seedBackdrop();
    }

    public Scene createScene() {
        root.setStyle("-fx-background-color: #081019;");
        root.setTop(buildHud());

        playfield.setMinSize(720, 560);
        playfield.prefWidthProperty().bind(centerPane.widthProperty());
        playfield.prefHeightProperty().bind(centerPane.heightProperty());
        playfield.getChildren().addAll(backdropLayer, bubbleLayer, guideLayer, cannonLayer, overlayLayer);

        centerPane.setPadding(new Insets(0, 18, 18, 18));
        centerPane.getChildren().add(playfield);
        root.setCenter(centerPane);

        configureOverlay();
        configureCannonVisuals();
        configureDangerLine();

        scene = new Scene(root, INITIAL_SCENE_WIDTH, INITIAL_SCENE_HEIGHT);
        hookInput();
        hookResize();
        refreshLayout();
        return scene;
    }

    public void start() {
        loadStage(1, true);
        loop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastTickNanos == 0) {
                    lastTickNanos = now;
                    return;
                }
                double deltaSeconds = Math.min(0.032, (now - lastTickNanos) / 1_000_000_000.0);
                lastTickNanos = now;
                update(deltaSeconds);
            }
        };
        loop.start();
    }

    public void stop() {
        if (loop != null) {
            loop.stop();
        }
        soundEngine.close();
    }

    public void refreshLayout() {
        double width = playfield.getWidth() > 0 ? playfield.getWidth() : INITIAL_SCENE_WIDTH - 36;
        double height = playfield.getHeight() > 0 ? playfield.getHeight() : INITIAL_SCENE_HEIGHT - 180;

        backdropRect.setWidth(width);
        backdropRect.setHeight(height);
        renderBackdrop(width, height);
        positionDangerLine(width, height);

        double angle = cannon == null ? 90.0 : cannon.angleDegrees();
        double oldWidth = lastPlayfieldWidth <= 0 ? width : lastPlayfieldWidth;
        double oldHeight = lastPlayfieldHeight <= 0 ? height : lastPlayfieldHeight;

        if (projectile != null) {
            projectile = projectile.withPosition(projectile.x() * width / oldWidth, projectile.y() * height / oldHeight);
        }

        cannon = new Cannon(width / 2.0, height - CANNON_BOTTOM_MARGIN);
        cannon.setAngleDegrees(angle);

        if (grid != null) {
            grid.setLayout(gridLeftPadding(width), GRID_TOP_PADDING);
            renderBoard();
        }

        updateCannonVisual();
        updateTrajectoryPreview();

        lastPlayfieldWidth = width;
        lastPlayfieldHeight = height;
    }

    private VBox buildHud() {
        titleLabel.setTextFill(Color.web("#eff7ff"));
        titleLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 30));

        stageLabel.setTextFill(Color.web("#9ad9ff"));
        stageLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 18));

        objectiveLabel.setTextFill(Color.web("#dde8f7"));
        objectiveLabel.setWrapText(true);
        objectiveLabel.setFont(Font.font("Verdana", 15));

        scoreLabel.setTextFill(Color.web("#ffd166"));
        scoreLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 20));

        comboLabel.setTextFill(Color.web("#ff84c1"));
        comboLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 18));

        pressureLabel.setTextFill(Color.web("#ffb88c"));
        pressureLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 18));

        statusLabel.setTextFill(Color.web("#d8e6f8"));
        statusLabel.setFont(Font.font("Verdana", 14));
        statusLabel.setWrapText(true);

        VBox textColumn = new VBox(6, titleLabel, stageLabel, scoreLabel, comboLabel, pressureLabel, objectiveLabel, statusLabel);
        textColumn.setAlignment(Pos.CENTER_LEFT);

        VBox currentCard = ammoCard("Current", currentPreview, currentLabel);
        VBox nextCard = ammoCard("Next", nextPreview, nextLabel);

        restartButton.setOnAction(event -> restartCampaign());
        restartButton.setFocusTraversable(false);
        soundButton.setOnAction(event -> toggleSound());
        soundButton.setFocusTraversable(false);

        styleHudButton(restartButton, "#2a7246", "#cffff0");
        styleHudButton(soundButton, "#314d78", "#d7ebff");

        VBox buttonColumn = new VBox(10, restartButton, soundButton);
        buttonColumn.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox hud = new HBox(18, textColumn, spacer, currentCard, nextCard, buttonColumn);
        hud.setAlignment(Pos.CENTER_LEFT);
        hud.setPadding(new Insets(16, 18, 16, 18));
        hud.setStyle("-fx-background-color: linear-gradient(to bottom, #0b1b2b, #0a1624);" +
                "-fx-border-color: rgba(130,190,255,0.18); -fx-border-width: 0 0 1 0;");

        VBox wrapper = new VBox(hud);
        return wrapper;
    }

    private VBox ammoCard(String titleText, Circle preview, Label valueLabel) {
        Label title = new Label(titleText);
        title.setTextFill(Color.web("#eff7ff"));
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 15));

        valueLabel.setTextFill(Color.web("#cfe4ff"));
        valueLabel.setFont(Font.font("Verdana", 13));

        VBox box = new VBox(8, title, preview, valueLabel);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(12));
        box.setMinWidth(110);
        box.setStyle("-fx-background-color: rgba(255,255,255,0.04);" +
                "-fx-background-radius: 16; -fx-border-radius: 16;" +
                "-fx-border-color: rgba(173,219,255,0.16);");
        renderAmmoCircle(preview, null);
        return box;
    }

    private void styleHudButton(Button button, String background, String textColor) {
        button.setStyle("-fx-background-radius: 18; -fx-padding: 10 18 10 18;" +
                "-fx-background-color: " + background + "; -fx-text-fill: " + textColor + ";" +
                "-fx-font-family: 'Verdana'; -fx-font-size: 14px; -fx-font-weight: bold;");
    }

    private void configureOverlay() {
        bannerLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 32));
        bannerLabel.setTextFill(Color.web("#eff7ff"));
        bannerLabel.setMouseTransparent(true);
        bannerLabel.setOpacity(0.0);
        overlayLayer.getChildren().add(bannerLabel);
    }

    private void configureCannonVisuals() {
        cannonBarrel.setStroke(Color.web("#8de7ff"));
        cannonBarrel.setStrokeWidth(8.0);
        cannonBarrel.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);

        cannonBase.setFill(new RadialGradient(
                0, 0, 0.4, 0.35, 1.0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#193d59")),
                new Stop(1, Color.web("#071521"))
        ));
        cannonBase.setStroke(Color.web("#c2f0ff"));
        cannonBase.setStrokeWidth(3.0);

        cannonCore.setStroke(Color.web("#d6fbff"));
        cannonCore.setStrokeWidth(2.5);

        cannonLayer.getChildren().addAll(cannonBarrel, cannonBase, cannonCore);
    }

    private void configureDangerLine() {
        dangerLine.setStroke(Color.web("#ff7f6b"));
        dangerLine.setStrokeWidth(2.0);
        dangerLine.getStrokeDashArray().addAll(8.0, 8.0);
        dangerLine.setOpacity(0.65);
        overlayLayer.getChildren().add(dangerLine);
    }

    private void hookInput() {
        playfield.setOnMouseMoved(event -> {
            if (cannon == null || phase == Phase.LOST) {
                return;
            }
            Point2D local = new Point2D(event.getX(), event.getY());
            cannon.aimAt(local.getX(), local.getY());
            updateCannonVisual();
            updateTrajectoryPreview();
        });

        playfield.setOnMouseClicked(event -> {
            if (event.isStillSincePress()) {
                triggerShot();
            }
        });

        scene.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case SPACE, ENTER -> triggerShot();
                case R -> restartCampaign();
                default -> {
                }
            }
        });
    }

    private void hookResize() {
        playfield.widthProperty().addListener((observable, oldValue, newValue) -> refreshLayout());
        playfield.heightProperty().addListener((observable, oldValue, newValue) -> refreshLayout());
    }

    private void seedBackdrop() {
        stars.clear();
        for (int i = 0; i < BACKDROP_STARS; i++) {
            stars.add(new Star(
                    0.04 + random.nextDouble() * 0.92,
                    0.04 + random.nextDouble() * 0.90,
                    1.2 + random.nextDouble() * 2.2,
                    0.22 + random.nextDouble() * 0.45
            ));
        }
    }

    private void renderBackdrop(double width, double height) {
        backdropLayer.getChildren().clear();
        backdropRect.setFill(new LinearGradient(
                0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#06101b")),
                new Stop(0.55, Color.web("#040b14")),
                new Stop(1, Color.web("#02060d"))
        ));
        backdropLayer.getChildren().add(backdropRect);

        Circle glowLeft = new Circle(width * 0.18, height * 0.18, Math.min(width, height) * 0.18);
        glowLeft.setFill(Color.web("#0d2947", 0.35));
        Circle glowRight = new Circle(width * 0.82, height * 0.30, Math.min(width, height) * 0.16);
        glowRight.setFill(Color.web("#18313d", 0.25));
        backdropLayer.getChildren().addAll(glowLeft, glowRight);

        for (Star star : stars) {
            Circle node = new Circle(star.nx() * width, star.ny() * height, star.radius(), Color.web("#dff6ff", star.opacity()));
            backdropLayer.getChildren().add(node);
        }
    }

    private void positionDangerLine(double width, double height) {
        double y = bottomBoundary(height);
        dangerLine.setStartX(24);
        dangerLine.setEndX(width - 24);
        dangerLine.setStartY(y);
        dangerLine.setEndY(y);
    }

    private void update(double deltaSeconds) {
        if (projectile == null) {
            return;
        }

        double remaining = deltaSeconds;
        double sliceSeconds = MAX_PROJECTILE_STEP / PROJECTILE_SPEED;
        while (remaining > 0 && projectile != null) {
            double slice = Math.min(remaining, sliceSeconds);
            ProjectilePhysics.AdvanceResult result = ProjectilePhysics.advance(
                    projectile,
                    slice,
                    playfieldWidth(),
                    BUBBLE_RADIUS,
                    grid.anchoredBubbles()
            );

            if (result.bounced()) {
                playSound(engine -> engine.playBounce());
            }
            if (result.anchored()) {
                anchorProjectile(result.projectile(), result.anchorHint());
                return;
            }

            projectile = result.projectile();
            updateProjectileNode();
            remaining -= slice;
        }
    }

    private void triggerShot() {
        if (phase == Phase.LOST) {
            restartCampaign();
            return;
        }
        if (projectile != null || currentAmmo == null || cannon == null) {
            return;
        }

        projectile = cannon.fire(currentAmmo, BUBBLE_RADIUS, PROJECTILE_SPEED);
        projectileNode = projectile.asCircle();
        bubbleLayer.getChildren().add(projectileNode);

        currentAmmo = nextAmmo;
        nextAmmo = rollAmmo();

        updateHud();
        updateTrajectoryPreview();
        playSound(engine -> engine.playShoot());
    }

    private void anchorProjectile(Bubble impactBubble, Bubble.GridPosition anchorHint) {
        bubbleLayer.getChildren().remove(projectileNode);
        projectileNode = null;
        projectile = null;

        Optional<Bubble> anchored = grid.snapBubble(impactBubble, anchorHint);
        if (anchored.isEmpty()) {
            return;
        }

        resolveShot(anchored.orElseThrow());
    }

    private void resolveShot(Bubble anchored) {
        Set<Bubble> cluster = grid.collectColorCluster(anchored, anchored.color());
        Set<Bubble> floating = Set.of();

        if (cluster.size() >= 3) {
            grid.removeBubbles(cluster);
            floating = grid.collectFloatingBubbles();
            if (!floating.isEmpty()) {
                grid.removeBubbles(floating);
            }

            combo++;
            int gained = (cluster.size() * 90) + (floating.size() * 120) + combo * 35;
            int removedCount = cluster.size() + floating.size();
            score += gained;
            misses = 0;
            statusLabel.setText("Burst +" + gained + ". Combo x" + combo + ".");
            playSound(engine -> engine.playPop(removedCount));
        } else {
            combo = 0;
            misses++;
            statusLabel.setText("No burst. Ceiling pressure " + misses + " / " + MISSES_PER_DROP + ".");
        }

        if (misses >= MISSES_PER_DROP) {
            misses = 0;
            List<BubbleColor> palette = new ArrayList<>(grid.activeColors());
            if (palette.isEmpty()) {
                palette = stageLayout.palette();
            }
            grid.pushPressureRow(palette, stageNumber);
            statusLabel.setText("A new wave pushed the ceiling lower.");
            playSound(engine -> engine.playBounce());
        }

        renderBoard();

        if (grid.isEmpty()) {
            score += stageNumber * 280;
            showBanner("Stage Clear", Color.web("#b8f1ff"));
            playSound(engine -> engine.playStageClear());
            PauseTransition pause = new PauseTransition(Duration.millis(650));
            int nextStage = stageNumber + 1;
            pause.setOnFinished(event -> loadStage(nextStage, false));
            pause.play();
            updateHud();
            return;
        }

        if (grid.hasReachedBottom(bottomBoundary(playfieldHeight()))) {
            phase = Phase.LOST;
            statusLabel.setText("The swarm reached the launcher. Press Restart or Space.");
            showBanner("Run Over", Color.web("#ff9278"));
            playSound(engine -> engine.playGameOver());
        }

        updateHud();
        updateTrajectoryPreview();
    }

    private void renderBoard() {
        bubbleLayer.getChildren().clear();
        for (Bubble bubble : grid.anchoredBubbles()) {
            bubbleLayer.getChildren().add(bubble.asCircle());
        }
        if (projectileNode != null) {
            bubbleLayer.getChildren().add(projectileNode);
            updateProjectileNode();
        }
    }

    private void updateCannonVisual() {
        if (cannon == null) {
            return;
        }
        Point2D origin = cannon.origin();
        double angleRadians = Math.toRadians(cannon.angleDegrees());
        double barrelLength = 58.0;

        cannonBarrel.setStartX(origin.getX());
        cannonBarrel.setStartY(origin.getY());
        cannonBarrel.setEndX(origin.getX() + Math.cos(angleRadians) * barrelLength);
        cannonBarrel.setEndY(origin.getY() - Math.sin(angleRadians) * barrelLength);

        cannonBase.setCenterX(origin.getX());
        cannonBase.setCenterY(origin.getY());

        renderAmmoCircle(cannonCore, currentAmmo);
        cannonCore.setCenterX(origin.getX());
        cannonCore.setCenterY(origin.getY());

        bannerLabel.setLayoutX(playfieldWidth() / 2.0 - 140);
        bannerLabel.setLayoutY(playfieldHeight() / 2.0 - 36);
    }

    private void updateTrajectoryPreview() {
        guideLayer.getChildren().clear();
        if (phase != Phase.PLAYING || currentAmmo == null || cannon == null || projectile != null) {
            return;
        }

        Bubble probe = cannon.fire(currentAmmo, BUBBLE_RADIUS, PROJECTILE_SPEED);
        for (int i = 0; i < 140; i++) {
            ProjectilePhysics.AdvanceResult result = ProjectilePhysics.advance(
                    probe,
                    GUIDE_SLICE_SECONDS,
                    playfieldWidth(),
                    BUBBLE_RADIUS,
                    grid == null ? List.of() : grid.anchoredBubbles()
            );
            probe = result.projectile();

            Circle marker = new Circle(probe.x(), probe.y(), i % 8 == 0 ? 3.6 : 2.3, Color.web("#95f4ff"));
            marker.setOpacity(Math.max(0.18, 0.56 - i * 0.007));
            guideLayer.getChildren().add(marker);

            if (result.anchored()) {
                Circle endMarker = new Circle(probe.x(), probe.y(), 5.0, Color.web("#ffd67a"));
                endMarker.setOpacity(0.9);
                guideLayer.getChildren().add(endMarker);
                break;
            }
        }
    }

    private void updateProjectileNode() {
        if (projectileNode == null || projectile == null) {
            return;
        }
        projectileNode.setCenterX(projectile.x());
        projectileNode.setCenterY(projectile.y());
    }

    private void loadStage(int stage, boolean resetScore) {
        stageNumber = stage;
        if (resetScore) {
            score = 0;
        }
        combo = 0;
        misses = 0;
        phase = Phase.PLAYING;
        projectile = null;
        projectileNode = null;

        stageLayout = stageGenerator.generate(stageNumber, GRID_COLUMNS, random.nextLong());
        if (grid == null) {
            grid = new BubbleGrid(GRID_COLUMNS, BUBBLE_RADIUS, gridLeftPadding(playfieldWidth()), GRID_TOP_PADDING);
        } else {
            grid.clear();
            grid.setLayout(gridLeftPadding(playfieldWidth()), GRID_TOP_PADDING);
        }
        grid.loadLayout(stageLayout);

        currentAmmo = rollAmmo();
        nextAmmo = rollAmmo();

        renderBoard();
        updateHud();
        updateCannonVisual();
        updateTrajectoryPreview();
        lastTickNanos = 0L;

        statusLabel.setText("Clear the formation before " + MISSES_PER_DROP + " misses push a new row.");
        if (!resetScore) {
            showBanner("Stage " + stageNumber, Color.web("#c2f6ff"));
        }
    }

    private void restartCampaign() {
        loadStage(1, true);
    }

    private BubbleAmmo rollAmmo() {
        List<BubbleColor> palette = new ArrayList<>(grid.activeColors());
        if (palette.isEmpty()) {
            palette = stageLayout == null ? List.of(BubbleColor.ROSE, BubbleColor.AZURE, BubbleColor.GOLD) : stageLayout.palette();
        }
        BubbleColor color = palette.get(random.nextInt(palette.size()));
        return BubbleAmmo.normal(color);
    }

    private void updateHud() {
        stageLabel.setText("Stage " + stageNumber + "  •  " + stageLayout.title());
        scoreLabel.setText("Score  " + score);
        comboLabel.setText("Combo  x" + Math.max(1, combo));
        pressureLabel.setText("Pressure  " + misses + " / " + MISSES_PER_DROP);
        objectiveLabel.setText(stageLayout.subtitle());

        renderAmmoCircle(currentPreview, currentAmmo);
        renderAmmoCircle(nextPreview, nextAmmo);
        currentLabel.setText(currentAmmo == null ? "empty" : currentAmmo.color().name());
        nextLabel.setText(nextAmmo == null ? "empty" : nextAmmo.color().name());

        soundButton.setText(soundEnabled ? "Sound: On" : "Sound: Off");
    }

    private void renderAmmoCircle(Circle circle, BubbleAmmo ammo) {
        if (ammo == null) {
            circle.setFill(Color.rgb(255, 255, 255, 0.08));
            circle.setStroke(Color.rgb(255, 255, 255, 0.18));
            circle.setStrokeWidth(1.8);
            return;
        }
        circle.setFill(ammo.kind().fill(ammo.color()));
        circle.setStroke(ammo.kind().stroke(ammo.color()));
        circle.setStrokeWidth(ammo.kind() == BubbleKind.NORMAL ? 2.0 : 3.0);
    }

    private void showBanner(String text, Color color) {
        bannerLabel.setText(text);
        bannerLabel.setTextFill(color);
        bannerLabel.setOpacity(1.0);
        FadeTransition fade = new FadeTransition(Duration.millis(850), bannerLabel);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);
        fade.play();
    }

    private void toggleSound() {
        soundEnabled = !soundEnabled;
        updateHud();
    }

    private void playSound(java.util.function.Consumer<SoundEngine> action) {
        if (soundEnabled) {
            action.accept(soundEngine);
        }
    }

    private double gridLeftPadding(double width) {
        double boardWidth = (GRID_COLUMNS - 1) * (BUBBLE_RADIUS * 2.0) + (BUBBLE_RADIUS * 3.0);
        return Math.max(BUBBLE_RADIUS, (width - boardWidth) / 2.0);
    }

    private double playfieldWidth() {
        return playfield.getWidth() > 0 ? playfield.getWidth() : lastPlayfieldWidth;
    }

    private double playfieldHeight() {
        return playfield.getHeight() > 0 ? playfield.getHeight() : lastPlayfieldHeight;
    }

    private double bottomBoundary(double height) {
        return height - DANGER_MARGIN;
    }
}
