package com.kilocade.bubbleshooter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * Tiny synth so the game has sound without shipping assets.
 */
public final class SoundEngine implements AutoCloseable {

    private static final AudioFormat FORMAT = new AudioFormat(44_100, 16, 1, true, false);

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "bubble-shooter-audio");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean enabled = true;

    public void playShoot() {
        play(new double[]{430, 620}, new int[]{45, 70}, 0.18, Wave.SINE);
    }

    public void playBounce() {
        play(new double[]{520, 410}, new int[]{30, 40}, 0.12, Wave.SQUARE);
    }

    public void playSwap() {
        play(new double[]{550, 700}, new int[]{55, 45}, 0.13, Wave.SINE);
    }

    public void playPop(int burstSize) {
        double base = 360 + Math.min(8, burstSize) * 45;
        play(new double[]{base, base * 1.35}, new int[]{55, 65}, 0.18, Wave.SINE);
    }

    public void playPulse() {
        play(new double[]{230, 280, 180}, new int[]{70, 60, 120}, 0.22, Wave.SAW);
    }

    public void playPrism() {
        play(new double[]{440, 554, 659}, new int[]{50, 50, 100}, 0.16, Wave.SINE);
    }

    public void playStageClear() {
        play(new double[]{392, 494, 659, 784}, new int[]{90, 90, 90, 180}, 0.16, Wave.SINE);
    }

    public void playGameOver() {
        play(new double[]{420, 310, 220}, new int[]{120, 160, 250}, 0.18, Wave.SAW);
    }

    private void play(double[] notes, int[] durationsMs, double volume, Wave wave) {
        if (!enabled) {
            return;
        }
        executor.execute(() -> synthesize(notes, durationsMs, volume, wave));
    }

    private void synthesize(double[] notes, int[] durationsMs, double volume, Wave wave) {
        try (SourceDataLine line = AudioSystem.getSourceDataLine(FORMAT)) {
            line.open(FORMAT, 4096);
            line.start();
            for (int i = 0; i < notes.length; i++) {
                writeTone(line, notes[i], durationsMs[i], volume, wave);
            }
            line.drain();
        } catch (LineUnavailableException | IllegalArgumentException ex) {
            enabled = false;
        }
    }

    private void writeTone(SourceDataLine line, double frequency, int durationMs, double volume, Wave wave) {
        int sampleCount = (int) (FORMAT.getSampleRate() * durationMs / 1000.0);
        byte[] buffer = new byte[sampleCount * 2];
        for (int i = 0; i < sampleCount; i++) {
            double time = i / FORMAT.getSampleRate();
            double envelope = Math.min(1.0, i / (FORMAT.getSampleRate() * 0.01));
            envelope *= Math.max(0.0, 1.0 - i / (double) sampleCount);
            short sample = (short) (wave.sample(frequency, time) * envelope * volume * Short.MAX_VALUE);
            buffer[i * 2] = (byte) (sample & 0xff);
            buffer[i * 2 + 1] = (byte) ((sample >> 8) & 0xff);
        }
        line.write(buffer, 0, buffer.length);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private enum Wave {
        SINE {
            @Override
            double sample(double frequency, double time) {
                return Math.sin(2 * Math.PI * frequency * time);
            }
        },
        SQUARE {
            @Override
            double sample(double frequency, double time) {
                return Math.sin(2 * Math.PI * frequency * time) >= 0 ? 1.0 : -1.0;
            }
        },
        SAW {
            @Override
            double sample(double frequency, double time) {
                double period = 1.0 / frequency;
                double position = (time % period) / period;
                return (position * 2.0) - 1.0;
            }
        };

        abstract double sample(double frequency, double time);
    }
}
