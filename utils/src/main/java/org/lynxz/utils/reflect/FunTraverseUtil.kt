package org.lynxz.utils.reflect

import android.text.TextUtils
import org.lynxz.utils.log.LoggerUtil
import org.lynxz.utils.reflect.FunTraverseUtil.Companion.getMethodSignature
import org.lynxz.utils.reflect.ProxyUtil.OnFunInvokeCallback
import org.lynxz.utils.reflect.ReflectUtil.generateDefaultTypeValue
import org.lynxz.utils.reflect.ReflectUtil.generateDefaultTypeValueList
import org.lynxz.utils.reflect.ReflectUtil.getSpecialDeclaredMethods
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*
import java.util.regex.Pattern

/**
 * @version 1.2
 * 遍历运行指定对象的public方法(非$开头)工具类
 * -    1. 方法执行后已默认打印日志
 * -    2. 不自动执行父类方法, 需要指定方法所在类: targetClz,默认为当前对象类的Class
 *
 * <pre>
 * FuncTraverseUtil.create(mainObj) // 必传对象
 *      .setTargetClz(Main.class) // 可选, 设置待验证方法所在的Class
 *      .setMethodNamePattern(".*") // 可选, 待测试方法名匹配规则, 正则
 *      .addExcludeMethodNames("release") // 可选, 不进行测试的方法名,可多次添加或一次添加多条
 *      .setSpecialMethodList(null) // 可选, 指定待测试的方法列表,此时 setRandomSortMethods 无效
 *      .setRandomSortMethods(false) // 可选, 是否随机运行方法,默认为false
 *      .addArgTypeValue(Int::class.java, 666) // 可选, 额外添加参数类型对应的值, 可多条,内置给定了默认值
 *      .enableDefaultMultiArtTypeValues() // 可选, 启用内置的多默认值
 *      .setMaxArgValueGroupSize(3) // 可选, 候选形参值组合过多时,可设置最大组合数, 负数表示不限制,默认为10个
 *      .setMethodArgGroupIndexMap(null) // 可选, 设置某些方法调用时所用的实参组合序号列表
 *      .setMethodArgGroupIndexList("xxxx",null) // 可选,单独设置某方法调用时所使用的的实参组合序号列表
 *      .addBeforeFuncInvokeAction(null) // 可选,可多条,方法执行前回调
 *      .addAfterFuncInvokeAction(null) // 可选,可多条,方法执行后回调
 *      .invokeAllPublic(); // 必须, 触发执行符合条件的所有方法
 * </pre>
 *
 *
 * 通过 [validMethodList] 获取待执行的方法列表
 * 通过 [invokeMethodSignatureList] 获取待执行的方法签名列表, 用于持久化
 * 通过 [getMethodSignature] 获取方法签名
 */
class FunTraverseUtil<T> private constructor(private val targetObj: T) {
    // 指定对象的 Class,只会处理该Class中的public方法
    private var targetClz: Class<in T>

    // 可执行的方法信息过滤条件
    // 方法匹配条件1: 方法名正则匹配,默认全部符合($开头及非public的除外)
    private var methodNamePattern = ".*" // 方法名正则匹配条件, 符合条件的才可能执行,默认都符合

    // 方法匹配条件2: 过滤掉指定的方法名
    private val excludeMethodNames = HashSet<String>() // 不执行的方法名,仅包括方法名,不包含括号和形参类型
    private val excludeMethodNamesWithParaType = HashSet<String>() // 不执行的方法名,括方法名+括号+形参类型列表

    // 方法匹配条件3-1:用户指定的待验证的方法(按顺序,此时忽略 randomSortMethods 属性)
    private var specialMethodList: List<String>? = null

    // 方法匹配条件3-2: 是否随机排序待执行的方法
    private var randomSortMethods = false

    var invokeMethodList: List<Method?>? = null  // 最终符合条件的方法列表
        private set

    private val argTypeValueMap: HashMap<Class<*>?, MutableList<Any?>?>  // 参数类型对应的数据
    private var enableMultiArgTypeValues = false // 是否启用内置多类型实例功能(引用类型会自动创建 null 和 具体实例对象)

    // 多参数方法, 每个参数又有多个可能值时, 排列组合数量可能太大, 通过本属性进行限制, 提取其中部分排列参数值进行验证
    // 是按顺序提取还是乱序,根据 randomSortMethods 确定
    // 负数表示不限制
    private var maxArgValueGroupSize = 10

