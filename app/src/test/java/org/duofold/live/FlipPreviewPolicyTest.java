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

 @Test public void captureRotationStaysNativeWhileShaderUsesHingeAxis() {
  for(int rotation=0;rotation<4;rotation++) {
   assertEquals(rotation,FlipPreviewPolicy.captureRotation(rotation));
   assertEquals((rotation+1)%4,FlipPreviewPolicy.shaderRotation(rotation));
  }
 }

 @Test public void tallInnerFlipUsesOneContinuousPanelNotHalfFoldCanvas() {
  assertTrue(FlipPreviewPolicy.singlePanelFallback(true, 360, 720));
  assertTrue(FlipPreviewPolicy.singlePanelFallback(true, 1080, 2640));
  assertFalse(FlipPreviewPolicy.singlePanelFallback(false, 360, 720));
  assertFalse(FlipPreviewPolicy.singlePanelFallback(true, 720, 360));
  assertFalse(FlipPreviewPolicy.singlePanelFallback(true, 0, 720));
 }

 @Test public void singlePanelFallbackUsesDestinationGeometryNotSourceBitmap() {
  // A cover screenshot can be landscape, while the destination is the tall inner panel.
  assertTrue(FlipPreviewPolicy.singlePanelFallback(true, 1080, 2640));
  assertFalse(FlipPreviewPolicy.singlePanelFallback(true, 748, 720));
  assertFalse(FlipPreviewPolicy.singlePanelFallback(false, 1080, 2640));
 }
 @Test public void liveShaderUsesTopDownPreviewOrientation() {
  assertEquals(3,FlipPreviewPolicy.liveShaderRotation(0));
  assertEquals(0,FlipPreviewPolicy.liveShaderRotation(1));
  assertEquals(1,FlipPreviewPolicy.liveShaderRotation(2));
  assertEquals(2,FlipPreviewPolicy.liveShaderRotation(3));
 }

 @Test public void coverTransitionAlwaysUsesCameraUpOrientation() {
  for(int rotation=0;rotation<4;rotation++) {
   assertEquals(3,FlipPreviewPolicy.coverShaderRotation(rotation));
  }
 }
 @Test(expected=IllegalArgumentException.class) public void invalidRotationRejected(){FlipPreviewPolicy.shaderRotation(-1);}
}
