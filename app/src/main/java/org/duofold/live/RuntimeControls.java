package org.duofold.live;
import android.content.*;
import org.duofold.live.wallpaperlayer.WallpaperRestore;
/** User intent is authoritative across process death, updates and boot. */
public final class RuntimeControls {
 private RuntimeControls(){}
 public static boolean wanted(Context c){
  android.content.SharedPreferences p=c.getSharedPreferences("standalone",0);
  boolean flip6=DeviceCompatibility.isFlip6(android.os.Build.MODEL);
  return RuntimeConsent.ready(c)&&(flip6?p.getBoolean("enabled",false):(p.getBoolean("enabled",false)||WallpaperRestore.enabled(c)||FoldAwakeDefault.enabled(c)));
 }
 public static void stopAll(Context c){
  c.getSharedPreferences("standalone",0).edit().putBoolean("enabled",false).putBoolean("custom_wallpaper_enabled",false).commit();
  if(!DeviceCompatibility.isFlip6(android.os.Build.MODEL)){
   FoldAwakeDefault.setEnabled(c,false);
   try{WallpaperRestore.setEnabled(c,false);}catch(java.io.IOException e){RecoveryLog.add("Wallpaper stop: "+e.getMessage());}
   WallpaperRestore.reconnect();
  }
  LiveAngles.stopCurrent();
  StandaloneService s=StandaloneService.Companion.getInstance();if(s!=null)s.restart();
  c.stopService(new Intent(c,FoldBackgroundService.class));
 }
}
