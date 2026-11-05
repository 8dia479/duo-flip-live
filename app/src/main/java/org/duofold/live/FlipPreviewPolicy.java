package org.duofold.live;

/** Isolated preview only: no display power, wallpaper, capture, or handoff control. */
public final class FlipPreviewPolicy {
 private FlipPreviewPolicy() {}
 public static boolean validAngle(float angle) {
  return Float.isFinite(angle) && angle >= 0f && angle <= 180f;
 }
 /** Map an assumed natural horizontal hinge into the existing book-hinge shader coordinates.
  * This is a preview convention, not a claim about a device's physical hinge direction. */
 public static int shaderRotation(int displayRotation) {
  if (displayRotation < 0 || displayRotation > 3) throw new IllegalArgumentException("Invalid rotation");
  return (displayRotation + 1) % 4;
 }
 /** Capture metadata remains in native display coordinates; only the shader uses the rotated hinge axis. */
 public static int captureRotation(int displayRotation) {
  if (displayRotation < 0 || displayRotation > 3) throw new IllegalArgumentException("Invalid rotation");
  return displayRotation;
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
