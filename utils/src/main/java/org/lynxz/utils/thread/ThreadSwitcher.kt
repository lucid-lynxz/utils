package org.lynxz.utils.thread

import android.os.Looper
import androidx.annotation.GuardedBy
import org.lynxz.utils.functions.RecookInfo
import org.lynxz.utils.log.LoggerUtil
import org.lynxz.utils.no
import org.lynxz.utils.otherwise
import org.lynxz.utils.reflect.EnabledResult
import org.lynxz.utils.reflect.FunTraverseUtil
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
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * @version 1.2
 * description: 简化回调方法自动切主换线程的工具类:
 * 1. 通过动态代理创建 interface 观察者实例(innerObserver), 用于各sdk,回调在sdk的库线程中
 * 2. 通过 [registerOuterObserver] 注入外部观察者(outerObserver)
 * 3. innerObserver 收到回调后会自动切换线程再触发 outerObserver 对应的方法
 * 使用方法:
 * 1. 创建不同实例:  [newInstance]
 * 2. 创建直接作用于sdk的observer实例(记为 innerObserver): [generateInnerObserverImpl]
 * 3. 获取已生成的 innerObserver(主要用于sdk的观测者移除操作): [getCachedInnerObserver]
 * 4. 外部(通常是UI层)注入/移除observer: [registerOuterObserver]
 * 5. 获取外部(通常是UI层)注入的observer: [getOuterRegisterObserver]
 * 6. 在指定的线程执行runnable: [runOnTargetThread]
 * 7. 设置某种类型observer的通用过滤条件: [registerGlobalObserverFilterMethod]
 * 8. 停用转换器: [deActive]
 * 9. 外部自行实现 innerObserver 时:
 * -    a. 注册到缓存中: [addInnerObserverToCache]
 * -    b. 自行进行线程切换: [invokeOuterObserverOnTargetThread]
 */
open class ThreadSwitcher private constructor(targetLooper: Looper = Looper.getMainLooper()) {

    /**
     * 需要回调outerObserver的方法信息
     */
    private data class MethodFilterBean(
        val enableMethods: Set<String>? = null, // 允许回调的方法信息(可使用方法名,获取方法签名),空表示不过滤,均可回调
        val disableMethods: Set<String>? = null  // 不允许回调的方法信息(可使用方法名,获取方法签名),空表示不过滤,均不禁止
    ) {
        /**
         * 方法是否需要回调到 outerObserver
         */
        fun canCallback(method: Method): Boolean {
            if (!canCallback(method.name, false)) {
                return false
            }
            if (!canCallback(FunTraverseUtil.getMethodSignature(method), true)) {
                return false
            }
            return true
        }

        // 指定字符串是否表示一个方法签名
        private fun isMethodSigName(name: String) = name.contains("(") && name.contains(")")

        /**
         * 指定的方法名/签名是否可回调到 outerObserver£
         * 需要同时满足在 [enableMethods] 并不在 [disableMethods] 中的才可回调
         * @param name 方法名或者方法签名(参考 FunTraverseUtil.getMethodSignature(...))
         * @param isSigName: [name] 是否表示方法签名
         */
        fun canCallback(name: String, isSigName: Boolean): Boolean {
            val enables = enableMethods?.filter { isSigName == isMethodSigName(it) }
            val disables = disableMethods?.filter { isSigName == isMethodSigName(it) }

            val enableHit = if (enables.isNullOrEmpty()) true else enables.contains(name)
            val disableHit = if (disables.isNullOrEmpty()) false else disables.contains(name)
            return enableHit && !disableHit
        }
    }

    //指定线程handler
    private val targetHandler: BizHandler = BizHandler(targetLooper)

    private val outerLock = Object()

    // 外部注入的观察者,运行在外部指定的线程
    @GuardedBy("outerLock")
    private val outerObserverMap: MutableMap<Class<*>, MutableSet<Any>?> = mutableMapOf()

    // 每个outerObserver允许回调的方法信息,若对应的value为null,则表示不做过滤, 所有方法均回调
    @GuardedBy("outerLock")
    private val outerObserverFilterMap: MutableMap<Any, MethodFilterBean?> = mutableMapOf()