    /**
     * 用于回放测试
     * key: method signature 通过 [.getMethodSignature] 生成
     * value: 该方法所使用的的形参组合序号列表
     */
    private var methodArgGroupIndexMap = mutableMapOf<String, MutableList<Int>?>()
    private val beforeInvokeActionSet = HashSet<OnFunInvokeCallback>() // 方法执行前触发调用
    private val afterInvokeActionSet = HashSet<OnFunInvokeCallback>() // 方法执行后触发调用

    init {
        targetClz = targetObj!!::class.java as Class<T>
        argTypeValueMap = generateDefaultTypeValueMap() // 默认每种类型形参只提供一个默认值

        // 可在此处添加默认不执行的方法
//        excludeMethodNames.add("xx");
    }

    /**
     * 设置待遍历的Class信息
     */
    fun setTargetClz(targetClz: Class<in T>): FunTraverseUtil<T> {
        this.targetClz = targetClz
        return this
    }

    /**
     * 设置可执行的方法名正则匹配表达式
     */
    fun setMethodNamePattern(methodNamePattern: String): FunTraverseUtil<T> {
        if (!TextUtils.isEmpty(methodNamePattern)) {
            try {
                Pattern.compile(methodNamePattern)
                this.methodNamePattern = methodNamePattern
            } catch (e: Exception) {
                e.printStackTrace()
                LoggerUtil.w(TAG, "setMethodNamePattern fail as pattern exception:${e.message}")
            }
        }
        return this
    }

    /**
     * 添加不执行的方法名信息, 支持两种格式:
     * 1. 仅提供方法名, 如:  test  , 则表示其重载方法也会被过滤
     * 2. 完整方法名, 如:  test(int,float) 则仅过滤满足该形参列表的方法,其他重载方法不影响, 空格会自动剔除
     */
    fun addExcludeMethodNames(vararg excludeMethodNames: String?): FunTraverseUtil<T> {
        val len = excludeMethodNames.size
        for (i in 0 until len) {
            var name = excludeMethodNames[i]
            if (TextUtils.isEmpty(name)) {
                continue
            }
            name = name!!.replace("\n", "").replace("\t", "").replace(" ", "")
            if (name.contains("(")) { // 表示用户限定了方法名和形参列表
                excludeMethodNamesWithParaType.add(name)
            } else { // 用户仅提供了方法名, 则表示其重载方法也会被一并过滤
                this.excludeMethodNames.add(name)
            }
        }
        //        if (excludeMethodNames != null && excludeMethodNames.length > 0) {
//            this.excludeMethodNames.addAll(Arrays.asList(excludeMethodNames).subList(0, excludeMethodNames.length));
//        }
        return this
    }

    /**
     * 增加指定类型对应的默认值
     * 同一个 typeClz,可添加多个值
     *
     * @param values 注意显示指定未对应类型
     */
    fun addArgTypeValue(
        typeClz: Class<*>,
        vararg values: Any?
    ): FunTraverseUtil<T> {
        addTypeValue(argTypeValueMap, typeClz, *values)
        return this
    }

    /**
     * 用户指定的待执行方法列表及顺序
     *
     * @param methodList 方法签名列表, 与 [getMethodSignatureInner] 相匹配, 默认为null,表示无效
     */
    fun setSpecialMethodList(methodList: List<String>?): FunTraverseUtil<T> {
        specialMethodList = methodList
        return this
    }

    /**
     * 启用内置多类型值
     * 未启用时,默认仅提供一个值
     */
    fun enableDefaultMultiArtTypeValues(): FunTraverseUtil<T> {
        initDefaultArgTypeValueMap()
        enableMultiArgTypeValues = true
        defaultArgTypeValueMap?.forEach { entry ->
            entry.value?.forEach { v ->
                addTypeValue(argTypeValueMap, entry.key!!, v)
            }
        }
        return this
    }

    /**
     * 形参值组合过多时,可通过本属性进行限制, 指定测试的最大组合数,负数表示不限制
     */
    fun setMaxArgValueGroupSize(maxArgValueGroupSize: Int): FunTraverseUtil<T> {
        this.maxArgValueGroupSize = maxArgValueGroupSize
        return this
    }

