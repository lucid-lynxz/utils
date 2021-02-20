package org.lynxz.utils.reflect

import org.lynxz.utils.FileUtil
import org.lynxz.utils.convert2Int
import java.io.File
import java.lang.reflect.Method

/**
 * 用于持久化方法遍历相关信息: 签名列表, 方法所用实参信息等
 * 作为 [FunTraverseUtil] 的配套工具类, 使用代码见下方
 *
 * [FunTraversePersistenceUtil]  来创建实例,并自动从本地日志中提取信息,以便继续执行后续方法
 * [pendingInvokeMethodSignatureList] 来获取待执行的方法列表,若返回null,则表示之前未执行过
 * [writeMethodList2Log] 写入新的方法签名列表到日志文件中
 * [writeMethodArgIndex2Log] 写入新的方法签名及其对应的实参index调用数据到日志中
 * 其他:
 * 1. 回放fuzz测试时,将已存在的  fun_traverse_log/ 目录复制到 [parentLogFolderPath] 目录即可
 * 2. 示例代码:
 * <pre>
 *   // 初始化日志工具辅助类
 *   val perUtil = FunTraversePersistenceUtil(SomeBean::class.java, "/sdcard/result/")
 *
 *   FunTraverseUtil.create(SomeBean())
 *       .enableDefaultMultiArtTypeValues()
 *       .setSpecialMethodList(perUtil.pendingInvokeMethodSignatureList) // 使用日志中的记录作为回放依据
 *       .setMethodArgGroupIndexMap(perUtil.methodArgGroupIndexMapFromLog) // 使用日志记录的参数组合
 *       .also { // 可选,清空无用数据
 *           perUtil.writeMethodListToLog(it.validMethodList)
 *           .deleteLogFile(FunTraversePersistenceUtil.LOG_METHOD_ARG_INDEX)
 *       }
 *       .addBeforeFuncInvokeAction(object : ProxyUtil.OnFunInvokeCallback() {
 *           override fun onFuncInvoke(method: Method, returnObj: Any?, argGroupIndex: Int, args: Array<out Any?>?) {
 *           // 每次方法执行前记录日志: 所用实参组合序号, 当前方法签名,方便后续回放
 *           // 设置当前正在进行方法遍历的method签名并写入到日志
 *           val methodSignature: String = FunTraverseUtil.getMethodSignature(method)
 *
 *           // 尝试记录当前方法调用顺序及其所用参数组合的序号信息
 *           perUtil.writeLastMethodSignatureToLog(methodSignature)
 *           perUtil.writeMethodArgIndexToLog(methodSignature, argGroupIndex)
 *           }
 *       })
 *       .invokeAllPublic() // 触发执行
 * </pre>
 */