    // 每种 Observer 通用的禁止回调的方法信息
    // 过滤顺序: globalObserverFilterMap -> outerObserverFilterMap 二者均允许回调的时候才会回调
    // 使用场景: 测试时,临时禁用某些接口回调ui层,等效于禁用了后续的逻辑
    @GuardedBy("outerLock")
    private val globalObserverFilterMap: MutableMap<Class<*>, MethodFilterBean> = mutableMapOf()

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

    /**
     * 启用转换器
     * */
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
            outerObserverFilterMap.clear()
            globalObserverFilterMap.clear()
        }
        // activeRunnableCount.set(0)
        LoggerUtil.w(TAG, "release end:$this,activeRunnableCount=${activeRunnableCount.get()}")
    }

    /**
     * 外部添加可用的观察者,最终在主线程中回调
     * 同一类型的observer,仅最后一个生效
     * @param observer outerObserver实例
     * @param clz outerObserver所属类型,通常若参数 [observer] 是通过匿名内部类创建的,则建议明确指定其类型,避免识别错误
     * @param add true-添加observer  false-移除observer
     * @param enableCallbackMethods 允许回调的方法名,若为空,则表示不过滤
     * @param disableCallbackMethods 禁止回调的方法名,若为空,则表示不过滤
     */
    @JvmOverloads
    fun <O : Any> registerOuterObserver(
        observer: O,
        clz: Class<out O> = observer.javaClass,
        add: Boolean = true,
        enableCallbackMethods: Set<String>? = null,
        disableCallbackMethods: Set<String>? = null
    ): Boolean {
        synchronized(outerLock) {
            val set = outerObserverMap[clz] ?: CopyOnWriteArraySet()
            outerObserverMap[clz] = set
            return if (add) {
                if (enableCallbackMethods != null || disableCallbackMethods != null) {
                    outerObserverFilterMap[observer] =
                        MethodFilterBean(enableCallbackMethods, disableCallbackMethods)
                }
                set.add(observer)
            } else {
                outerObserverFilterMap.remove(observer)
                set.isEmpty() || !set.contains(observer) || set.remove(observer)
            }
        }
    }

    /**
     * 注册某种类型的observer允许回调的方法信息
     * @param add: true-添加方法信息 false-删除指定 observerClz 的通用过滤信息
     * @param enableCallbackMethods: [add]=true时有效,表示通用的允许回调的方法信息
     * @param disableCallbackMethods: [add]=true时有效, 表示通用的禁止回调的方法信息
     */
    fun <O : Any> registerGlobalObserverFilterMethod(
        observerClz: Class<out O>,
        add: Boolean = true,
        enableCallbackMethods: Set<String>? = null,
        disableCallbackMethods: Set<String>? = null
    ) {
        synchronized(outerLock) {
            if (add) {
                globalObserverFilterMap[observerClz] =
                    MethodFilterBean(enableCallbackMethods, disableCallbackMethods)
            } else {
                globalObserverFilterMap.remove(observerClz)
            }
        }
    }

    /**
     * 获取外部注入的观测者
     *
     * @param observerClz 观测者类型
     */
    @Suppress("UNCHECKED_CAST")
    fun <O> getOuterRegisterObserver(observerClz: Class<O>): Set<O>? {
        if (outerObserverMap.isEmpty()) {
            return null
        }

        synchronized(outerLock) {
            val outObservers = mutableSetOf<O>()
            outerObserverMap.forEach {
                if (it.key == observerClz || it.key.isAssignableFrom(observerClz)) {
                    it.value?.forEach { ob ->
                        outObservers.add(ob as O)
                    }
                }
            }
            return outObservers
        }
    }

    /**
     * 根据 Observer class类型,获取该类型或其父类型class的全局允许回调方法列表
     */
    private fun <O> getGlobalObserverFilterInfoByClass(observerClz: Class<O>): Set<MethodFilterBean>? {
        if (globalObserverFilterMap.isNullOrEmpty()) return null
        synchronized(outerLock) {
            if (globalObserverFilterMap.isNullOrEmpty()) return null
            val result = mutableSetOf<MethodFilterBean>()
            globalObserverFilterMap.forEach {
                if (it.key == observerClz || it.key.isAssignableFrom(observerClz)) {
                    result.add(it.value)
                }
            }
            return result
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
            ): EnabledResult<Any>? {
                // 由于是通过动态代理生成的interface实例, 额外实现了 toString/hashCode/equals 方法, 这三个可不用回调,此处过滤
                if (excludeCallbackMethods.contains(method.name)) {
                    return null
                }
                isActive.get().no { return null }  // 已停止的线程切换器无需执行
                val finalArgs = recookArgsAction?.recook(method, args) ?: args // 对方法实参进行二次处理,如copy等
                // 外部未注入observer时,不用抛线程
                getOuterRegisterObserver(observerClz)?.forEach out@{ outerObserver ->
                    // 已禁用该类型observer方法的,无需执行
                    val globalFilters =
                        getGlobalObserverFilterInfoByClass(outerObserver::class.java)
                    globalFilters?.forEach {
                        if (!it.canCallback(method)) {
                            return@out
                        }
                    }

                    val filterBean = outerObserverFilterMap[outerObserver]
                    if (filterBean?.canCallback(method) == false) {
                        return@out
                    }

                    val runnable = Runnable {
                        isActive.get().yes {
                            try {
                                activeRunnableCount.incrementAndGet()
                                // val activeRunnableCount = activeRunnableCount.incrementAndGet()
                                // LoggerUtil.d(TAG, "obUI method start:${method.name},obUui=${obOuter.hashCode()},activeRunnableCount=$activeRunnableCount,${this.hashCode()}")
                                when (args?.size ?: 0) {
                                    0 -> method.invoke(outerObserver)
                                    1 -> method.invoke(outerObserver, finalArgs!![0])
                                    else -> method.invoke(outerObserver, finalArgs)
                                }
                            } catch (e: IllegalAccessException) {
                                e.printStackTrace()
                            } catch (e: InvocationTargetException) {
                                e.printStackTrace()
                            }
                            activeRunnableCount.decrementAndGet()
                            // val activeRunnableCount = activeRunnableCount.decrementAndGet()
                            // LoggerUtil.d(TAG,"obUI method end:${method.name},activeRunnableCount=$activeRunnableCount,${this.hashCode()}")
                        }
                    }

                    // LoggerUtil.d(TAG, "isNeedSwitch=$isNeedSwitch, method=${method.name}")
                    runOnTargetThread(runnable)
                }
                return null
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
    fun <I : Any> addInnerObserverToCache(
        innerObserver: I,
        observerClz: Class<I>,
        tag: String? = null
    ) {
        innerObserverMap[getFullClassNameWithTag(observerClz, tag)] = innerObserver
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
        val tMethod: Method = if (paraSize == 0) {
            ReflectUtil.getDeclaredMethod(observerClz, methodName)
        } else {
            ReflectUtil.getDeclaredMethod(observerClz, methodName, *parameterTypes)
        } ?: return

        // 获取 outerObserver
        val outerObservers = getOuterRegisterObserver(observerClz) ?: return

        // 在目标线程中回调 outerObserver
        outerObservers.forEach out@{ ob ->
            val globalFilters = getGlobalObserverFilterInfoByClass(ob!!::class.java)
            globalFilters?.forEach {
                if (!it.canCallback(tMethod)) {
                    return@out
                }
            }

            val filterBean = outerObserverFilterMap[ob]
            if (filterBean?.canCallback(tMethod) == false) {
                return@out
            }

            runOnTargetThread {
                tMethod.invoke(ob, *args)
            }
        }
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
        @Suppress("UNCHECKED_CAST")
        private val observerClz: Class<O> =
            (javaClass.genericSuperclass as ParameterizedType?)!!.actualTypeArguments[0] as Class<O>
        private val wrfHost: WeakReference<ThreadSwitcher> = WeakReference(host)

        abstract operator fun invoke(ob: O)

        override fun run() {
            val switcher = wrfHost.get()
            if (switcher == null || !switcher.isActive.get()) {
                return
            }
            switcher.getOuterRegisterObserver(observerClz)?.forEach { invoke(it) }
        }
    }

    companion object {
        private const val TAG = "ThreadSwitcher"

        // 不需要进行回调的方法,主要是动态代理额外实现的部分接口
        private val excludeCallbackMethods = listOf("equals", "hashCode", "toString")

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