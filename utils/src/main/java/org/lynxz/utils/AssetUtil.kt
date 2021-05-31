package org.lynxz.utils

import android.content.Context
import java.io.*

/**
 * 项目asset工具类
 * 支持主项目和espresso测试项目资源
 */
object AssetUtil {

    /**
     * 文件是否存在
     * @param name 文件名如: c 表示 assets/{parentRelPath}/name
     * @param parentRelPath 文件所在目录相对路径,如: a/b 表示 asset/a/b/ 目录, 默认表示assets/目录
     *          兼容了写法 a/b/ (结尾分隔符), 会删除结尾的分隔符
     */
    fun isExist(context: Context, name: String, parentRelPath: String = "") =
            if (name.isBlank()) false else context.assets.list(FileUtil.processPath(parentRelPath, true))?.contains(name)
                    ?: false

    /**
     * 判断指定路径的asset文件是否是非空目录
     * 对于普通文件,直接返回false
     * @param name 目录名或其相对路径,如: c 表示 asset/{parentRelPath}/name
     * @param parentRelPath 文件所在目录相对路径,如: a/b 表示 asset/a/b/ 目录
     */
    fun isNotEmptyDir(context: Context, name: String, parentRelPath: String = ""): Boolean {
        if (name.isBlank()) {
            return false
        }
        var path = if (parentRelPath.isBlank()) name else "$parentRelPath/$name"
        path = FileUtil.processPath(path)
        if (path.endsWith("/")) {
            path = path.substring(0, path.length - 1)
        }

        return context.assets.list(path)?.size ?: -1 > 0
    }

    /**
     * 读取assets文件内容字节数组
     *
     * @param assetFilePath assets文件相对路径, 如: f/a, 表示 assets/f/a 文件
     */
    fun readAllBytes(context: Context, assetFilePath: String?): ByteArray {
        var result: ByteArray = byteArrayOf()
        if (assetFilePath.isNullOrBlank()) {
            return result
        }

        var inputStream: InputStream? = null
        return try {
            inputStream = context.assets.open(assetFilePath)
            result = ByteArray(inputStream.available())
            inputStream.read(result)
            result
        } catch (e: IOException) {
            e.printStackTrace()
            result
        } finally {
            inputStream.closeSafety()
        }
    }

    /**
     * 读取assets文件内容字符串数组,作用于文本文件
     *
     * @param assetFilePath assets文件相对路径, 如: f/a, 表示 assets/f/a 文件
     */
    fun readAllLines(context: Context, assetFilePath: String?): List<String> {
        var result = listOf<String>()
        if (assetFilePath.isNullOrBlank()) {
            return result
        }
        var inputStream: InputStream? = null
        var ir: InputStreamReader? = null
        var br: BufferedReader? = null
        return try {
            inputStream = context.assets.open(assetFilePath)
            ir = InputStreamReader(inputStream)
            br = BufferedReader(ir)
            result = br.readLines()
            result
        } catch (e: IOException) {
            e.printStackTrace()
            result
        } finally {
            inputStream.closeSafety()
            ir.closeSafety()
            br.closeSafety()
        }
    }

    /**
     * 复制assets文件到指定目录
     *
     * @param destDiPath         要复制的目标目录路径,要求可读写,asset文件会被复制到该目录下,并生成同名文件
     * @param assetFilePath      待复制的assert文件相对路径
     * @param isDir              asset文件是否是目录, 对于递归空子目录,暂时无法通过api确定其是否是目录,复制为普通文件
     * @param forceCopyEvenExist true-无论dest文件是否存在,都进行复制  false-若目标文件存在,则不复制
     * @return 是否复制成功
     */
    fun copy(
            context: Context,
            destDiPath: String,
            assetFilePath: String,
            isDir: Boolean = false,
            forceCopyEvenExist: Boolean = false
    ): Boolean {
        val assetName = FileUtil.getName(assetFilePath)
        val destFilePath = FileUtil.processPath("${destDiPath}/$assetName")
        if (!forceCopyEvenExist && FileUtil.isExist(destFilePath)) {
            return true
        }
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        return try {
            FileUtil.create(destFilePath, isDir)
            val assetManager = context.assets
            if (isDir) { // 目录, 递归复制子文件
                val fileNames = assetManager.list(assetFilePath)
                // val size = if (fileNames.isNullOrEmpty()) 0 else fileNames.size
                fileNames?.forEach { name ->
                    val tAssetFilePath = FileUtil.processPath("$assetFilePath/$name")
                    val isNotEmptyDir = isNotEmptyDir(context, tAssetFilePath) // 是否是非空目录
                    copy(context, destFilePath, tAssetFilePath, isNotEmptyDir, forceCopyEvenExist)
                }
            } else { // 普通文件,直接复制
                inputStream = assetManager.open(assetFilePath)
                outputStream = FileOutputStream(destFilePath)
                val buff = ByteArray(1024)
                var len: Int
                while (inputStream.read(buff).also { len = it } >= 0) {
                    outputStream.write(buff, 0, len)
                }
                outputStream.flush()
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        } finally {
            inputStream.closeSafety()
            outputStream.closeSafety()
        }
    }
}