    /**
     * 设置多个方法测试时所使用的的形参组合序号列表
     * 若最终执行时, methodArgGroupIndexMap 不包含某个方法,则直接随机运行
     */
    fun setMethodArgGroupIndexMap(methodArgGroupIndexMap: MutableMap<String, MutableList<Int>?>?): FunTraverseUtil<T> {
        if (methodArgGroupIndexMap != null) {
            this.methodArgGroupIndexMap = methodArgGroupIndexMap
        }
        return this
    }

    /**
     * 设置某个方法测试时所使用的的形参组合序号列表
     *
     * @param methodSignature         方法签名, 通过 [.getMethodSignature] 获取, 确保唯一性
     * @param methodArgGroupIndexList 该方法所使用的实参组合序号, 若为null,则表示随机
     */
    fun setMethodArgGroupIndexList(
        methodSignature: String,
        methodArgGroupIndexList: MutableList<Int>?
    ): FunTraverseUtil<T> {
        methodArgGroupIndexMap[methodSignature] = methodArgGroupIndexList
        return this
    }

    /**
     * 是否随机执行所有方法
     */
    fun setRandomSortMethods(enable: Boolean): FunTraverseUtil<T> {
        randomSortMethods = enable
        return this
    }

    /**
     * 设置方法执行前hook动作
     */
    fun addBeforeFuncInvokeAction(beforeFuncInvokeAction: OnFunInvokeCallback?): FunTraverseUtil<T> {
        if (beforeFuncInvokeAction != null) {
            beforeInvokeActionSet.add(beforeFuncInvokeAction)
        }
        return this
    }

    /**
     * 设置方法执行后hook操作
     */
    fun addAfterFuncInvokeAction(afterFuncInvokeAction: OnFunInvokeCallback?): FunTraverseUtil<T> {
        if (afterFuncInvokeAction != null) {
            afterInvokeActionSet.add(afterFuncInvokeAction)
        }
        return this
    }

    /**
     * 获取待验证的方法签名列表
     */

    val invokeMethodSignatureList: List<String?>
        get() {
            val methodList = validMethodList
            val size = methodList!!.size
            val result: MutableList<String?> = ArrayList()
            for (i in 0 until size) {
                val method = invokeMethodList!![i]
                result.add(getMethodSignatureInner(method))
            }
            return result
        }// 随机排序待测试的方法列表(有点类似monkey,进行随机组合,发现未知bug)// 调用方已指定了方法执行顺序,则按指定顺序生成方法列表// 仅方法名和形参列表,如: test(int,float)// 完整方法限定名, 如: static_test(int,float)_void// 过滤掉不执行的方法,比如对象内存的释放: delete 等

    /**
     * 获取符合条件的待测方法列表
     */
    val validMethodList: List<Method?>?
        get() {
            if (invokeMethodList != null) {
                return invokeMethodList
            }
            val resultMethodList: MutableList<Method?> = ArrayList()
            val allGetMethods =
                getSpecialDeclaredMethods(targetClz, methodNamePattern, null, Modifier.PUBLIC)
            val signatureMethodMap: MutableMap<String?, Method> = HashMap()
            for (method in allGetMethods) {
                val methodName = method.name
                // 过滤掉不执行的方法,比如对象内存的释放: delete 等
                if (excludeMethodNames.contains(methodName)
                    || excludeMethodNames.contains(methodName.toLowerCase(Locale.getDefault()))
                    || methodName.startsWith("$")
                ) {
                    continue
                }
                val sig = getMethodSignatureInner(method) // 完整方法限定名, 如: static_test(int,float)_void
                val pureMethodSig = sig.trim { it <= ' ' }
                    .replace("static_", "").split("_")
                    .toTypedArray()[0] // 仅方法名和形参列表,如: test(int,float)
                if (excludeMethodNamesWithParaType.contains(pureMethodSig)
                    || excludeMethodNamesWithParaType.contains(pureMethodSig.toLowerCase(Locale.getDefault()))
                ) {
                    continue
                }
                resultMethodList.add(method)
                signatureMethodMap[sig] = method
            }
            if (resultMethodList.isEmpty()) {
                return resultMethodList
            }
            if (specialMethodList != null && specialMethodList!!.isNotEmpty()) { // 调用方已指定了方法执行顺序,则按指定顺序生成方法列表
                resultMethodList.clear()
                for (sig in specialMethodList!!) {
                    val method = signatureMethodMap[sig]
                    if (method != null) {
                        resultMethodList.add(method)
                    }
                }
            } else if (randomSortMethods) {  // 随机排序待测试的方法列表(有点类似monkey,进行随机组合,发现未知bug)
                resultMethodList.shuffle()
            }
            invokeMethodList = resultMethodList
            return invokeMethodList
        }

