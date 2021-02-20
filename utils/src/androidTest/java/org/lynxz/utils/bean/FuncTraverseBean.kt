package org.lynxz.utils.bean

import org.lynxz.utils.log.LoggerUtil

class FuncTraverseBean {
    companion object {
        private const val TAG = "FuncTraverseBean"
    }

    fun fun1(para1: Int?, para2: Long?) {
        LoggerUtil.d(TAG, "fun1 $para1 $para2")
    }

    fun fun2(info: String?) {
        LoggerUtil.d(TAG, "fun2 $info ")
    }
}