package org.duofold.live;

/** Eligibility is not proof of a firmware's Samsung wallpaper/API compatibility. */
public final class DeviceCompatibility {
 private DeviceCompatibility() {}
 public static boolean isFold8(String model) {
  return model != null && model.matches("SM-F971[A-Z0-9]+(?:/DS)?");
 }
 public static boolean isFlip6(String model) {
  return model != null && model.matches("SM-F741[A-Z0-9]+(?:/DS)?");
 }
 public static boolean isRecognized(String model) {
  return model != null && model.matches("SM-F(?:971|976|966)[A-Z0-9]+(?:/DS)?");
 }
 public static String modelWarning(String model) {
  return isRecognized(model) ? "" : "Unrecognized device: " + String.valueOf(model)
   + ". You can continue, but compatibility is unverified and features may not work. Samsung wallpaper and API checks still apply.";
 }
 public static boolean isEligible(String model, int sdk) {
  return sdk == 37 && !isFlip6(model);
 }
 public static void requireEligible(String model, int sdk) {
  if (isFlip6(model)) throw new IllegalStateException("Flip 6 system handoff and Fold wallpaper profiles are not verified; use preview only");
  if (!isEligible(model, sdk)) throw new IllegalStateException(
   "This Samsung profile requires Android 17; received " + model + " / SDK " + sdk);
 }
}
