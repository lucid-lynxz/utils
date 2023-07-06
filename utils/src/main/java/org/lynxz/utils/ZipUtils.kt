package org.lynxz.utils

import java.io.*
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Created by lynxz on 2017
 * zip压缩工具
 */
object ZipUtils {
    private const val BUFF_SIZE = 4 * 1024
    private const val ZIP_SUFFIX = ".zip"

    fun zipFile(srcPath: String) = zipFile(File(srcPath))

    /**
     * 将指定的源文件压缩到当前目录下
     *
     * @param srcFile 要压缩的文件
     * @return true-压缩成功 false-压缩失败
     */
    fun zipFile(srcFile: File): Boolean {
        val srcFilePath = srcFile.absolutePath
        var zipPath = srcFilePath
        val ext = FileUtil.getExt(srcFilePath)
        if (ext.isNotEmpty()) {
            val nameOnly = srcFilePath.substring(0, srcFilePath.lastIndexOf(ext) - 1)
            zipPath = "${nameOnly}${ZIP_SUFFIX}"
        }
        return zipFile(srcFilePath, zipPath)
    }

    /**
     * 将指定的源文件压缩为zip文件
     * @param srcPath 待压缩的文件或目录路径
     * @param dstPath 压缩后的zip文件路径
     * @return 只要待压缩的文件有一个压缩失败就停止压缩并返回(等价于windows上直接进行压缩)
     */
    fun zipFile(srcPath: String, dstPath: String): Boolean {
        val srcFile = File(srcPath)
        if (!srcFile.exists()) {
            return false
        }
        val buffer = ByteArray(BUFF_SIZE)
        try {
            val zos = ZipOutputStream(FileOutputStream(dstPath))
            val result = zipFile(zos, "", srcFile, buffer)
            zos.close()
            return result
        } catch (ex: IOException) {
            ex.printStackTrace()
        }
        return false
    }

    /**
     * @param zos           压缩流
     * @param parentDirName 父目录
     * @param file          待压缩文件,要求文件存在
     * @param buffer        缓冲区
     * @return 只要目录中有一个文件压缩失败，就停止并返回
     */
    private fun zipFile(
        zos: ZipOutputStream, parentDirName: String, file: File, buffer: ByteArray
    ): Boolean {
        var zipFilePath = parentDirName + file.name
        return if (file.isDirectory) {
            zipFilePath += File.separator
            for (f in Objects.requireNonNull(file.listFiles())) {
                if (!zipFile(zos, zipFilePath, f, buffer)) {
                    return false
                }
            }
            true
        } else {
            try {
                val bis = BufferedInputStream(FileInputStream(file))
                val zipEntry = ZipEntry(zipFilePath)
                zipEntry.size = file.length()
                zos.putNextEntry(zipEntry)
                while (true) {
                    val len = bis.read(buffer)
                    if (len != -1) {
                        zos.write(buffer, 0, len)
                    } else {
                        break
                    }
                }
                bis.close()
                return true
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
            false
        }
    }

    /**
     * @param srcZipPath 待解压的zip文件
     * @param dstPath zip解压后待存放的目录路径,若为空,则解压到zip同目录下
     * @return 只要解压过程中发生错误，就立即停止并返回(等价于windows上直接进行解压)
     */
    fun unzipFile(srcZipPath: String, dstPath: String? = null): Boolean {
        if (srcZipPath.isBlank()) {
            return false
        }

        val srcFile = File(srcZipPath)
        if (!srcFile.exists()) {
            return false
        }

        var tDstPath = if (dstPath.isNullOrBlank()) {
            srcFile.parentFile!!.absolutePath
        } else {
            dstPath
        }

        val dstFile = File(tDstPath)
        if (!dstFile.exists() || !dstFile.isDirectory) {
            dstFile.mkdirs()
        }

        try {
            val zis = ZipInputStream(FileInputStream(srcFile))
            val bis = BufferedInputStream(zis)
            var zipEntry: ZipEntry? = null
            val buffer = ByteArray(BUFF_SIZE)
            if (!tDstPath.endsWith(File.separator)) {
                tDstPath += File.separator
            }
            while (zis.nextEntry.also { zipEntry = it } != null) {
                val fileName = tDstPath + zipEntry!!.name
                val file = File(fileName)
                val parentDir = file.parentFile
                if (!parentDir.exists()) {
                    parentDir.mkdirs()
                }
                val fos = FileOutputStream(file)
                while (true) {
                    val len = bis.read(buffer)
                    if (len != -1) {
                        fos.write(buffer, 0, len)
                    } else {
                        break
                    }
                }
                fos.close()
            }
            bis.close()
            zis.close()
            return true
        } catch (ex: IOException) {
            ex.printStackTrace()
        }
        return false
    }
}