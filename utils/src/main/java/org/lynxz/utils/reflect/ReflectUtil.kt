package org.lynxz.utils.reflect

import org.lynxz.utils.reflect.ReflectUtil.closeAndroidPDialog
import org.lynxz.utils.reflect.ReflectUtil.generateDefaultTypeValue
import org.lynxz.utils.reflect.ReflectUtil.generateDefaultTypeValueList
import org.lynxz.utils.reflect.ReflectUtil.getAllDeclareFieldsKVMap
import org.lynxz.utils.reflect.ReflectUtil.getAllFieldsKVMap
import org.lynxz.utils.reflect.ReflectUtil.getDeclaredField
import org.lynxz.utils.reflect.ReflectUtil.getDeclaredMethod
import org.lynxz.utils.reflect.ReflectUtil.getRefClass
import org.lynxz.utils.reflect.ReflectUtil.getSpecialDeclaredMethods
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.*
import java.util.regex.Pattern

/**
 * 反射相关工具类
 * 根据类路径反射获取对应类: [getRefClass]
 * 获取类所有属性名及其值: [getAllFieldsKVMap], [getAllDeclareFieldsKVMap]
 * 获取/修改指定类属性:  [getDeclaredField]
 * 获取指定的类方法: [getDeclaredMethod], [getSpecialDeclaredMethods]
 * 获取指定类型对应的默认待测值值: [generateDefaultTypeValue], [generateDefaultTypeValueList]
 * 关闭 android P API兼容弹框: [closeAndroidPDialog]
 */
object ReflectUtil {
    private const val TAG = "AutoReflectUtil"

    /**
     * 反射获取指定类
     *
     * @param classFullPath 类完整路径(包含包名)
     */
    @JvmStatic
    fun getRefClass(classFullPath: String): Class<*>? {
        var clazz: Class<*>? = null
        if (classFullPath.isNotBlank()) {
            try {
                clazz = Class.forName(classFullPath)
            } catch (e: ClassNotFoundException) {
                e.printStackTrace()
            }
        }
        return clazz
    }


