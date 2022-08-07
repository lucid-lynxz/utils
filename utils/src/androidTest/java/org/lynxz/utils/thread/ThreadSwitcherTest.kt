package org.lynxz.utils.thread

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.lynxz.utils.log.LoggerUtil
import org.lynxz.utils.observer.ISimpleObserver
import kotlin.concurrent.thread

/**
 * 线程切换验证
 * */
@RunWith(AndroidJUnit4::class)
class ThreadSwitcherTest {
    private val TAG = "ThreadSwitcherTest"

    /**
     * 使用默认的方法自动创建 innerObserver
     * */
    @Test(timeout = 30000)
    fun switcherTest() {
        switcherTestImpl()
    }

    @Test
    fun switcherTestByMethod() {
        switcherTestImpl(setOf("onInvoke", "onInvoke1"))
    }

    @Test
    fun switcherTestByMethod1() {
        switcherTestImpl(setOf("onInvoke2"))
    }

    private fun switcherTestImpl(canCallbackMethods: Set<String>? = null) {
        // 1. 根据上层callback需要运行的线程,创建switcher
        val mainLooper = Looper.getMainLooper()
        val switcher = ThreadSwitcher.newInstance(mainLooper)

        // 2. 使用默认方法生成 innerObserver
        val innerObserver = switcher.generateInnerObserverImpl(ISimpleObserver::class.java)

        // 3. 创建 outerObserver并注册
        var targetLooper: Looper? = null
        val onInvokeMethods = mutableSetOf<String>() // outerObserver被触发的方法名
        val outerObserver = object : ISimpleObserver {
            override fun onInvoke(msg: String?) {
                onInvokeMethods.add("onInvoke")
                targetLooper = Looper.myLooper()
                LoggerUtil.w(
                    TAG,
                    "outerObserver run thread isMain=${targetLooper == mainLooper}"
                )
            }

            override fun onInvoke1() {
                targetLooper = Looper.myLooper()
                onInvokeMethods.add("onInvoke1")
            }

            override fun onInvoke2() {
                targetLooper = Looper.myLooper()
                onInvokeMethods.add("onInvoke2")
            }
        }

        // 4. 外部注入业务层observer
        // 注意: outerObserver 是通过匿名内部类创建的,registerOuterObserver时明确指定其class
        switcher.registerOuterObserver(
            outerObserver,
            ISimpleObserver::class.java,
            enableCallbackMethods = canCallbackMethods
        )

        // 5. 模拟sdk线程回调 innerCallback, 验证 innerCallback 是否会自动切换到targetThread并触发 outerCallback
        thread {
            LoggerUtil.w(TAG, "thread start....")
            innerObserver.onInvoke("hello, invoke in sub thread")
            innerObserver.onInvoke1()
            innerObserver.onInvoke2()
            LoggerUtil.w(TAG, "thread end....")
        }

        // 等待回调完成
        Thread.sleep(1000)

        // 验证回调线程与指定线程一致
        Assert.assertEquals(mainLooper, targetLooper)

        // 验证仅指定的方法被回调了
        LoggerUtil.w(
            TAG,
            "onInvokeMethods:$onInvokeMethods ,canCallbackMethods:$canCallbackMethods"
        )

        if (canCallbackMethods != null) {
            onInvokeMethods.forEach {
                Assert.assertTrue("$it 方法未被允许回调", canCallbackMethods.contains(it))
            }
        }
    }

