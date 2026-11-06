package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;

public class CompositorBlurRegionsTest {
 @Test public void innerNeverBlursCleanHalfInAnyRotation(){
  for(int rotation=0;rotation<4;rotation++)for(int step=1;step<=100;step++){
   float[][] regions=CompositorBlurRegions.build(1200,1800,true,rotation,step/100f,120,.4f);
   for(float[] r:regions){
    assertTrue(r[1]>0&&r[1]<=1);assertTrue(r[0]>=1&&r[0]<=500);
    if(rotation==0)assertTrue(r[4]<=600);
    if(rotation==1)assertTrue(r[3]>=900);
    if(rotation==2)assertTrue(r[2]>=600);
    if(rotation==3)assertTrue(r[5]<=900);
   }
  }
 }
 @Test public void coverBoundaryAdvancesFromRightAndReversesWithProgress(){
  float[][] early=CompositorBlurRegions.build(1000,1000,false,0,.2f,120,.4f);
  float[][] late=CompositorBlurRegions.build(1000,1000,false,0,.8f,120,.4f);
  assertTrue(leftmost(early)>leftmost(late));assertEquals(1000,early[0][4],0);
  assertEquals(leftmost(early),leftmost(CompositorBlurRegions.build(1000,1000,false,0,.2f,120,.4f)),0);
 }
 @Test public void foldingFlipCanLimitEffectToUpperHalfOnly(){
  float[][] regions=CompositorBlurRegions.build(1200,1800,false,0,.9f,120,.4f,true);
  assertEquals(65,regions.length);
  for(float[] r:regions){
   assertTrue(r[2]>=0&&r[3]>=0);
   assertTrue("upper-half effect must not cross the horizontal midpoint",r[5]<=900);
   assertTrue(r[4]<=1200);
  }
 }
 @Test public void upperHalfLimitDoesNotChangeNormalCoverRendering(){
  float[][] normal=CompositorBlurRegions.build(1200,1800,false,0,.9f,120,.4f);
  float[][] limited=CompositorBlurRegions.build(1200,1800,false,0,.9f,120,.4f,true);
  assertTrue(normal[0][5]>900);
  assertTrue(limited[0][5]<=900);
 }
 @Test public void strongerBlurChangesRadiusNotMaskOrAlpha(){
  float[][] weak=CompositorBlurRegions.build(1000,1000,true,0,1,100,.4f);
  float[][] strong=CompositorBlurRegions.build(1000,1000,true,0,1,200,.4f);
  assertTrue(strong[0][0]>weak[0][0]);
  for(int i=0;i<weak.length;i++)for(int field=1;field<10;field++)assertEquals(weak[i][field],strong[i][field],0);
 }
 @Test public void invalidInputsAndZeroStrengthCannotLeaveBlur(){
  assertEquals(0,CompositorBlurRegions.build(1000,1000,true,0,0,120,.4f).length);
  assertEquals(0,CompositorBlurRegions.build(1000,1000,false,0,Float.NaN,120,.4f).length);
  assertEquals(0,CompositorBlurRegions.build(1000,1000,false,0,1,120,0).length);
  assertEquals(0,CompositorBlurRegions.build(1000,1000,false,0,1,0,1).length);
  assertEquals(250,CompositorBlurRegions.radius(Float.NaN),0);
  assertEquals(500,CompositorBlurRegions.radius(10000),0);
 }
 @Test public void bandsHaveNoOverlapAndStayWithinSurface(){
  for(boolean inner:new boolean[]{false,true})for(int rot=0;rot<4;rot++){
   float[][] regions=CompositorBlurRegions.build(1200,1800,inner,rot,.7f,240,1);
   for(int i=0;i<regions.length;i++){
    float[] r=regions[i];assertTrue(r[2]>=0&&r[3]>=0&&r[4]<=1200&&r[5]<=1800);
    assertTrue(r[4]>r[2]&&r[5]>r[3]);
    for(int j=0;j<i;j++){float[] q=regions[j];assertTrue(Math.min(r[4],q[4])<=Math.max(r[2],q[2])+.001f || Math.min(r[5],q[5])<=Math.max(r[3],q[3])+.001f);}
   }
  }
 }
 @Test public void featherReusesOneRadiusWithSmallAlphaSteps(){
  float[][] regions=CompositorBlurRegions.build(1200,1800,false,0,.5f,120,.4f);
  assertEquals(65,regions.length);assertEquals(1,regions[0][1],0);
  for(int i=1;i<regions.length;i++){
   assertEquals(regions[0][0],regions[i][0],0);
   assertTrue(regions[i-1][1]>=regions[i][1]);
   assertTrue(regions[i-1][1]-regions[i][1]<.043f);
  }
  assertTrue(regions[regions.length-1][1]<.001f);
 }
 @Test public void widerSweepReachesFartherWithoutRaisingRadius(){
  // Half progress now covers ~84%, while retaining the same radius.
  float[][] regions=CompositorBlurRegions.build(1000,1000,false,0,.5f,120,.4f);
  assertTrue(leftmost(regions)<160);assertEquals(60,regions[0][0],0);
  assertEquals(0,leftmost(CompositorBlurRegions.build(1000,1000,false,0,.8f,120,.4f)),.001f);
 }
 @Test public void broadFalloffHasNoFlatCoreAndKeepsVisibleBlurPastMidpoint(){
  float[][] regions=CompositorBlurRegions.build(1200,1800,false,0,.5f,120,.4f);
  assertTrue(regions[20][1]<regions[10][1]);
  assertTrue(regions[32][1]>.85f);
  assertTrue(regions[48][1]>.35f);
 }
 private static float leftmost(float[][] regions){float min=1000;for(float[] r:regions)min=Math.min(min,r[2]);return min;}
}
