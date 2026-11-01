package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class FlipHomePolicyTest {
 @Test public void observationsAreNotMisrepresentedAsContinuous() {
  assertEquals(FlipHomePolicy.SensorStatus.UNAVAILABLE,FlipHomePolicy.sensor(false,false,false,FlipDiagnostics.Observation.NO_SAMPLES));
  assertEquals(FlipHomePolicy.SensorStatus.ERROR,FlipHomePolicy.sensor(true,false,false,FlipDiagnostics.Observation.NO_SAMPLES));
  assertEquals(FlipHomePolicy.SensorStatus.WAITING,FlipHomePolicy.sensor(true,true,false,FlipDiagnostics.Observation.NO_SAMPLES));
  assertEquals(FlipHomePolicy.SensorStatus.LIMITED,FlipHomePolicy.sensor(true,true,false,FlipDiagnostics.Observation.ONLY_STANDARD_STEPS));
  assertEquals(FlipHomePolicy.SensorStatus.VARIED_UNVERIFIED,FlipHomePolicy.sensor(true,true,false,FlipDiagnostics.Observation.VARIED_VALUES_OBSERVED));
  assertEquals(FlipHomePolicy.SensorStatus.ERROR,FlipHomePolicy.sensor(true,true,true,FlipDiagnostics.Observation.VARIED_VALUES_OBSERVED));
 }
}
