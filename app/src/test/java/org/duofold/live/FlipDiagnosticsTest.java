package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;

public class FlipDiagnosticsTest {
 @Test public void panelCandidatesAreOrientationIndependentAndUntrusted() {
  assertEquals(FlipDiagnostics.Panel.INNER_CANDIDATE, FlipDiagnostics.panelCandidate(1080,2640));
  assertEquals(FlipDiagnostics.Panel.INNER_CANDIDATE, FlipDiagnostics.panelCandidate(2640,1080));
  assertEquals(FlipDiagnostics.Panel.COVER_CANDIDATE, FlipDiagnostics.panelCandidate(720,748));
  assertEquals(FlipDiagnostics.Panel.UNKNOWN, FlipDiagnostics.panelCandidate(1080,2400));
  assertEquals(FlipDiagnostics.Panel.INVALID, FlipDiagnostics.panelCandidate(0,748));
  assertEquals(FlipDiagnostics.Panel.INVALID, FlipDiagnostics.panelCandidate(-1,748));
 }
 @Test public void reportsObservedSamplesWithoutInventingAngles() {
  FlipDiagnostics.Hinge h = new FlipDiagnostics.Hinge();
  assertEquals(FlipDiagnostics.Observation.NO_SAMPLES,h.observation());
  assertTrue(h.add(0,1,1,0)); assertTrue(h.add(90,2,2,0)); assertTrue(h.add(180,3,3,0));
  assertEquals(FlipDiagnostics.Observation.ONLY_STANDARD_STEPS,h.observation());
  assertEquals(180f,h.displayed(),0f);
  assertTrue(h.add(20,4,4,0)); assertTrue(h.add(21,5,5,0)); assertTrue(h.add(22,6,6,0));
  assertEquals(FlipDiagnostics.Observation.VARIED_VALUES_OBSERVED,h.observation());
  assertFalse(h.add(Float.NaN,7,7,0)); assertFalse(h.add(30,5,8,0));
  assertEquals(22f,h.raw(),0f); assertEquals(2,h.rejected());
  h.reset(); assertTrue(Float.isNaN(h.raw())); assertEquals(0,h.accepted());
 }
 @Test public void deadbandDoesNotInventIntermediateValuesAndReversalWorks() {
  FlipDiagnostics.Hinge h = new FlipDiagnostics.Hinge();
  h.add(90,1,1,1); h.add(90.4f,2,2,1); assertEquals(90f,h.displayed(),0f);
  h.add(110,3,3,1); assertEquals(110f,h.displayed(),0f);
  h.add(88,4,4,1); assertEquals(88f,h.displayed(),0f);
  h.add(90,5,5,Float.NaN); assertEquals(90f,h.displayed(),0f);
 }
}
