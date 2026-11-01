package org.duofold.live

import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/** Mirrors the app's animation switch; wallpaper and animation preferences stay intact. */
class DuoTileService:TileService(),SharedPreferences.OnSharedPreferenceChangeListener {
 private val prefs by lazy{getSharedPreferences("standalone",0)}
 override fun onStartListening(){super.onStartListening();prefs.registerOnSharedPreferenceChangeListener(this);refresh()}
 override fun onStopListening(){prefs.unregisterOnSharedPreferenceChangeListener(this);super.onStopListening()}
 override fun onDestroy(){prefs.unregisterOnSharedPreferenceChangeListener(this);super.onDestroy()}
 override fun onTileAdded(){super.onTileAdded();refresh()}
 override fun onSharedPreferenceChanged(shared:SharedPreferences,key:String?){if(key=="enabled")refresh()}
 private fun refresh(){
  val blocked=DeviceCompatibility.isFlip6(android.os.Build.MODEL)
  val enabled=prefs.getBoolean("enabled",false) && !blocked
  qsTile?.apply{label="Duo Flip";subtitle=if(blocked)"Not verified" else if(enabled)"On" else "Off";state=if(enabled)Tile.STATE_ACTIVE else Tile.STATE_INACTIVE;contentDescription="Duo Flip, ${if(blocked)"not verified" else if(enabled)"on" else "off"}";updateTile()}
 }
 override fun onClick(){super.onClick();if(isLocked)unlockAndRun{toggle()}else toggle()}
 private fun toggle(){
  val wasEnabled=prefs.getBoolean("enabled",false)
  if(!wasEnabled && DeviceCompatibility.isFlip6(android.os.Build.MODEL)){
   Toast.makeText(this,"Flip 6 system animation is not verified. Use the isolated preview.",Toast.LENGTH_LONG).show();return
  }
  if(!wasEnabled && (!RuntimeConsent.ready(this)||FirstRun.required(this)||!Settings.canDrawOverlays(this)||StandaloneService.instance==null)){
   val intent=Intent(this,FlipHomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
   startActivityAndCollapse(PendingIntent.getActivity(this,35,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
   Toast.makeText(this,"Complete Duo Fold Live setup to enable animation",Toast.LENGTH_LONG).show()
   return
  }
  try{
   prefs.edit().putBoolean("enabled",!wasEnabled).apply()
   startForegroundService(Intent(this,FoldBackgroundService::class.java))
   StandaloneService.instance?.restart()
  }catch(error:Exception){
   prefs.edit().putBoolean("enabled",wasEnabled).apply()
   RecoveryLog.add("Quick Settings toggle failed: ${error.javaClass.simpleName}")
   Toast.makeText(this,"Could not toggle animation. Open Duo Fold Live to try again.",Toast.LENGTH_LONG).show()
  }
  refresh()
 }
}