class FunTraversePersistenceUtil(
    private val targetClz: Class<*>, // 待验证的类信息,用于匹配日志目录现有数据,若一致,则提取日志参数作为本轮测试数据
    private val parentLogFolderPath: String // 日志文件所在父目录, 要求可读写
) {
    companion object {
        private const val LOG_DIR_NAME = "fun_traverse_log" // 反射方法fuzz运行日志目录名
        const val LOG_CLZ = "clz_path.txt" // 待验证的方法所在类完整路径信息
        const val LOG_METHOD_LIST = "method_list.txt" // 全部方法签名列表
        const val LOG_LAST_SIG = "last_invoke_method_sig.txt" // 最后执行的方法签名日志
        const val LOG_METHOD_ARG_INDEX = "invoke_arg_group_index.txt" // 记录实参组合序号日志
    }

    private val logPathList = mutableListOf<String>() // 所有的日志路径
    private var methodSignatureListFromLog: List<String>? = null // 待执行的方法签名列表
    private var lastMethodSignatureFromLog: String? = null // 从日志文件中获取上次最后执行的方法签名内容

    /**
     * 获取上次使用的实参信息(形参组合序号列表),用于回放
     */
    val methodArgGroupIndexMapFromLog = mutableMapOf<String, MutableList<Int>?>()

    /**
     *各日志文件最终所在路径
     */
    private val dirPath: String by lazy {
        FileUtil.processPath("$parentLogFolderPath${File.separator}$LOG_DIR_NAME${File.separator}")
    }
    private val clzLogPath: String by lazy { "$dirPath$LOG_CLZ" }
    private val methodListLogPath: String by lazy { "$dirPath$LOG_METHOD_LIST" }
    private val lastSigLogPath: String by lazy { "$dirPath$LOG_LAST_SIG" }
    private val methodArgIndexLogPath: String by lazy { "$dirPath$LOG_METHOD_ARG_INDEX" }

    init {
        // 添加所有日志路径到列表中,并创建日志文件
        with(logPathList) {
            clear()
            add(clzLogPath)
            add(methodListLogPath)
            add(lastSigLogPath)
            add(methodArgIndexLogPath)
            forEach { FileUtil.create(it) }
        }

        // 读取本地记录的方法遍历类名日志
        val targetClzPathName = targetClz.name
        val logClzPath = FileUtil.getFirstLineInfo(
            clzLogPath, reverse = true, skipEmptyLine = true, defaultMsg = null
        )

        // 匹配到之前本地记录的类信息,则进行读取方法签名信息
        if (targetClzPathName == logClzPath) {
            // 每行表示一个方法签名
            methodSignatureListFromLog =
                FileUtil.readAllLine(methodListLogPath).filter { it.isNotBlank() }

            // 获取上次最后执行的方法签名信息,用于断点继续执行
            lastMethodSignatureFromLog = FileUtil.getFirstLineInfo(
                lastSigLogPath,
                reverse = true,
                skipEmptyLine = true,
                defaultMsg = null
            )

            // 获取确认的方法签名及其所使用的形参组合序号信息
            val methodArgIndexLines = FileUtil.readAllLine(methodArgIndexLogPath)
            for (line in methodArgIndexLines) {
                val arr: Array<String> =
                    line.trim { it <= ' ' }.split("\\s+".toRegex()).toTypedArray()
                val methodSig = arr[0]
                val list = mutableListOf<Int>()
                arr[arr.size - 1]
                    .trim { it <= ' ' }
                    .split(",")
                    .toTypedArray()
                    .map { it.convert2Int(-1, 10) }
                    .filter { it >= 0 }
                    .forEach { list.add(it) }
                methodArgGroupIndexMapFromLog[methodSig] = list
            }
        }
        // 写入本次类完整路径信息
        writeLastClzPath2Log()
    }


    /**
     * 删除指定日志
     *
     * @param logNames 日志文件名,可多个, 若为null或者空,则表示删除全部日志
     * [LOG_LAST_SIG], [LOG_METHOD_LIST],
     * [LOG_METHOD_ARG_INDEX], [LOG_CLZ]
     */
    fun deleteLogFile(vararg logNames: String?): FunTraversePersistenceUtil {
        if (logNames.isNullOrEmpty()) {
            FileUtil.create(dirPath, isDirPath = true, recreateIfExist = true)
        } else {
            logNames.filter { it?.isNotBlank() ?: false }.forEach { FileUtil.delete("$dirPath$it") }
        }
        return this
    }

    /**
     * 根据本地记录, 获取本轮待执行的方法列表
     * 若返回null,则表示全部执行
     */
    val pendingInvokeMethodSignatureList: List<String>?
        get() {
            if (methodSignatureListFromLog.isNullOrEmpty()) {
                return null
            }

            // 未找到最后执行方法,表明数据异常, 直接全量执行
            if (lastMethodSignatureFromLog.isNullOrBlank()) {
                return methodSignatureListFromLog
            }

            val size = methodSignatureListFromLog?.size ?: 0
            val index = methodSignatureListFromLog?.lastIndexOf(lastMethodSignatureFromLog) ?: -1
            if (index == -1) {
                return null
            }

            // 若最后执行的方法为方法列表中最后一个,则应从头开始执行所有方法
            return if (index >= size - 1) methodSignatureListFromLog
            else methodSignatureListFromLog?.subList(index + 1, size)
        }

    /**
     * 写入类完整路径到日志中
     * */
    fun writeLastClzPath2Log() {
        FileUtil.writeToFile(targetClz.name, clzLogPath, append = false, autoAddCTRL = false)
    }

    /**
     * 写入日志: 当前调用的方法签名
     * P.S. 不更新 [lastMethodSignature]
     */
    fun writeLastMethodSignature2Log(
        lastMethodSignature: String?,
        append: Boolean = false
    ): FunTraversePersistenceUtil {
        FileUtil.writeToFile(
            lastMethodSignature,
            lastSigLogPath,
            append = append,
            autoAddCTRL = false
        )
        return this
    }

    /**
     * 写入日志: 本次使用的方法签名列表
     * 每个方法签名独占一行
     * P.S. 不更新 [methodSignatureListFromLog]
     */
    fun writeMethodList2Log(
        methodListInfo: String?
    ): FunTraversePersistenceUtil {
        // 直接覆盖写入所有方法签名信息
        FileUtil.writeToFile(methodListInfo, methodListLogPath, append = false, autoAddCTRL = false)
        return this
    }

    /**
     * 写入日志: 本次使用的方法签名列表
     * 自动为每个方法签名追加一行
     * @param methodList List<Method?> 所有待测方法列表
     * @return FuncTraversePersistenceUtil
     */
    fun writeMethodList2Log(
        methodList: List<Method?>?
    ): FunTraversePersistenceUtil {
        // 清空之前已有的数据
        FileUtil.create(methodListLogPath, false, recreateIfExist = true)
        // 逐行写入
        methodList?.map { FunTraverseUtil.getMethodSignature(it) }
            ?.filter { it.isNotBlank() }
            ?.forEach {
                FileUtil.writeToFile(
                    it,
                    methodListLogPath,
                    append = true,
                    autoAddCTRL = true
                )
            }
        return this
    }

    /**
     * 写入日志: 当前方法调用顺序及其所用参数组合的序号信息
     * P.S. 不更新 [methodArgGroupIndexMapFromLog]
     *
     * @param methodSignature 方法签名
     * @param argGroupIndexes 使用的实参组合序号,可多条
     */
    fun writeMethodArgIndex2Log(
        methodSignature: String,
        vararg argGroupIndexes: Int
    ): FunTraversePersistenceUtil {
        // 判断是否需要重新写入一行
        val lastLine = FileUtil.getFirstLineInfo(
            methodArgIndexLogPath,
            reverse = true,
            skipEmptyLine = true,
            defaultMsg = ""
        )
        for (argGroupIndex in argGroupIndexes) {
            val msg = if (lastLine?.contains(methodSignature) == true) { // 已开始记录序号,则继续追加
                "$argGroupIndex,"
            } else { // 方法尚未记录,则另起一行进行记录
                "\n$methodSignature\t$argGroupIndex,"
            }

            FileUtil.writeToFile(msg, methodArgIndexLogPath, append = true, autoAddCTRL = false)
        }
        return this
    }
}