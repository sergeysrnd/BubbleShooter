package com.kilocade.bubbleshooter;

import com.kilocade.bubbleshooter.Bubble.GridPosition;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.util.Duration;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;

/**
 * Central gameplay controller: instantiates the grid and cannon, wires input handlers,
 * drives the game loop, and coordinates collision / popping logic.
 */
public final class GameController {

    private static final double SCENE_WIDTH = 800;
    private static final double SCENE_HEIGHT = 600;
    private static final double BUBBLE_RADIUS = 18;
    private static final int GRID_COLUMNS = 18;
    private static final double GRID_TOP_PADDING = 80;
    private static final double BOTTOM_BOUNDARY = SCENE_HEIGHT - 70;
    private static final double AIM_SPEED_DEG_PER_SEC = 180;
    private static final double AIM_LINE_LENGTH = 120;

    private enum Difficulty {
        EASY(4, 3, 7),
        MEDIUM(6, 4, 5),
        HARD(8, 5, 3);

        final int initialRows;
        final int colorCount;
        final int turnsPerNewRow;

        Difficulty(int initialRows, int colorCount, int turnsPerNewRow) {
            this.initialRows = initialRows;
            this.colorCount = colorCount;
            this.turnsPerNewRow = turnsPerNewRow;
        }
    }

    private Difficulty difficulty = Difficulty.MEDIUM;

    private Scene scene;
    private final Pane playfield = new Pane();
    private final Group bubbleLayer = new Group();
    private final Line aimLine = new Line();
    private final Circle cannonBase = new Circle(25);

    private final IntegerProperty score = new SimpleIntegerProperty();
    private final Label scoreLabel = new Label();
    private final Label difficultyLabel = new Label();
    private final Label infoLabel = new Label();

    private final Map<Bubble, Circle> anchoredNodes = new IdentityHashMap<>();

    private BubbleGrid grid;
    private Cannon cannon;

    private AnimationTimer loop;
    private long lastFrameNanos;

    private Bubble projectile;
    private Circle projectileNode;

    private boolean leftHeld;
    private boolean rightHeld;
    private boolean spaceHeld;
    private boolean gameOver;

    private int turnsSinceRow;

    /**
     * Creates the top-level game controller and prepares static UI components.
     */
    public GameController() {
        playfield.setPrefSize(SCENE_WIDTH, SCENE_HEIGHT);
        playfield.setStyle("-fx-background-color: linear-gradient(to bottom, #0a1729, #040b16);");
        
        cannonBase.setStroke(Color.WHITE);
        cannonBase.setStrokeWidth(3);
        
        playfield.getChildren().addAll(bubbleLayer, cannonBase, aimLine);

        aimLine.setStroke(Color.rgb(255, 255, 255, 0.65));
        aimLine.setStrokeWidth(2);
        aimLine.getStrokeDashArray().setAll(10.0, 10.0);

        initialiseGameState();
    }

    /**
     * Builds the JavaFX scene graph and wires control panels.
     *
     * @return configured scene ready to be displayed.
     */
    public Scene createScene() {
        BorderPane root = new BorderPane(playfield);
        root.setPrefSize(SCENE_WIDTH, SCENE_HEIGHT);

        configureTopBar(root);
        configureInputHandlers();

        scene = new Scene(root, SCENE_WIDTH, SCENE_HEIGHT);
        return scene;
    }

