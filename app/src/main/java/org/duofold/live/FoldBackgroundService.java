package org.duofold.live;
import android.app.*;
import android.content.*;
import android.os.*;
import rikka.shizuku.Shizuku;
/** User-enabled lifetime independent of the settings Activity. */
public class FoldBackgroundService extends Service {
 public static boolean running=false;
 private final Handler main=new Handler(Looper.getMainLooper());
 private boolean wanted(){return RuntimeControls.wanted(this);}
 private LiveAngles angles;
 private Flip6LiveRuntime flip6Runtime;
 private long nextFlip6ReaderAttempt;
 private FoldAwakeGuard awakeGuard;
 private ClosureDiagnostics closureDiagnostics;
 private long asleepSince=-1,nextReaderAttempt;
 private String lastNotice="";
 private boolean lastReachable=false;
 private final rikka.shizuku.Shizuku.OnBinderReceivedListener binderReady=()->main.post(()->{if(PrivilegedAccess.rootSelected())return;RecoveryLog.add("Shizuku Binder received");resetHelpers("Shizuku connection refreshed");kick();});
 private final rikka.shizuku.Shizuku.OnBinderDeadListener binderDead=()->main.post(()->{if(PrivilegedAccess.rootSelected())return;RecoveryLog.add("Shizuku Binder death notification");resetHelpers("Shizuku disconnected");kick();});
 private final BroadcastReceiver screen=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
  ClosureDiagnostics.screen(i.getAction());RecoveryLog.add("Background received "+i.getAction());
  boolean flip6=DeviceCompatibility.isFlip6(Build.MODEL);
  boolean transientFlipHandoff=flip6 && Intent.ACTION_SCREEN_OFF.equals(i.getAction()) && getSystemService(PowerManager.class).isInteractive();
  if(Intent.ACTION_SCREEN_OFF.equals(i.getAction()) && !transientFlipHandoff)clearEffect();
  else if(Intent.ACTION_SCREEN_ON.equals(i.getAction()))nextReaderAttempt=0;
  if(!flip6)FoldAwakeDefault.reconnect();
  if(transientFlipHandoff)RecoveryLog.add("Flip SCREEN_OFF treated as panel handoff; retaining hinge reader and live overlay");
  kick();
 }};
 private final android.database.ContentObserver foldSettingObserver=new android.database.ContentObserver(main){
  @Override public void onChange(boolean selfChange){if(!DeviceCompatibility.isFlip6(Build.MODEL))FoldAwakeDefault.reconnect();kick();}
 };
 private final Shizuku.OnRequestPermissionResultListener permissionResult=(requestCode,grantResult)->main.post(()->{if(PrivilegedAccess.rootSelected())return;if(!DeviceCompatibility.isFlip6(Build.MODEL))FoldAwakeDefault.reconnect();kick();});
 private void stopFlip6Runtime(String reason){
  if(flip6Runtime!=null){flip6Runtime.stop(reason);flip6Runtime=null;}
  else {LiveAngles.setExternalCaptureBinder(null);LiveAngles.clearExternalAngle(reason);}
 }
 private void resetHelpers(String reason){
  clearEffect();
  if(DeviceCompatibility.isFlip6(Build.MODEL)){stopFlip6Runtime(reason);nextFlip6ReaderAttempt=0;return;}
  if(angles!=null){angles.stop();angles=null;}
  HelperBinding.reset(reason);nextReaderAttempt=0;
  org.duofold.live.wallpaperlayer.WallpaperRestore.reconnect();
  FoldAwakeDefault.reconnect();InnerDecorRecovery.reconnect();
 }
 private void clearEffect(){StandaloneService service=StandaloneService.Companion.getInstance();if(service!=null)service.clearUnavailableEffect();}
 private void kick(){if(!running)return;main.removeCallbacks(supervise);main.post(supervise);}
 public static void reconnectHelpers(Context c){c.startForegroundService(new Intent(c,FoldBackgroundService.class).setAction("RECONNECT_HELPERS"));}
 public static String connectionReport(){
  if(PrivilegedAccess.rootSelected())return PrivilegedAccess.report()+"\n"+HelperBinding.report();
  try{return "Shizuku binder: "+(Shizuku.getBinder()==null?"absent":("alive="+Shizuku.getBinder().isBinderAlive()+", ping="+PrivilegedAccess.pingBinder()))+"; permission="+(PrivilegedAccess.pingBinder()?PrivilegedAccess.checkSelfPermission():"unavailable")+"; server="+Shizuku.getVersion()+"; UID="+Shizuku.getUid()+"\n"+HelperBinding.report();}
  catch(Exception e){return "Shizuku check: "+e.getClass().getSimpleName();}
 }
 public IBinder onBind(Intent i){return null;}
 public void onCreate(){super.onCreate();RecoveryLog.init(this);RecoveryLog.add("Background service created, PID "+android.os.Process.myPid());
  boolean flip6=DeviceCompatibility.isFlip6(Build.MODEL);
  closureDiagnostics=flip6?null:new ClosureDiagnostics(this,main);
  if(!flip6){FoldAwakeDefault.persist(this);awakeGuard=new FoldAwakeGuard(this);awakeGuard.start();}
  Shizuku.addBinderReceivedListenerSticky(binderReady);Shizuku.addBinderDeadListener(binderDead);Shizuku.addRequestPermissionResultListener(permissionResult);
  if(!flip6)try{getContentResolver().registerContentObserver(android.provider.Settings.System.getUriFor("fold_lock_behavior"),false,foldSettingObserver);}catch(Exception e){RecoveryLog.add("Fold setting observer unavailable; periodic verification active");}
  IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_USER_PRESENT);registerReceiver(screen,filter,Context.RECEIVER_NOT_EXPORTED);