    // 缓存method及其对应的签名字符串
    private val methodSignatureMap: MutableMap<Method?, String?> = HashMap()

    /**
     * 拼接生成方法签名字符串,用于持久化, 格式为 [static_]methodName([para1,para2...])_returnType
     * 优先从缓存中提取,若未找到再生成
     */
    private fun getMethodSignatureInner(method: Method?): String {
        var sig = methodSignatureMap[method]
        if (sig != null) {
            return sig
        }
        sig = if (method == null) "unknown" else getMethodSignature(method)
        methodSignatureMap[method] = sig
        return sig
    }

    /**
     * 根据所给参数类型顺序, 生成可能的形参值组合列表
     * P.S. 返回固定的排列组合结果, 不做随机
     *
     * @param parameterTypes 形参类型数组(非空,长度自行确保大于0返回有效形参值组合列表)
     * @return 形参值组合列表, 每个元素表示一套形参值
     */
    private fun generateArgValuesGroupList(parameterTypes: Array<Class<*>>): List<Array<Any?>>? {
        val argsValueList: MutableList<Array<Any?>> = ArrayList() // 参数类型对应的待测试值列表
        val paraSize = parameterTypes.size
        if (paraSize == 0) {
            return argsValueList
        }

        // 查找形参类型对应的值列表
        for (type in parameterTypes) {
            var list = argTypeValueMap[type]
            if (list == null) {
                if (enableMultiArgTypeValues) {
                    list = generateDefaultTypeValueList(type)
                } else {
                    list = ArrayList()
                    list.add(generateDefaultTypeValue(type))
                }
                argTypeValueMap[type] = list
            }
            val size = list.size
            var objects: Array<Any?>
            if (size > 0) {
                objects = arrayOfNulls(size)
                for (i in 0 until size) {
                    objects[i] = list[i]
                }
            } else {
                objects = arrayOf(generateDefaultTypeValue(type))
            }
            argsValueList.add(objects)
        }

        // 生成所有可能的参数值组合
        return combination(argsValueList, 0, null)
    }

    /**
     * 执行所有符合条件的方法列表
     * @param beforeAction Runnable? 运行前的前置操作
     */
    fun invokeAllPublic(beforeAction: Runnable? = null): FunTraverseUtil<T> {
        beforeAction?.run()
        val allGetMethods = validMethodList ?: return this
        LoggerUtil.w(
            TAG,
            "== start invoke ${targetClz.simpleName} all public methods(${allGetMethods.size}个) ==="
        )
        for (method in allGetMethods) {
            val parameterTypes = method!!.parameterTypes // 形参类型列表
            val paraSize = parameterTypes.size // 形参个数
            var invokeObj: Any? = targetObj
            val modifiers = method.modifiers
            if (Modifier.isStatic(modifiers)) { // 若是静态方法,则invoke时首个参数传null
                invokeObj = null
            }
            if (paraSize == 0) { // 无形参
                invokeInner(invokeObj, method, -1)
            } else {
                val argGroupList = generateArgValuesGroupList(parameterTypes)
                val argGroupSize = argGroupList!!.size // 备选形参值组合总数量

                // 若指定了方法对应的实参组合序号,则根据序号提取对应实参并执行
                val methodSignature = getMethodSignature(method)
                var argGroupIndexList = methodArgGroupIndexMap[methodSignature]
                var argGroupIndexListSize = argGroupIndexList?.size ?: 0

                // 用户未指定实参序号列表,则直接重新进行随机
                if (argGroupIndexListSize == 0) {
                    argGroupIndexList = ArrayList()
                    for (i in 0 until argGroupSize) {
                        argGroupIndexList.add(i)
                    }
                    Collections.shuffle(argGroupIndexList) // 对参数组合的序号进行随机
                    if (argGroupSize > maxArgValueGroupSize && maxArgValueGroupSize > 0) { // 按需截取指定个数的参数组合
                        argGroupIndexList = argGroupIndexList.subList(0, maxArgValueGroupSize)
                    }
                }
                argGroupIndexListSize = argGroupIndexList!!.size

                // 根据确定的实参组合的序号列表,逐个提取实参组合,并运行方法
                for (i in 0 until argGroupIndexListSize) {
                    val index = argGroupIndexList[i]
                    if (index >= argGroupSize || index < 0) {
                        continue
                    }
                    val args = argGroupList[index]
                    invokeInner(invokeObj, method, index, *args)
                }
            }
        }
        return this
    }

