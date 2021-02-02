package org.lynxz.utils.log

import androidx.annotation.StringDef

/**
 * 日志等级名称缩写
 */
object LogLevelName {
    const val VERBOSE = "V" // 所有日志
    const val DEBUG = "D"
    const val INFO = "I"
    const val WARN = "W"
    const val ERROR = "E"
    const val NONE = "N" // 不打印任何级别日志

    @StringDef(VERBOSE, DEBUG, INFO, WARN, ERROR, NONE)
    annotation class LogLevelName1

    /**
     * 根据日志等级获取其对应的leveName
     */
    @LogLevelName1
    fun getName(@LogLevel.LogLevel1 level: Int) = when (level) {
        LogLevel.VERBOSE -> VERBOSE
        LogLevel.DEBUG -> DEBUG
        LogLevel.INFO -> INFO
        LogLevel.WARN -> WARN
        LogLevel.ERROR -> ERROR
        LogLevel.NONE -> ERROR
        else -> "?"
    }
}