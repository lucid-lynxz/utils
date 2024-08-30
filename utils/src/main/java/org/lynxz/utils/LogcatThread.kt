package org.lynxz.utils

import androidx.core.util.Predicate
import org.lynxz.utils.log.LoggerUtil
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * @property consumed Boolean 当 调用[test]返回true时,会自动设置为true,表示该logConsumer已被消费, 后续不再触发
 */
abstract class LogConsumer : Predicate<String> {
    var consumed: Boolean = false

    abstract override fun test(logLine: String): Boolean
}

/**
 * logcat监听线程
 * 建议测试类中重写下: [assertTrue] 和 [assertFalse] 方法, 改为通过 Assert 类进行断言
 * 使用方式:
 * <pre>
 * new LogcatThread()
 *     .saveLogcat() // [可选] 是否要将收到的logcat日志存储到log文件中, 默认不存储, 需要调用该方法后才存储, 格式为: "收到logcat日志:{日志内容}"
 *     .recreateLogcatProcessDelayMs(1000) // [可选] logcat process重建时的延迟, 单位:ms, 默认0ms
 *     .setKeywords("naviFace")  // [可选] 设置日志行关键字, 可多个, 命中任意一个则可将该日志行返回给 LogLineConsumer 处理
 *     .setTagAndLevels("*:D")  // [可选] 日志tag和等级, 如: "tag1:D  tag2:E", 默认为: "*:D"
 *     .begin(["threadName"]) // 必须, 启动线程, 形参表示线程名, 可不填, 默认为: LogcatThread
 *
 *     .setLogLineConsumer((logLine) -> {   // 必须, 设置日志行回调
 *             // do sth...
 *            return true;  // 需要在某些时刻返回true, 表示日志符合条件, 无需再判断后续日志
 *         })
 *     .doAction(this::enterLaneActivity) // [可选] 执行某些操作
 *     .waitLogConsumerFinish(60) // [可选] 等待上方设置的 logcatConsumer 返回true, 并可设置等待超时,单位:s
 *     .assertLogConsumerSuccess() // [可选] 断言上方设置的 logcatConsumer 已经返回了true
 *
 *     .reuseLastLogLineConsumer() // [可选] 复用上一次使用的 logLineConsumer (若为设置过,则不生效)
 *     .doAction(this::enterLaneActivity) // [可选] 执行某些操作
 *     .waitLogConsumerFinish(60) // [可选] 等待上方设置的 logcatConsumer 返回true, 并可设置等待超时,单位:s
 *     .assertLogConsumerSuccess() // [可选] 断言上方设置的 logcatConsumer 已经返回了true
 *
 *     .exitThread() // [可选] 无需继续等待时,主动退出进程,与 waitFinish() 方法三选一即可
 *     .exitAfterLogConsumerFinished() // [可选] 当 logLineConsumer 返回true时,退出线程
 *     .waitFinish(); // 等待logcat线程退出, 也可传入等待超时,单位:s
 * </pre>
 */
open class LogcatThread : Thread() {
    companion object {
        private const val TAG = "LogcatThread"
    }

    // 要过滤的 logcat tag及其日志等级要求
    private var tagAndLevels: String = "*:D"

    // logcat process监听线程退出后, 重建线程的间隔延时, 默认1000ms
    private var recreateLogcatProcessDelayMs = 0L

    // 当前已创建的次数,默认最多重建30次,超过直接报错不再监听
    private val recreateLogcatProcessCnt = AtomicInteger(0)

    // 是否要保存收到的日志到log中
    private val saveLogcat = AtomicBoolean(false)

    // 退出线程
    private val keywordExitThread = "__exit_espresso_logcatThread__"

    // logcat日志行中需要包含的关键字, 可多个, 匹配任意一个则该日志行有效, 会回调给 logLineConsumer 进行二次判断
    private val keywords = mutableListOf<String>()

    // 调用方对符合的日志信息进行更精细的判断和处理, 需在某些时候返回true, 线程才会退出
    private val lastLogConsumer = AtomicReference<LogConsumer>() // 缓存最后一个logConsumer
    private val logConsumerManager = mutableListOf<LogConsumer>() // 缓存所有可用的logConsumer
    private val countdownInt = AtomicInteger(0)

    // 是否要退出线程
    private val exitThreadNow = AtomicBoolean(false)

    // logConsumer返回true时,是否立即退出监听线程,默认false,保持与原先逻辑一致
    private val exitAfterLogConsumerFinish = AtomicBoolean(false)

    /**
     * 设置logcat的过滤tag和等级, 可多个, 默认为: "*:D", 注意 tag名称要写完整
     * 如: tag1:V  tag2:W  *:E
     * 日志等级如下:
     * V —— Verbose
     * D —— Debug
     * I —— Info
     * W —— Warning
     * E —— Error
     */
    fun setTagAndLevels(param: String) = this.apply { tagAndLevels = param }

    /**
     * logcat process退出后, 重建前延时时长, 单位:ms
     */
    fun setRecreateLogcatProcessDelayMs(delayMs: Long) =
        this.apply { recreateLogcatProcessDelayMs = delayMs }

