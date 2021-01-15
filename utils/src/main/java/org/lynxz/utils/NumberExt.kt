package org.lynxz.utils

import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.*

//
//  字符串数字转换工具类,空安全, 支持默认值
//  字符串转数值: [convert2Int] [convert2Long] [convert2Float] [convert2Double]
//  浮点数保留指定位数:  [double2fixStr]
//  科学计数法转字符串: [scientificNotation2str]
//
/**
 * 字符串转数字
 *
 * @param radix        进制,默认10
 * @param defaultValue 若转换失败,则返回默认值
 */
fun String?.convert2Int(defaultValue: Int, radix: Int = 10) =
    if (this.isNullOrBlank()) {
        defaultValue
    } else try {
        this.toInt(radix)
    } catch (e: Exception) {
        defaultValue
    }

/**
 * 字符串转数字
 *
 * @param radix        进制,默认10
 * @param defaultValue 若转换失败,则返回默认值
 */
fun String?.convert2Long(defaultValue: Long, radix: Int = 10) =
    if (this.isNullOrBlank()) {
        defaultValue
    } else try {
        this.toLowerCase(Locale.getDefault()).replace("l", "").toLong(radix)
    } catch (e: Exception) {
        defaultValue
    }

fun String?.convert2Float(defaultValue: Float) =
    if (this.isNullOrBlank()) {
        defaultValue
    } else try {
        this.toLowerCase(Locale.getDefault()).replace("f", "").toFloat()
    } catch (e: Exception) {
        defaultValue
    }

fun String?.convert2Double(defaultValue: Double) =
    if (this.isNullOrBlank()) {
        defaultValue
    } else try {
        this.toLowerCase(Locale.getDefault()).replace("f", "").toDouble()
    } catch (e: Exception) {
        defaultValue
    }

/**
 * double的科学计数法转 string,去除e标记
 */
fun Double?.scientificNotation2str(): String? {
    val dstr = "$this"
    if (!dstr.toLowerCase().contains("e")) {
        return dstr
    }
    val nf = NumberFormat.getInstance()
    // 是否以逗号隔开, 默认true以逗号隔开,如[123,456,789.128]
    nf.isGroupingUsed = false
    // 结果未做任何处理
    return nf.format(this)
}

/**
 * double值转成保留指定小数位数的字符串, 会四舍五入
 * @param decimalCount Int 最多保留的小数位数 [0,+∞)
 * @param retainZeroTail 是否要保留小数部分最后的0, 默认为True, 若为false,则会删除小数部分最后的0
 */
fun Double?.double2fixStr(decimalCount: Int = 2, retainZeroTail: Boolean = true): String {
    val flag = if (retainZeroTail) "0" else "#"
    val df = DecimalFormat("0.${flag.repeat(decimalCount)}")
    var format = df.format(this)
    if (format.endsWith(".")) {
        format = format.replace(".", "")
    }
    return format
}


