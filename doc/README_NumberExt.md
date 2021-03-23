# `NumberExt` 扩展函数

## 功能

字符串转数字的扩展函数 浮点数保留指定小数位的方法 科学计数法转字符串

### 字符串转数字

[可参考测试用例 NumberUtilTest](../utils/src/test/java/org/lynxz/utils/NumberUtilTest.kt)

```kotlin
// 1. 字符串转整数: String?.convert2Int(defaultValue: Int, radix: Int = 10) 
"88".convert2Int(0, 10) // 88
"1e6".convert2Int(0, 10) // 转换失败, 返回默认值 0
"3.8".convert2Int(0, 10) // 转换失败, 返回默认值 0

// 2. 字符串转长整形: String?.convert2Long(defaultValue: Long, radix: Int = 10)
"88".convert2Long(0, 10) // 88
"9223372036854775807L".convert2Long(defaultValue) // 9223372036854775807L
"1e6".convert2Long(0, 10) // 转换失败, 返回默认值 0
"3.8".convert2Long(0, 10) // 转换失败, 返回默认值 0

// 3. 字符串转浮点数: String?.convert2Float(defaultValue: Float)
"88.0".convert2Float(0) // 88.0f
"88.0f".convert2Float(0) // 88.0f
"1e6".convert2Float(0) // 1000000f

// 4. 字符串转双精度浮点数: String?.convert2Double(defaultValue: Double)
"".convert2Double(0) // 返回默认值 0.0
"3.8".convert2Double(0) // 3.8
"1e6".convert2Double(0) // 1000000.0

```

### 浮点数转字符串

```kotlin
// 1. 科学记数法转字符串: Double?.scientificNotation2str()
2.019110601E9.scientificNotation2str() // "2019110601"
2.019110601.scientificNotation2str() // "2.019110601"

// 2. 保留指定位数的小数: Double?.double2fixStr(decimalCount: Int = 2, retainZeroTail: Boolean = true)
3.654321.double2fixStr() // "3.65"
3.654321.double2fixStr(1) // "3.7"
3.001234.double2fixStr(retainZeroTail = false) // "3"
3.001234.double2fixStr(retainZeroTail = true) // "3.00"
```
