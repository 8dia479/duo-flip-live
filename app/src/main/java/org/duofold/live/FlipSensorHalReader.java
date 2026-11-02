package org.duofold.live;

import android.os.Binder;
import android.os.Bundle;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Shizuku user-service: reads only the sensors-hal log tag and exports parsed
 * hinge samples. It does not change device state, displays, or wallpaper.
 */
public final class FlipSensorHalReader extends Binder {
    public static final String DESCRIPTOR = "org.duofold.live.flip.SensorHalReader";
    public static final int CMD_START = 1;
    public static final int CMD_READ = 2;
    public static final int CMD_STOP = 3;
    private static final int SHIZUKU_DESTROY = 16777115;

    private int ownerUid = -1;
    private int generation = 0;
    private java.lang.Process process;
    private volatile String state = "idle";
    private volatile float angle = Float.NaN;
    private volatile long timestampNanos = 0L;
    private volatile int source = -1;
    private volatile long sequence = 0L;
    private volatile long logLines = 0L;
    private volatile long parsedLines = 0L;
    private volatile long staleLines = 0L;
    private volatile String lastLine = "";

    public FlipSensorHalReader() {
        attachInterface(null, DESCRIPTOR);
    }

    @Override
    protected synchronized boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        if (code == INTERFACE_TRANSACTION) {
            reply.writeString(DESCRIPTOR);
            return true;
        }
        if (code == SHIZUKU_DESTROY) {
            stopReader();
            System.exit(0);
            return true;
        }
        data.enforceInterface(DESCRIPTOR);
        int caller = Binder.getCallingUid();
        if (ownerUid < 0) ownerUid = caller;
        if (caller != ownerUid) throw new SecurityException("Wrong caller UID");

        if (code == CMD_START) {
            startReader();
            reply.writeNoException();
            return true;
        }
        if (code == CMD_READ) {
            reply.writeNoException();
            reply.writeBundle(snapshot());
            return true;
        }
        if (code == CMD_STOP) {
            stopReader();
            reply.writeNoException();
            return true;
        }
        return super.onTransact(code, data, reply, flags);
    }

    private Bundle snapshot() {
        Bundle result = new Bundle();
        result.putString("state", state);
        result.putFloat("angle", angle);
        result.putLong("timestampNanos", timestampNanos);
        result.putInt("source", source);
        result.putLong("sequence", sequence);
        result.putLong("logLines", logLines);
        result.putLong("parsedLines", parsedLines);
        result.putLong("staleLines", staleLines);
        result.putLong("ageMs", timestampNanos > 0L
                ? Math.max(0L, (SystemClock.elapsedRealtimeNanos() - timestampNanos) / 1_000_000L)
                : -1L);
        result.putString("lastLine", lastLine);
        return result;
    }

    private synchronized void startReader() {
        if (process != null) return;
        final int currentGeneration = ++generation;
        angle = Float.NaN;
        timestampNanos = 0L;
        source = -1;
        sequence = logLines = parsedLines = staleLines = 0L;
        lastLine = "";
        state = "starting";
        Thread thread = new Thread(() -> {
            java.lang.Process child = null;
            try {
                child = new ProcessBuilder(
                        "logcat", "-v", "epoch", "-T", "1",
                        "-s", "sensors-hal:V", "*:S")
                        .redirectErrorStream(true)
                        .start();
                synchronized (this) {
                    if (currentGeneration != generation) {
                        child.destroy();
                        return;
                    }
                    process = child;
                    state = "listening";
                }

                try (BufferedReader input = new BufferedReader(
                        new InputStreamReader(child.getInputStream()))) {
                    String line;
                    while ((line = input.readLine()) != null) {
                        FlipSensorHalParser.Sample sample = FlipSensorHalParser.parse(line);
                        synchronized (this) {
                            if (currentGeneration != generation) return;
                            logLines++;
                            if (sample == null) continue;

                            long epochMs = FlipSensorHalParser.logcatEpochMillis(line);
                            long ageMs = epochMs < 0L ? 0L : System.currentTimeMillis() - epochMs;
                            if (ageMs < -100L || ageMs > 2000L) {
                                staleLines++;
                                continue;
                            }

                            long nowNanos = SystemClock.elapsedRealtimeNanos();
                            long sampleNanos = sample.timestampNanos;
                            // HAL timestamps should share Android's boot-time clock. Fall back to
                            // receive time if a firmware build uses a different clock domain.
                            if (sampleNanos <= 0L || sampleNanos > nowNanos + 100_000_000L
                                    || nowNanos - sampleNanos > 5_000_000_000L) {
                                sampleNanos = nowNanos;
                            }

                            angle = sample.angle;
                            timestampNanos = sampleNanos;
                            source = sample.source;
                            sequence++;
                            parsedLines++;
                            lastLine = line;
                            state = "receiving";
                        }
                    }
                }
                synchronized (this) {
                    if (currentGeneration == generation) state = "log reader ended";
                }
            } catch (Exception error) {
                synchronized (this) {
                    if (currentGeneration == generation) {
                        state = "reader error: " + error.getClass().getSimpleName() + ": " + error.getMessage();
                    }
                }
            } finally {
                if (child != null) child.destroy();
            }
        }, "duo-flip-sensors-hal");
        thread.setDaemon(true);
        thread.start();
    }

    private synchronized void stopReader() {
        generation++;
        java.lang.Process child = process;
        process = null;
        if (child != null) child.destroy();
        state = "stopped";
    }
}
