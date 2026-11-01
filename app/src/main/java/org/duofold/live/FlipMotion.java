package org.duofold.live;

/** Direction tracking for the isolated Flip preview, not a system handoff signal. */
public final class FlipMotion {
 public enum Direction { UNKNOWN, FOLDING, UNFOLDING }
 private float anchor = Float.NaN;
 private Direction direction = Direction.UNKNOWN;
 /** Uses a fixed angular hysteresis; no clocks, prediction, or synthetic angles. */
 public Direction update(float angle, float sensitivityDegrees) {
  if (!FlipPreviewPolicy.validAngle(angle)) return direction;
  if (!Float.isFinite(anchor)) { anchor = angle; return direction; }
  float threshold = Float.isFinite(sensitivityDegrees) ? Math.max(.1f, Math.min(5f, sensitivityDegrees)) : 1f;
  float delta = angle - anchor;
  if (Math.abs(delta) >= threshold) {
   direction = delta < 0 ? Direction.FOLDING : Direction.UNFOLDING;
   anchor = angle;
  }
  return direction;
 }
 public void reset() { anchor = Float.NaN; direction = Direction.UNKNOWN; }
 public static boolean effectEnabled(Direction direction, boolean fold, boolean unfold) {
  if (direction == Direction.FOLDING) return fold;
  if (direction == Direction.UNFOLDING) return unfold;
  return fold && unfold;
 }
}
