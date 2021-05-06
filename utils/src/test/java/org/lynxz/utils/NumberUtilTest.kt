package org.lynxz.utils

import org.junit.Assert
import org.junit.Test

class NumberUtilTest {
    /**
     * 字符串转整数
     * */
    @Test
    fun str2IntTest() {
        val defaultValue = 0
        Assert.assertEquals(0, "".convert2Int(defaultValue))
        Assert.assertEquals(123, "123".convert2Int(defaultValue))
        Assert.assertEquals(-1, "-1".convert2Int(defaultValue))
        Assert.assertEquals(Int.MAX_VALUE, "2147483647".convert2Int(defaultValue))
        Assert.assertEquals(Int.MIN_VALUE, "-2147483648".convert2Int(defaultValue))
        Assert.assertEquals(0, "3.8".convert2Int(defaultValue))
        Assert.assertEquals(0, "1e6".convert2Int(defaultValue))
    }

    @Test
    fun str2LongTest() {
        val defaultValue = 0L
        Assert.assertEquals(0, "".convert2Long(defaultValue))
        Assert.assertEquals(123, "123".convert2Long(defaultValue))
        Assert.assertEquals(-1, "-1".convert2Long(defaultValue))
        Assert.assertEquals(2147483647L, "2147483647".convert2Long(defaultValue))
        Assert.assertEquals(-2147483648, "-2147483648".convert2Long(defaultValue))
        Assert.assertEquals(
            Long.MAX_VALUE,
            "9223372036854775807".convert2Long(defaultValue)
        )
        Assert.assertEquals(
            Long.MAX_VALUE,
            "9223372036854775807L".convert2Long(defaultValue)
        )
        Assert.assertEquals(
            Long.MAX_VALUE,
            "9223372036854775807l".convert2Long(defaultValue)
        )
        Assert.assertEquals(0, "3.8".convert2Long(defaultValue))
        Assert.assertEquals(0, "1e6".convert2Long(defaultValue))
    }

    @Test
    fun str2Float() {
        val defaultValue = 0.0f
        val delta = 0.1f
        Assert.assertEquals(0f, "".convert2Float(defaultValue), delta)
        Assert.assertEquals(123f, "123".convert2Float(defaultValue), delta)
        Assert.assertEquals(-1f, "-1".convert2Float(defaultValue), delta)
        Assert.assertEquals(
            2147483647f,
            "2147483647".convert2Float(defaultValue),
            delta
        )
        Assert.assertEquals(
            -2147483648f,
            "-2147483648".convert2Float(defaultValue),
            delta
        )
        Assert.assertEquals(
            9223372036854775807f,
            "9223372036854775807.0".convert2Float(defaultValue), delta
        )
        Assert.assertEquals(
            9223372036854775807f,
            "9223372036854775807.1f".convert2Float(defaultValue), delta
        )
        Assert.assertEquals(
            9223372036854775807f,
            "9223372036854775807".convert2Float(defaultValue), delta
        )
        Assert.assertEquals(3.8f, "3.8".convert2Float(defaultValue), delta)
        Assert.assertEquals(3.8f, "3.8f".convert2Float(defaultValue), delta)
        Assert.assertEquals(1000000f, "1e6".convert2Float(defaultValue), delta)
    }

    @Test
    fun str2Double() {
        val defaultValue = 0.0
        val delta = 0.1

        Assert.assertEquals(0.0, "".convert2Double(defaultValue), delta)
        Assert.assertEquals(123.0, "123".convert2Double(defaultValue), delta)
        Assert.assertEquals(-1.0, "-1".convert2Double(defaultValue), delta)
        Assert.assertEquals(
            2147483647.0,
            "2147483647".convert2Double(defaultValue),
            delta
        )
        Assert.assertEquals(
            -2147483648.0,
            "-2147483648".convert2Double(defaultValue),
            delta
        )
        Assert.assertEquals(
            9223372036854775807.0,
            "9223372036854775807.0".convert2Double(defaultValue), delta
        )
        Assert.assertEquals(
            9223372036854775807.0,
            "9223372036854775807.1f".convert2Double(defaultValue), delta
        )
        Assert.assertEquals(
            9223372036854775807.0,
            "9223372036854775807".convert2Double(defaultValue), delta
        )
        Assert.assertEquals(3.8, "3.8".convert2Double(defaultValue), delta)
        Assert.assertEquals(3.8, "3.8f".convert2Double(defaultValue), delta)
        Assert.assertEquals(3.8, "3.8f".convert2Double(defaultValue), delta)
        Assert.assertEquals(1000000.0, "1e6".convert2Double(defaultValue), delta)
    }

    @Test
    fun scientificNotation2strTest() {
        var d = 2.019110601E9
        var s = d.scientificNotation2str()
        Assert.assertEquals("2019110601", s)

        d = 2.019110601
        s = d.scientificNotation2str()
        Assert.assertEquals("2.019110601", s)
    }

    @Test
    fun double2fixStrTest() {
        Assert.assertEquals("3.65", 3.654321.double2fixStr())
        Assert.assertEquals("3", 3.001234.double2fixStr(retainZeroTail = false)) // 不保留小数结尾的0占位符
        Assert.assertEquals("3.00", 3.001234.double2fixStr(retainZeroTail = true))
        Assert.assertEquals("3.7", 3.654321.double2fixStr(1))
        Assert.assertEquals("4", 3.654321.double2fixStr(0))
        Assert.assertEquals("3.0", 3.004321.double2fixStr(1))
    }
}