    /**
     * 调用方自行实现innerObserver,并手动添加到缓存中及自行进行线程切换
     * */
    @Test(timeout = 30000)
    fun switcherTest1() {
        // 1. 根据上层callback需要运行的线程,创建switcher
        val mainLooper = Looper.getMainLooper()
        val switcher = ThreadSwitcher.newInstance(mainLooper)

        // 2. 调用方自行实现 innerObserver
        val innerObserver = object : ISimpleObserver {
            override fun onInvoke(msg: String?) {
                val targetLooper = Looper.myLooper()
                LoggerUtil.w(
                    TAG,
                    "innerObserver run thread isMain=${targetLooper == mainLooper}"
                )

                // 自行进行线程切换
                switcher.invokeOuterObserverOnTargetThread(
                    ISimpleObserver::class.java,
                    object : Any() {},
                    msg
                )
            }

            override fun onInvoke1() {
                // 自行进行线程切换
                switcher.invokeOuterObserverOnTargetThread(
                    ISimpleObserver::class.java,
                    object : Any() {},
                )
            }

            override fun onInvoke2() {
                // 自行进行线程切换
                switcher.invokeOuterObserverOnTargetThread(
                    ISimpleObserver::class.java,
                    object : Any() {},
                )
            }
        }

        // 3. 将实现的 innerObserver 加到缓存中
        switcher.addInnerObserverToCache(innerObserver, ISimpleObserver::class.java)

        // 4. 创建 outerObserver, 并注册
        var targetLooper: Looper? = null
        val onInvokeMethods = arrayOf(false, false, false) // onInvoke,onInvoke1,onInvoke2是否被触发
        val outerObserver = object : ISimpleObserver {
            override fun onInvoke(msg: String?) {
                onInvokeMethods[0] = true
                targetLooper = Looper.myLooper()
                LoggerUtil.w(
                    TAG,
                    "outerObserver run thread isMain=${targetLooper == mainLooper}"
                )
            }

            override fun onInvoke1() {
                onInvokeMethods[1] = true
            }

            override fun onInvoke2() {
                onInvokeMethods[2] = true
            }
        }

        // 外部注入业务层observer
        // 注意: outerObserver 是通过匿名内部类创建的,registerOuterObserver时明确指定其class
        switcher.registerOuterObserver(
            outerObserver,
            ISimpleObserver::class.java,
            disableCallbackMethods = setOf("onInvoke2")
        )

        // 模拟sdk线程回调 innerCallback, 验证 innerCallback 是否会自动切换到targetThread并触发 outerCallback
        thread {
            LoggerUtil.w(TAG, "thread start....")
            innerObserver.onInvoke("hello, invoke in sub thread")
            innerObserver.onInvoke1()
            innerObserver.onInvoke2()
            LoggerUtil.w(TAG, "thread end....")
        }

        // 等待回调完成
        while (targetLooper == null) {
            LoggerUtil.w(TAG, "targetLooper == null sleep 1s")
            Thread.sleep(1000)
        }

        // 验证回调线程与指定线程一致
        Assert.assertEquals(mainLooper, targetLooper)

        Thread.sleep(1000)
        Assert.assertTrue("onInvoke1 方法未被触发了", onInvokeMethods[1])
        Assert.assertFalse("onInvoke2 方法被触发了", onInvokeMethods[2])
    }

    /**
     * 通用过滤方法测试
     */
    @Test
    fun globalFilterTest() {
        // 1. 根据上层callback需要运行的线程,创建switcher
        val mainLooper = Looper.getMainLooper()
        val switcher = ThreadSwitcher.newInstance(mainLooper)

        // 2. 调用方自行实现 innerObserver
        val innerObserver = object : ISimpleObserver {
            override fun onInvoke(msg: String?) {
                val targetLooper = Looper.myLooper()
                LoggerUtil.w(TAG, "innerObserver run thread ")

                // 自行进行线程切换
                switcher.invokeOuterObserverOnTargetThread(
                    ISimpleObserver::class.java,
                    object : Any() {},
                    msg
                )
            }

            override fun onInvoke1() {
            }

            override fun onInvoke2() {
            }
        }

        // 3. 将实现的 innerObserver 加到缓存中
        switcher.addInnerObserverToCache(innerObserver, ISimpleObserver::class.java)

        // 4. 创建 outerObserver, 并注册
        val onOuterInvoke = arrayOf(false)
        val outerObserver = object : ISimpleObserver {
            override fun onInvoke(msg: String?) {
                onOuterInvoke[0] = true
                LoggerUtil.w(TAG, "outerObserver onInvoke:$msg")
            }

            override fun onInvoke1() {
            }

            override fun onInvoke2() {
            }
        }

        // 外部注入业务层observer
        // 注意: outerObserver 是通过匿名内部类创建的,registerOuterObserver时明确指定其class
        switcher.registerOuterObserver(outerObserver, ISimpleObserver::class.java)

        // 模拟sdk线程回调 innerCallback, 验证 innerCallback 是否会自动切换到targetThread并触发 outerCallback
        thread {
            LoggerUtil.w(TAG, "thread start....")
            innerObserver.onInvoke("hello, invoke in sub thread")
            LoggerUtil.w(TAG, "thread end....")
        }

        // 等待回调完成
        Thread.sleep(1000)
        Assert.assertTrue("未收到onInvoke回调", onOuterInvoke[0])

        // 添加全局过滤限制
        onOuterInvoke[0] = false
        switcher.registerGlobalObserverFilterMethod(
            ISimpleObserver::class.java,
            disableCallbackMethods = setOf("onInvoke")
        )

        // 再次模拟sdk线程回调 innerCallback, 验证 innerCallback 是否会自动切换到targetThread并触发 outerCallback
        thread {
            LoggerUtil.w(TAG, "thread start2....")
            innerObserver.onInvoke("hello, invoke in sub thread2")
            LoggerUtil.w(TAG, "thread end2....")
        }

        Thread.sleep(1000)
        Assert.assertFalse("收到了onInvoke回调", onOuterInvoke[0])
    }
}