@file:Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")

package org.lynxz.utils

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.os.Environment
import android.os.StatFs
import android.text.TextUtils
import org.lynxz.utils.log.LoggerUtil
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.Arrays
import java.util.regex.Pattern

/**
 * 文件相关操作
 * 包括: 创建, 删除, 复制, 重命名, 信息获取, 读/写(覆盖,追加)等
 * */
object FileUtil {
    private const val TAG = "FileUtil"

    /**
     * 检查文件是否存在
     */
    @JvmStatic
    fun isExist(filepath: String?): Boolean {
        return !filepath.isNullOrBlank() && File(filepath).exists()
    }

    /**
     * 判断指定目录是否为空
     *
     * @param folderPath 目录路径
     */
    @JvmStatic
    fun isDirEmpty(folderPath: String?): Boolean {
        val files = listSubFiles(folderPath)
        val size = files.size
        return size == 0
    }

    /**
     * 创建文件
     */
    @JvmStatic
    @JvmOverloads
    fun create(
        absPath: String?, // 文件绝对路径,若为空,则返回false
        isDirPath: Boolean = false, // [absPath] 是否表示目录路径
        recreateIfExist: Boolean = false // 若目标文件存在, 确认是否删除重建
    ): Boolean {
        if (absPath.isNullOrBlank()) {
            return false
        }

        val targetFile = File(absPath)
        var exists = targetFile.exists()
        val isDirectory = targetFile.isDirectory // 目标文件是否是目录
        // 强制删除重建 或者 文件类型不匹配时也需要删除重建
        if (recreateIfExist
            || (exists && isDirPath && !isDirectory)
            || (exists && !isDirPath && isDirectory)
        ) {
            delete(targetFile)
        }
        targetFile.parentFile.mkdirs() // 尝试创建父目录

        exists = targetFile.exists() // 此时存在则表明类型匹配
        if (exists) {
            return true
        }

        return if (isDirPath) targetFile.mkdirs() else targetFile.createNewFile()
    }

    /**
     * 删除文件
     */
    @JvmStatic
    fun delete(target: String?): Boolean {
        if (target.isNullOrBlank()) {
            return true
        }
        return delete(File(target))
    }

    /**
     * 删除指定目录下符合正则规则的文件
     */
    fun deleteByPattern(
        dirPath: String,
        fileNamePattern: String,
        ignoreCase: Boolean = false
    ): Boolean = !listSubFileNames(dirPath, fileNamePattern, ignoreCase)
        .map { "$dirPath/$it" }
        .map { processPath(it) }
        .map { delete(it) }
        .any { !it }

    /**
     * 删除文件
     * P.S. 建议调用方自行先对待删除的文件/目录进行重命名,然后调用本方法
     */
    @JvmStatic
    fun delete(target: File): Boolean {
        if (!target.exists()) { // 文件不存在,等效于删除成功
            return true
        }

        // 非目录文件,直接删除
        if (!target.isDirectory) {
            return target.delete()
        }

        // 目录文件,递归删除
        for (file in target.listFiles()) {
            delete(file)
        }
        return target.delete() // 删除空目录本身
    }

    /**
     * 移动文件到指定位置并重命名
     *
     * @param srcFilePath  源文件绝对路径
     * @param destFilePath 要移动到的目标位置绝对路径
     */
    @JvmStatic
    fun rename(srcFilePath: String, destFilePath: String): Boolean {
        val srcFile = File(srcFilePath)
        if (!srcFile.exists()) {
            return false
        }
        val dest = File(destFilePath)
        dest.parentFile.mkdirs()
        return srcFile.renameTo(dest)
    }

    /**
     * 读取文件原始字节数组
     */
    @JvmStatic
    fun readAllBytes(absPath: String?): ByteArray {
        var result = ByteArray(0)
        if (absPath.isNullOrBlank()) {
            return result
        }
        val file = File(absPath)
        if (!file.exists()) {
            return result
        }
        var channel: FileChannel? = null
        var fs: FileInputStream? = null
        return try {
            fs = FileInputStream(file)
            channel = fs.channel
            val byteBuffer = ByteBuffer.allocate(channel.size().toInt())
            while (channel.read(byteBuffer) > 0) {
                // do nothing
            }
            result = byteBuffer.array()
            result
        } catch (e: IOException) {
            e.printStackTrace()
            result
        } finally {
            channel.closeSafety()
            fs.closeSafety()
        }
    }

    /**
     * 按行读取指定文件的所有内容
     */
    @JvmStatic
    fun readAllLine(absPath: String?): List<String> {
        val contentList = emptyList<String>()
        if (absPath.isNullOrBlank()) {
            return contentList
        }

        val file = File(absPath)
        if (!file.exists()) {
            return contentList
        }
        return file.readLines()
    }