    /**
     * 执行指定的方法
     *
     * @param obj           方法所在对象
     * @param method        待执行方法
     * @param argGroupIndex 本次运行所使用的实参组合序号, 负数表示无效
     * @param args          本次运行所使用的的实参组合具体信息, null 表示该方法无形参列表需要传
     */
    private fun invokeInner(obj: Any?, method: Method, argGroupIndex: Int, vararg args: Any?) {
        try {
            for (beforeFuncInvokeAction in beforeInvokeActionSet) {
                runHookAction(beforeFuncInvokeAction, method, null, argGroupIndex, args)
            }
            val paraSize = args.size
            val result = if (paraSize == 0) { // 无参
                printMethodInvokeInfo(method, "_BInvokeTag0_$argGroupIndex", true, *args)
                method.invoke(obj)
            } else { // 有参
                printMethodInvokeInfo(method, "_BInvokeTag1_$argGroupIndex", true, *args)
                method.invoke(obj, *args)
            }

            for (afterFuncInvokeAction in afterInvokeActionSet) {
                runHookAction(afterFuncInvokeAction, method, result, argGroupIndex, args)
            }
            printMethodInvokeInfo(method, result, false, *args)
        } catch (e: IllegalAccessException) {
            // e.printStackTrace();
            for (afterFuncInvokeAction in afterInvokeActionSet) {
                runHookAction(afterFuncInvokeAction, method, e, argGroupIndex, null)
            }
            printMethodInvokeInfo(method, e, false, *args)
            throw RuntimeException(e) // 重新抛出异常,避免崩溃被拦截导致未能及时发现
        } catch (e: IllegalArgumentException) {
            for (afterFuncInvokeAction in afterInvokeActionSet) {
                runHookAction(afterFuncInvokeAction, method, e, argGroupIndex, null)
            }
            printMethodInvokeInfo(method, e, false, *args)
            throw RuntimeException(e)
        } catch (e: InvocationTargetException) {
            for (afterFuncInvokeAction in afterInvokeActionSet) {
                runHookAction(afterFuncInvokeAction, method, e, argGroupIndex, null)
            }
            printMethodInvokeInfo(method, e, false, *args)
            throw RuntimeException(e)
        }
    }

    private fun runHookAction(
        action: OnFunInvokeCallback?,
        method: Method,
        returnObj: Any?,
        argGroupIndex: Int,
        args: Array<out Any?>?
    ) {
        action?.onFuncInvoke(method, returnObj, argGroupIndex, args)
    }

    /**
     * 打印法方法执行结果
     * @param method Method?
     * @param result Any? method执行结果
     * @param debugLogLevel Boolean true-debug日志 false-warn日志界别
     * @param args Array<out Any?>
     */
    private fun printMethodInvokeInfo(
        method: Method?,
        result: Any?,
        debugLogLevel: Boolean,
        vararg args: Any?
    ) {
        // 拼接生成方法签名字符串,格式为 [static] methodName([para1,para2...]):returnType
        val methodSignature = getMethodSignatureInner(method)

        // 添加形参实际值信息
        val sbArg = StringBuilder(100)
        val argSize = args.size
        if (argSize > 0) {
            sbArg.append(",args=")
            for (i in 0 until argSize) {
                val arg = args[i]
                sbArg.append(arg)
                if (i != argSize - 1) {
                    sbArg.append(",")
                }
            }
        }

        val resultMsg = if (result is Exception) {
            "Occur exception:${result.message}"
        } else {
            "$result"
        }

        if (debugLogLevel) {
            LoggerUtil.d(TAG, "$methodSignature,result=$resultMsg$sbArg")
        } else {
            LoggerUtil.w(TAG, "$methodSignature,result=$resultMsg$sbArg")
        }
    }

