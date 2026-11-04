package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;

public class FlipPreviewPolicyTest {
 @Test public void acceptsOnlyFinitePhysicalAnglesAndMonotonicEvents() {
  FlipPreviewPolicy.Samples s = new FlipPreviewPolicy.Samples();
  assertFalse(s.accept(Float.NaN,1,1));
  assertFalse(s.accept(-1,1,1));
  assertFalse(s.accept(181,1,1));
  assertFalse(s.accept(90,2,1));
  assertTrue(s.accept(45,2,2));
  assertFalse(s.accept(60,2,3));
  assertFalse(s.accept(60,1,3));
  assertEquals(45f,s.value(),0f);
  assertTrue(s.accept(90,3,3));
  s.reset();
  assertTrue(Float.isNaN(s.value()));
  assertTrue(s.accept(180,1,1));
 }
 @Test public void flipAxisRotatesInAllOrientations() {
  assertEquals(1,FlipPreviewPolicy.shaderRotation(0));
  assertEquals(2,FlipPreviewPolicy.shaderRotation(1));
  assertEquals(3,FlipPreviewPolicy.shaderRotation(2));
  assertEquals(0,FlipPreviewPolicy.shaderRotation(3));
 }
 @Test(expected=IllegalArgumentException.class) public void invalidRotationRejected(){FlipPreviewPolicy.shaderRotation(-1);}
 @Test public void panelHandoffSnapsAtThresholdAndUsesReturnGap() {
  int panel = FlipPreviewPolicy.COVER_PANEL;
  assertEquals(FlipPreviewPolicy.COVER_PANEL, FlipPreviewPolicy.nextPanel(panel, 84.9f));
  panel = FlipPreviewPolicy.nextPanel(panel, 85f);
  assertEquals(FlipPreviewPolicy.MAIN_PANEL, panel);
  panel = FlipPreviewPolicy.nextPanel(panel, 83f);
  assertEquals(FlipPreviewPolicy.MAIN_PANEL, panel);
  panel = FlipPreviewPolicy.nextPanel(panel, 81f);
  assertEquals(FlipPreviewPolicy.COVER_PANEL, panel);
  panel = FlipPreviewPolicy.nextPanel(panel, 85f);
  assertEquals(FlipPreviewPolicy.MAIN_PANEL, panel);
 }
 @Test public void invalidAngleKeepsLastPanel() {
  assertEquals(FlipPreviewPolicy.MAIN_PANEL, FlipPreviewPolicy.nextPanel(FlipPreviewPolicy.MAIN_PANEL, Float.NaN));
  assertEquals(FlipPreviewPolicy.COVER_PANEL, FlipPreviewPolicy.nextPanel(FlipPreviewPolicy.COVER_PANEL, 181f));
 }
}