    /**
     * 写文件
     * 若文件不存在,则会自动创建
     *
     * @param msg         写入的内容
     * @param absFilePath 文件绝对路径
     * @param append      是否是追加模式,默认为 false
     * @param autoAddCTRL 自动在结尾添加回测换行符,默认为 false
     * @return 是否写入成功
     */
    @JvmStatic
    @JvmOverloads
    fun writeToFile(
        msg: String?,
        absFilePath: String,
        append: Boolean = false,
        autoAddCTRL: Boolean = false
    ): Boolean {
        val tAbsPath = processPath(absFilePath)
        val tMsg = msg ?: ""

        if (!create(tAbsPath, isDirPath = false, recreateIfExist = false)) { // 文件不存在
            return false
        }

        var fw: FileWriter? = null
        var bfw: BufferedWriter? = null
        return try {
            val file = File(tAbsPath)
            fw = FileWriter(file, append)
            bfw = BufferedWriter(fw)
            bfw.write(tMsg)
            if (autoAddCTRL) {
                bfw.write("\r\n")
            }
            bfw.flush()
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        } finally {
            bfw.closeSafety()
            fw.closeSafety()
        }
    }

    /**
     * 写入字节数组到文件中
     * @param msg ByteArray 待写入的字节数组
     * @param absFilePath String 文件绝对路径
     * @param append Boolean 是否是追加模式
     * @return Boolean
     */
    @JvmStatic
    fun writeToFile(
        msg: ByteArray, // 未避免传null时跟重载的String方法混乱,此处不允许传null
        absFilePath: String,
        append: Boolean = false,
    ): Boolean {
        val tAbsPath = processPath(absFilePath)

        // 待写入内容为空,则表示清空原有内容,直接强制创建文件即可
        if (msg.isEmpty()) {
            return create(tAbsPath, isDirPath = false, recreateIfExist = true)
        }

        // 尝试创建目标文件
        if (!create(tAbsPath)) {
            return false
        }

        var os: FileOutputStream? = null
        return try {
            os = FileOutputStream(tAbsPath, append)
            os.write(msg)
            os.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            os.closeSafety()
        }
    }

    /**
     * 复制文件到指定位置
     *
     * @param fromPath 源文件路径,可以是目录
     * @param toPath 目标位置
     * @return 复制成功或失败(对单文件复制结果准确)
     */
    @JvmStatic
    fun copy(fromPath: String?, toPath: String): Boolean {
        val absPath = processPath(fromPath)
        if (!isExist(absPath)) {
            return false
        }

        val sep = File.separator
        val srcFile = File(absPath)
        var copyResult = true

        if (srcFile.isDirectory) { // 目录-递归处理
            create(toPath, true)
            val files = srcFile.listFiles()
            val len = files?.size ?: 0
            if (len >= 1) {
                for (i in 0 until len) {
                    val file = files!![i]
                    val dstFilePath = toPath + sep + file.name
                    val copySuccess = copy(file.absolutePath, dstFilePath)
                    if (!copySuccess) { // 任一文件复制失败,则退出,整个目录复制失败
                        copyResult = false
                        break
                    }
                }
            }
        } else { // 文件-直接复制
            var inputStream: InputStream? = null
            var os: FileOutputStream? = null
            val dstPath = if (toPath.endsWith(sep)) "${toPath}${srcFile.name}" else toPath
            create(dstPath)

            val dstFile = File(dstPath)
            try {
                inputStream = FileInputStream(srcFile)
                os = FileOutputStream(dstFile)
                val buffer = ByteArray(2048)
                var readLen = inputStream.read(buffer)
                while (readLen != -1) {
                    os.write(buffer, 0, readLen)
                    readLen = inputStream.read(buffer)
                }
                os.flush()
                copyResult = true
            } catch (e: Exception) {
                e.printStackTrace()
                copyResult = false
            } finally {
                inputStream.closeSafety()
                os.closeSafety()
            }
        }
        return copyResult
    }

