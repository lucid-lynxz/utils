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

//    override fun getResources(): Resources {
//        val resources = super.getResources()
//        val displayMetrics = resources.displayMetrics
//        val configuration = resources.configuration
//        LoggerUtil.w("xxx","density1="+ displayMetrics.density)
//        displayMetrics.density=3.5f
//        LoggerUtil.w("xxx","density2="+ displayMetrics.density+",ori="+resources.displayMetrics.density)
//        resources.updateConfiguration(configuration, displayMetrics)
//        LoggerUtil.w("xxx","density3="+ displayMetrics.density+",ori="+resources.displayMetrics.density)
//        return resources
//    }
}