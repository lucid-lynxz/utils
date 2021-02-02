package org.lynxz.utils.log

import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Created by lynxz on 31/01/2017.
 * V1.1
 * 默认不可打印,请通过设置 LoggerUtil.init(logLevel,Tag) 来设定可打印等级
 * 格式化打印日志,若是需要打印json,可使用 LoggerUtil.json(jsonStr),或者 LoggerUtil.json(tag,jsonStr)
 * LoggerUtil.i(tag,msg) 或 LoggerUtil.i(msg) 使用通用的tag值
 */
object LoggerUtil {
    private var lTag = "default_logger" // 默认tag
    private const val JSON_INDENT = 2
    private const val MIN_STACK_OFFSET = 3
    private var logPersistenceImpl: ILogPersistence? = null // 持久化实现类

    @LogLevel.LogLevel1
    var logLevel = LogLevel.DEBUG // 需要打印的日志等级(大于等于该等级的日志会被打印)

    /**
     * 支持用户自己传tag，可扩展性更好
     */
    @JvmStatic
    fun init(
        @LogLevel.LogLevel1 level: Int,
        tag: String,
        logPersistenceImpl: ILogPersistence? = null
    ): LoggerUtil {
        this.lTag = tag
        this.logLevel = level
        this.logPersistenceImpl = logPersistenceImpl
        return this
    }

    @JvmStatic
    fun d(msg: String) {
        d(lTag, msg)
    }

    @JvmStatic
    fun i(msg: String) {
        i(lTag, msg)
    }

    @JvmStatic
    fun w(msg: String) {
        w(lTag, msg)
    }

    @JvmStatic
    fun e(msg: String) {
        e(lTag, msg)
    }

    @JvmStatic
    fun e(tag: String, msg: String) {
        if (LogLevel.ERROR >= logLevel) {
            if (msg.isNotBlank()) {
                val s = getMethodNames()
                Log.e(tag, String.format(s, msg))
                filterPersistenceLog(LogLevel.ERROR, tag, msg)
            }
        }
    }

    @JvmStatic
    fun w(tag: String, msg: String) {
        if (LogLevel.WARN >= logLevel) {
            if (msg.isNotBlank()) {
                val s = getMethodNames()
                Log.w(tag, String.format(s, msg))
                filterPersistenceLog(LogLevel.WARN, tag, msg)
            }
        }
    }

    @JvmStatic
    fun i(tag: String, msg: String) {
        if (LogLevel.INFO >= logLevel) {
            if (msg.isNotBlank()) {
                val s = getMethodNames()
                Log.i(tag, String.format(s, msg))
                filterPersistenceLog(LogLevel.INFO, tag, msg)
            }
        }
    }

    @JvmStatic
    fun d(tag: String, msg: String) {
        if (LogLevel.DEBUG >= logLevel) {
            if (msg.isNotBlank()) {
                val s = getMethodNames()
                Log.d(tag, String.format(s, msg))
                filterPersistenceLog(LogLevel.DEBUG, tag, msg)
            }
        }
    }

    /**
     * 打印json格式化字符串,在log过滤条中使用关键字 "system.out" 来搜索查找
     * @param tag 当打印或解析出错时,打印日志用
     * */
    @JvmStatic
    fun json(tag: String, json: String) {
        var jsonT = json

        if (jsonT.isBlank()) {
            d("Empty/Null json content", tag)
            return
        }

        try {
            jsonT = jsonT.trim { it <= ' ' }
            if (jsonT.startsWith("{")) {
                val jsonObject = JSONObject(jsonT)
                var message = jsonObject.toString(JSON_INDENT)
                message = message.replace("\n".toRegex(), "\n║ ")
                val s = getMethodNames()
                println(String.format(s, message))
                return
            }
            if (jsonT.startsWith("[")) {
                val jsonArray = JSONArray(jsonT)
                var message = jsonArray.toString(JSON_INDENT)
                message = message.replace("\n".toRegex(), "\n║ ")
                val s = getMethodNames()
                println(String.format(s, message))
                return
            }
            e("Invalid Json", tag)
        } catch (e: JSONException) {
            e("Invalid Json", tag)
        }
    }

    /**
     * 获取程序执行的线程名,类名和方法名,以及行号等信息
     * */
    private fun getMethodNames(): String {
        val sElements = Thread.currentThread().stackTrace
        var stackOffset = getStackOffset(sElements)
        stackOffset++
        val builder = StringBuilder()

        //builder.append(Thread.currentThread().name).append(" ")
        builder.append(sElements[stackOffset].methodName)
            .append("(").append(sElements[stackOffset].fileName)
            .append(":").append(sElements[stackOffset].lineNumber)
            .append(") ").append("%s")
        return builder.toString()
    }

    private fun getStackOffset(trace: Array<StackTraceElement>): Int {
        var i = MIN_STACK_OFFSET
        while (i < trace.size) {
            val e = trace[i]
            val name = e.className
            if (name != LoggerUtil::class.java.name) {
                return --i
            }
            i++
        }
        return -1
    }

    /**
     * 所有日志语句都最终执行到这里, 判断是否需要持久化
     */
    private fun filterPersistenceLog(
        @LogLevel.LogLevel1 logLevel: Int,
        tag: String,
        msg: String?
    ) {
        logPersistenceImpl?.filterPersistenceLog(logLevel, tag, msg)
    }
}