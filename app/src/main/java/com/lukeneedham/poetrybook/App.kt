package com.lukeneedham.poetrybook

import android.app.Application
import com.lukeneedham.common.DebugKit
import com.lukeneedham.common.DebugKitConfig
import com.lukeneedham.common.logger.Logger
import com.lukeneedham.poetrybook.di.KoinModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // First thing, so crashes and logs from the rest of start-up are captured.
        DebugKit.init(DebugKitConfig(enabled = BuildConfig.DEBUG))
        Logger.i("App started")
        startKoin {
            androidContext(this@App)
            modules(KoinModules.modules)
        }
    }
}
