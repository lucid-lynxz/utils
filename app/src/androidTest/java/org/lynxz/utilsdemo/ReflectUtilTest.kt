package org.lynxz.utilsdemo

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.lynxz.utils.log.LoggerUtil
import org.lynxz.utils.reflect.ReflectUtil


@RunWith(AndroidJUnit4::class)
class ReflectUtilTest {
    private val TAG = "ReflectUtilTest"

    @Test
    fun getAllDeclareFieldsKVMapTest() {
        val rIdClassPath = "${BuildConfig.APPLICATION_ID}.R\$id"
        val declaredFields = ReflectUtil.getAllDeclareFieldsKVMap(rIdClassPath)
        Assert.assertTrue(declaredFields.containsKey("tv_msg"))
//        declaredFields.forEach {
//            LoggerUtil.d(TAG, "$it")
//        }
    }

    @Test
    fun getSpecialDeclaredMethodsTest() {
        val obj = Any()
        val method = ReflectUtil.getDeclaredMethod(obj, "toString")
        Assert.assertNotNull(method)
        LoggerUtil.d(TAG, "obj.toString=${method?.invoke(obj)}")
    }

    @Test
    fun generateDefaultTypeValueListTest() {
        val list = ReflectUtil.generateDefaultTypeValueList(Int::class.java)
        Assert.assertTrue(list.contains(0))
        Assert.assertTrue(list.contains(Int.MAX_VALUE))
        Assert.assertTrue(list.contains(Int.MIN_VALUE))
    }
}