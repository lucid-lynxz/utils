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

    @Test(timeout = 30000)
    fun switcherTest() {
        // 根据上层callback需要运行的线程,创建switcher
        val mainLooper = Looper.getMainLooper()
        val switcher = ThreadSwitcher.newInstance(mainLooper)
        val innerObserver = switcher.generateInnerObserverImpl(ISimpleObserver::class.java)

        var targetLooper: Looper? = null
        val outerObserver = object : ISimpleObserver {
            override fun onInvoke(msg: String?) {
                targetLooper = Looper.myLooper()
                LoggerUtil.w(
                    TAG,
                    "outerObserver run thread isMain=${targetLooper == mainLooper}"
                )
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
        while (targetLooper == null) {
            LoggerUtil.w(TAG, "targetLooper == null sleep 1s")
            Thread.sleep(1000)
        }

        // 验证回调线程与指定线程一致
        Assert.assertEquals(mainLooper, targetLooper)
    }
}