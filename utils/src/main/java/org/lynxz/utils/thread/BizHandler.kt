package org.lynxz.utils.thread

import android.os.Handler
import android.os.Looper
import android.os.Message
import org.lynxz.utils.otherwise
import org.lynxz.utils.yes

/**
 * 构造函数: [BizHandler] 传入要目标线程looper,默认为主线程looper
 * 启用/停用handler: [enable]
 * 停止并清空队列中未执行的message: [stop]
 */
class BizHandler(looper: Looper = Looper.getMainLooper()) : Handler(looper) {
    var enable = true

    /**
     * 清空消息队列,并禁止后续消息继续发送
     */
    fun stop() {
        enable = false
        removeCallbacksAndMessages(null)
    }

    override fun sendMessageAtTime(msg: Message, uptimeMillis: Long) =
        enable.yes { super.sendMessageAtTime(msg, uptimeMillis) }.otherwise { false }
}