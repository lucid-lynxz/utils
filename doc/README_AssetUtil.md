# `AssetUtil` 工具类的使用

## 主要功能

工程内 `assets/` 目录子文件的访问工具类, 包括判断是否存在, 复制, 读取操作

## 用法

可参考 [测试用例AssetUtilTest](../utils/src/androidTest/java/org/lynxz/utils/AssetUtilTest.kt)

```kotlin
// 1. 判断文件是否存在, 若: assets/a/b/abc.txt 存在则返回true
// 形参 `name` 表示文件名, `parentRelPath` 表示文件所在目录的相对路径,如 "a/b/" 表示 `assets/a/b/` 目录
val exist = AssetUtil.isExist(context, name = "abc.txt", parentRelPath = "a/b/")
val exist1 = AssetUtil.isExist(context, name = "abc.txt", parentRelPath = "a/b")

// 2. 判断目录是否为非空目录, 如下表示判断 assets/a/b 是否是非空目录, 若b是文件,则返回false 
val isNotEmptyDir = AssetUtil.isNotEmptyDir(context, "a/b")

// 3. 读取文件所有行数据
val txt1Lines: List<String> = AssetUtil.readAllLines(context, "a/b.txt")

// 4. 读取文件所有字节数据
val bytes: ByteArray = AssetUtil.readAllBytes(context, "a/b.txt")

// 5. 复制文件/目录,返回是否复制成功
val copySuccess = AssetUtil.copy(context, "destPath", "srcAssetPath", isDir = true, forceCopyEvenExist = true)
```