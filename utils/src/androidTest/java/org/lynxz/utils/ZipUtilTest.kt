package org.lynxz.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZipUtilTest {
    @Test
    fun zipFileTest() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val absCopyDirPath = "${context.getExternalFilesDir(null)?.absolutePath}/zip/"
        var success = AssetUtil.copy(context, absCopyDirPath, "doc/test_t02.txt")
        Assert.assertTrue("拷贝文件失败", success)

        val srcFilePath = "${absCopyDirPath}/test_t02.txt"
        val srcContent = FileUtil.readAllLine(srcFilePath)

        // 验证压缩方法
        success = ZipUtils.zipFile(srcFilePath)
        Assert.assertTrue("压缩test_t02.txt失败", success)
        val zipPath = "${absCopyDirPath}/test_t02.zip"
        Assert.assertTrue("test_t02.zip不存在,压缩失败,请排查", FileUtil.isExist(zipPath))

        // 验证解压缩方法
        success = FileUtil.delete(srcFilePath)
        Assert.assertTrue("删除源文件失败", success)
        Assert.assertFalse("源文件仍存在,请检查", FileUtil.isExist(srcFilePath))
        success = ZipUtils.unzipFile(zipPath)
        Assert.assertTrue("解压缩test_t02.zip失败", success)
        Assert.assertTrue("test_t02.txt不存在,压缩失败,请排查", FileUtil.isExist(srcFilePath))

        // 验证解压后文件内容与源文件一致
        val zipContent = FileUtil.readAllLine(srcFilePath)
        Assert.assertEquals("解压后文件与源文件内容一致", srcContent, zipContent)
    }

    @Test
    fun zipDirTest() {

    }
}