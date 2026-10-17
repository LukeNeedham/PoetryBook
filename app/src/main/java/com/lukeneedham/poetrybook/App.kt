package com.lukeneedham.poetrybook

import android.app.Application
import com.lukeneedham.common.DebugKit
import com.lukeneedham.common.DebugKitConfig
import com.lukeneedham.common.logger.Logger

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // First thing, so crashes and logs from the rest of start-up are captured.
        DebugKit.init(DebugKitConfig(enabled = BuildConfig.DEBUG))
        Logger.i("App started")
    }
}
