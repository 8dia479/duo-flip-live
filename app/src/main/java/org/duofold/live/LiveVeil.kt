package org.duofold.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import android.view.Surface
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.exp

/** Screenshot-free reference darkening over the live wallpaper and apps; no blur or 3D projection. */
@Composable
internal fun DuoLiveShade(host: StandaloneFoldHost, intensity: Float, innerPanel: Boolean? = null, frozenFrame:GlassFrame? = null, reflectedCover:Boolean = false, opaqueContent:Boolean = false, liveFrame:GlassFrame? = null, flipMode:Boolean = false) {
    var angle by remember { mutableFloatStateOf(Float.NaN) }
    var angleTimestampMs by remember { mutableLongStateOf(0L) }
    var visualAngle by remember { mutableFloatStateOf(Float.NaN) }
    var amount by remember { mutableFloatStateOf(0f) }
    var fadeAlpha by remember { mutableFloatStateOf(1f) }
    val expanded=innerPanel ?: LocalConfiguration.current.isRegular()
    val plainPrefs=LocalContext.current.getSharedPreferences("standalone",0)
    val plain=!flipMode && plainPrefs.getString("animation_mode","")=="plain_fade"
    val tuning=remember(expanded){PlainFadeSettings.read(plainPrefs,expanded)}
    val handoff=HandoffSettings.angle(plainPrefs.getFloat("handoff_angle",HandoffSettings.DEFAULT))
    val openThreshold=LocalContext.current.getSharedPreferences("standalone",0).getFloat("open_threshold",172f)
    val closedThreshold=LocalContext.current.getSharedPreferences("standalone",0).getFloat("closed_threshold",2f)
    val configuredSmoothingMs=FrameSmoothing.sanitize(LocalContext.current.getSharedPreferences("standalone",0).getFloat("smoothing_ms",FrameSmoothing.DEFAULT_MS))
    // Flip's physical handoff needs a responsive single-vsync interpolation, not the
    // Fold-oriented 120ms easing which visibly trails a fast clamshell movement.
    val smoothingMs=if(flipMode)minOf(configuredSmoothingMs,FrameSmoothing.FAST_MS) else configuredSmoothingMs
    val currentView=LocalView.current
    val displayId=currentView.display?.displayId ?: 0
    val displayRotation=currentView.display?.rotation ?: Surface.ROTATION_0
    val rotation=if(flipMode) {
        if(expanded) FlipPreviewPolicy.liveShaderRotation(displayRotation)
        else FlipPreviewPolicy.coverShaderRotation(displayRotation)
    } else displayRotation
    val hinge=LocalHinge.current
    LaunchedEffect(flipMode,expanded,displayId,displayRotation,rotation) {
        if(flipMode) RecoveryLog.add("FLIP_RENDER_TARGET panel="+(if(expanded) "inner" else "cover")+"; display="+displayId+"; displayRotation="+displayRotation+"; shaderRotation="+rotation)
    }
    val latestExpanded by rememberUpdatedState(expanded)
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(Unit){
        val listener=LiveAngles.Listener { value, nanos ->
            angle=value
            if(value.isFinite() && nanos>0L)angleTimestampMs=nanos/1_000_000L
        }
        LiveAngles.add(listener)
        onDispose { LiveAngles.remove(listener) }
    }
    LaunchedEffect(lifecycle){lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
        var previous=0L
        var lastGoodSampleAtMs=0L
        while(true){
            val sensorFresh=LiveAngles.fresh() && angle.isFinite()
            val knownFlipPose=flipMode && angle.isFinite() && angleTimestampMs>0L
            if(!LiveAngles.effectAllowed || (!flipMode && !sensorFresh) ||
                (flipMode && !sensorFresh && !visualAngle.isFinite() && !knownFlipPose)){
                amount=0f;visualAngle=Float.NaN;fadeAlpha=0f;previous=0L;lastGoodSampleAtMs=0L
                delay(30);continue
            }
            withFrameNanos { now ->
                if(sensorFresh){
                    val dt=if(previous==0L)8.33f else ((now-previous)/1_000_000f).coerceIn(1f,50f)
                    previous=now
                    visualAngle=FrameSmoothing.step(visualAngle,angle,dt,smoothingMs)
                    if(flipMode)lastGoodSampleAtMs=angleTimestampMs
                    amount=if(flipMode)FlipGlassCurve.progress(visualAngle,latestExpanded,LiveAngles.externalDirection(),openThreshold,closedThreshold)
                      else if(latestExpanded && !plain && frozenFrame==null && !plainPrefs.getBoolean("dual",false))MotionGlass.innerAmount(DuoShadeCurve.progress(visualAngle,true,openThreshold),angle>=FoldThreshold.sanitize(openThreshold))
                      else if(latestExpanded && angle>=FoldThreshold.sanitize(openThreshold))0f
                      else if(plain)PlainFadeCurve.progress(visualAngle,latestExpanded,openThreshold,handoff)
                      else DuoShadeCurve.progress(visualAngle,latestExpanded,openThreshold)
                    fadeAlpha=1f
                }else if(flipMode){
                    // Keep the last valid pose even if the wallpaper reader temporarily
                    // clears its angle during display remapping. The retained panel fades
                    // only after the same bounded hold used by the live Flip effect.
                    if(!visualAngle.isFinite() && knownFlipPose){
                        visualAngle=angle
                        lastGoodSampleAtMs=angleTimestampMs
                        amount=FlipGlassCurve.progress(visualAngle,latestExpanded,LiveAngles.externalDirection(),openThreshold,closedThreshold)
                    }
                    val ageMs=(SystemClock.elapsedRealtime()-lastGoodSampleAtMs).coerceAtLeast(0L)
                    if(lastGoodSampleAtMs>0L && LiveEffectFadePolicy.active(ageMs)){
                        fadeAlpha=LiveEffectFadePolicy.alpha(ageMs)
                    }else{
                        amount=0f;visualAngle=Float.NaN;fadeAlpha=0f;previous=0L;lastGoodSampleAtMs=0L
                    }
                }
            }
        }
    }}
    SideEffect { host.onFrame(amount>.003f && fadeAlpha>.003f,amount) }
    val fadePrefs=LocalContext.current.getSharedPreferences("standalone",0)
    val debug=!flipMode && AnimationModePolicy.blackFade(fadePrefs.getString("animation_mode",AnimationModePolicy.DEFAULT),fadePrefs.getBoolean("debug_mode",false))
    if(!debug || (liveFrame!=null && IncomingGhostDiagnostic.mode!=0)){DuoGlassSurface(visualAngle,amount,intensity,expanded,rotation,frozenFrame,reflectedCover,opaqueContent,liveFrame,flipMode,renderAlpha=fadeAlpha);return}
    Canvas(Modifier.fillMaxSize().graphicsLayer { alpha=fadeAlpha }){
        if(opaqueContent && frozenFrame!=null)drawScreenshotBackdrop(drawContext.canvas.nativeCanvas,frozenFrame,size.width.toInt(),size.height.toInt())
        if(plain)drawRect(Color.Black.copy(alpha=PlainFadeCurve.dim(amount,tuning)))
        val horizontal=rotation==Surface.ROTATION_90 || rotation==Surface.ROTATION_270
        val reversed=rotation==Surface.ROTATION_90 || rotation==Surface.ROTATION_180
        val extent=if(horizontal)size.height else size.width
        val center=hinge?.takeIf { it.vertical != horizontal }?.let { (it.startPx+it.endPx)/2f } ?: extent/2f
        val hingeAt=center.coerceIn(1f,(extent-1f).coerceAtLeast(1f))
        val segments=if(plain)256 else 64
        val stops=(0..segments).map { i ->
            val fraction=i/segments.toFloat()
            val coord=fraction*extent
            val edge=if(expanded){if(reversed)(coord-hingeAt)/(extent-hingeAt) else (hingeAt-coord)/hingeAt}
                     else if(reversed)1f-fraction else fraction
            fraction to Color.Black.copy(alpha=if(plain)PlainFadeCurve.wipe(amount,1f-edge,tuning) else DuoShadeCurve.alpha(amount,edge,intensity))
        }.toTypedArray()
        drawRect(Brush.linearGradient(*stops,start=Offset.Zero,end=if(horizontal)Offset(0f,extent) else Offset(extent,0f)))
    }
}
