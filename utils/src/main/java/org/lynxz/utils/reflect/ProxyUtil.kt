package org.lynxz.utils.reflect

import javassist.util.proxy.MethodHandler
import javassist.util.proxy.ProxyFactory
import org.lynxz.utils.log.LoggerUtil
import org.lynxz.utils.reflect.ProxyUtil.generateDefaultImplObj
import org.lynxz.utils.reflect.ReflectUtil.generateDefaultTypeValue
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

/**
 * 代理工具类
 * 通过 [generateDefaultImplObj] 创建指定class的代理实现类,支持接口/抽象类和普通类型
 */
object ProxyUtil {
    private const val TAG = "ProxyUtil"

    /**
     * 方法调用信息
     */
    abstract class OnFunInvokeCallback {
        /**
         * @param method        方法信息
         * @param returnObj     返回值,可能null
         * @param argGroupIndex 实参列表序号, 负数表示无效(未知序号或者方法无参)
         * @param args          方法参数列表, 可空
         */
        abstract fun onFuncInvoke(
            method: Method,
            returnObj: Any?,
            argGroupIndex: Int,
            args: Array<out Any?>?
        )
    }

    /**
     * 创建某个接口/普通类(非abstract)的实例
     *
     * @param clz      接口或普通类(非abstract) Class 对象
     * @param callback 对接口或抽象方法有效, 在对应方法被触发时回调该callback
     * @return 若实例化失败, 则返回null
     */
    fun <T> generateDefaultImplObj(clz: Class<T>?, callback: OnFunInvokeCallback?): T? {
        val modifier = clz?.modifiers ?: 0
        return when {
            clz == null -> null
            Modifier.isInterface(modifier) -> generateInterfaceImpl(clz, callback)  // 接口实例化
            Modifier.isAbstract(modifier) -> generateAbsClassInstance(clz, callback)  // 抽象类实例化
            else -> generateClassInstance(clz) // 普通类实例化(此处忽略了enum等类型)
        }
    }

    /**
     * 创建指定接口的默认实现
     *
     * @param clz      需要创建的接口类型class
     * @param callback 接口方法被调用时触发回调
     */
    private fun <T> generateInterfaceImpl(clz: Class<T>, callback: OnFunInvokeCallback?): T? {
        val modifier = clz.modifiers
        if (!Modifier.isInterface(modifier)) {
            return null
        }

        val obj = Proxy.newProxyInstance(
            clz.classLoader, arrayOf<Class<*>>(clz)
        ) { _: Any?, method: Method, args: Array<Any?>? ->
            // 默认实现, 基本类型boolean返回false,其他基本类型返回0, String类型返回"",其他引用类型返回null
            val returnType = method.returnType
            val isPrimitive = returnType.isPrimitive
            var retValue = generateDefaultTypeValue(returnType)
            // LoggerUtil.d(TAG, " generateDefaultInterfaceImpl invoke method:" + method.getName() + ", returnType=" + returnType + ",retValue=" + retValue + ",isPrimitive=" + isPrimitive);
            if ("toString" == method.name) {
//                val retStr = retValue as String?
//                if (retStr.isNullOrBlank()) {
                retValue = "instanceOf:${clz.simpleName}"
//                }
            }
            callback?.onFuncInvoke(method, retValue, -1, args)
            retValue
        }
        return if (clz.isInstance(obj)) obj as T else null
    }

    /**
     * 新建一个普通类(非abstract)对象实例
     *
     * @param clz 需要创建的类型class
     */
    private fun <T> generateClassInstance(clz: Class<T>): T? {
        val modifier = clz.modifiers
        if (Modifier.isInterface(modifier) || Modifier.isAbstract(modifier)) {
            return null
        }
        var returnObj: Any? = null
        val constructors = clz.declaredConstructors
        val size = constructors.size
        for (i in 0 until size) {
            val constructor = constructors[i]
            if (i == size - 1) {
                constructor.isAccessible = true
            }
            // val modifiers = constructor.modifiers
            if (constructor.isAccessible || Modifier.isPublic(modifier)) {
                val parameterTypes = constructor.parameterTypes
                val paraSize = parameterTypes.size
                val args = arrayOfNulls<Any>(paraSize) // 形参值列表
                for (j in 0 until paraSize) {
                    args[j] = generateDefaultTypeValue(parameterTypes[j])
                }
                try {
                    returnObj =
                        if (paraSize == 0) constructor.newInstance() else constructor.newInstance(*args)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                break
            }
        }
        return if (clz.isInstance(returnObj)) returnObj as T? else null
    }

    /**
     * 新建一个抽象类对象实例
     *
     * @param clz 需要创建的抽象类Class
     */
    private fun <T> generateAbsClassInstance(
        clz: Class<T>,
        callback: OnFunInvokeCallback?
    ): T? {
        // 需要导入javassist库: implementation("org.javassist:javassist:3.27.0-GA")
        val modifier = clz.modifiers
        if (!Modifier.isAbstract(modifier)) {
            return null
        }
        val factory = ProxyFactory()
        factory.superclass = clz
        factory.setFilter { method: Method -> Modifier.isAbstract(method.modifiers) }
        val handler =
            MethodHandler { _: Any?, thisMethod: Method, _: Method?, args: Array<Any?>? ->
                // 默认实现, 基本类型boolean返回false,其他基本类型返回0, String类型返回"",其他引用类型返回null
                val returnType = thisMethod.returnType
                val isPrimitive = returnType.isPrimitive
                LoggerUtil.w(
                    TAG,
                    " MethodHandler invoke method:${thisMethod.name}, returnType=$returnType,isPrimitive=$isPrimitive"
                )
                val retObj = generateDefaultTypeValue(returnType)
                callback?.onFuncInvoke(thisMethod, retObj, -1, args)
                retObj
            }
        var returnObj: Any? = null
        try {
            returnObj = factory.create(arrayOfNulls(0), arrayOfNulls(0), handler)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return if (clz.isInstance(returnObj)) returnObj as T? else null
    }
}