    /**
     * 启用或禁用保存全部符合条件的logcat日志到log文件中
     * P.S. 建议不启用, 并在 logConsumer 中按需调用 {@link LoggerUtil#writeLog(String,String)} 进行持久化
     */
    @JvmOverloads
    fun saveLogcat(active: Boolean = true) = this.apply { saveLogcat.set(active) }

    /**
     * 设置日志过滤的关键字, 每行logcat日志, 只要包含任意一个关键字就会回调 logLineConsumer, 通过 grep 功能实现
     */
    fun setKeywords(vararg keys: String) = this.apply {
        keywords.clear()
        keywords.addAll(keys)
    }

    /**
     * 兼容原有用例,保持接口不变
     * @param consumer Function1<String, Boolean>
     * @return LogcatThread
     */
    @JvmOverloads
    fun setLogLineConsumer(consumer: (String) -> Boolean, condition: Boolean = true) =
        this.apply {
            setLogLineConsumer(object : LogConsumer() {
                override fun test(logLine: String) = consumer.invoke(logLine)
            }, condition)
        }

    /**
     * 清空并设置日志行处理回调函数, 函数返回true时,则后续logcat日志不再回调, 进程也会退出
     *
     * @param condition Boolean condition为true时,才会添加consumer
     */
    @JvmOverloads
    fun setLogLineConsumer(consumer: LogConsumer?, condition: Boolean = true) =
        this.apply { addLogLineConsumer(consumer, condition, true) }

    @JvmOverloads
    fun addLogLineConsumer(
        consumer: (String) -> Boolean,
        condition: Boolean = true,
        clearFirst: Boolean = false
    ) = this.apply {
        addLogLineConsumer(object : LogConsumer() {
            override fun test(logLine: String) = consumer.invoke(logLine)
        }, condition, clearFirst)
    }

    /**
     * 追加一个logConsumer
     * P.S. 自动清空已被消费的logConsumer
     *
     * @param consumer 若为空,则仅做无效consumer的清理等操作
     * @param condition Boolean condition为true时,才会添加consumer
     * @param clearFirst Boolean 若为true,则会清空现有的logConsumer
     * @return LogcatThread
     */
    @JvmOverloads
    fun addLogLineConsumer(
        consumer: LogConsumer?,
        condition: Boolean = true,
        clearFirst: Boolean = false
    ) = this.apply {
        synchronized(this) {
            if (clearFirst || countdownInt.get() == 0) {
                logConsumerManager.clear()
                countdownInt.set(0)
            } else {
                clearInActiveConsumer()
            }

            if (condition) {
                val lastConsumer = lastLogConsumer.getAndSet(consumer)
                consumer?.let {
                    it.consumed = false
                    logConsumerManager.add(it)
                    countdownInt.incrementAndGet()
                }
                LoggerUtil.writeLog(
                    TAG,
                    "setLogLineConsumer countdownInt=${countdownInt.get()},size=${logConsumerManager.size},last=$lastConsumer,current=$consumer,"
                )
            }
        }
    }

