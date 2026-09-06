package com.kilocade.bubbleshooter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
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
import javafx.scene.shape.Ellipse;
import javafx.scene.text.Text;
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
    private static final double GRID_TOP_PADDING = 74.0;
    private static final double CANNON_BOTTOM_MARGIN = 78.0;
    private static final double DANGER_MARGIN = 108.0;
    private static final double GUIDE_SLICE_SECONDS = 0.012;
    private static final double KEYBOARD_AIM_STEP = 3.5;
    private static final double HUD_TEXT_WIDTH = 340.0;
    private static final double HUD_OBJECTIVE_HEIGHT = 18.0;
    private static final double HUD_STATUS_HEIGHT = 34.0;
    private static final double HUD_STAT_WIDTH = 84.0;
    private static final double HUD_AMMO_WIDTH = 78.0;
    private static final double HUD_BUTTON_WIDTH = 108.0;
    private static final int GRID_COLUMNS = 16;
    private static final int MISSES_PER_DROP = 6;
    private static final int BACKDROP_STARS = 56;

    private enum Phase {
        PLAYING,
        PAUSED,
        TRANSITION,
        LOST
    }

    private record Star(double nx, double ny, double radius, double opacity) {
    }

    private final BorderPane root = new BorderPane();
    private final BooleanBinding compactHud = root.widthProperty().lessThan(980);
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
    private final Label recordLabel = new Label();
    private final Tooltip recordTooltip = new Tooltip();
    private final Text sectorText = new Text();
    private final Text remainingText = new Text();
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
    private final Button pauseButton = new Button("Pause");
    private final PauseTransition stageTransition = new PauseTransition(Duration.millis(1100));
    private FadeTransition bannerFade;

    private final StageGenerator stageGenerator = new StageGenerator();
    private final SoundEngine soundEngine = new SoundEngine();
    private final List<Star> stars = new ArrayList<>();
    private final Random random = new Random();
    private final GameEffects effects = new GameEffects();
    private final HighScoreStore records;
    private boolean shotRicochet;

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
        this(HighScoreStore.local());
    }

    GameController(HighScoreStore records) {
        this.records = records;
        seedBackdrop();
    }

    public Scene createScene() {
        root.setStyle("-fx-background-color: #050a12;");
        root.setTop(buildHud());

        playfield.setMinSize(720, 560);
        playfield.prefWidthProperty().bind(centerPane.widthProperty());
        playfield.prefHeightProperty().bind(centerPane.heightProperty());
        playfield.getChildren().addAll(backdropLayer, bubbleLayer, guideLayer, cannonLayer, effects.layer(), overlayLayer);
        Rectangle effectsClip = new Rectangle();
        effectsClip.widthProperty().bind(playfield.widthProperty());
        effectsClip.heightProperty().bind(playfield.heightProperty());
        effects.layer().setClip(effectsClip);

        centerPane.setPadding(new Insets(0, 14, 14, 14));
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
        records.record(score);
        effects.clear();
        stageTransition.stop();
        if (bannerFade != null) bannerFade.stop();
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
        effects.shift(gridLeftPadding(width) - gridLeftPadding(oldWidth));
        if (projectile != null) {
            // Translate with the board: resizing must not bend a shot or push it into a cluster.
            projectile = projectile.withPosition(projectile.x() + gridLeftPadding(width) - gridLeftPadding(oldWidth), projectile.y());
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
        titleLabel.fontProperty().bind(Bindings.createObjectBinding(
                () -> Font.font("Verdana", FontWeight.BOLD, compactHud.get() ? 22 : 28), compactHud));
        var textWidth = Bindings.when(compactHud).then(200.0).otherwise(HUD_TEXT_WIDTH);

        stageLabel.setTextFill(Color.web("#9ad9ff"));
        stageLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 16));

        objectiveLabel.setTextFill(Color.web("#b8cde2"));
        objectiveLabel.setWrapText(true);
        objectiveLabel.setFont(Font.font("Verdana", 13));
        objectiveLabel.setMinWidth(0);
        objectiveLabel.prefWidthProperty().bind(textWidth);
        objectiveLabel.maxWidthProperty().bind(textWidth);
        objectiveLabel.setMinHeight(HUD_OBJECTIVE_HEIGHT);
        objectiveLabel.setPrefHeight(HUD_OBJECTIVE_HEIGHT);
        objectiveLabel.setMaxHeight(HUD_OBJECTIVE_HEIGHT);

        statusLabel.setTextFill(Color.web("#e6f3ff"));
        statusLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 13));
        statusLabel.setWrapText(true);
        statusLabel.setMinWidth(0);
        statusLabel.prefWidthProperty().bind(textWidth);
        statusLabel.maxWidthProperty().bind(textWidth);
        statusLabel.setMinHeight(HUD_STATUS_HEIGHT);
        statusLabel.setPrefHeight(HUD_STATUS_HEIGHT);
        statusLabel.setMaxHeight(HUD_STATUS_HEIGHT);

        VBox textColumn = new VBox(4, titleLabel, stageLabel, objectiveLabel, statusLabel);
        textColumn.setAlignment(Pos.CENTER_LEFT);
        textColumn.minWidthProperty().bind(textWidth);
        textColumn.prefWidthProperty().bind(textWidth);
        textColumn.maxWidthProperty().bind(textWidth);

        VBox scoreChip = statChip("Score", scoreLabel, "#ffd166");
        recordLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 9));
        recordLabel.setTextFill(Color.web("#b9a1f4"));
        recordLabel.setTooltip(recordTooltip);
        scoreChip.getChildren().add(recordLabel);
        VBox comboChip = statChip("Combo", comboLabel, "#ff84c1");
        VBox pressureChip = statChip("Pressure", pressureLabel, "#ffb88c");
        HBox stats = new HBox(8, scoreChip, comboChip, pressureChip);
        stats.setAlignment(Pos.CENTER_LEFT);
        stats.setFillHeight(false);
        stats.setMinWidth(Region.USE_PREF_SIZE);

        HBox ammoQueue = new HBox(8, ammoCard("Loaded", currentPreview, currentLabel), ammoCard("Queued", nextPreview, nextLabel));
        ammoQueue.setAlignment(Pos.CENTER);
        ammoQueue.setMinWidth(0);

        restartButton.setOnAction(event -> restartCampaign());
        restartButton.setFocusTraversable(false);
        soundButton.setOnAction(event -> toggleSound());
        soundButton.setFocusTraversable(false);
        pauseButton.setOnAction(event -> togglePause());
        pauseButton.setFocusTraversable(false);

        styleHudButton(restartButton, "#594ca3", "#faf5ff");
        styleHudButton(soundButton, "#233550", "#dfeaff");
        styleHudButton(pauseButton, "#233550", "#dfeaff");

        VBox buttonColumn = new VBox(6, restartButton, soundButton, pauseButton);
        buttonColumn.setAlignment(Pos.CENTER_LEFT);

        HBox rightCluster = new HBox(12, ammoQueue, buttonColumn);
        rightCluster.setAlignment(Pos.CENTER_RIGHT);
        rightCluster.setMinWidth(Region.USE_PREF_SIZE);

        HBox hud = new HBox(20, textColumn, stats, rightCluster);
        hud.setAlignment(Pos.CENTER_LEFT);
        hud.setMinWidth(0);
        hud.setPadding(new Insets(14, 18, 14, 18));
        hud.setStyle("-fx-background-color: linear-gradient(to bottom right, #181a36, #0b1626);" +
                "-fx-border-color: rgba(181,164,255,0.22); -fx-border-width: 0 0 1 0;");

        Label controls = new Label("Mouse / A D: aim   •   Click / Space: shoot   •   X / right click: swap   •   P / Esc: pause   •   R: restart");
        controls.setStyle("-fx-text-fill: #9fb7cd; -fx-font-size: 11px; -fx-padding: 5 18 8 18;");
        VBox wrapper = new VBox(hud, controls);
        return wrapper;
    }

    private VBox statChip(String titleText, Label valueLabel, String accentColor) {
        Label title = new Label(titleText);
        title.setTextFill(Color.web("#9fb7cd"));
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 10));

        valueLabel.setTextFill(Color.web(accentColor));
        valueLabel.fontProperty().bind(Bindings.createObjectBinding(
                () -> Font.font("Verdana", FontWeight.BOLD, compactHud.get() ? 16 : 20), compactHud));
        valueLabel.setMinWidth(0);
        var chipWidth = Bindings.when(compactHud).then(64.0).otherwise(HUD_STAT_WIDTH);
        valueLabel.prefWidthProperty().bind(chipWidth.subtract(12));
        valueLabel.maxWidthProperty().bind(chipWidth.subtract(12));
        valueLabel.setAlignment(Pos.CENTER_LEFT);

        VBox chip = new VBox(2, title, valueLabel);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.setPadding(new Insets(8, 6, 8, 6));
        chip.minWidthProperty().bind(chipWidth);
        chip.prefWidthProperty().bind(chipWidth);
        chip.maxWidthProperty().bind(chipWidth);
        chip.setStyle("-fx-background-color: rgba(255,255,255,0.055);" +
                "-fx-background-radius: 14; -fx-border-radius: 14;" +
                "-fx-border-color: rgba(180,222,255,0.16);");
        return chip;
    }

    private VBox ammoCard(String titleText, Circle preview, Label valueLabel) {
        Label title = new Label(titleText);
        title.setTextFill(Color.web("#bcd2e8"));
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 10));

        valueLabel.setTextFill(Color.web("#f4f9ff"));
        valueLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 11));
        valueLabel.setMinWidth(0);
        var cardWidth = Bindings.when(compactHud).then(64.0).otherwise(HUD_AMMO_WIDTH);
        valueLabel.prefWidthProperty().bind(cardWidth.subtract(18));
        valueLabel.maxWidthProperty().bind(cardWidth.subtract(18));
        valueLabel.setAlignment(Pos.CENTER);

        VBox box = new VBox(6, title, preview, valueLabel);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(8, 6, 8, 6));
        box.minWidthProperty().bind(cardWidth);
        box.prefWidthProperty().bind(cardWidth);
        box.maxWidthProperty().bind(cardWidth);
        box.setStyle("-fx-background-color: rgba(255,255,255,0.055);" +
                "-fx-background-radius: 14; -fx-border-radius: 14;" +
                "-fx-border-color: rgba(173,219,255,0.18);");
        renderAmmoCircle(preview, null);
        return box;
    }

    private void styleHudButton(Button button, String background, String textColor) {
        var buttonWidth = Bindings.when(compactHud).then(96.0).otherwise(HUD_BUTTON_WIDTH);
        button.minWidthProperty().bind(buttonWidth);
        button.prefWidthProperty().bind(buttonWidth);
        button.maxWidthProperty().bind(buttonWidth);
        button.setStyle("-fx-background-radius: 8; -fx-padding: 9 8 9 8;" +
                "-fx-background-color: " + background + "; -fx-text-fill: " + textColor + ";" +
                "-fx-font-family: 'Verdana'; -fx-font-size: 13px; -fx-font-weight: bold;");
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
            if (cannon == null || phase != Phase.PLAYING) {
                return;
            }
            Point2D local = new Point2D(event.getX(), event.getY());
            cannon.aimAt(local.getX(), local.getY());
            updateCannonVisual();
            updateTrajectoryPreview();
        });

        playfield.setOnMouseClicked(event -> {
            if (event.isStillSincePress()) {
                if (event.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                    swapAmmo();
                } else if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                    triggerShot();
                }
            }
        });

        scene.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case SPACE, ENTER -> triggerShot();
                case LEFT, A -> adjustAim(KEYBOARD_AIM_STEP);
                case RIGHT, D -> adjustAim(-KEYBOARD_AIM_STEP);
                case R -> restartCampaign();
                case X -> swapAmmo();
                case P, ESCAPE -> togglePause();
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
                    0.6 + random.nextDouble() * 1.2,
                    0.15 + random.nextDouble() * 0.35
            ));
        }
    }

    private void renderBackdrop(double width, double height) {
        backdropLayer.getChildren().clear();
        backdropRect.setFill(new LinearGradient(
                0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#11162e")),
                new Stop(0.55, Color.web("#0a1424")),
                new Stop(1, Color.web("#080f1e"))
        ));
        backdropLayer.getChildren().add(backdropRect);

        Circle glowLeft = new Circle(width * 0.22, height * 0.28, Math.min(width, height) * 0.5);
        glowLeft.setFill(new RadialGradient(0, 0, .5, .5, .5, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#7359b9", .19)), new Stop(1, Color.TRANSPARENT)));
        Circle glowRight = new Circle(width * 0.8, height * 0.64, Math.min(width, height) * 0.42);
        glowRight.setFill(new RadialGradient(0, 0, .5, .5, .5, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#36a5af", .11)), new Stop(1, Color.TRANSPARENT)));
        backdropLayer.getChildren().addAll(glowLeft, glowRight);

        for (int i = 0; i < 3; i++) {
            Ellipse orbit = new Ellipse(width / 2, height * .48, width * (.31 + i * .055), height * .22);
            orbit.setFill(Color.TRANSPARENT);
            orbit.setStroke(Color.web("#b0b9ef", .055));
            orbit.setRotate(-28);
            backdropLayer.getChildren().add(orbit);
        }

        for (Star star : stars) {
            Circle node = new Circle(star.nx() * width, star.ny() * height, star.radius(), Color.web("#dff6ff", star.opacity()));
            backdropLayer.getChildren().add(node);
        }
        double left = gridLeftPadding(width) - BUBBLE_RADIUS;
        double right = gridLeftPadding(width) + GRID_COLUMNS * 2 * BUBBLE_RADIUS;
        double top = GRID_TOP_PADDING - BUBBLE_RADIUS;
        Rectangle arena = new Rectangle(left, top, right - left, Math.max(0, height - 28 - top));
        arena.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#526388", .09)), new Stop(1, Color.web("#071323", .16))));
        backdropLayer.getChildren().add(arena);
        for (Line rail : List.of(new Line(left, height - 28, left, top),
                new Line(left, top, right, top), new Line(right, top, right, height - 28))) {
            rail.setStroke(Color.web("#96b8ef", 0.4));
            rail.setStrokeWidth(1.5);
            backdropLayer.getChildren().add(rail);
        }
        for (double x : new double[]{left, right}) {
            Circle cap = new Circle(x, top, 3, Color.web("#d0c0ff"));
            backdropLayer.getChildren().add(cap);
            for (int tick = 0; tick < 5; tick++) {
                double y = top + 60 + tick * 80;
                if (y < height - 28) {
                    Line mark = new Line(x - 3, y, x + 3, y);
                    mark.setStroke(Color.web("#99b6e5", .5));
                    backdropLayer.getChildren().add(mark);
                }
            }
        }
        sectorText.setFont(Font.font("Verdana", FontWeight.BOLD, 10));
        sectorText.setFill(Color.web("#b7a6e5"));
        sectorText.setX(left);
        sectorText.setY(top - 18);
        remainingText.setFont(Font.font("Verdana", FontWeight.BOLD, 10));
        remainingText.setFill(Color.web("#9aafc9"));
        remainingText.setX(right - 64);
        remainingText.setY(top - 18);
        backdropLayer.getChildren().addAll(sectorText, remainingText);
    }

    private void positionDangerLine(double width, double height) {
        double y = bottomBoundary(height);
        dangerLine.setStartX(gridLeftPadding(width) - BUBBLE_RADIUS);
        dangerLine.setEndX(gridLeftPadding(width) + GRID_COLUMNS * 2 * BUBBLE_RADIUS);
        dangerLine.setStartY(y);
        dangerLine.setEndY(y);
    }

    private void update(double deltaSeconds) {
        if (phase == Phase.PAUSED) return;
        effects.advance(deltaSeconds);
        if (projectile == null || phase != Phase.PLAYING) {
            return;
        }

        effects.trail(projectile);
        ProjectilePhysics.AdvanceResult result = ProjectilePhysics.advance(
                projectile, deltaSeconds, grid.projectileBounds(), BUBBLE_RADIUS, grid.anchoredBubbles());

        if (result.bounced()) {
            shotRicochet = true;
            var bounds = grid.projectileBounds();
            double wallX = projectile.vx() < 0 ? bounds.minX() : bounds.maxX();
            double time = projectile.vx() == 0 ? 0 : Math.max(0, (wallX - projectile.x()) / projectile.vx());
            effects.ring(wallX + (projectile.vx() < 0 ? -BUBBLE_RADIUS : BUBBLE_RADIUS),
                    projectile.y() + projectile.vy() * time, Color.web("#ffe6b0"));
            playSound(engine -> engine.playBounce());
        }
        if (result.anchored()) {
            anchorProjectile(result.projectile(), result.anchorHint());
            return;
        }

        projectile = result.projectile();
        updateProjectileNode();
    }

    private void triggerShot() {
        if (phase == Phase.LOST) {
            restartCampaign();
            return;
        }
        if (phase != Phase.PLAYING || projectile != null || currentAmmo == null || cannon == null) {
            return;
        }

        projectile = cannon.fire(currentAmmo, BUBBLE_RADIUS, PROJECTILE_SPEED);
        shotRicochet = false;
        projectileNode = projectile.asCircle();
        bubbleLayer.getChildren().add(projectileNode);
        updateCannonVisual();
        updateTrajectoryPreview();
        playSound(engine -> engine.playShoot());
    }

    private void adjustAim(double degrees) {
        if (phase != Phase.PLAYING || cannon == null || projectile != null) {
            return;
        }
        cannon.adjustAim(degrees);
        updateCannonVisual();
        updateTrajectoryPreview();
    }

    private void anchorProjectile(Bubble impactBubble, Bubble.GridPosition anchorHint) {
        Optional<Bubble> anchored = grid.snapBubble(impactBubble, anchorHint);
        if (anchored.isEmpty()) {
            bubbleLayer.getChildren().remove(projectileNode);
            projectileNode = null;
            projectile = null;
            // The queue is consumed only after a legal placement. Therefore a
            // malformed board returns precisely the loaded bubble and keeps
            // the previewed next bubble intact.
            statusLabel.setText("No legal attachment slot — bubble returned.");
            updateHud();
            updateTrajectoryPreview();
            return;
        }

        bubbleLayer.getChildren().remove(projectileNode);
        projectileNode = null;
        projectile = null;

        currentAmmo = nextAmmo;
        nextAmmo = rollAmmo();

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
            ShotReward reward = ShotReward.calculate(cluster.size(), floating.size(), combo, shotRicochet);
            int gained = reward.total();
            int removedCount = cluster.size() + floating.size();
            score += gained;
            misses = 0;
            statusLabel.setText("Burst +" + gained + " · Combo x" + combo
                    + (reward.ricochetBonus() > 0 ? " · Bank +150" : "")
                    + (reward.avalancheBonus() > 0 ? " · Drop +250" : ""));
            cluster.forEach(effects::burst);
            floating.forEach(effects::drop);
            effects.caption("+" + gained + "  /  COMBO " + combo, playfieldWidth() / 2,
                    Math.min(anchored.y() + 70, bottomBoundary(playfieldHeight()) - 40), Color.web("#fff0c9"));
            if (reward.ricochetBonus() > 0 || reward.avalancheBonus() > 0) {
                effects.caption(reward.avalancheBonus() > 0 ? "AVALANCHE +250" : "BANK SHOT +150",
                        playfieldWidth() / 2, bottomBoundary(playfieldHeight()) - 12, Color.web("#a5f4ee"));
            }
            playSound(engine -> engine.playPop(removedCount));
        } else {
            effects.ring(anchored.x(), anchored.y(), anchored.color().toFxColor());
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
            // Ignore input until the next layout replaces this cleared board.
            // Otherwise a click during the transition fires a projectile that is
            // immediately discarded by loadStage().
            phase = Phase.TRANSITION;
            showBanner("Stage Clear", Color.web("#b8f1ff"));
            playSound(engine -> engine.playStageClear());
            int nextStage = stageNumber + 1;
            stageTransition.setOnFinished(event -> loadStage(nextStage, false));
            stageTransition.playFromStart();
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
        int remaining = grid.anchoredBubbles().size();
        remainingText.setText(remaining + (remaining == 1 ? " ORB" : " ORBS"));
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
        List<Bubble> obstacles = grid.anchoredBubbles();
        int steps = (int) Math.ceil((probe.y() - GRID_TOP_PADDING) / -probe.vy() / GUIDE_SLICE_SECONDS) + 1;
        for (int i = 0; i < steps; i++) {
            ProjectilePhysics.AdvanceResult result = ProjectilePhysics.advance(
                    probe,
                    GUIDE_SLICE_SECONDS,
                    grid.projectileBounds(),
                    BUBBLE_RADIUS,
                    obstacles
            );
            probe = result.projectile();

            Circle marker = new Circle(probe.x(), probe.y(), i % 8 == 0 ? 3.6 : 2.3, Color.web("#95f4ff"));
            marker.setOpacity(Math.max(0.18, 0.56 - i * 0.007));
            guideLayer.getChildren().add(marker);

            if (result.anchored()) {
                grid.attachmentCell(probe, result.anchorHint()).ifPresent(cell -> {
                    Circle landing = new Circle(grid.cellCenterX(cell.row(), cell.column()),
                            grid.cellCenterY(cell.row()), BUBBLE_RADIUS);
                    landing.setFill(currentAmmo.color().toFxColor().deriveColor(0, 1, 1, 0.25));
                    landing.setStroke(Color.web("#ffd67a"));
                    landing.getStrokeDashArray().addAll(4.0, 4.0);
                    guideLayer.getChildren().add(landing);
                });
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
        effects.clear();
        shotRicochet = false;
        stageTransition.stop();
        if (bannerFade != null) bannerFade.stop();
        bannerLabel.setOpacity(0);
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

        statusLabel.setText("Click / Space to shoot. " + MISSES_PER_DROP + " misses add a row.");
        if (!resetScore) {
            showBanner("Stage " + stageNumber, Color.web("#c2f6ff"));
        }
    }

    private void restartCampaign() {
        loadStage(1, true);
    }

    private void swapAmmo() {
        if (phase != Phase.PLAYING || projectile != null) return;
        BubbleAmmo previous = currentAmmo;
        currentAmmo = nextAmmo;
        nextAmmo = previous;
        updateHud();
        updateCannonVisual();
        updateTrajectoryPreview();
    }

    private void togglePause() {
        if (phase == Phase.PLAYING) {
            phase = Phase.PAUSED;
            if (bannerFade != null) bannerFade.stop();
            bannerLabel.setText("Paused");
            bannerLabel.setOpacity(1);
        } else if (phase == Phase.PAUSED) {
            phase = Phase.PLAYING;
            bannerLabel.setOpacity(0);
            lastTickNanos = 0;
        }
        updateHud();
        updateTrajectoryPreview();
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
        records.record(score);
        recordLabel.setText("BEST " + compactNumber(records.best()));
        recordTooltip.setText("Best score: " + records.best() + (records.saved() ? " · saved locally" : " · session only; storage unavailable"));
        recordLabel.setTextFill(Color.web(records.saved() ? "#b9a1f4" : "#ffb88c"));
        sectorText.setText(String.format("SECTOR %02d  /  COMET BLOOM", stageNumber));
        stageLabel.setText("Stage " + stageNumber + "  •  " + stageLayout.title());
        scoreLabel.setText(compactNumber(score));
        scoreLabel.setTooltip(new Tooltip(Integer.toString(score)));
        comboLabel.setText("x" + Math.max(1, combo));
        pressureLabel.setText(misses + " / " + MISSES_PER_DROP);
        objectiveLabel.setText(stageLayout.subtitle());

        renderAmmoCircle(currentPreview, currentAmmo);
        renderAmmoCircle(nextPreview, nextAmmo);
        currentLabel.setText(currentAmmo == null ? "EMPTY" : currentAmmo.displayName());
        nextLabel.setText(nextAmmo == null ? "EMPTY" : nextAmmo.displayName());

        soundButton.setText(soundEnabled ? "Sound: On" : "Sound: Off");
        pauseButton.setText(phase == Phase.PAUSED ? "Resume" : "Pause");
        pauseButton.setDisable(phase == Phase.TRANSITION || phase == Phase.LOST);
        updateCannonVisual();
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
        if (bannerFade != null) bannerFade.stop();
        bannerFade = new FadeTransition(Duration.millis(850), bannerLabel);
        bannerFade.setFromValue(1.0);
        bannerFade.setToValue(0.0);
        bannerFade.play();
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
        return Math.max(BUBBLE_RADIUS, (width - boardWidth) / 2.0 + BUBBLE_RADIUS);
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

    private static String compactNumber(int value) {
        if (value < 10_000) return Integer.toString(value);
        if (value < 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fk", value / 1000.0);
        return String.format(java.util.Locale.ROOT, "%.1fm", value / 1_000_000.0);
    }
}
