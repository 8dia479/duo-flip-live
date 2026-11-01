package org.duofold.live

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** Explicit opt-in; keep a durable pre-change value and never overwrite a later external choice. */
internal object FoldAwakeDefault {
 private var pending=false
 private var nextAttempt=0L
 private var epoch=0L
 private val main=Handler(Looper.getMainLooper())
 @JvmField var status="Keep-awake off"
 private fun prefs(c:Context)=c.getSharedPreferences("standalone",0)
 @JvmStatic fun enabled(c:Context)=!DeviceCompatibility.isFlip6(android.os.Build.MODEL)&&prefs(c).getBoolean("keep_awake_opt_in",false)&&prefs(c).getBoolean("auto_keep_cover_awake",false)
 @JvmStatic fun persist(c:Context){
  // Earlier builds forced this on. Ask once instead of treating that forced value as consent.
  if(!prefs(c).contains("keep_awake_opt_in"))prefs(c).edit().putBoolean("keep_awake_opt_in",false).putBoolean("auto_keep_cover_awake",false).commit()
 }
 @JvmStatic fun setEnabled(c:Context,value:Boolean){
  if(value&&DeviceCompatibility.isFlip6(android.os.Build.MODEL)){status="Keep-awake unavailable on unverified Flip 6";return}
  if(value&&!RuntimeConsent.ready(c))return
  prefs(c).edit().putBoolean("keep_awake_opt_in",value).putBoolean("auto_keep_cover_awake",value).commit()
  reconnect();tick(c.applicationContext)
 }
 @JvmStatic fun reconnect(){epoch++;nextAttempt=0L}
 @JvmStatic fun tick(context:Context){
  persist(context)
  val p=prefs(context);val on=enabled(context)&&RuntimeConsent.ready(context)
  val original=p.getString("fold_setting_original",null)
  if(!on&&original==null){status="Keep-awake off; Samsung fold-lock setting is unchanged";return}
  if(pending||SystemClock.elapsedRealtime()<nextAttempt)return
  if(!runCatching{PrivilegedAccess.pingBinder()&&PrivilegedAccess.checkSelfPermission()==0}.getOrDefault(false)){
   status=if(on)"Keep-awake waiting for authorization" else "Keep-awake off; restoration pending. Reconnect Shizuku and reopen Privacy & controls, or change Lock when folded in Samsung Settings."
   return
  }
  pending=true
  val expectedEpoch=epoch
  // Read and persist before writing, so process death cannot lose the original value.
  val action=if(!on)"restore" else if(original==null)"read" else "always"
  FoldSettingsClient.request(context,action,original){result->
   pending=false
   if(result.getBoolean("ok")){
    if(action=="read")p.edit().putString("fold_setting_original",result.getString("value")).commit()
    if(action=="restore")p.edit().remove("fold_setting_original").commit()
    status=if(action=="restore")"Keep-awake off; ${result.getString("value")}" else "Keep-awake: ${result.getString("value") }"
    nextAttempt=SystemClock.elapsedRealtime()+if(action=="read"||expectedEpoch!=epoch)0L else 300000L
    if(action=="read"||expectedEpoch!=epoch)main.post{tick(context)}
   }else{
    status="Keep-awake ${if(on)"verification" else "restoration"} pending: ${result.getString("error")}. Reopen Privacy & controls to retry."
    nextAttempt=SystemClock.elapsedRealtime()+15000L
    // Finish a stop issued during an in-flight helper operation without restarting the service.
    if(result.getBoolean("busy"))main.postDelayed({nextAttempt=0;tick(context)},1000)
   }
  }
 }
}
