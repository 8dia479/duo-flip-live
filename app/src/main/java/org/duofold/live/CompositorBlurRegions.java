package org.duofold.live;

import java.util.ArrayList;

/** Physical-panel mask, independent of texture stretch and color. Rotation follows
 * the existing renderer's canonical hinge axis. Regions never cross the inner seam. */
final class CompositorBlurRegions {
 static float radius(float value){return Float.isFinite(value)?Math.max(0,Math.min(500,value)):250;}
 static float[][] build(int width,int height,boolean inner,int rotation,float progress,float requested,float strength){
  return build(width,height,inner,rotation,progress,requested,strength,false);
 }
 static float[][] build(int width,int height,boolean inner,int rotation,float progress,float requested,float strength,boolean upperHalfOnly){
  if(width<=0 || height<=0 || !Float.isFinite(progress) || !Float.isFinite(strength))return new float[0][];
  float p=Math.max(0,Math.min(1,progress));
  if(p<=.003f || strength<=0)return new float[0][];
  float motion=p*p*(3-2*p);
  // Reach leads strength: spread farther at the same hinge angle, without crossing the seam.
  float sweep=Math.min(1,p*1.5f);
  float reach=(inner?.5f:1f)*sweep*sweep*(3-2*sweep);
  float peak=Math.min(500,radius(requested)*Math.min(3,strength/.4f))*motion;
  boolean horizontal=rotation==1 || rotation==3,reverse=rotation==1 || rotation==2;
  ArrayList<float[]> regions=new ArrayList<>();
  int blur=Math.round(peak);
  if(blur<1)return new float[0][];
  // Spread across the entire reach without a rectangular constant-opacity core.
  // Quintic falloff of squared distance holds visible blur farther out, then
  // eases smoothly to zero. One radius and 65 disjoint strips retain bounded cost.
  final int strips=65;
  for(int i=0;i<strips;i++){
   float low=reach*i/strips;
   float high=reach*(i+1)/strips;
   double t=i==0?0:(i+.5)/strips;
   double x=t*t;
   float alpha=(float)(1-x*x*x*(x*(x*6-15)+10));
   float from=inner?low:1-high,to=inner?high:1-low;
   if(reverse){float old=from;from=1-to;to=1-old;}
   // Radius, alpha, left, top, right, bottom, four corner radii (AOSP JNI layout).
   float left=horizontal?0:from*width;
   float top=horizontal?from*height:0;
   float right=horizontal?width:to*width;
   float bottom=horizontal?to*height:height;
   // The Flip closing sweep is clipped to the upper half in screen coordinates,
   // regardless of which axis the hinge sweep uses for the current rotation.
   if(upperHalfOnly)bottom=Math.min(bottom,height*.5f);
   if(bottom<=top)continue;
   regions.add(new float[]{blur,alpha,left,top,right,bottom,0,0,0,0});
  }
  return regions.toArray(new float[0][]);
 }
}
