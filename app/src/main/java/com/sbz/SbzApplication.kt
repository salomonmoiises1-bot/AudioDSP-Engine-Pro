package com.sbz

import android.app.Application
import android.util.Log

class SbzApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("SbzApp", "sBz initialized")
    }
}
