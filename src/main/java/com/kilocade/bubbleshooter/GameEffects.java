package com.kilocade.bubbleshooter;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/** Visual-only particles, advanced by the same clock as the game so pause freezes them. */
final class GameEffects {
    private static final int MAX_PARTICLES = 600;
    private final Group layer = new Group();
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();

    GameEffects() { layer.setMouseTransparent(true); layer.setManaged(false); }
    Group layer() { return layer; }
    int size() { return particles.size(); }

    void clear() { particles.clear(); layer.getChildren().clear(); }

    void shift(double dx) {
        for (Particle p : particles) p.node.setTranslateX(p.node.getTranslateX() + dx);
    }

    void advance(double seconds) {
        for (var iterator = particles.iterator(); iterator.hasNext();) {
            Particle p = iterator.next();
            p.age += seconds;
            if (p.age >= p.life) {
                layer.getChildren().remove(p.node);
                iterator.remove();
                continue;
            }
            p.node.setTranslateX(p.node.getTranslateX() + p.vx * seconds);
            p.node.setTranslateY(p.node.getTranslateY() + p.vy * seconds + .5 * p.gravity * seconds * seconds);
            p.vy += p.gravity * seconds;
            double t = p.age / p.life;
            p.node.setOpacity(Math.min(1, (1 - t) * 2));
            double scale = 1 + p.growth * t;
            p.node.setScaleX(scale);
            p.node.setScaleY(scale);
        }
    }

    void trail(Bubble bubble) {
        Circle dot = new Circle(bubble.x(), bubble.y(), bubble.radius() * .35,
                bubble.color().toFxColor().deriveColor(0, .7, 1, .45));
        add(dot, 0, 0, 0, .22, -.85);
    }

    void ring(double x, double y, Color color) {
        Circle ring = new Circle(x, y, 8, Color.TRANSPARENT);
        ring.setStroke(color);
        ring.setStrokeWidth(2);
        add(ring, 0, 0, 0, .35, 2.8);
    }

    void burst(Bubble bubble) {
        ring(bubble.x(), bubble.y(), bubble.color().toFxColor());
        for (int i = 0; i < 7; i++) {
            double angle = i * Math.PI * 2 / 7 + random.nextDouble() * .3;
            double speed = 55 + random.nextDouble() * 105;
            Circle spark = new Circle(bubble.x(), bubble.y(), 2 + random.nextDouble() * 2,
                    bubble.color().toFxColor().interpolate(Color.WHITE, .3));
            add(spark, Math.cos(angle) * speed, Math.sin(angle) * speed, 90, .45 + random.nextDouble() * .2, -.8);
        }
    }

    void drop(Bubble bubble) {
        add(bubble.asCircle(), random.nextDouble() * 90 - 45, 40, 1400, 1.0, -.25);
    }

    void caption(String message, double x, double y, Color color) {
        Text text = new Text(message);
        text.setFont(Font.font("Verdana", FontWeight.BOLD, 18));
        text.setFill(color);
        text.setX(x - text.getLayoutBounds().getWidth() / 2);
        text.setY(y);
        add(text, 0, -42, 0, 1.05, .08);
    }

    private void add(Node node, double vx, double vy, double gravity, double life, double growth) {
        if (particles.size() >= MAX_PARTICLES) {
            layer.getChildren().remove(particles.remove(0).node);
        }
        particles.add(new Particle(node, vx, vy, gravity, life, growth));
        layer.getChildren().add(node);
    }

    private static final class Particle {
        final Node node;
        final double vx, gravity, life, growth;
        double vy, age;
        Particle(Node node, double vx, double vy, double gravity, double life, double growth) {
            this.node = node; this.vx = vx; this.vy = vy; this.gravity = gravity; this.life = life; this.growth = growth;
        }
    }
}
