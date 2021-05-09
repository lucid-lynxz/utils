package org.lynxz.utilsdemo

import android.app.Application
import org.lynxz.utils.ScreenUtil

class DemoApplication : Application() {
    companion object {
        lateinit var app: Application
    }

    override fun onCreate() {
        super.onCreate()
        app = this
        ScreenUtil.enableFontScaleChange(false)
        ScreenUtil.init(this)
    }
}