    /**
     * Starts the animation loop responsible for driving projectile movement and updates.
     */
    public void start() {
        if (loop != null) {
            return;
        }
        loop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastFrameNanos == 0) {
                    lastFrameNanos = now;
                    return;
                }
                double deltaSeconds = (now - lastFrameNanos) / 1_000_000_000.0;
                lastFrameNanos = now;
                update(deltaSeconds);
            }
        };
        loop.start();
    }

    /**
     * Stops the animation loop (invoked when the JavaFX application shuts down).
     */
    public void stop() {
        if (loop != null) {
            loop.stop();
            loop = null;
        }
        lastFrameNanos = 0;
    }

    private void initialiseGameState() {
        double gridWidth = GRID_COLUMNS * BUBBLE_RADIUS * 2;
        double leftPadding = (SCENE_WIDTH - gridWidth) / 2.0;
        grid = new BubbleGrid(GRID_COLUMNS, BUBBLE_RADIUS, leftPadding, GRID_TOP_PADDING,
                              difficulty.initialRows, difficulty.colorCount);
        Point2D origin = new Point2D(SCENE_WIDTH / 2.0, SCENE_HEIGHT - 80);
        cannon = new Cannon(origin.getX(), origin.getY());
        cannon.setColorCount(difficulty.colorCount);
        
        cannonBase.setCenterX(origin.getX());
        cannonBase.setCenterY(origin.getY());

        projectile = null;
        if (projectileNode != null) {
            bubbleLayer.getChildren().remove(projectileNode);
            projectileNode = null;
        }
        score.set(0);
        infoLabel.setText("Use mouse or arrows to aim, Space or mouse click to shoot.");
        turnsSinceRow = 0;
        leftHeld = rightHeld = spaceHeld = false;
        gameOver = false;

        rebuildAnchoredNodes();
        updateAimLine();
        updateCannonColor();
    }

    private void configureTopBar(BorderPane root) {
        score.set(0);
        scoreLabel.setText("Score: 0");
        scoreLabel.textProperty().bind(score.asString("Score: %d"));
        scoreLabel.setTextFill(Color.WHITE);
        scoreLabel.setFont(Font.font("Consolas", 20));
        scoreLabel.setMinWidth(120);

        difficultyLabel.setText("Level: " + difficulty.name());
        difficultyLabel.setTextFill(Color.LIGHTGREEN);
        difficultyLabel.setFont(Font.font("Consolas", 18));
        difficultyLabel.setMinWidth(150);

        infoLabel.setTextFill(Color.LIGHTSKYBLUE);
        infoLabel.setFont(Font.font("Consolas", 11));
        infoLabel.setWrapText(true);
        infoLabel.setMaxWidth(250);

        Button easyBtn = new Button("Easy");
        easyBtn.setOnAction(e -> changeDifficulty(Difficulty.EASY));
        styleButton(easyBtn, "#2c3e50");
        
        Button mediumBtn = new Button("Medium");
        mediumBtn.setOnAction(e -> changeDifficulty(Difficulty.MEDIUM));
        styleButton(mediumBtn, "#2c3e50");
        
        Button hardBtn = new Button("Hard");
        hardBtn.setOnAction(e -> changeDifficulty(Difficulty.HARD));
        styleButton(hardBtn, "#2c3e50");

        Button restartButton = new Button("Restart");
        restartButton.setOnAction(event -> initialiseGameState());
        styleButton(restartButton, "#27ae60");

        HBox topBar = new HBox(12, scoreLabel, difficultyLabel, easyBtn, mediumBtn, hardBtn, restartButton, infoLabel);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(8, 12, 8, 12));
        topBar.setStyle("-fx-background-color: rgba(5, 10, 18, 0.85);");

        root.setTop(topBar);
    }

    private void styleButton(Button btn, String bgColor) {
        btn.setStyle("-fx-text-fill: white; -fx-background-color: " + bgColor +
                     "; -fx-padding: 5 10; -fx-font-size: 12px; -fx-cursor: hand;");
        btn.setMinWidth(70);
    }

    private void changeDifficulty(Difficulty newDifficulty) {
        difficulty = newDifficulty;
        difficultyLabel.setText("Level: " + difficulty.name());
        initialiseGameState();
    }

    private void configureInputHandlers() {
        playfield.setOnMouseMoved(event -> {
            Point2D local = playfield.sceneToLocal(event.getSceneX(), event.getSceneY());
            cannon.aimAt(local.getX(), local.getY());
            updateAimLine();
        });

        playfield.setOnMouseClicked(event -> {
            playfield.requestFocus();
            triggerShot();
        });

        playfield.setOnMouseExited(event -> updateAimLine());

        playfield.setOnMouseEntered(event -> {
            playfield.requestFocus();
            updateAimLine();
        });

        playfield.setFocusTraversable(true);

        playfield.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.LEFT || event.getCode() == KeyCode.A) {
                leftHeld = true;
            }
            if (event.getCode() == KeyCode.RIGHT || event.getCode() == KeyCode.D) {
                rightHeld = true;
            }
            if (event.getCode() == KeyCode.SPACE) {
                if (!spaceHeld) {
                    triggerShot();
                }
                spaceHeld = true;
            }
        });

        playfield.setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.LEFT || event.getCode() == KeyCode.A) {
                leftHeld = false;
            }
            if (event.getCode() == KeyCode.RIGHT || event.getCode() == KeyCode.D) {
                rightHeld = false;
            }
            if (event.getCode() == KeyCode.SPACE) {
                spaceHeld = false;
            }
        });
    }

    private void update(double deltaSeconds) {
        if (gameOver) {
            return;
        }

        if (leftHeld) {
            cannon.adjustAim(-AIM_SPEED_DEG_PER_SEC * deltaSeconds);
            updateAimLine();
        }
        if (rightHeld) {
            cannon.adjustAim(AIM_SPEED_DEG_PER_SEC * deltaSeconds);
            updateAimLine();
        }

        if (projectile != null) {
            updateProjectile(deltaSeconds);
        }
    }

    private void triggerShot() {
        if (gameOver) {
            initialiseGameState();
            return;
        }
        if (projectile != null) {
            return;
        }
        Bubble shot = cannon.shoot();
        projectile = shot;
        projectileNode = shot.asCircle();
        projectileNode.setStrokeWidth(2);
        projectileNode.setStroke(Color.WHITE);
        bubbleLayer.getChildren().add(projectileNode);
        updateCannonColor();
    }

    private void updateProjectile(double deltaSeconds) {
        double nextX = projectile.x() + projectile.vx() * deltaSeconds;
        double nextY = projectile.y() + projectile.vy() * deltaSeconds;

        Collision collision = detectCollision(nextX, nextY);
        switch (collision) {
            case Collision.Wall wall -> {
                projectile = projectile
                        .withPosition(wall.clampedX(), nextY)
                        .withVelocity(wall.reflectedVx(), projectile.vy());
                projectileNode.setCenterX(projectile.x());
                projectileNode.setCenterY(projectile.y());
            }
            case Collision.Grid gridImpact -> anchorProjectile(projectile.withPosition(nextX, nextY), gridImpact.anchorHint());
            case null, default -> {
                projectile = projectile.withPosition(nextX, nextY);
                projectileNode.setCenterX(nextX);
                projectileNode.setCenterY(nextY);
            }
        }
    }

    private Collision detectCollision(double nextX, double nextY) {
        double vx = projectile.vx();

        if (nextX <= BUBBLE_RADIUS) {
            return new Collision.Wall(BUBBLE_RADIUS, Math.abs(vx));
        }
        if (nextX >= SCENE_WIDTH - BUBBLE_RADIUS) {
            return new Collision.Wall(SCENE_WIDTH - BUBBLE_RADIUS, -Math.abs(vx));
        }
        if (nextY <= BUBBLE_RADIUS) {
            return new Collision.Grid(null);
        }

        List<Bubble> anchored = grid.anchoredBubbles();
        for (Bubble other : anchored) {
            double dx = nextX - other.x();
            double dy = nextY - other.y();
            double distance = Math.hypot(dx, dy);
            if (distance <= (BUBBLE_RADIUS * 2) - 0.5) {
                return new Collision.Grid(other.gridPosition());
            }
        }
        return null;
    }

    private void anchorProjectile(Bubble candidate, GridPosition hint) {
        Optional<Bubble> anchored = grid.snapBubble(candidate);
        bubbleLayer.getChildren().remove(projectileNode);
        projectile = null;
        projectileNode = null;

        anchored.ifPresent(this::afterBubbleAnchored);
    }

    private void afterBubbleAnchored(Bubble anchored) {
        // Record the new bubble visually.
        Circle circle = anchored.asCircle();
        bubbleLayer.getChildren().add(circle);
        anchoredNodes.put(anchored, circle);

        resolveClusters(anchored);
        advanceTurn();
        updateAimLine();
    }

    private void resolveClusters(Bubble anchored) {
        Set<Bubble> cluster = grid.collectCluster(anchored);
        if (cluster.size() >= 3) {
            score.set(score.get() + cluster.size() * 50);
            grid.removeBubbles(cluster);
            removeAnchoredNodes(cluster);

            Set<Bubble> floating = grid.collectFloatingBubbles();
            if (!floating.isEmpty()) {
                score.set(score.get() + floating.size() * 25);
                grid.removeBubbles(floating);
                removeAnchoredNodes(floating);
            }
        }
        checkGameOver();
    }

    private void advanceTurn() {
        turnsSinceRow++;
        if (turnsSinceRow >= difficulty.turnsPerNewRow) {
            turnsSinceRow = 0;
            grid.pushNewRow(difficulty.colorCount);
            rebuildAnchoredNodes();
            infoLabel.setText("New row added!");
            checkGameOver();
        } else {
            infoLabel.setText("Next row in " + (difficulty.turnsPerNewRow - turnsSinceRow));
        }
    }

    private void updateAimLine() {
        Point2D origin = cannon.origin();
        double angleRadians = Math.toRadians(cannon.angleDegrees());
        double endX = origin.getX() + Math.cos(angleRadians) * AIM_LINE_LENGTH;
        double endY = origin.getY() - Math.sin(angleRadians) * AIM_LINE_LENGTH;

        aimLine.setStartX(origin.getX());
        aimLine.setStartY(origin.getY());
        aimLine.setEndX(endX);
        aimLine.setEndY(endY);
    }

    private void updateCannonColor() {
        BubbleColor nextColor = cannon.previewColor();
        cannonBase.setFill(nextColor.toFxColor());
    }

    private void rebuildAnchoredNodes() {
        bubbleLayer.getChildren().clear();
        anchoredNodes.clear();

        List<Circle> visuals = new ArrayList<>();
        for (Bubble bubble : grid.anchoredBubbles()) {
            Circle circle = bubble.asCircle();
            anchoredNodes.put(bubble, circle);
            visuals.add(circle);
        }
        bubbleLayer.getChildren().addAll(visuals);

        if (projectileNode != null) {
            bubbleLayer.getChildren().add(projectileNode);
        }
    }

    private void removeAnchoredNodes(Set<Bubble> toRemove) {
        List<FadeTransition> fades = new ArrayList<>();
        for (Bubble bubble : toRemove) {
            Circle circle = anchoredNodes.remove(bubble);
            if (circle == null) {
                continue;
            }
            FadeTransition fade = new FadeTransition(Duration.millis(180), circle);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setOnFinished(event -> bubbleLayer.getChildren().remove(circle));
            fades.add(fade);
        }
        fades.forEach(FadeTransition::play);
    }

    private void checkGameOver() {
        if (gameOver) {
            return;
        }
        if (grid.hasReachedBottom(BOTTOM_BOUNDARY)) {
            gameOver = true;
            infoLabel.setText("Game over! Press Restart or Space to try again.");
        }
    }

    /**
     * Defines collision outcomes for the moving projectile.
     */
    private sealed interface Collision permits Collision.Wall, Collision.Grid {
        record Wall(double clampedX, double reflectedVx) implements Collision {
        }

        record Grid(GridPosition anchorHint) implements Collision {
        }
    }
}