package org.lynxz.utils

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileUtilTest {
    companion object {
        private const val TAG = "FileUtilTest"

        private val mCxt = InstrumentationRegistry.getInstrumentation().context
        private val mTCxt = InstrumentationRegistry.getInstrumentation().targetContext

        private val externalFilesDir = mCxt.getExternalFilesDir(null)
        private val tExternalFilesDir = mTCxt.getExternalFilesDir(null)
    }

    init {
        Log.d(TAG, "externalFilesDir=$externalFilesDir,tExternalFilesDir=$tExternalFilesDir")
    }

    @Test
    fun createTest() {
        val dirPath = externalFilesDir?.absolutePath
        FileUtil.delete(dirPath) // 清空目录,避免干扰断言

        val absPath = "$dirPath/abc.txt"
        val absPath1 = "${tExternalFilesDir?.absolutePath}/abc1.9.txt"
        val absPath2 = "${tExternalFilesDir?.absolutePath}/abcDir"
        Assert.assertTrue(FileUtil.create(absPath))
        Assert.assertTrue(FileUtil.create(absPath1))
        Assert.assertTrue(FileUtil.create(absPath2, true))

        Assert.assertTrue(FileUtil.isExist(absPath))
        Assert.assertTrue(FileUtil.isExist(absPath1))
        Assert.assertTrue(FileUtil.isExist(absPath2))

        Assert.assertEquals(0, FileUtil.getLen(absPath))
        Assert.assertEquals(0, FileUtil.getLen(absPath1))
        Assert.assertEquals(0, FileUtil.listSubFiles(absPath2).size)

        Assert.assertEquals("abc.txt", FileUtil.getName(absPath))
        Assert.assertEquals("abc1.9.txt", FileUtil.getName(absPath1))

        Assert.assertEquals("txt", FileUtil.getExt(absPath))
        Assert.assertEquals("9.txt", FileUtil.getExt(absPath1))
    }

    @Test
    fun deleteTest() {
        val dirPath = "${externalFilesDir?.absolutePath}/a"
        val absPath = "${dirPath}/b/abc.txt"
        Assert.assertTrue(FileUtil.create(absPath)) // 创建单个普通文件
        Assert.assertTrue(FileUtil.delete(absPath))  // 删除普通文件
        Assert.assertFalse(FileUtil.isExist(absPath)) // 确认文件已删除

        FileUtil.create(dirPath, true, recreateIfExist = true) // 确保目录已清空

        val absPath2 = "${dirPath}/c/xyz.txt"
        Assert.assertTrue(FileUtil.writeToFile("abc", absPath)) // 创建文件并写入内容
        Assert.assertTrue(FileUtil.writeToFile("xyz", absPath2))

        Assert.assertEquals(2, FileUtil.listSubFiles(dirPath).size)

        Assert.assertTrue(FileUtil.delete(dirPath)) // 删除目录(包含有子目录)
        Assert.assertFalse(FileUtil.isExist(absPath)) // 确认目录已递归删除
        Assert.assertFalse(FileUtil.isExist(absPath2))
        Assert.assertFalse(FileUtil.isExist(dirPath))
    }

    @Test
    fun writeTest() {
        val absPath = "${externalFilesDir?.absolutePath}/a/abc.txt"
        Assert.assertTrue(FileUtil.create(absPath, recreateIfExist = true))

        Assert.assertTrue(FileUtil.writeToFile("", absPath))
        Assert.assertTrue(FileUtil.readAllBytes(absPath).isEmpty())
        Assert.assertEquals(0, FileUtil.readAllLine(absPath).size)

        Assert.assertTrue(FileUtil.writeToFile(null, absPath))
        Assert.assertTrue(FileUtil.readAllBytes(absPath).isEmpty())
        Assert.assertEquals(0, FileUtil.readAllLine(absPath).size)

        Assert.assertTrue(FileUtil.writeToFile("hello", absPath))
        Assert.assertEquals(5, FileUtil.readAllBytes(absPath).size)
        Assert.assertEquals(1, FileUtil.readAllLine(absPath).size)

        Assert.assertTrue(FileUtil.writeToFile("", absPath, append = true, autoAddCTRL = true))
        Assert.assertEquals(1, FileUtil.readAllLine(absPath).size) // 最后的空白行不算

        Assert.assertTrue(FileUtil.writeToFile("line2", absPath, append = true, autoAddCTRL = true))
        Assert.assertEquals(2, FileUtil.readAllLine(absPath).size)

        Assert.assertTrue(FileUtil.writeToFile(null, absPath, append = true))
        Assert.assertEquals(2, FileUtil.readAllLine(absPath).size)
    }

    @Test
    fun writeByteTest() {
        val absPath = "${externalFilesDir?.absolutePath}/a/abc_byte.bin"
        Assert.assertTrue(FileUtil.create(absPath, recreateIfExist = true))

        Assert.assertTrue(FileUtil.writeToFile(byteArrayOf(), absPath))
        Assert.assertTrue(FileUtil.readAllBytes(absPath).isEmpty())

        val ba = byteArrayOf(1, 2, 3, 4, 5)
        Assert.assertTrue(FileUtil.writeToFile(ba, absPath))
        Assert.assertTrue(FileUtil.readAllBytes(absPath).size == ba.size)

        Assert.assertTrue(FileUtil.writeToFile(msg = ba, absFilePath = absPath, append = true))
        Assert.assertTrue(FileUtil.readAllBytes(absPath).size == ba.size * 2)
        Assert.assertTrue(FileUtil.readAllBytes(absPath)[0] == ba[0])

        Assert.assertTrue(FileUtil.writeToFile(ba, absPath, false))
        Assert.assertTrue(FileUtil.readAllBytes(absPath).size == ba.size)
        Assert.assertTrue(FileUtil.readAllBytes(absPath)[0] == ba[0])
    }


    @Test
    fun copyTest() {
        val absPath = "${externalFilesDir?.absolutePath}/a/abc.txt"
        Assert.assertTrue(FileUtil.create(absPath, recreateIfExist = true))

        val absPathCopy = "${externalFilesDir?.absolutePath}/b/abc_copy.txt"
        Assert.assertTrue(FileUtil.copy(absPath, absPathCopy))
        Assert.assertTrue(FileUtil.isExist(absPathCopy))

        val absPath2 = "${externalFilesDir?.absolutePath}/a/xyz.txt"
        Assert.assertTrue(FileUtil.writeToFile("hello", absPath2))

        val absPathCopyDir1 = "${externalFilesDir?.absolutePath}/a"
        val absPathCopyDir2 = "${externalFilesDir?.absolutePath}/c"
        Assert.assertTrue(FileUtil.copy(absPathCopyDir1, absPathCopyDir2))
        Assert.assertTrue(FileUtil.isExist("${absPathCopyDir2}/abc.txt"))
        Assert.assertTrue(FileUtil.isExist("${absPathCopyDir2}/xyz.txt"))
    }

    @Test
    fun renameTest() {
        val srcFilePath = "${externalFilesDir?.absolutePath}/a/abc.txt"
        Assert.assertTrue(FileUtil.writeToFile("helloAbc", srcFilePath))
        Assert.assertTrue(FileUtil.isExist(srcFilePath))

        val destFilePath = "${externalFilesDir?.absolutePath}/aaa/abc2.txt"
        Assert.assertTrue(FileUtil.rename(srcFilePath, destFilePath))
        Assert.assertFalse(FileUtil.isExist(srcFilePath))
        Assert.assertTrue(FileUtil.isExist(destFilePath))
    }

    @Test
    fun isDirEmptyTest() {
        val srcFilePath = "${externalFilesDir?.absolutePath}/a/abc.txt"
        Assert.assertTrue(FileUtil.create(srcFilePath))
        Assert.assertTrue(FileUtil.isDirEmpty(srcFilePath)) // 作用于已存在的文件,判断为空

        val srcFilePath1 = "${externalFilesDir?.absolutePath}/ab"
        Assert.assertTrue(FileUtil.create(srcFilePath1, isDirPath = true, recreateIfExist = true))
        Assert.assertTrue(FileUtil.isDirEmpty(srcFilePath1)) // 作用于空目录,判断为空

        Assert.assertTrue(FileUtil.create("${srcFilePath1}/abc.txt"))
        Assert.assertFalse(FileUtil.isDirEmpty(srcFilePath1)) // 作用于非空目录,判断结果为非空

        Assert.assertTrue(FileUtil.delete(srcFilePath1))
        Assert.assertTrue(FileUtil.isDirEmpty(srcFilePath1)) // 作用于不存在的路径,判断为空
        Assert.assertTrue(FileUtil.isDirEmpty(null))
        Assert.assertTrue(FileUtil.isDirEmpty(""))
    }

    @Test
    fun getNameTest() {
        val fPath = "/sdcard/abc.txt"
        val dPath = "/sdcard/xyz/"
        val dPath1 = "/sdcard\\xyz\\"
        val dPath2 = "/sdcard/xyz\\/////"

        Assert.assertEquals("abc.txt", FileUtil.getName(fPath))
        Assert.assertEquals("xyz", FileUtil.getName(dPath))
        Assert.assertEquals("xyz", FileUtil.getName(dPath1))
        Assert.assertEquals("xyz", FileUtil.getName(dPath2))

        Assert.assertEquals("", FileUtil.getName(null))
        Assert.assertEquals("", FileUtil.getName(""))
        Assert.assertEquals("", FileUtil.getName("      \n\t "))
    }
}