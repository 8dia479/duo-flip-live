package org.duofold.live;

/** Isolated preview only: no display power, wallpaper, capture, or handoff control. */
public final class FlipPreviewPolicy {
 private FlipPreviewPolicy() {}
 public static boolean validAngle(float angle) {
  return Float.isFinite(angle) && angle >= 0f && angle <= 180f;
 }
 public static final int COVER_PANEL = 0;
 public static final int MAIN_PANEL = 1;
 /** Snap at the project's existing opening handoff angle; retain a 4° return gap. */
 public static int nextPanel(int currentPanel, float angle) {
  int current = currentPanel == MAIN_PANEL ? MAIN_PANEL : COVER_PANEL;
  if (!validAngle(angle)) return current;
  float opening = HandoffSettings.angle(HandoffSettings.DEFAULT);
  float closing = HandoffSettings.closing(opening);
  if (current == COVER_PANEL && angle >= opening) return MAIN_PANEL;
  if (current == MAIN_PANEL && angle <= closing) return COVER_PANEL;
  return current;
 }
 /** Map an assumed natural horizontal hinge into the existing book-hinge shader coordinates.
  * This is a preview convention, not a claim about a device's physical hinge direction. */
 public static int shaderRotation(int displayRotation) {
  if (displayRotation < 0 || displayRotation > 3) throw new IllegalArgumentException("Invalid rotation");
  return (displayRotation + 1) % 4;
 }
 /** Sensor events use elapsed-realtime nanoseconds. Never accept out-of-order samples. */
 public static final class Samples {
  private long timestamp;
  private float angle = Float.NaN;
  public boolean accept(float value, long whenNanos, long nowNanos) {
   if (!validAngle(value) || whenNanos <= 0 || whenNanos <= timestamp || whenNanos > nowNanos) return false;
   angle = value;
   timestamp = whenNanos;
   return true;
  }
  public float value() { return angle; }
  public long timestamp() { return timestamp; }
  public void reset() { timestamp = 0; angle = Float.NaN; }
 }
}
