package org.lynxz.utilsdemo

import android.app.Application

class DemoApplication : Application() {
    companion object {
        lateinit var app: Application
    }

    override fun onCreate() {
        super.onCreate()
        app = this
    }
}