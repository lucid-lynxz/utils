# FileUtil 工具类使用

## 主要功能
文件/目录创建, 删除, 复制, 重命名, 信息获取, 读/写(覆盖,追加)等
调用方自行确保目标文件有读/写等权限

## 方法介绍

可参考 [测试用例FileUtilTest](../utils/src/androidTest/java/org/lynxz/utils/FileUtilTest.kt)

```kotlin
val fPath = "/sdcard/abc.txt"
val dPath = "/sdcard/xyz/"

// 判断文件是否存在,支持传入目录路径
val bExist = FileUtil.isExist(fPath)

// 判断目录是否为空,若传入的是文件路径(fPath),返回true
val bEmpty = FileUtil.isDirEmpty(dPath)

// 创建文件/目录,支持传入目录/文件路径
// isDirPath = true表示创建目录,否则创建普通文件
// recreateIfExist = true 表示目标文件存在时,删除并新建,否则短路返回创建成功 
val bSuccess = FileUtil.create(fPath, isDirPath = true, recreateIfExist = true)

// 删除文件/目录
// P.S. 建议删除前先自行重命名目标文件
val bSuccess = FileUtil.delete(fPath)

// 重命名/移动文件, 依次传入源文件路径和目标文件路径
// 参数 srcFilePath 表示源文件绝对路径
// 参数 destFilePath 表示要移动到的目标位置绝对路径
val dPath = "/sdcard/abc_copy.txt"
val bSuccess = FileUtil.rename(srcFilePath = fPath, destFilePath = dPath)

// 文件内容读取
// 分为两种: 读取为字符串列表 以及 字节列表
// 参数 absPath 表示待读写的目标文件绝度路径
val bByteArray = FileUtil.readAllBytes(absPath = fPath)
val strList = FileUtil.readAllLine(absPath = fPath)

// 写入到文件(若文件不存在,会自动创建)
// 同样分为两种: 写入字符串 以及 字节数据
// 参数 msg 表示待写入的内容
// 参数 absFilePath 表示待写入的目标文件绝对路径
// 参数 append=true 表示追加到文件结尾, 否则为覆盖,默认为false
// 参数 autoAddCTRL 表示是否要再写入的内容
val bSuccess = FileUtil.writeToFile(msg ="", absFilePath = fPath, append = true, autoAddCTRL = true)
val bSuccess = FileUtil.writeToFile(msg = byteArrayOf(), absFilePath = fPath, append = true)

// 复制文件/目录到指定位置
// 返回是否复制成功(对于普通文件的复制结果准确)
val bSuccess = FileUtil.copy(fromPath = fPath, toPath = dPath)

// 获取目录下的子文件列表
// 若传入的是普通文件路径,则返回空列表
// 若需要对文件进行排序,则传入 comparator ,默认为null 
val fileArr = FileUtil.listSubFiles(filePath = dPath, comparator = null)

// 获取指定文件的字节大小, 若文件不存在则返回0
val byteLen = FileUtil.getLen(fPath)

// 获取文件名, 包括扩展名, 如: x.9.png
// 支持目录路径, 以"/"切分路径并提取最后的非空字符串作为文件名
val fileName = FileUtil.getName(fPath)

// 获取文件扩展名(不包括点.), 若是点9文件,则返回 "9.png"
val fileExt = FileUtil.getExt(fPath)

// 从文件中读取满足指定条件的首行信息
// 参数 reverse 表是是否从文件结尾开始读取
// 参数 skipEmptyLine 表是是否跳过空白行
// 参数 defaultMsg 表是读取异常时,默认返回的内容
val firstLineMsg = FileUtil.getFirstLineInfo(fPath, reverse = true, skipEmptyLine = true, defaultMsg = null)
```