package org.duofold.live;
final class GlassFramePolicy {
 static boolean usable(long stamp,long now,int width,int height,int currentWidth,int currentHeight){
  long age=now-stamp;
  if(age<0 || age>350 || width<=0 || height<=0 || currentWidth<=0 || currentHeight<=0)return false;
  if(width==currentWidth && height==currentHeight)return true;
  // Samsung may report logical bounds and SurfaceView output at slightly different
  // scales after a physical-panel handoff. Keep the same panel aspect, not stale size.
  double source=width/(double)height,target=currentWidth/(double)currentHeight;
  return Math.abs(source-target)<=Math.max(source,target)*0.02;
 }
}
