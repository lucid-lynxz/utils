package org.lynxz.utils.reflect

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.lynxz.utils.bean.EnumStatusBean
import org.lynxz.utils.bean.User
import org.lynxz.utils.log.LoggerUtil


@RunWith(AndroidJUnit4::class)
class ReflectUtilTest {
    private val TAG = "ReflectUtilTest"

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

    /**
     * 反射获取enum对象
     * */
    @Test
    fun getEnumInfo() {
        val clz = EnumStatusBean::class.java

        val enumBean = ProxyUtil.generateDefaultImplObj(clz)
        Assert.assertTrue("实际类型为:$enumBean", enumBean == EnumStatusBean.START)

        val objs = clz.enumConstants
        LoggerUtil.d(TAG, "objs.size=${objs?.size}")
        ReflectUtil.getDeclaredMethod(clz, "getInfo")?.let { getInfo ->
            objs?.forEach {
                val info = getInfo.invoke(it)
                LoggerUtil.d(TAG, "info:$info,beanType:$it,name:${it.name}")
            }
        }
    }

    /**
     * 获取方法参数注解
     * */
    @Test
    fun getMethodParaAnnotation() {
        val user = User()
        val m = ReflectUtil.getDeclaredMethod(
            user,
            "updateGender",
            String::class.java,
            String::class.java
        )
        // 获取该方法所有形参对应的注解列表,返回二维数据 Annotation[][]
        // 其中 Annotation[0] 表示第一个参数上的注解, 可能为null
        val arr = m?.parameterAnnotations?.get(0) ?: return
        arr.forEach {
            LoggerUtil.d(TAG, "annotation: $it")
        }
    }
}