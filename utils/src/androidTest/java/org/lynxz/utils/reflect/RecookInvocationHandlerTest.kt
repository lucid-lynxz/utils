package org.lynxz.utils.reflect

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.lynxz.utils.log.LoggerUtil
import org.lynxz.utils.observer.ISimpleObserver
import org.lynxz.utils.observer.ISimpleObserver2
import java.lang.reflect.Method

@RunWith(AndroidJUnit4::class)
class RecookInvocationHandlerTest {
    private val TAG = "InvocationHandlerTest"

    /**
     * 生成hook对象, 可以指定方法对应的字
     */
    private fun generateFuncInvokeCallback(methodRetMap: MutableMap<String, EnabledResult<Any>?>? = null): ProxyUtil.IFuncInvokeCallback {
        return object : ProxyUtil.IFuncInvokeCallback {
            override fun onFuncInvoke(
                method: Method,
                returnObj: Any?,
                argGroupIndex: Int,
                args: Array<out Any?>?
            ): EnabledResult<Any>? {
                LoggerUtil.w(
                    TAG,
                    "onFuncInvoke name=${method.name},retMap=${methodRetMap?.get("onInvoke2")?.obj}"
                )
                return methodRetMap?.get(method.name)
            }
        }
    }

    @Test
    fun singleInterfaceTest() {
        val methodName = "onInvoke2"
        val map = mutableMapOf<String, EnabledResult<Any>?>(
            methodName to EnabledResult(obj = null, enabled = true)
        )
        val callback = generateFuncInvokeCallback(map)

        val proxy = RecookInvocationHandler(
            null,
            arrayOf(ISimpleObserver2::class.java),
            object : ProxyUtil.IFuncInvokeCallback {
                override fun onFuncInvoke(
                    method: Method,
                    returnObj: Any?,
                    argGroupIndex: Int,
                    args: Array<out Any?>?
                ): EnabledResult<Any>? {
                    return null
                }
            },
            callback
        ).newProxyInstance()

        Assert.assertTrue("proxy对象不是 ISimpleObserver2 类的实例", proxy is ISimpleObserver2)
        var onInvoke2 = (proxy as ISimpleObserver2).onInvoke2("hello")
        Assert.assertFalse(onInvoke2) // callback 返回 null, 类型不匹配, 使用默认值 false

        map[methodName] = EnabledResult(true, enabled = false)
        onInvoke2 = proxy.onInvoke2("hello2")
        Assert.assertFalse(onInvoke2) // callback 返回 true,但是不启用, 使用默认值 false

        map[methodName] = EnabledResult(true, enabled = true)
        onInvoke2 = proxy.onInvoke2("hello3")
        Assert.assertTrue(onInvoke2) // callback 返回 true,并且启用

        map[methodName] = EnabledResult(null, enabled = false)
        onInvoke2 = proxy.onInvoke2("hello4")
        Assert.assertFalse(onInvoke2) // callback 返回 true,并且启用
    }

    @Test
    fun twoInterfaceTest() {
        val methodName = "onInvokeChar"
        val map = mutableMapOf<String, EnabledResult<Any>?>(
            methodName to EnabledResult(obj = null, enabled = true)
        )
        val callback = generateFuncInvokeCallback(map)

        val proxy = RecookInvocationHandler(
            null, arrayOf(ISimpleObserver::class.java, ISimpleObserver2::class.java),
            null, callback
        ).newProxyInstance()

        Assert.assertTrue("proxy对象不是 ISimpleObserver 类的实例", proxy is ISimpleObserver)
        Assert.assertTrue("proxy对象不是 ISimpleObserver2 类的实例", proxy is ISimpleObserver2)
        var onInvoke2 = (proxy as ISimpleObserver2).onInvokeChar()
        Assert.assertEquals(0.toChar(), onInvoke2)

        map[methodName] = EnabledResult(1.toChar(), true)
        onInvoke2 = proxy.onInvokeChar()
        Assert.assertEquals(1.toChar(), onInvoke2)

        map[methodName] = EnabledResult(1.toChar(), false)
        onInvoke2 = proxy.onInvokeChar()
        Assert.assertEquals(0.toChar(), onInvoke2)

        map[methodName] = EnabledResult(null, false)
        onInvoke2 = proxy.onInvokeChar()
        Assert.assertEquals(0.toChar(), onInvoke2)

        map[methodName] = EnabledResult(null, true)
        onInvoke2 = proxy.onInvokeChar()
        Assert.assertEquals(0.toChar(), onInvoke2)
    }

    /**
     * 验证可对现有接口实现类进行增强
     * 通过动态代理, 在实现类方法触发前后执行其他操作
     * */
    @Test
    fun dynamicProxyEnhancedTest() {
        val enhanceMsg = mutableListOf<String>()
        val msgBefore = "msg before method invoked"
        val msgAfter = "msg after method invoked"

        // 接口实现类
        val realObj = object : ISimpleObserver {
            override fun onInvoke(msg: String?) {
                LoggerUtil.d(TAG, "onInvoke:  $msg")
            }
        }

        // 通过动态代理对实现类方法进行增强
        val proxy = RecookInvocationHandler(realObj, null,
            object : ProxyUtil.IFuncInvokeCallback {
                override fun onFuncInvoke(
                    method: Method,
                    returnObj: Any?,
                    argGroupIndex: Int,
                    args: Array<out Any?>?
                ): EnabledResult<Any>? {
                    // 增强1: 在 realObj 方法触发前执行其他操作
                    LoggerUtil.d(TAG, msgBefore)
                    enhanceMsg.add(msgBefore)
                    return null
                }
            },
            object : ProxyUtil.IFuncInvokeCallback {
                override fun onFuncInvoke(
                    method: Method,
                    returnObj: Any?,
                    argGroupIndex: Int,
                    args: Array<out Any?>?
                ): EnabledResult<Any> {
                    // 增强1: 在 realObj 方法触发后执行其他操作
                    LoggerUtil.d(TAG, msgAfter)
                    enhanceMsg.add(msgAfter)
                    return EnabledResult(null, true)
                }
            }).newProxyInstance()

        Assert.assertTrue(proxy is ISimpleObserver)
        (proxy as ISimpleObserver).onInvoke("hello")
        Assert.assertEquals(2, enhanceMsg.size)
        Assert.assertEquals(msgBefore, enhanceMsg[0])
        Assert.assertEquals(msgAfter, enhanceMsg[1])
    }

}