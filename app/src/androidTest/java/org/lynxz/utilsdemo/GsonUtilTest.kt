package org.lynxz.utilsdemo

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.lynxz.utilsdemo.util.gson.GsonUtil

@RunWith(AndroidJUnit4::class)
class GsonUtilTest {

    data class MosaicParaBean(
        var enable: Boolean = false,
        var detailMosaicMap: MutableMap<String, String> = mutableMapOf()
    )

    @Test
    fun beanMapFieldTest() {
        val json = "{\"detailMosaicMap\":{\"55\":\"通过\"},\"enable\":true}"
//        val bean: MosaicParaBean? = GsonUtil.parseJson<MosaicParaBean>(json)
        val bean: MosaicParaBean? =
            GsonUtil.parseJson(json, MosaicParaBean::class.java)
        println("bean=$bean, map=${bean?.detailMosaicMap}")
        Assert.assertTrue(bean?.detailMosaicMap is MutableMap)

    }
}