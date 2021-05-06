package org.lynxz.utils.thread

import android.os.Looper
import androidx.annotation.GuardedBy
import org.lynxz.utils.functions.RecookInfo
import org.lynxz.utils.log.LoggerUtil
import org.lynxz.utils.no
import org.lynxz.utils.otherwise
import org.lynxz.utils.reflect.ProxyUtil.IFuncInvokeCallback
import org.lynxz.utils.reflect.ProxyUtil.generateDefaultImplObj
import org.lynxz.utils.reflect.ReflectUtil
import org.lynxz.utils.thread.ThreadSwitcher.Companion.newInstance
import org.lynxz.utils.yes
import java.lang.ref.WeakReference
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * @version 1.1
 * description: 简化回调方法自动切主换线程的工具类:
 * 1. 通过动态代理创建 interface 观察者实例(innerObserver), 用于各sdk,回调在sdk的库线程中
 * 2. 通过 [registerOuterObserver] 注入外部观察者(outerObserver)
 * 3. innerObserver 收到回调后会自动切换线程再触发 outerObserver 对应的方法
 * 使用方法:
 * 1. 创建不同实例:  [newInstance]
 * 2. 创建直接作用于sdk的observer实例(记为 innerObserver): [generateInnerObserverImpl]
 * 3. 获取已生成的 innerObserver(主要用于sdk的观测者移除操作): [getCachedInnerObserver]
 * 4. 外部(通常是UI层)注入的observer: [registerOuterObserver]
 * 5. 获取外部(通常是UI层)注入的observer: [getOuterRegisterObserver]
 * 6. 在指定的线程执行runnable: [runOnTargetThread]
 * 7. 停用转换器: [deActive]
 * 8. 外部自行实现 innerObserver 时:
 * -    a. 注册到缓存中: [addInnerObserverToCache]
 * -    b. 自行进行线程切换: [invokeOuterObserverOnTargetThread]
 */
open class ThreadSwitcher private constructor(targetLooper: Looper = Looper.getMainLooper()) {
    //指定线程handler
    private val targetHandler: BizHandler = BizHandler(targetLooper)

    private val outerLock = Object()

    // 外部注入的观察者,运行在外部指定的线程
    @GuardedBy("outerLock")
    private val outerObserverMap: MutableMap<Class<*>, Any?> = mutableMapOf()

    // 内部生成的观察者,运行在sdk库回调线程
    private val innerObserverMap: MutableMap<String, Any> = mutableMapOf()

    // 正在运行的Runnable个数
    private val activeRunnableCount = AtomicInteger(0)

    // 当前类是否可以用 在 init~uninit 之间为可用
    private val isActive = AtomicBoolean(true)

    /**
     * 获取正在目标线程执行的runnable个数
     */
    fun getActiveRunnableCount() = activeRunnableCount.get()

    fun active() {
        isActive.set(true)
        targetHandler.enable = true
    }

    /**
     * 停止转换器
     */
    fun deActive() {
        isActive.set(false)
        targetHandler.stop()
        LoggerUtil.w(TAG, "deActive end:$this,activeRunnableCount=${activeRunnableCount.get()}")
    }

    fun isActive() = isActive.get()

    /**
     * 清理释放数据
     */
    fun release() {
        deActive()
        innerObserverMap.clear()
        synchronized(outerLock) {
            outerObserverMap.clear()
        }
        activeRunnableCount.set(0)
        LoggerUtil.w(TAG, "release end:$this,activeRunnableCount=${activeRunnableCount.get()}")
    }

    /**
     * 外部添加可用的观察者,最终在主线程中回调
     * 同一类型的observer,仅最后一个生效
     * @param observer outerObserver实例
     * @param clz outerObserver所属类型,通常若参数 [observer] 是通过匿名内部类创建的,则建议明确指定其类型,避免识别错误
     */
    @JvmOverloads
    fun <O : Any> registerOuterObserver(observer: O, clz: Class<out O> = observer.javaClass) {
        synchronized(outerLock) {
            outerObserverMap[clz] = observer
        }
    }

