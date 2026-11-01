package org.duofold.live;

import java.util.HashSet;
import java.util.Set;

/** Read-only diagnostics; candidates are never used for display control. */
public final class FlipDiagnostics {
 private FlipDiagnostics() {}
 public enum Observation { NO_SAMPLES, OBSERVING, ONLY_STANDARD_STEPS, VARIED_VALUES_OBSERVED }
 public enum Panel { INVALID, UNKNOWN, INNER_CANDIDATE, COVER_CANDIDATE }

 /** Physical dimensions are a conservative model-specific hint, not a display identity. */
 public static Panel panelCandidate(int width, int height) {
  if (width <= 0 || height <= 0) return Panel.INVALID;
  int shortSide = Math.min(width, height), longSide = Math.max(width, height);
  if (near(shortSide, 1080) && near(longSide, 2640)) return Panel.INNER_CANDIDATE;
  if (near(shortSide, 720) && near(longSide, 748)) return Panel.COVER_CANDIDATE;
  return Panel.UNKNOWN;
 }
 private static boolean near(int actual, int expected) {
  return Math.abs(actual - expected) <= expected * .02f;
 }

 /** This describes the samples observed, not a guarantee of sensor continuity or accuracy. */
 public static final class Hinge {
  private final FlipPreviewPolicy.Samples samples = new FlipPreviewPolicy.Samples();
  private final Set<Integer> varied = new HashSet<>();
  private int accepted, rejected;
  private float displayed = Float.NaN;
  public boolean add(float angle, long timestamp, long now, float deadband) {
   if (!samples.accept(angle, timestamp, now)) { rejected++; return false; }
   accepted++;
   if (Math.abs(angle) > 2 && Math.abs(angle - 90) > 2 && Math.abs(angle - 180) > 2)
    varied.add(Math.round(angle));
   float threshold = Float.isFinite(deadband) ? Math.max(0, Math.min(deadband, 3)) : 0;
   if (!Float.isFinite(displayed) || Math.abs(angle - displayed) >= threshold) displayed = angle;
   return true;
  }
  public float raw() { return samples.value(); }
  public float displayed() { return displayed; }
  public long timestamp() { return samples.timestamp(); }
  public int accepted() { return accepted; }
  public int rejected() { return rejected; }
  public Observation observation() {
   if (accepted == 0) return Observation.NO_SAMPLES;
   if (varied.size() >= 3) return Observation.VARIED_VALUES_OBSERVED;
   if (accepted >= 3 && varied.isEmpty()) return Observation.ONLY_STANDARD_STEPS;
   return Observation.OBSERVING;
  }
  public void reset() { samples.reset(); varied.clear(); accepted = rejected = 0; displayed = Float.NaN; }
 }
}