createNotificationChannel(this);}
 public static void createNotificationChannel(Context context){
  NotificationChannel channel=new NotificationChannel("fold_live","Fold animation running",NotificationManager.IMPORTANCE_LOW);
  channel.setDescription("Routine running status. You can hide this category while Duo Fold Live keeps running.");
  context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
 }
 private Notification notification(String message){
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,FlipHomeActivity.class),PendingIntent.FLAG_IMMUTABLE);
  PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,FoldBackgroundService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE);
  return new Notification.Builder(this,"fold_live").setSmallIcon(android.R.drawable.ic_menu_compass).setContentTitle("Duo Fold Live is running").setContentText(message).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).addAction(new Notification.Action.Builder(null,"Stop all",stop).build()).build();
 }
 public int onStartCommand(Intent i,int flags,int id){
  // Must promote even when restoring a now-disabled service, then stop cleanly.
  startForeground(112,notification("Connecting to the live angle reader"));lastNotice="";
  if(i!=null&&"RECONNECT_HELPERS".equals(i.getAction()))resetHelpers("Manual helper reconnect");
  if(i!=null&&"STOP".equals(i.getAction())){
   RuntimeControls.stopAll(this);
   if(!wanted()){stopSelf();return START_NOT_STICKY;}
  }
  if(!wanted()){stopSelf();return START_NOT_STICKY;}
  if(!running){running=true;main.post(supervise);}else if(i!=null&&"RECONNECT_HELPERS".equals(i.getAction()))kick();
  return START_STICKY;
 }
 private final Runnable supervise=new Runnable(){public void run(){
  if(!running)return;
  if(!wanted()){stopSelf();return;}
  boolean interactive=getSystemService(PowerManager.class).isInteractive();
  long now=SystemClock.elapsedRealtime();
  if(interactive)asleepSince=-1;else if(asleepSince<0)asleepSince=now;
  HealthTrace.sample(FoldBackgroundService.this);
  // Ignore transient screen-off during a panel handoff; suspend sustained sleep.
  if(!interactive&&now-asleepSince>=3000){
   if(angles!=null){angles.stop();angles=null;RecoveryLog.add("Reader suspended for screen-off");}
   if(DeviceCompatibility.isFlip6(Build.MODEL)&&flip6Runtime!=null)stopFlip6Runtime("Screen asleep — Flip 6 reader suspended");
   LiveAngles.status="Screen asleep — reader suspended";
   main.postDelayed(this,30000);return;
  }
  boolean flip6=DeviceCompatibility.isFlip6(Build.MODEL);
  if(interactive&&!flip6)org.duofold.live.wallpaperlayer.WallpaperRestore.tick(FoldBackgroundService.this);
  if(!flip6&&RotationMigration.needed(FoldBackgroundService.this)){
   if(angles!=null){angles.stop();angles=null;}
   clearEffect();nextReaderAttempt=0;
   if(interactive&&!getSystemService(KeyguardManager.class).isKeyguardLocked())RotationMigration.tick(FoldBackgroundService.this);
   LiveAngles.status=RotationMigration.status;
   if(!LiveAngles.status.equals(lastNotice)){lastNotice=LiveAngles.status;getSystemService(NotificationManager.class).notify(112,notification(lastNotice));}
   main.postDelayed(this,2000);return;
  }
  boolean foldEnabled=getSharedPreferences("standalone",0).getBoolean("enabled",false);
  try{
   if(!foldEnabled){if(angles!=null){angles.stop();angles=null;}if(flip6)stopFlip6Runtime("Fold animation disabled");LiveAngles.status="Fold animation disabled";}
   else if(flip6){
    if(now>=nextFlip6ReaderAttempt&&(flip6Runtime==null||!LiveAngles.effectFresh())){
     if(flip6Runtime!=null)stopFlip6Runtime("Reconnecting to Flip 6 hinge sensor");
     nextFlip6ReaderAttempt=now+8000L;
     flip6Runtime=new Flip6LiveRuntime(FoldBackgroundService.this);
     flip6Runtime.start();
    }
   }else{
   boolean reachable=PrivilegedAccess.pingBinder();
   if(reachable!=lastReachable){lastReachable=reachable;RecoveryLog.add("Privileged connection reachability="+reachable);}
   if(!reachable){
    clearEffect();
    if(angles!=null){angles.stop();angles=null;}
    LiveAngles.status=PrivilegedAccess.rootSelected()?"Root unavailable — check root authorization in Connection & setup":"Shizuku connection unavailable — check whether Shizuku says Running";
   }else if(PrivilegedAccess.checkSelfPermission()!=0){
    if(angles!=null){angles.stop();angles=null;}
    LiveAngles.status="Connection authorization needed — check Connection & setup";
   }else if(now>=nextReaderAttempt&&(angles==null||!angles.isRunning()||angles.stalled())){
    nextReaderAttempt=now+30000;
    if(angles!=null)angles.stop();
    angles=new LiveAngles(FoldBackgroundService.this);angles.start();
   }
   }
  }catch(Exception e){LiveAngles.status="Waiting for privileged access: "+e.getClass().getSimpleName();}
  if(!flip6){FoldAwakeDefault.tick(FoldBackgroundService.this);InnerDecorRecovery.tick(FoldBackgroundService.this);}
  String notice=!foldEnabled?"Fold animation disabled":LiveAngles.effectFresh()?(flip6?"Flip 6 hinge animation active":"Live hinge control active"):LiveAngles.status;
  if(!notice.equals(lastNotice)){lastNotice=notice;getSystemService(NotificationManager.class).notify(112,notification(notice));}
  SupportPrompts.tick(FoldBackgroundService.this);
  main.postDelayed(this,2000);
 }};
 public void onTaskRemoved(Intent intent){RecoveryLog.add("Settings task dismissed; background service remains running");super.onTaskRemoved(intent);}
 public void onDestroy(){if(closureDiagnostics!=null)closureDiagnostics.close();if(awakeGuard!=null)awakeGuard.close();if(flip6Runtime!=null)stopFlip6Runtime("Flip 6 runtime stopped");RecoveryLog.add("Background service destroyed");Shizuku.removeBinderReceivedListener(binderReady);Shizuku.removeBinderDeadListener(binderDead);Shizuku.removeRequestPermissionResultListener(permissionResult);getContentResolver().unregisterContentObserver(foldSettingObserver);try{unregisterReceiver(screen);}catch(Exception ignored){}running=false;main.removeCallbacksAndMessages(null);if(angles!=null){angles.stop();angles=null;}stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}