    /**
     * 反射获取指定类的成员变量,并按需更新其值
     *
     * @param classFullPath     类完整路径
     * @param declaredFieldName 成员变量名
     * @param updateFiled       是否需要更新该变量的值
     * @param ownObj            updateFiled=true时,更新指定对象的该变量
     * @param newValue          updateFiled=true时,该变量的新值
     */
    @JvmStatic
    fun getDeclaredField(
        classFullPath: String,
        declaredFieldName: String,
        updateFiled: Boolean = false,
        ownObj: Any? = null,
        newValue: Any? = null
    ): Field? {
        var field: Field? = null
        try {
            val clazz = getRefClass(classFullPath)
            field = clazz?.getDeclaredField(declaredFieldName)
            if (updateFiled) {
                field?.isAccessible = true
                field?.set(ownObj, newValue)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return field
    }

    /**
     * 循环遍历反射获取指定的method
     *
     * @param targetObjOrClass   方法所在的类(或其子类)对象或者类的Class
     * @param declaredMethodName 方法名
     * @param parameterTypes     方法参数类型
     */
    @JvmStatic
    fun getDeclaredMethod(
        targetObjOrClass: Any,
        declaredMethodName: String,
        vararg parameterTypes: Class<*>?
    ): Method? {
        var tObjClz: Class<*>? = targetObjOrClass.javaClass
        if (targetObjOrClass is Class<*>) {
            tObjClz = targetObjOrClass
        }
        while (tObjClz != null) {
            try {
                val method = tObjClz.getDeclaredMethod(declaredMethodName, *parameterTypes)
                method.isAccessible = true
                return method
            } catch (e: Exception) {
                e.printStackTrace()
            }
            tObjClz = tObjClz.superclass
        }
        return null
    }

    /**
     * 搜索指定类中的特定类型方法
     *
     * @param targetObjOrClass  要搜索的方法所在的类,不支持 Class 类本身
     * @param methodNamePattern 方法名中的关键字信息,正则表达式, 如 "^get.*" ,大小写敏感
     * @param returnTypeName    方法返回类型,如 "void", 大小写敏感, 若传入null表示不做验证
     * @param containModifiers  方法权限修饰符,要求全部匹配,参考 [Modifier] 类 ,如 Modifier.PUBLIC
     */
    @JvmStatic
    fun getSpecialDeclaredMethods(
        targetObjOrClass: Any,
        methodNamePattern: String,
        returnTypeName: String?,
        vararg containModifiers: Int
    ): List<Method> {
        val methodList: MutableList<Method> = ArrayList()
        var tObjClz: Class<*> = targetObjOrClass.javaClass
        if (targetObjOrClass is Class<*>) {
            tObjClz = targetObjOrClass
        }
        if (tObjClz != Any::class.java) {
            try {
                val declaredMethods = tObjClz.declaredMethods
                for (method in declaredMethods) {
                    // 匹配方法名称
                    val name = method.name
                    if (!Pattern.matches(methodNamePattern, name)) {
                        continue
                    }

                    // 验证返回类型是否符合要求
                    if (!returnTypeName.isNullOrBlank()) {
                        val returnType = method.returnType
                        val actualReturnTypeName = returnType.simpleName
                        if (actualReturnTypeName != returnTypeName) {
                            continue
                        }
                    }

                    // 验证权限修饰符是否符合要求
                    val actualModifiers = method.modifiers
                    val containModifiersLen = containModifiers.size
                    var contain = true // 是否包含所有指定的权限修饰符
                    for (i in 0 until containModifiersLen) {
                        val containModifier = containModifiers[i]
                        contain = actualModifiers and containModifier != 0
                        if (!contain) {
                            break
                        }
                    }
                    if (contain) {
                        methodList.add(method)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return methodList
    }

    /**
     * 反射获取指定类的所有成员变量名和值(不包含父类变量)
     *
     * @param classFullPath 类完整路径
     */
    @JvmStatic
    fun getAllDeclareFieldsKVMap(classFullPath: String) =
        HashMap<String, Any?>().apply {
            getRefClass(classFullPath)?.let { clz ->
                clz.declaredFields.forEach { field ->
                    field.isAccessible = true
                    try {
                        put(field.name, field.get(clz))
                    } catch (ignore: IllegalAccessException) {
                    }
                }
            }
        }

    /**
     * 反射获取指定类的所有成员变量名和值(包含从父类继承的变量)
     *
     * @param classFullPath 类完整路径
     */
    @JvmStatic
    fun getAllFieldsKVMap(classFullPath: String) =
        HashMap<String, Any?>().apply {
            getRefClass(classFullPath)?.let { clz ->
                clz.fields.forEach { field ->
                    field.isAccessible = true
                    try {
                        put(field.name, field.get(clz))
                    } catch (ignore: IllegalAccessException) {
                    }
                }
            }
        }

    /**
     * 在 android P 可能会弹API兼容弹框, 每次app启动前可反射禁用掉
     * 目前在espresso用例执行前禁用, 避免弹框影响UI操作
     */
    @JvmStatic
    fun closeAndroidPDialog() {
        try {
            val aClass = Class.forName("android.content.pm.PackageParser\$Package")
            val declaredConstructor = aClass.getDeclaredConstructor(String::class.java)
            declaredConstructor.isAccessible = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            val cls = Class.forName("android.app.ActivityThread")
            val declaredMethod = cls.getDeclaredMethod("currentActivityThread")
            declaredMethod.isAccessible = true
            val activityThread = declaredMethod.invoke(null)
            val mHiddenApiWarningShown = cls.getDeclaredField("mHiddenApiWarningShown")
            mHiddenApiWarningShown.isAccessible = true
            mHiddenApiWarningShown.setBoolean(activityThread, true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 获取指定类型对应的单个默认值,基本类型返回 false 或  等 , 其他引用类型返回空
     */
    @JvmStatic
    fun generateDefaultTypeValue(returnType: Class<*>): Any? {
        val objects = generateDefaultTypeValueList(returnType)
        return if (objects.size >= 1) objects[0] else null
    }

    /**
     * 获取指定类型对应的多个默认值,基本类型返回 false/true 或 0/-1/9999 等 , 其他引用类型返回空及默认实例
     */
    @JvmStatic
    fun generateDefaultTypeValueList(returnType: Class<*>) =
        mutableListOf<Any?>().apply {
            when (returnType) {
                Void.TYPE -> add(null)
                Boolean::class.java -> {
                    add(false)
                    add(true)
                }
                Byte::class.java -> {
                    add(0.toByte())
                    add(Byte.MAX_VALUE)
                    add(Byte.MIN_VALUE)
                }
                Short::class.java -> {
                    add(0.toShort())
                    add(Short.MAX_VALUE)
                    add(Short.MIN_VALUE)
                }
                Char::class.java -> {
                    add(0.toChar())
                    add(Char.MAX_VALUE)
                    add(Char.MIN_VALUE)
                }
                Int::class.java -> {
                    add(0)
                    add(Int.MAX_VALUE)
                    add(Int.MIN_VALUE)
                }
                Long::class.java -> {
                    add(0L)
                    add(Long.MAX_VALUE)
                    add(Long.MIN_VALUE)
                }
                Float::class.java -> {
                    add(0f)
                    add(Float.MAX_VALUE)
                    add(Float.MIN_VALUE)
                }
                Double::class.java -> {
                    add(0.0)
                    add(Double.MAX_VALUE)
                    add(Double.MIN_VALUE)
                }
                String::class.java -> {
                    add(null)
                    add("")
                    add("测试wsdf$%&【。。.】；$‘：’")
                }
                else -> {
                    add(null)
                    ProxyUtil.generateDefaultImplObj(returnType, null)?.let {
                        add(it)
                    }
                }
            }
        }
}