    /**
     * 列出指定路径目录的所有子文件列表(只包含一级子文件, 按照文件修改时间递增/递减排序返回)
     *
     * @param folderPath 目录路径
     * @param ascending  是否按照升序排列 true-升序  false-降序
     */
    fun listSubFiles(folderPath: String?, ascending: Boolean): Array<File> {
        val files: Array<File> = listSubFiles(folderPath) // orderByModifiedTs
        val len = files.size
        if (len <= 1) {
            return files
        }

        // 创建一个保存文件和时间戳的二维数组
        val fileWithTS = Array(len) { arrayOf<Long>(2) }
        for (i in 0 until len) {
            fileWithTS[i][0] = i.toLong() // 保存原始索引
            fileWithTS[i][1] = files[i].lastModified() // 保存时间戳
        }

        // 根据时间戳升序排序
        if (ascending) {
            Arrays.sort(fileWithTS, Comparator.comparingLong { o: Array<Long> -> o[1] })
        } else { // 根据时间戳降序排序
            Arrays.sort(fileWithTS) { o1: Array<Long>, o2: Array<Long> -> o2[1].compareTo(o1[1]) }
        }

        // 重新排列文件数组
        val sortedFiles = arrayOf<File>()
        for (i in 0 until len) {
            sortedFiles[i] = files[fileWithTS[i][0].toInt()]
        }
        return sortedFiles
    }

    /**
     * 列出指定路径目录的所有子文件列表(只包含一级子文件)
     * 若所给路径并未表示目录, 则返回空数据
     *
     * @param filePath   文件路径,若表示非目录,则返回空数组
     * @param comparator 对目录下的子文件进行排序,若为null,则不做排序,直接返回
     */
    @JvmStatic
    fun listSubFiles(filePath: String?, comparator: Comparator<File>? = null): Array<File> {
        val absPath = processPath(filePath)
        val emptyList = arrayOf<File>()
        if (absPath.isBlank()) {
            return emptyList
        }
        val folder = File(absPath)
        val files = folder.listFiles() ?: emptyList
        if (files.isNotEmpty() && comparator != null) {
            Arrays.sort(files, comparator)
        }
        return files
    }

    /**
     * 获取指定目录下, 文件名满足指定正则规则的子文件名
     *
     * @param dirPath         目录绝对路径, 只判断其一级子文件名
     * @param fileNamePattern 文件名正则表达式
     * @param ignoreCase      是否忽略大小写
     * @return 返回符合条件的文件名列表, 可能多个
     */
    fun listSubFileNames(
        dirPath: String?,
        fileNamePattern: String,
        ignoreCase: Boolean = false
    ): List<String> {
        val pattern = Pattern.compile(fileNamePattern)
        return listSubFiles(dirPath)
            .map { it.name }
            .filter { pattern.matcher(ignoreCase.yes { it.lowercase() }.otherwise { it }).find() }
            .toList()
    }

    /**
     * 获取指定文件的字节大小, 若文件不存在则返回0
     */
    @JvmStatic
    fun getLen(absPath: String?): Long {
        return if (absPath.isNullOrBlank()) return 0 else File(absPath).length()
    }

    /**
     * 对文件路径进行处理, 对(反)斜杠进行处理
     * @param trimLastSep 是否要删除路径最后的分隔符,默认为false
     * @return String
     */
    @JvmStatic
    @JvmOverloads
    fun processPath(path: String?, trimLastSep: Boolean = false): String {
        var tPath = path?.replace("\\", File.separator)?.replace("//", File.separator) ?: ""
        if (trimLastSep && tPath.endsWith(File.separator)) {
            tPath = tPath.substring(0, tPath.length - File.separator.length)
        }
        return tPath
    }

    /**
     * 获取文件名, 包括扩展名, 如: x.9.png
     * 以"/"切分路径并提取最后的非空字符串
     *
     * @param filePath 文件路径
     */
    @JvmStatic
    fun getName(filePath: String?): String {
        val tPath = processPath(filePath)
        val arr = tPath.split(File.separator).toTypedArray()
        return arr.reversedArray().firstOrNull { it.isNotBlank() } ?: ""
    }

    /**
     * @param absPath 文件路径
     * @return 文件扩展名(不包括点.), 若是点9文件,则返回 "9.png"
     */
    @JvmStatic
    fun getExt(absPath: String?): String {
        val fileName = getName(absPath)
        var fileExt = ""
        if (fileName.isBlank()) {
            return fileExt
        }
        val split = fileName.split(".").toTypedArray()
        val len = split.size
        if (len >= 2) {
            fileExt = split[len - 1]
        }
        if (len >= 3) { // 可能是点9文件, 如 .9.png, .9.avsg 等
            val subFileExt = split[len - 2]
            if ("9" == subFileExt) {
                fileExt = "9.$fileExt"
            }
        }
        return fileExt
    }

