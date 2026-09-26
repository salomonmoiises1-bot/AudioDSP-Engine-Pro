package com.sbz.dsp

import android.media.audiofx.Virtualizer
import android.util.Log

/** Optional session-bound Android virtualizer adapter. It is never treated as a global effect. */
class VirtualizerManager(val audioSessionId:Int) {
    private var virtualizer:Virtualizer?=null
    init { try { virtualizer=Virtualizer(100,audioSessionId) } catch(e:Exception) { Log.w("SbzVirtualizer","Unavailable for session $audioSessionId: ${e.message}") } }
    fun apply(enabled:Boolean,strength:Short) { val v=virtualizer?:return; try { v.enabled=enabled; if(enabled&&v.strengthSupported)v.setStrength(strength.coerceIn(0,1000)) } catch(e:Exception){ Log.w("SbzVirtualizer","Apply failed: ${e.message}") } }
    fun release(){ try{virtualizer?.release()}catch(_:Exception){}; virtualizer=null }
}