    // 清除所有已失效的logConsumer
    private fun clearInActiveConsumer() {
        synchronized(this) {
            val iterator = logConsumerManager.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                if (next.consumed) {
                    iterator.remove()
                }
            }
        }
    }

    /**
     * 复用上一次设置的 logLineConsumer
     * 若之前未设置过,则复用失败
     */
    fun reuseLastLogLineConsumer() = this.apply {
        val lastConsumer = lastLogConsumer.get() ?: return this
        addLogLineConsumer(lastConsumer, condition = true, clearFirst = false)
        LoggerUtil.writeLog(
            TAG,
            "reuseLastLogLineConsumer last=$lastConsumer,countdownInt=${countdownInt.get()}"
        )
    }

    /**
     * 启动logcat监听
     */
    @JvmOverloads
    fun begin(threadName: String = "LogcatThread"): LogcatThread {
        name = threadName
        recreateLogcatProcessCnt.set(0)
        LoggerUtil.writeLog(TAG, "keywords=${keywords.joinToString(",")}")
        start()
        return this
    }

    /**
     * 执行某些操作
     */
    open fun doAction(runnable: Runnable): LogcatThread {
        runnable.run()
        return this
    }

    /***
     * logConsumer返回true时,是否自动退出线程,默认false
     */
    fun exitAfterLogConsumerFinished() = this.apply {
        exitAfterLogConsumerFinish.set(true)
    }

    /**
     * 等待logcat线程执行结束
     * @param maxWaitSeconds Int 最长等待时长, 单位:s
     * @param exitThreadForce 完成等待后, 是否强制退出线程
     *                        若为false,则用于多次setLogLineConsumer的场景, 用于等待上一个 LogLineConsumer 返回true
     */
    @JvmOverloads
    open fun waitFinish(
        maxWaitSeconds: Int = Int.MAX_VALUE,
        exitThreadForce: Boolean = true
    ): LogcatThread = this.apply {
        var finished = countdownInt.get() <= 0 || state == State.TERMINATED
        var totalMs = 0L
        val durationMs = 1000L
        val maxMs = maxWaitSeconds * 1000L
        while (!finished) {
            sleep(durationMs)
            totalMs += durationMs
            finished = countdownInt.get() <= 0 || state == State.TERMINATED
            if (totalMs >= maxMs) {
                break
            }
        }

        LoggerUtil.w(TAG, "waitFinish end,count=${countdownInt.get()},Thread state=$state")
        if (exitThreadForce) {
            this.exitThread()
        }
    }

    /**
     * 等待上一个 LogLineConsumer 返回true或者线程已退出
     */
    fun waitLogConsumerFinish(maxWaitSeconds: Int = Int.MAX_VALUE) =
        this.apply { waitFinish(maxWaitSeconds, false) }

    /**
     * 断言前一个logConsumer返回了true
     */
    open fun assertLogConsumerSuccess(): LogcatThread = this.apply {
        assertTrue(
            "assertLogConsumerSuccess 失败, 当前countdownInt=${countdownInt.get()}",
            countdownInt.get() <= 0
        )
    }

    open fun assertTrue(failMsg: String, b: Boolean) = this.apply {
//        Assert.assertTrue(failMsg, b)
        if (!b) {
            throw RuntimeException(failMsg)
        }
    }

    open fun assertFalse(failMsg: String, b: Boolean) = assertTrue(failMsg, !b)

    /**
     * 发送一条logcat日志, 进行退出
     * */
    fun exitThread(): LogcatThread {
        if (!isAlive) {
            LoggerUtil.w(TAG, "exitThread success as not alive now")
            return this
        }

        exitThreadNow.set(true)
        val exitLog =
            if (keywords.isEmpty()) keywordExitThread else "${keywords[0]}$keywordExitThread"
        LoggerUtil.w(TAG, exitLog)
        return this
    }

    override fun run() {
        super.run()
        LoggerUtil.w(TAG, "LogcatThread start run,name=$name")
        Runtime.getRuntime().exec("logcat -c").waitFor() // 清空已有的logcat

        // logcat日志行可能为空,导致未超时前, 就提前退出了线程, 此处增加重试, 直到主动调用 exitThread()
        while (!exitThreadNow.get()) {
            val grepOpt =
                if (keywords.isEmpty()) "" else "| grep -E '${keywords.joinToString("|")}'"

            // 注意: 不能输出到logcat
            val cmd = arrayOf("logcat", "-v time", tagAndLevels, grepOpt)
            LoggerUtil.writeLog(TAG, "run cmd=${cmd.joinToString(" ")}")

            val recreateCnt = recreateLogcatProcessCnt.incrementAndGet()
            if (recreateCnt > 30) {
                LoggerUtil.w(TAG, "recreateLogcatProcessCnt=$recreateCnt,超过30次,退出监听")
                break
            }
            val process = Runtime.getRuntime().exec(cmd)
            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))

            var line: String? = bufferReadLine(bufferedReader)
            LoggerUtil.writeLog(TAG, "$name get first logcat line:$line")
            while (line != null) {
                val logLine = line

                // 有发现grep可能不生效,此处增加判断
                var validLog = false
                for (key in keywords) {
                    if (logLine.contains(key)) {
                        validLog = true
                        break
                    }
                }

                if (validLog) {
                    if (saveLogcat.get()) {
                        LoggerUtil.writeLog(TAG, "收到logcat日志:$logLine")
                    }

                    if (logLine.contains(keywordExitThread)) {
                        LoggerUtil.writeLog(TAG, "get exit thread log:$logLine")
                        countdownInt.set(0)
                        lastLogConsumer.set(null)
                        logConsumerManager.clear()
                        exitThreadNow.set(true)
                        break
                    } else if (countdownInt.get() >= 1) { // logConsumer未返回true时才需要继续回调
                        synchronized(this) {
                            val iterator = logConsumerManager.iterator()
                            while (iterator.hasNext()) {
                                val next = iterator.next()
                                if (next.consumed) {
                                    iterator.remove()
                                } else {
                                    if (next.test(logLine)) {
                                        next.consumed = true
                                        countdownInt.decrementAndGet()
                                        iterator.remove()
                                    }
                                }
                            }
                        }

                        if (countdownInt.get() < 1) {
                            LoggerUtil.writeLog(
                                TAG,
                                "all logLineConsumer return true, log:$logLine"
                            )
                            if (exitAfterLogConsumerFinish.get()) {
                                exitThreadNow.set(true)
                                break
                            }
                        }
                    }
                }
                line = bufferReadLine(bufferedReader)
            }
            LoggerUtil.writeLog(TAG, "logLine is null, destroy current logcat process")
            process.destroy()

            if (recreateLogcatProcessDelayMs > 0) {
                sleep(recreateLogcatProcessDelayMs)
            }
        }
        LoggerUtil.w(TAG, "LogcatThread exit run,name=$name")
    }

    // 可能报错java.io.IOException: Bad file descriptor
    private fun bufferReadLine(buffer: BufferedReader?) = try {
        buffer?.readLine()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}