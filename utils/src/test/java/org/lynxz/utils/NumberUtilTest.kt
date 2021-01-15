package org.lynxz.utils

import org.junit.Assert
import org.junit.Test

class NumberUtilTest {
    @Test
    fun str2IntTest() {
        val defaultValue = 0
        Assert.assertEquals(0, NumberUtil.convert2Int("", defaultValue))
        Assert.assertEquals(123, NumberUtil.convert2Int("123", defaultValue))
        Assert.assertEquals(-1, NumberUtil.convert2Int("-1", defaultValue))
        Assert.assertEquals(Int.MAX_VALUE, NumberUtil.convert2Int("2147483647", defaultValue))
        Assert.assertEquals(Int.MIN_VALUE, NumberUtil.convert2Int("-2147483648", defaultValue))
        Assert.assertEquals(0, NumberUtil.convert2Int("3.8", defaultValue))
        Assert.assertEquals(0, NumberUtil.convert2Int("1e6", defaultValue))
    }

    @Test
    fun str2LongTest() {
        val defaultValue = 0L
        Assert.assertEquals(0, NumberUtil.convert2Long("", defaultValue))
        Assert.assertEquals(123, NumberUtil.convert2Long("123", defaultValue))
        Assert.assertEquals(-1, NumberUtil.convert2Long("-1", defaultValue))
        Assert.assertEquals(2147483647L, NumberUtil.convert2Long("2147483647", defaultValue))
        Assert.assertEquals(-2147483648, NumberUtil.convert2Long("-2147483648", defaultValue))
        Assert.assertEquals(
            Long.MAX_VALUE,
            NumberUtil.convert2Long("9223372036854775807", defaultValue)
        )
        Assert.assertEquals(
            Long.MAX_VALUE,
            NumberUtil.convert2Long("9223372036854775807L", defaultValue)
        )
        Assert.assertEquals(
            Long.MAX_VALUE,
            NumberUtil.convert2Long("9223372036854775807l", defaultValue)
        )
        Assert.assertEquals(0, NumberUtil.convert2Long("3.8", defaultValue))
        Assert.assertEquals(0, NumberUtil.convert2Long("1e6", defaultValue))
    }

    @Test
    fun str2Float() {
        val defaultValue = 0.0f
        val delta = 0.1f
        Assert.assertEquals(0f, NumberUtil.convert2Float("", defaultValue), delta)
        Assert.assertEquals(123f, NumberUtil.convert2Float("123", defaultValue), delta)
        Assert.assertEquals(-1f, NumberUtil.convert2Float("-1", defaultValue), delta)
        Assert.assertEquals(
            2147483647f,
            NumberUtil.convert2Float("2147483647", defaultValue),
            delta
        )
        Assert.assertEquals(
            -2147483648f,
            NumberUtil.convert2Float("-2147483648", defaultValue),
            delta
        )
        Assert.assertEquals(
            9223372036854775807f,
            NumberUtil.convert2Float("9223372036854775807.0", defaultValue), delta
        )
        Assert.assertEquals(
            9223372036854775807f,
            NumberUtil.convert2Float("9223372036854775807.1f", defaultValue), delta
        )
        Assert.assertEquals(
            9223372036854775807f,
            NumberUtil.convert2Float("9223372036854775807", defaultValue), delta
        )
        Assert.assertEquals(3.8f, NumberUtil.convert2Float("3.8", defaultValue), delta)
        Assert.assertEquals(3.8f, NumberUtil.convert2Float("3.8f", defaultValue), delta)
        Assert.assertEquals(3.8f, NumberUtil.convert2Float("3.8f", defaultValue), delta)
        Assert.assertEquals(1000000f, NumberUtil.convert2Float("1e6", defaultValue), delta)
    }

    @Test
    fun str2Double() {
        val defaultValue = 0.0
        val delta = 0.1

        Assert.assertEquals(0.0, NumberUtil.convert2Double("", defaultValue), delta)
        Assert.assertEquals(123.0, NumberUtil.convert2Double("123", defaultValue), delta)
        Assert.assertEquals(-1.0, NumberUtil.convert2Double("-1", defaultValue), delta)
        Assert.assertEquals(
            2147483647.0,
            NumberUtil.convert2Double("2147483647", defaultValue),
            delta
        )
        Assert.assertEquals(
            -2147483648.0,
            NumberUtil.convert2Double("-2147483648", defaultValue),
            delta
        )
        Assert.assertEquals(
            9223372036854775807.0,
            NumberUtil.convert2Double("9223372036854775807.0", defaultValue), delta
        )
        Assert.assertEquals(
            9223372036854775807.0,
            NumberUtil.convert2Double("9223372036854775807.1f", defaultValue), delta
        )
        Assert.assertEquals(
            9223372036854775807.0,
            NumberUtil.convert2Double("9223372036854775807", defaultValue), delta
        )
        Assert.assertEquals(3.8, NumberUtil.convert2Double("3.8", defaultValue), delta)
        Assert.assertEquals(3.8, NumberUtil.convert2Double("3.8f", defaultValue), delta)
        Assert.assertEquals(3.8, NumberUtil.convert2Double("3.8f", defaultValue), delta)
        Assert.assertEquals(1000000.0, NumberUtil.convert2Double("1e6", defaultValue), delta)
    }

    @Test
    fun scientificNotation2strTest() {
        var d = 2.019110601E9
        var s = NumberUtil.scientificNotation2str(d)
        Assert.assertEquals("2019110601", s)

        d = 2.019110601
        s = NumberUtil.scientificNotation2str(d)
        Assert.assertEquals("2.019110601", s)
    }

    @Test
    fun double2fixStrTest() {
        Assert.assertEquals("3.65", NumberUtil.double2fixStr(3.654321))
        Assert.assertEquals("3", NumberUtil.double2fixStr(3.001234, retainZeroTail = false))
        Assert.assertEquals("3.7", NumberUtil.double2fixStr(3.654321, 1))
        Assert.assertEquals("4", NumberUtil.double2fixStr(3.654321, 0))
        Assert.assertEquals("3.0", NumberUtil.double2fixStr(3.004321, 1))
    }
}