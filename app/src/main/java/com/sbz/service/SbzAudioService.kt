package com.sbz.service

import android.app.*
import android.content.*
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.*
import androidx.core.app.NotificationCompat
import com.sbz.MainActivity
import com.sbz.R
import com.sbz.data.PresetRepository
import com.sbz.dsp.SbzDspEngine
import com.sbz.dsp.model.DspConfig

class SbzAudioService:Service(){
 companion object{
  private const val CHANNEL="sbz_dsp_channel"; private const val ID=31415
  const val ACTION_START="com.sbz.action.START"; const val ACTION_STOP="com.sbz.action.STOP"; const val ACTION_TOGGLE_DSP="com.sbz.action.TOGGLE_DSP"
  const val ACTION_ATTACH_SESSION="com.sbz.action.ATTACH_SESSION"; const val ACTION_DETACH_SESSION="com.sbz.action.DETACH_SESSION"; const val ACTION_RECLAIM_CONTROL="com.sbz.action.RECLAIM_CONTROL"
  const val EXTRA_SESSION_ID="extra_session_id"; const val EXTRA_PACKAGE_NAME="extra_package_name"
 }
 private val binder=LocalBinder(); val dspEngine=SbzDspEngine(); private lateinit var repo:PresetRepository; private lateinit var audioManager:AudioManager; private var config=DspConfig()
 inner class LocalBinder:Binder(){fun getService()=this@SbzAudioService}
 private val deviceCallback=object:AudioDeviceCallback(){override fun onAudioDevicesAdded(d:Array<out AudioDeviceInfo>?){dspEngine.reclaimAllControl()};override fun onAudioDevicesRemoved(d:Array<out AudioDeviceInfo>?){dspEngine.reclaimAllControl()}}
 override fun onCreate(){super.onCreate();repo=PresetRepository(this);audioManager=getSystemService(AUDIO_SERVICE) as AudioManager;config=repo.loadActiveConfig();createChannel();audioManager.registerAudioDeviceCallback(deviceCallback,null);dspEngine.start();dspEngine.updateConfig(config)}
 override fun onStartCommand(i:Intent?,flags:Int,startId:Int):Int{startForeground(ID,notification());when(i?.action?:ACTION_START){ACTION_START->if(!dspEngine.engineState.value.isRunning)dspEngine.start();ACTION_STOP->{dspEngine.stop();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY};ACTION_TOGGLE_DSP->updateConfig(config.copy(isEnabled=!config.isEnabled));ACTION_ATTACH_SESSION->i.getIntExtra(EXTRA_SESSION_ID,-1).takeIf{it>=0}?.let{dspEngine.attachSession(it)};ACTION_DETACH_SESSION->i.getIntExtra(EXTRA_SESSION_ID,-1).takeIf{it>=0}?.let{dspEngine.detachSession(it)};ACTION_RECLAIM_CONTROL->dspEngine.reclaimAllControl()};return START_STICKY}
 fun updateConfig(c:DspConfig){config=c.normalized();repo.saveActiveConfig(config);dspEngine.updateConfig(config)}
 fun getCurrentConfig()=config
 private fun createChannel(){if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"sBz DSP",NotificationManager.IMPORTANCE_LOW))}
 private fun notification():Notification=NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle("sBz DSP").setContentText("Audio DSP service").setContentIntent(PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).setOngoing(true).build()
 override fun onBind(i:Intent?)=binder
 override fun onDestroy(){try{audioManager.unregisterAudioDeviceCallback(deviceCallback)}catch(_:Exception){};dspEngine.stop();super.onDestroy()}
}
