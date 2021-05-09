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
        ScreenUtil.enableFontScaleChange(false) // 禁止字体随系统设置缩放
        //ScreenUtil.addExcludeActivity("org.lynxz.utilsdemo") // 不对特定页面进行屏幕适配
        ScreenUtil.init(this) // 初始化
    }
}