    /**
     * 生成各形参所有可能值的组合列表
     * 每个元素都是一组可用参数
     */
    private fun combination(
        srcDataList: List<Array<Any?>>,
        index: Int,
        resultList: List<Array<Any?>>?
    ): List<Array<Any?>>? {
        if (index == srcDataList.size) {
            return resultList
        }
        val resultList0 = mutableListOf<Array<Any?>>()
        if (index == 0) {
            val objArr = srcDataList[0]
            for (obj in objArr) {
                resultList0.add(arrayOf(obj))
            }
        } else {
            val objArr = srcDataList[index]
            for (objArr0 in resultList!!) {
                for (obj in objArr) {
                    //复制数组并扩充新元素
                    val objArrCopy = arrayOfNulls<Any>(objArr0.size + 1)
                    System.arraycopy(objArr0, 0, objArrCopy, 0, objArr0.size)
                    objArrCopy[objArrCopy.size - 1] = obj

                    //追加到结果集
                    resultList0.add(objArrCopy)
                }
            }
        }
        return combination(srcDataList, index + 1, resultList0)
    }

    companion object {
        private const val TAG = "FuncTraverseUtil"

        // 内置的形参默认值信息, 默认list只有一个元素
        private var defaultArgTypeValueMap: HashMap<Class<*>?, MutableList<Any?>?>? = null

        @JvmStatic
        fun <T> create(targetObj: T): FunTraverseUtil<T> {
            initDefaultArgTypeValueMap()
            return FunTraverseUtil(targetObj)
        }

        /**
         * 创建并初始化类型值
         */
        private fun initDefaultArgTypeValueMap() {
            if (defaultArgTypeValueMap == null) {
                defaultArgTypeValueMap = HashMap()
                val clzArr = arrayOf<Class<*>?>(
                    Boolean::class.javaPrimitiveType,
                    Short::class.javaPrimitiveType,
                    Byte::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Long::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                    Double::class.javaPrimitiveType,
                    String::class.java
                )
                for (clz in clzArr) {
                    val values = generateDefaultTypeValueList(
                        clz!!
                    )
                    defaultArgTypeValueMap?.let {
                        for (value in values) {
                            addTypeValue(it, clz, value)
                        }
                    }
                }
            }
        }

        /**
         * 额外增加指定类型对应的默认值
         * 同一个 typeClz,可添加多个值
         * 然后并通过 [.enableDefaultMultiArtTypeValues] 来启用,否则
         */
        fun addMultiDefaultArgTypeValues(typeClz: Class<*>, vararg values: Any?) {
            initDefaultArgTypeValueMap()
            defaultArgTypeValueMap?.let {
                addTypeValue(it, typeClz, *values)
            }
        }

        fun getMethodSignature(method: Method?): String {
            if (method == null) {
                return ""
            }
            // 返回类型名
            val returnTypeName = method.returnType.simpleName
            // 获取形参信息字符串
            val parameterTypes = method.parameterTypes // 形参类型列表
            // val paraSize = parameterTypes.size // 形参个数
            val sbParaInfo = StringBuilder(30)
            sbParaInfo.append("(")
            for (parameterType in parameterTypes) {
                sbParaInfo.append(parameterType.simpleName).append(",")
            }
            sbParaInfo.append(")")
            val paraInfoStr = sbParaInfo.toString().replace(",)", ")")

            // 静态方法标志位判断
            val modifiers = method.modifiers
            val staticFlag = if (Modifier.STATIC and modifiers != 0) "static_" else ""

            // 拼接生成方法签名字符串,格式为 [static] methodName([para1,para2...]):returnType
            return "$staticFlag${method.name}${paraInfoStr}_$returnTypeName"
        }

        /**
         * @return 生成默认值信息map, 每种类型可对应多个值, 默认提供了基本类型值(0,false)
         */
        private fun generateDefaultTypeValueMap() = HashMap<Class<*>?, MutableList<Any?>?>().apply {
            arrayOf<Class<*>>(
                Boolean::class.java,
                Short::class.java,
                Byte::class.java,
                Int::class.java,
                Long::class.java,
                Float::class.java,
                Double::class.java,
                String::class.java
            ).forEach { clz ->
                addTypeValue(this, clz, generateDefaultTypeValue(clz))
            }
        }

        /**
         * 给指定类型添加默认的待测试值
         */
        private fun addTypeValue(
            map: HashMap<Class<*>?, MutableList<Any?>?>,
            typeClz: Class<*>,
            vararg values: Any?
        ) {
            val list = map[typeClz] ?: mutableListOf()
            map[typeClz] = list

            // 指定对象不存在是添加
            values.forEach { item ->
                val index = list.indexOf(item)
                if (index < 0) {
                    list.add(item)
                }
            }
        }
    }
}