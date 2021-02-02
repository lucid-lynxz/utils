package org.lynxz.utilsdemo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.lynxz.utils.AssetUtil

@RunWith(AndroidJUnit4::class)
class AssetUtilTest {

    @Test
    fun isExistTest() {
//        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = DemoApplication.app
        Assert.assertTrue(AssetUtil.isExist(app, "test_02.jpg", "img"))
        Assert.assertTrue(AssetUtil.isExist(app, "test_01.jpg", "img/png"))
    }
}