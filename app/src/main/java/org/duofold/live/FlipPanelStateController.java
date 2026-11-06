package org.duofold.live;

import android.os.Handler;
import android.os.HandlerThread;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Requests Samsung's concurrent-inner posture while the Flip is moving.
 * Runs as the Shizuku shell user service, so Android's cmd device_state interface
 * is available without blocking the sensor log reader.
 */
public final class FlipPanelStateController {
    private static final Pattern STATE = Pattern.compile("identifier=(\\d+),\\s*name='([^']+)'");
    private static final Pattern ACTIVE_OVERRIDE = Pattern.compile("(?im)^\\s*Override state:\\s*(\\d+)");
    private static final long COMMAND_TIMEOUT_MS = 1_200L;

    private final Object lock = new Object();
    private HandlerThread thread;
    private Handler handler;
    private boolean initialized;
    private boolean externalOverride;
    private boolean ownsOverride;
    private int dualStateId = -1;
    private int closedStateId = -1;
    private int desiredMode = FlipPanelTransitionPolicy.AUTO;
    private int appliedMode = FlipPanelTransitionPolicy.AUTO;
    private volatile String status = "Flip dual-panel controller starting";

    private final Runnable applyLatest = this::applyDesiredMode;

    public void start() {
        synchronized (lock) {
            if (handler == null) {
                thread = new HandlerThread("duo-flip-panel-state");
                thread.start();
                handler = new Handler(thread.getLooper());
            }
            if (!initialized) handler.post(this::initialize);
        }
    }

    /** Called for each accepted HAL angle sample. Work is coalesced off the sensor thread. */
    public void update(float angle) {
        synchronized (lock) {
            if (handler == null) return;
            int next = FlipPanelTransitionPolicy.next(desiredMode, angle);
            if (next == desiredMode) return;
            desiredMode = next;
            handler.removeCallbacks(applyLatest);
            handler.post(applyLatest);
        }
    }

    public String status() { return status; }

    private void initialize() {
        CommandResult states = run("cmd", "device_state", "print-states");
        if (!states.ok) {
            status = "Flip panel state unavailable: " + states.summary();
            return;
        }

        dualStateId = findState(states.output, "CONCURRENT_INNER_DEFAULT", true);
        closedStateId = findState(states.output, "CLOSED", false);
        if (closedStateId < 0) closedStateId = findState(states.output, "FOLDED", false);
        if (dualStateId < 0) {
            status = "Flip dual-panel state missing; One UI default handoff remains active";
            initialized = true;
            return;
        }

        CommandResult policy = run("cmd", "device_state", "state");
        if (!policy.ok) {
            status = "Flip panel state ownership unavailable: " + policy.summary();
            return;
        }
        Matcher override = ACTIVE_OVERRIDE.matcher(policy.output);
        externalOverride = override.find();
        initialized = true;
        if (externalOverride) {
            status = "Another device-state override is active; Duo left it untouched";
            return;
        }

        status = "Flip panel controller ready: concurrent-inner=" + dualStateId
                + "; closed=" + (closedStateId < 0 ? "automatic" : closedStateId);
        applyDesiredMode();
    }

    private void applyDesiredMode() {
        final int target;
        synchronized (lock) { target = desiredMode; }
        if (!initialized) return;
        if (externalOverride && !ownsOverride) return;
        if (target == appliedMode) return;

        if (target == FlipPanelTransitionPolicy.AUTO) {
            if (!ownsOverride) {
                appliedMode = target;
                status = "Flip panel state follows Samsung's automatic posture";
                return;
            }
            CommandResult reset = run("cmd", "device_state", "state", "reset");
            if (reset.ok) {
                ownsOverride = false;
                appliedMode = FlipPanelTransitionPolicy.AUTO;
                status = "Flip panel override released at full-open posture";
            } else {
                status = "Flip panel reset failed: " + reset.summary();
                retryLatest();
            }
            return;
        }

        int stateId = target == FlipPanelTransitionPolicy.DUAL ? dualStateId : closedStateId;
        if (target == FlipPanelTransitionPolicy.CLOSED && stateId < 0) {
            // Some firmwares expose no explicit CLOSED state. Returning to the base
            // posture lets Samsung's own hinge policy select the cover panel at 0°.
            if (ownsOverride) {
                CommandResult reset = run("cmd", "device_state", "state", "reset");
                if (reset.ok) {
                    ownsOverride = false;
                    appliedMode = FlipPanelTransitionPolicy.AUTO;
                    status = "No CLOSED state; returned to Samsung automatic posture";
                } else {
                    status = "Automatic cover fallback failed: " + reset.summary();
                    retryLatest();
                }
            } else {
                appliedMode = target;
                status = "No explicit CLOSED state; Samsung automatic posture retained";
            }
            return;
        }

        CommandResult request = run("cmd", "device_state", "state", Integer.toString(stateId));
        if (request.ok) {
            ownsOverride = true;
            appliedMode = target;
            status = target == FlipPanelTransitionPolicy.DUAL
                    ? "Both Flip panels requested ON (state " + stateId + ")"
                    : "Flip cover-only posture requested (state " + stateId + ")";
            synchronized (lock) {
                if (desiredMode != appliedMode) {
                    handler.removeCallbacks(applyLatest);
                    handler.post(applyLatest);
                }
            }
        } else {
            status = "Flip panel state request failed: " + request.summary();
            retryLatest();
        }
    }

    private void retryLatest() {
        synchronized (lock) {
            if (handler != null) {
                handler.removeCallbacks(applyLatest);
                handler.postDelayed(applyLatest, 350L);
            }
        }
    }

    /** Restore automatic posture before the Shizuku service is unbound or destroyed. */
    public void resetAndWait() {
        final Handler current;
        synchronized (lock) {
            current = handler;
            desiredMode = FlipPanelTransitionPolicy.AUTO;
        }
        if (current == null) return;
        CountDownLatch done = new CountDownLatch(1);
        current.post(() -> {
            try {
                if (ownsOverride) {
                    CommandResult reset = run("cmd", "device_state", "state", "reset");
                    if (reset.ok) {
                        ownsOverride = false;
                        appliedMode = FlipPanelTransitionPolicy.AUTO;
                        status = "Flip panel override restored to automatic posture";
                    } else {
                        status = "Flip panel restore pending: " + reset.summary();
                    }
                } else if (!externalOverride) {
                    status = "No Duo panel override to restore";
                }
            } finally {
                done.countDown();
            }
        });
        try {
            if (!done.await(1_800L, TimeUnit.MILLISECONDS)) {
                status = "Flip panel restore timed out; system posture may still be overridden";
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            status = "Flip panel restore interrupted";
        }
    }

    private static int findState(String text, String exactName, boolean allowInnerVariant) {
        Matcher matcher = STATE.matcher(text);
        while (matcher.find()) {
            String name = matcher.group(2);
            if (exactName.equals(name)
                    || (allowInnerVariant && name.startsWith("CONCURRENT") && name.contains("INNER"))) {
                try { return Integer.parseInt(matcher.group(1)); }
                catch (NumberFormatException ignored) { return -1; }
            }
        }
        return -1;
    }

    private static CommandResult run(String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return new CommandResult(-1, "timeout");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return new CommandResult(process.exitValue(), output);
        } catch (Exception error) {
            if (process != null) process.destroyForcibly();
            return new CommandResult(-1, error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private static final class CommandResult {
        final int exitCode;
        final String output;
        CommandResult(int exitCode, String output) { this.exitCode = exitCode; this.output = output; }
        boolean ok() { return exitCode == 0; }
        String summary() { return output.isEmpty() ? "exit " + exitCode : output; }
    }
}