    /**
     * 获取外部注入的观测者
     *
     * @param observerClz 观测者类型
     */
    @Suppress("UNCHECKED_CAST")
    fun <O> getOuterRegisterObserver(observerClz: Class<O>): O? {
        synchronized(outerLock) {
            val outerOb = outerObserverMap[observerClz] as? O
            // 若未找到, 则寻找已注入的父类,兼容匿名内部类情况
            // 但建议注册匿名内部类创建的 outerObserver 时, 明确指定其所属 class 类型
            if (outerOb == null) {
                for ((_, v) in outerObserverMap) {
                    if (observerClz.isInstance(v)) {
                        val o = v as? O
                        outerObserverMap[observerClz] = o
                        return o
                    }
                }
            }
            return outerOb
        }
    }

    /**
     * 从缓存中提取自动生成的指定类型的observer
     */
    @Suppress("UNCHECKED_CAST")
    fun <I> getCachedInnerObserver(observerClz: Class<I>, tag: String? = null) =
        innerObserverMap[getFullClassNameWithTag(observerClz, tag)] as? I

    /**
     * 创建wrapper/presenter等内部使用的observer实现类,回调时自动切换主线程运行相同用户注入的同类型observer的相同方法
     * 若需要在BL回调时进行定制操作(非仅仅切换线程),则自行 new *observer
     * P.S. 每种类型的observer默认只会创建一个,优先从缓存中获取, 若需要创建多个,需要指定其tag值
     *
     * @param recookArgsAction 允许在切换线程前对方法实参进行二次处理
     * @param forceRecreate true-强制重新创建 false-优先从缓存提取
     * @param shouldCached true-创建后需要存入缓存 false-不缓存
     * @param tag 额外的tag信息
     */
    fun <I : Any> generateInnerObserverImpl(
        observerClz: Class<I>,
        recookArgsAction: RecookInfo<Method?, Array<out Any?>?>? = null,
        forceRecreate: Boolean = false,
        shouldCached: Boolean = true,
        tag: String? = null
    ): I {
        // 优先从缓存中提取,若无再创建
        val cachedInnerObserver = getCachedInnerObserver(observerClz)
        if (cachedInnerObserver != null && !forceRecreate) {
            return cachedInnerObserver
        }

        // 创建新的inner observer,并缓存
        return generateInterfaceImpl(observerClz, object : IFuncInvokeCallback {
            override fun onFuncInvoke(
                method: Method,
                returnObj: Any?,
                argGroupIndex: Int,
                args: Array<out Any?>?
            ) {
                isActive.get().no { return }  // 已停止的线程切换器无需执行
                val finalArgs = recookArgsAction?.recook(method, args) ?: args // 对方法实参进行二次处理,如copy等
                val obOuter =
                    getOuterRegisterObserver(observerClz) ?: return // 外部未注入observer时,不用抛线程
                val runnable = Runnable {
                    isActive.get().yes {
                        try {
                            val activeRunnableCount = activeRunnableCount.incrementAndGet()
                            // LoggerUtil.d(TAG, "obUI method start:${method.name},obUui=${obOuter.hashCode()},activeRunnableCount=$activeRunnableCount,${this.hashCode()}")
                            when (args?.size ?: 0) {
                                0 -> method.invoke(obOuter)
                                1 -> method.invoke(obOuter, finalArgs!![0])
                                else -> method.invoke(obOuter, finalArgs)
                            }
                        } catch (e: IllegalAccessException) {
                            e.printStackTrace()
                        } catch (e: InvocationTargetException) {
                            e.printStackTrace()
                        }
                        val activeRunnableCount = activeRunnableCount.decrementAndGet()
                        // LoggerUtil.d(TAG,"obUI method end:${method.name},activeRunnableCount=$activeRunnableCount,${this.hashCode()}")
                    }
                }

                // LoggerUtil.d(TAG, "isNeedSwitch=$isNeedSwitch, method=${method.name}")
                runOnTargetThread(runnable)
            }
        }).also { obInner ->
            shouldCached.yes {
                innerObserverMap[getFullClassNameWithTag(observerClz, tag)] = obInner
            }
        }
    }

