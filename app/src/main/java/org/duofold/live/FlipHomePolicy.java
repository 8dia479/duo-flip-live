package org.duofold.live;

/** UI status only: never grants access to privileged animation or changes the device. */
public final class FlipHomePolicy {
 private FlipHomePolicy() {}
 public enum SensorStatus { UNAVAILABLE, WAITING, LIMITED, VARIED_UNVERIFIED, ERROR }
 public static SensorStatus sensor(boolean available, boolean registered, boolean error, FlipDiagnostics.Observation observed) {
  if (!available) return SensorStatus.UNAVAILABLE;
  if (error || !registered) return SensorStatus.ERROR;
  if (observed == FlipDiagnostics.Observation.ONLY_STANDARD_STEPS) return SensorStatus.LIMITED;
  if (observed == FlipDiagnostics.Observation.VARIED_VALUES_OBSERVED) return SensorStatus.VARIED_UNVERIFIED;
  return SensorStatus.WAITING;
 }
}
