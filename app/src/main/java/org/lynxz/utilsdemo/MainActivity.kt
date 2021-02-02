package org.lynxz.utilsdemo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.lynxz.utils.AssetUtil
import org.lynxz.utils.log.LoggerUtil

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        LoggerUtil.d("assetUtil", "${AssetUtil.isExist(this, "01.jpg", "img/")}")
    }
}