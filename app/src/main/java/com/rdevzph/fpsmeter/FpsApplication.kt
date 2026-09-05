package com.rdevzph.fpsmeter

import android.app.Application
import com.rdevzph.fpsmeter.crash.CrashHandler

class FpsApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
    }
}
