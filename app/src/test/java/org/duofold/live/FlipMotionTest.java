package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;

public class FlipMotionTest {
 @Test public void pauseNoiseAndReverseDoNotDependOnTime() {
  FlipMotion motion = new FlipMotion();
  assertEquals(FlipMotion.Direction.UNKNOWN, motion.update(160,1));
  assertEquals(FlipMotion.Direction.UNKNOWN, motion.update(159.5f,1));
  assertEquals(FlipMotion.Direction.FOLDING, motion.update(150,1));
  for (int i=0;i<100;i++) assertEquals(FlipMotion.Direction.FOLDING,motion.update(150,1));
  assertEquals(FlipMotion.Direction.FOLDING,motion.update(150.5f,1));
  assertEquals(FlipMotion.Direction.UNFOLDING,motion.update(151,1));
  assertEquals(FlipMotion.Direction.UNFOLDING,motion.update(Float.NaN,1));
  motion.reset(); assertEquals(FlipMotion.Direction.UNKNOWN,motion.update(10,1));
 }
 @Test public void behaviorRequiresKnownDirectionForSingleDirection() {
  assertFalse(FlipMotion.effectEnabled(FlipMotion.Direction.UNKNOWN,true,false));
  assertTrue(FlipMotion.effectEnabled(FlipMotion.Direction.UNKNOWN,true,true));
  assertTrue(FlipMotion.effectEnabled(FlipMotion.Direction.FOLDING,true,false));
  assertFalse(FlipMotion.effectEnabled(FlipMotion.Direction.UNFOLDING,true,false));
 }
}