    /**
     * 获取指定文件及其子文件(若存在)的修改时间
     *
     * @param getSubFileModifiedTime  true-提取子文件修改时间(目录有效) false-返回指定文件(目录)修改时间
     */
    @JvmStatic
    fun getLastModified(
        filePath: String,
        getSubFileModifiedTime: Boolean = false
    ): HashMap<String, Long> {
        val abspath = processPath(filePath)
        val ts = HashMap<String, Long>()
        if (abspath.isBlank()) {
            ts[abspath] = 0L
            return ts
        }
        val file = File(abspath)
        if (isExist(abspath)) {
            ts[abspath] = file.lastModified()
            if (getSubFileModifiedTime) { // 是否获取子文件时间
                val files = file.listFiles() // 非目录时返回null
                val length = files?.size ?: 0
                if (length >= 1) {
                    for (i in 0 until length) {
                        val subFile = files!![i]
                        ts[subFile.absolutePath] = subFile.lastModified()
                    }
                }
            }
        }
        return ts
    }

    /**
     * 从文件中读取满足指定条件的首行信息
     *
     * @param filePath      文件绝对路径
     * @param reverse       是否反向读取 true-从文件最后一行开始往前读取非空行
     * @param skipEmptyLine 是否跳过空白行
     * @param defaultMsg    读取异常时,默认返回的内容
     * @return
     */
    @JvmStatic
    fun getFirstLineInfo(
        filePath: String?,
        reverse: Boolean,
        skipEmptyLine: Boolean,
        defaultMsg: String?
    ): String? {
        val allLInes = readAllLine(filePath)
        val size = allLInes.size
        if (size == 0) {
            return defaultMsg
        }
        if (reverse) {
            for (i in size - 1 downTo 0) {
                val line = allLInes[i].trim { it <= ' ' }
                if (skipEmptyLine && line.isEmpty()) {
                    continue
                }
                return line
            }
        } else {
            for (i in 0 until size) {
                val line = allLInes[i].trim { it <= ' ' }
                if (skipEmptyLine && line.isEmpty()) {
                    continue
                }
                return line
            }
        }
        return defaultMsg
    }

    /**
     * 保存图片到文件文件
     *
     * @param filePath 要保存的路径
     */
    fun saveImage(bitmap: Bitmap?, filePath: String): Boolean {
        if (bitmap == null || TextUtils.isEmpty(filePath)) {
            return false
        }

        var out: FileOutputStream? = null
        try {
            create(filePath)
            val file = File(filePath)
            out = FileOutputStream(file)
            val format =
                if (Bitmap.Config.ARGB_8888 == bitmap.config) CompressFormat.PNG else CompressFormat.JPEG
            bitmap.compress(format, 100, out)
            out.flush()
            return true
        } catch (e: Exception) {
            LoggerUtil.e(TAG, "saveImage failed filePath=$filePath,errorMsg=${e.message}")
            e.printStackTrace()
            return false
        } finally {
            out.closeSafety()
        }
    }

    /**
     * 获取可用空间大小,单位:MB
     */
    fun getAvailableSpaceMB(): Long {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val availableBlocks = stat.availableBlocksLong
        return blockSize * availableBlocks / (1024 * 1024) //返回M
    }

    enum class FileUnit {
        B, KB, MB, GB
    }

    /**
     * 创建指定大小的文件
     *
     * @param path 文件绝对路径
     * @param len  文件大小
     * @param unit 单位，B,KB,MB，GB, 默认:B (字节)
     */
    fun createFile(path: String, len: Int, unit: FileUnit = FileUnit.B): Boolean {
        //指定每次分配的块大小
        val kb1 = 1024 // 1kb
        val mb1 = 1024 * 1024 // 1mb
        val mb10 = 1024 * 1024 * 10 // 10mb
        val lenB = when (unit) { // 要创建的文件大小,单位:B
            FileUnit.B -> len
            FileUnit.KB -> len * 1024
            FileUnit.MB -> len * 1024 * 1024
            FileUnit.GB -> len * 1024 * 1024 * 1024
        }

        // 删除重建文件
        create(path, recreateIfExist = true)
        var fos: FileOutputStream? = null
        val file = File(path)
        try {
            val batchSize = when {
                lenB < mb1 -> kb1
                lenB < mb10 -> mb1
                else -> mb10
            }

            val count = lenB / batchSize
            val last = lenB % batchSize

            fos = FileOutputStream(file)
            val fileChannel = fos.channel
            for (i in 0 until count) {
                val buffer = ByteBuffer.allocate(batchSize)
                fileChannel.write(buffer)
            }

            if (last != 0) {
                val buffer = ByteBuffer.allocate(last)
                fileChannel.write(buffer)
            }
            return true
        } catch (e: IOException) {
            LoggerUtil.e(TAG, "createFile by len=$lenB,fail:${e.message}")
            e.printStackTrace()
        } finally {
            fos.closeSafety()
        }
        return false
    }
}