    private fun <I> generateInterfaceImpl(
        observerClz: Class<I>,
        callback: IFuncInvokeCallback?
    ): I {
        require(Modifier.isInterface(observerClz.modifiers)) { "请传入接口(observerClz),其他类型无效" }
        return generateDefaultImplObj(observerClz, callback)!!
    }

    /**
     * 若外部自行实现了某个 innerObserver,可将其添加到缓存中, 后续通过 [getCachedInnerObserver] 获取
     * 注意: 外部自行实现时,主线程切换功能也要同步实现
     * */
    fun <I> addInnerObserverToCache(
        innerObserver: I,
        observerClz: Class<I>,
        tag: String? = null
    ) {
        innerObserverMap[getFullClassNameWithTag(observerClz, tag)] = innerObserver!!
    }

    /**
     * 回调 outerObserver 对应的方法
     * */
    fun <I> invokeOuterObserverOnTargetThread(
        observerClz: Class<I>,
        obj: Any = object : Any() {},
        vararg args: Any?
    ) {
        // 获取 obj 所在的方法, 并转换为 observerClz 中的对应 Method 对象
        val enclosingMethod = obj.javaClass.enclosingMethod ?: return
        val methodName = enclosingMethod.name
        val parameterTypes = enclosingMethod.parameterTypes
        val paraSize = parameterTypes.size
        val tMethod: Method?
        tMethod = if (paraSize == 0) {
            ReflectUtil.getDeclaredMethod(observerClz, methodName)
        } else {
            ReflectUtil.getDeclaredMethod(observerClz, methodName, *parameterTypes)
        }

        // 获取 outerObserver
        val outerObserver = getOuterRegisterObserver(observerClz) ?: return

        // 在目标线程中回调 outerObserver
        runOnTargetThread { tMethod?.invoke(outerObserver, *args) }
    }

    /**
     * 切换到当前switcher指定的线程中并执行
     */
    fun runOnTargetThread(runnable: Runnable) = isActive.get().yes {
        isNeedSwitch.yes { targetHandler.post(runnable) } otherwise { runnable.run() }
    }

    // 是否需要切换线程进行触发 outerObserver
    private val isNeedSwitch: Boolean
        get() = Looper.myLooper() != targetHandler.looper

    /**
     * 获取指定类型的 out observer,并封装为runnable, 最终执行 [invoke]
     */
    abstract class OuterObserverRunnable<O>(host: ThreadSwitcher) : Runnable {
        private val observerClz: Class<O> =
            (javaClass.genericSuperclass as ParameterizedType?)!!.actualTypeArguments[0] as Class<O>
        private val wrfHost: WeakReference<ThreadSwitcher> = WeakReference(host)

        abstract operator fun invoke(ob: O)

        override fun run() {
            val switcher = wrfHost.get()
            if (switcher == null || !switcher.isActive.get()) {
                return
            }
            val ob = switcher.getOuterRegisterObserver(observerClz)
            ob?.let { invoke(it) }
        }
    }

    companion object {
        private const val TAG = "ThreadSwitcher"

        /**
         * 创建线程切换器
         *
         * @param targetLooper 最终要运行的线程looper
         */
        @JvmStatic
        fun newInstance(targetLooper: Looper = Looper.getMainLooper()): ThreadSwitcher {
            return ThreadSwitcher(targetLooper).apply {
                LoggerUtil.w(TAG, "ThreadSwitcher created $targetLooper,$this")
            }
        }
    }

    /**
     * 获取对象的完整类名(包名+类名)
     */
    fun getFullClassNameWithTag(obj: Any?, tag: String? = null): String {
        val tTag = if (tag.isNullOrBlank()) "" else tag.trim()
        if (obj == null) return tTag
        return if (obj is Class<*>) {
            "${obj.canonicalName}$tTag"
        } else "${obj.javaClass.canonicalName}$tTag"
    }
}