package org.lynxz.utils.log

import android.util.Log
import org.lynxz.utils.FileUtil
import org.lynxz.utils.closeSafety
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * log日志持久化实现类
 * 注意:本类内部若要打印日志,不能使用 [LoggerUtil] 进行日志打印,避免死循环
 * 创建对象时,指定日志文件所在目录
 */
class LogPersistenceImpl(
    private val logDirPath: String, // 日志所在目录绝对路径(外部存储路径),要求可读写
    @LogLevel.LogLevel1
    private var level: Int = LogLevel.WARN,// 设置要持久化的日志等级,大于等于该等级的才会进行持久化
    private val maxCacheLen: Int = 0, // 缓冲区大小,日志超过该长度则执行flush,0表示每条日志都立即写入文件
    private val logSizeLimit: Int = 1024 * 1024, // 单个日志文件大小限制,单位:b, 默认1Mb
) : ILogPersistence {
    companion object {
        private const val TAG = "LogPersistenceImpl"
        private const val LOG_SUFFIX = ".txt" // 日志文件后缀名
    }

    private val tagSet = mutableSetOf<String>() // 需要持久化的tag信息
    private var logPath = "" // 当前日志文件路径
    private var logFileWriter: FileWriter? = null

    // 缓冲区
    private val sbCache = StringBuffer()
    private var curLogFileLen = 0L // 日志文件文件,单位:b
    private var curCacheLen = 0L // 当前已缓存的日志长度,超过一定长度自动flush写入日志

    // 日志文件名时间规则
    private val logFileSdf = SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.CHINA)

    // 每条日志持久化时记录的时间规则
    private val logSdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA)

    override fun addTag(tag: String): ILogPersistence {
        tagSet.add(tag)
        return this
    }

    /**
     * 设置要持久化的日志等级,大于等于该等级的才会进行持久化
     * */
    override fun setLevel(@LogLevel.LogLevel1 logLevel: Int): ILogPersistence {
        level = logLevel
        return this
    }

    init {
        val create = FileUtil.create(logDirPath, true, recreateIfExist = false)
        if (!create) throw IllegalStateException("日志目录创建失败,请检查 $logDirPath")

        val files = File(logDirPath).listFiles()
        if (files.isNullOrEmpty()) {
            createNewLogFile()
        } else {
            val lastLog = files[files.size - 1]
            curLogFileLen = lastLog.length()
            Log.d(TAG, "last demoLog size:$curLogFileLen,path:${lastLog.absolutePath}")
            if (curLogFileLen >= logSizeLimit) {
                createNewLogFile()
            } else {
                flush()
                close()
                logPath = lastLog.absolutePath
            }
        }
    }

    /**
     * 创建新日志文件
     */
    private fun createNewLogFile() {
        flush()
        close()
        clearEmptyLogFile()

        val logName = getDate(logFileSdf)
        logPath = "$logDirPath/$logName$LOG_SUFFIX"
        FileUtil.create(logPath)
        curLogFileLen = 0
    }

    /**
     * 清除空白日志文件
     */
    private fun clearEmptyLogFile() {
        File(logDirPath).listFiles()
            ?.filter { !it.isDirectory && it.length() <= 10 }
            ?.forEach { it.delete() }
    }

    /**
     * 将缓存的日志信息写入到文件中
     */
    @Synchronized
    override fun flush() {
        if (sbCache.isEmpty()) {
            return
        }
        val msg: String = sbCache.toString()

        // 避免被人为删除后无法写入
        if (!FileUtil.isExist(logPath)) {
            logFileWriter.closeSafety()
            logFileWriter = null
            FileUtil.create(logPath)
        }

        if (logFileWriter == null) {
            try {
                logFileWriter = FileWriter(logPath, true) //SD卡中的路径
            } catch (ignore: IOException) {
                // ignore.printStackTrace();
                Log.e(TAG, "flush fail as create FileWriter fail, logPath=$logPath")
            }
        }

        try {
            logFileWriter?.write(msg)
            logFileWriter?.flush()
            curLogFileLen += sbCache.length
            curCacheLen = 0
            sbCache.setLength(0) // 写入成功则清空日志
        } catch (e: Exception) {
            Log.e(TAG, "flush fail: ${e.message}")
        }
    }

    @Synchronized
    override fun close() {
        logFileWriter?.closeSafety()
        sbCache.setLength(0)
        curCacheLen = 0
        logFileWriter = null
    }

    override fun filterPersistenceLog(
        @LogLevel.LogLevel1 logLevel: Int,
        tag: String,
        msg: String?,
        keepFormat: Boolean
    ) {
        if (msg.isNullOrBlank() || (level > logLevel && !tagSet.contains(tag))) {
            return
        }

        val logLevelName = LogLevelName.getName(level)
        val tMsg = if (keepFormat) msg else msg.replace("\n".toRegex(), " ")
        sbCache.append(getDate(logSdf)).append(" ")
            .append(logLevelName).append(" ")
            .append(tag).append("\t")
            .append(tMsg).append("\n")

        val length = msg.length
        curCacheLen += length.toLong()

        // 缓冲数据超过最大值,进行flush操作
        if (curCacheLen >= maxCacheLen) {
            flush()
        }

        // 日志文件大小超过限制,创建新日志
        if (curLogFileLen >= logSizeLimit) {
            createNewLogFile()
        }
    }

    private fun getDate(format: SimpleDateFormat, time: Long = System.currentTimeMillis()) =
        format.format(Date(time))
}