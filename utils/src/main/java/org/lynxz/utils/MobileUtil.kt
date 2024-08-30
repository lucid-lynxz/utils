package org.lynxz.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.TextUtils
import org.lynxz.utils.log.LoggerUtil

/**
 * 获取手机信息
 */
object MobileUtil {
    private const val TAG = "MobileUtil"
    private var ANDROID_ID: String = ""
    private var SERIAL_NUMBER: String = ""

    /**
     * 获取手机IMEI
     * android 10以下获取imei, 若获取为空,则获取android_id
     */
    @SuppressLint("HardwareIds", "MissingPermission")
    fun getIMEI(context: Context): String? {
        val androidId = getAndroidId(context)

        // android 10及以上使用 ANDROID_ID, imei获取不到, android9经测试还能获取到
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            return androidId
        } else {
            var imei: String? = null
            try {
                val telephonyManager =
                    context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                imei = telephonyManager.deviceId
            } catch (e: Exception) {
                LoggerUtil.e(TAG, "getIMEI fail: " + e.message)
            }

            if (TextUtils.isEmpty(imei)) {
                imei = androidId
            }
            return imei
        }
    }

    @SuppressLint("HardwareIds")
    fun getAndroidId(context: Context?): String {
        if (!TextUtils.isEmpty(ANDROID_ID) || context == null) {
            return ANDROID_ID
        }

        try {
            ANDROID_ID =
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            LoggerUtil.e(TAG, "getAndroidId fail: ${e.message}")
        }
        return ANDROID_ID
    }

    /**
     * 获取手机序列号
     * @param enableByAdb 是否通过adb命令获取(有root权限时有效)
     */
    @SuppressLint("MissingPermission")
    fun getSerialNumber(enableByAdb: Boolean = false): String {
        if (SERIAL_NUMBER.isNotBlank()) {
            return SERIAL_NUMBER
        }

        var serial = ""
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { //9.0+
                serial = Build.getSerial()
            } else if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N) { //8.0+
                serial = Build.SERIAL
            } else { //8.0-
                val c = Class.forName("android.os.SystemProperties")
                val get = c.getMethod("get", String::class.java)
                serial = get.invoke(c, "ro.serialno") as String
            }
        } catch (e: java.lang.Exception) {
            LoggerUtil.e(TAG, "getSerialNumber fail: ${e.message}")
        }

        if (TextUtils.isEmpty(serial) && enableByAdb && RootUtil.isRooted()) {
            val cmdList = java.util.ArrayList<String>()
            cmdList.add("getprop ro.serialno")
            val result = ShellUtil.execCommand(cmdList, true)
            LoggerUtil.w(
                TAG,
                "getprop ro.serialno successMsg=${result.successMsg},errorMsg=${result.errorMsg}"
            )
            serial = result.successMsg
        }

        SERIAL_NUMBER = serial
        return serial
    }

    fun getPhoneInfo(context: Context, splitFlag: String = ","): String {
        return ("型号:" + Build.MODEL
                + splitFlag + "厂商:" + Build.BRAND
                + splitFlag + "isHarmonyOS:" + isHarmonyOS
                + splitFlag + "IMEI:" + getIMEI(context)
                + splitFlag + "ANDROID_ID:" + getAndroidId(context)
                + splitFlag + "serialno:" + getSerialNumber()
                + splitFlag + "系统版本:" + Build.VERSION.RELEASE
                + splitFlag + "SDK_INT:" + Build.VERSION.SDK_INT)
    }

    fun getPhoneInfoMap(context: Context): MutableMap<String, Any?> = mutableMapOf(
        "model" to Build.MODEL, // 型号, 如: Pixel XL
        "brand" to Build.BRAND, // 厂商, 如: 谷歌
        "imei" to getIMEI(context), // imei, 如: 352693082503868
        "android_id" to getAndroidId(context),
        "serialno" to getSerialNumber(), // 序列号, 如: 709KPNX0043238
        "version" to Build.VERSION.RELEASE, // 系统版本, 如:8.1.0
        "sdk_int" to Build.VERSION.SDK_INT // 系统sdk版本号, 如:27
    )

    /**
     * 是否为鸿蒙系统
     */
    val isHarmonyOS: Boolean
        get() {
            try {
                val buildExClass = Class.forName("com.huawei.system.BuildEx")
                val osBrand = buildExClass.getMethod("getOsBrand").invoke(buildExClass).toString()
                return "Harmony".equals(osBrand, ignoreCase = true)
            } catch (x: Throwable) {
                return false
            }
        }

    /**
     * 获取鸿蒙系统版本号
     */
    val harmonyVersion: String
        get() = getProp("hw_sc.build.platform.version", "")

    private fun getProp(property: String, defaultValue: String = ""): String {
        try {
            val spClz = Class.forName("android.os.SystemProperties")
            val method = spClz.getDeclaredMethod("get", String::class.java)
            val value = method.invoke(spClz, property) as String
            if (TextUtils.isEmpty(value)) {
                return defaultValue
            }
            return value
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return defaultValue
    }
}
