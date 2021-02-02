package org.lynxz.utils.log

import androidx.annotation.IntDef

/**
 * log日志等级
 */
object LogLevel {
    const val VERBOSE = 0 // 所有日志
    const val DEBUG = 1
    const val INFO = 2
    const val WARN = 3
    const val ERROR = 4
    const val NONE = 10 // 不打印任何级别日志

    @IntDef(VERBOSE, DEBUG, INFO, WARN, ERROR, NONE)
    annotation class LogLevel1
}