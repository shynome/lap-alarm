package com.shynome.lap

import android.app.Application
import com.shynome.lap.core.Lap

class LapApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Lap.init(this)
    }
}
