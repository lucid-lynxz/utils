package org.lynxz.utils.reflect

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * 构造方法中允许传入一个接口实现类对象,通过动态代理扩展功能
 * 若不传实现类对象,则仅使用动态代理生成指定接口列表的实现类
 *
 * @param realObj 待进行动态代理的接口的子类对象,若非空,则表示使用动态代理进行功能扩展,,忽略 [interfaceClsArray]
 * @param interfaceClsArray 待进行动态代理的接口数组,若为空则表示对 [realObj] 的父接口进行动态代理
 * @param beforeMethodInvokeHook 在接口方法触发前实现
 * @param onMethodInvokedHook 在接口方法被触发时回调,若为空,则返回默认值
 * */
class RecookInvocationHandler(
    var realObj: Any? = null,
    var interfaceClsArray: Array<Class<*>>? = null,
    var beforeMethodInvokeHook: ProxyUtil.IFuncInvokeCallback? = null,
    var onMethodInvokedHook: ProxyUtil.IFuncInvokeCallback? = null
) : InvocationHandler {

    private val objHashCode by lazy { Object().hashCode() }

    /**
     * 创建动态代理实现类对象
     */
    fun newProxyInstance(): Any {
        val realObjInterfaces: Array<Class<*>>? = realObj?.javaClass?.interfaces
        val specificIntfSize = interfaceClsArray?.size ?: 0
        val realObjIntfSize = realObjInterfaces?.size ?: 0

        if (realObjIntfSize == 0 && specificIntfSize == 0) {
            throw IllegalArgumentException("there is not interface to be implemented")
        }

        val tInterfaces = realObjInterfaces ?: interfaceClsArray!!
        return Proxy.newProxyInstance(tInterfaces[0].classLoader, tInterfaces, this)
    }

    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        // 默认实现, 基本类型boolean返回false,其他基本类型返回0, String类型返回"",其他引用类型返回null
        val returnType: Class<*> = method.returnType
        // LoggerUtil.d(TAG, " generateDefaultInterfaceImpl invoke method:" + method.getName() + ", returnType=" + returnType + ",retValue=" + retValue + ",isPrimitive=" + isPrimitive);
        var retValue = when (method.name) {
            "toString" -> "${proxy.javaClass.simpleName}@${hashCode()}"
            "equals" -> this == (args?.get(0) ?: false)
            "hashCode" -> objHashCode
            else -> ReflectUtil.generateDefaultTypeValue(returnType)
        }
        // 在方法触发前回调, 抛弃结果
        beforeMethodInvokeHook?.onFuncInvoke(method, retValue, -1, args)

        // 若调用方有传入接口实现类对象,则回调该对象,并使用返回值作为当前方法的返回值
        realObj?.let {
            retValue = when (method.parameterTypes.size) {
                0 -> method.invoke(realObj)
                1 -> method.invoke(realObj, args?.get(0))
                else -> method.invoke(realObj, args)
            }
        }

        // 接口回调后触发hook操作,并使用其返回值作为当前方法得最终返回值
        onMethodInvokedHook?.onFuncInvoke(method, retValue, -1, args)?.let {
            if (it.enabled && ReflectUtil.isInstanceOf(it.obj, returnType)) {
                retValue = it.obj
            }
        }
        return retValue
    }
}