package org.lynxz.utils.reflect

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.lynxz.utils.bean.FuncTraverseBean
import org.lynxz.utils.log.LoggerUtil
import java.lang.reflect.Method

@RunWith(AndroidJUnit4::class)
class FuncTraverseTest {
    companion object {
        private const val TAG = "FuncTraverseTest"
    }

    @Test
    fun traverseTest() {
        val obj = FuncTraverseBean()
        val util = FunTraverseUtil.create(obj)
            .addArgTypeValue(Int::class.java, 666)
            .setMethodNamePattern("fun2")
            .enableDefaultMultiArtTypeValues()
            .setRandomSortMethods(true)
        LoggerUtil.d(TAG, "validMethodList: ${util.validMethodList}")
        LoggerUtil.d(TAG, "invokeMethodSignatureList: ${util.invokeMethodSignatureList}")
        util.invokeAllPublic()
    }

    /**
     * 验证日志持久化操作
     * */
    @Test
    fun traversePersistenceTest() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val fileDir = context.getExternalFilesDir(null)

        val perUtil =
            FunTraversePersistenceUtil(FuncTraverseBean::class.java, fileDir!!.absolutePath)

        LoggerUtil.d(TAG, "fileDir!!.absolutePath=${fileDir.absolutePath} ")

        FunTraverseUtil.create(FuncTraverseBean())
            .enableDefaultMultiArtTypeValues()
            .setSpecialMethodList(perUtil.pendingInvokeMethodSignatureList)
            .setMethodArgGroupIndexMap(perUtil.methodArgGroupIndexMapFromLog)
            .also {
                perUtil.writeMethodList2Log(it.validMethodList)
                    .deleteLogFile(FunTraversePersistenceUtil.LOG_METHOD_ARG_INDEX)
            }
            .addBeforeFuncInvokeAction(object : ProxyUtil.IFuncInvokeCallback {
                override fun onFuncInvoke(
                    method: Method,
                    returnObj: Any?,
                    argGroupIndex: Int,
                    args: Array<out Any?>?
                ) {
                    // 每次方法执行前记录日志: 所用实参组合序号, 当前方法签名,方便后续回放
                    // 设置当前正在进行方法遍历的method签名并写入到日志
                    val methodSignature: String = FunTraverseUtil.getMethodSignature(method)

                    // 尝试记录当前方法调用顺序及其所用参数组合的序号信息
                    perUtil.writeLastMethodSignature2Log(methodSignature)
                    perUtil.writeMethodArgIndex2Log(methodSignature, argGroupIndex)
                }
            })
            .invokeAllPublic()
    }
}