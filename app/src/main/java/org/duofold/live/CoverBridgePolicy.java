package org.duofold.live;
final class CoverBridgePolicy {
 static boolean freshSource(long now,long stamp){return stamp>0 && now>=stamp && now-stamp<=300;}
 static boolean active(long now,long start){return start>0 && now>=start && LiveEffectFadePolicy.active(now-start);}
}
