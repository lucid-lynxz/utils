package org.lynxz.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssetUtilTest {

    @Test
    fun isExistTest() {
        // 访问的是 utils/src/androidTest/assets/**
        val context = InstrumentationRegistry.getInstrumentation().context
        Assert.assertTrue(AssetUtil.isExist(context, "test_02.jpg", "img"))
        Assert.assertFalse(AssetUtil.isExist(context, "test_02.jpg", "img/"))
        Assert.assertTrue(AssetUtil.isExist(context, "test_01.jpg", "img/png"))

        // 不支持和该种写法
        Assert.assertFalse(AssetUtil.isExist(context, "img/png/test_01.jpg"))
        Assert.assertFalse(AssetUtil.isExist(context, "png/test_01.jpg", "img"))
    }

    @Test
    fun isNotEmptyDirTest() {
        // 访问的是 utils/src/androidTest/assets/**
        val context = InstrumentationRegistry.getInstrumentation().context
        Assert.assertFalse(AssetUtil.isNotEmptyDir(context, "emptyDir", ""))
        Assert.assertFalse(AssetUtil.isNotEmptyDir(context, "test_02.jpg", "img"))
        Assert.assertFalse(AssetUtil.isNotEmptyDir(context, "test_02.jpg", "img/"))
        Assert.assertFalse(AssetUtil.isNotEmptyDir(context, "test_01.jpg", "img/png"))
        Assert.assertFalse(AssetUtil.isNotEmptyDir(context, ""))
        Assert.assertFalse(AssetUtil.isNotEmptyDir(context, " ", " "))

        Assert.assertTrue(AssetUtil.isNotEmptyDir(context, "img"))
        Assert.assertTrue(AssetUtil.isNotEmptyDir(context, "img/"))
        Assert.assertTrue(AssetUtil.isNotEmptyDir(context, "img/png"))
        Assert.assertTrue(AssetUtil.isNotEmptyDir(context, "img/png/"))
        Assert.assertTrue(AssetUtil.isNotEmptyDir(context, "img\\/png/"))
        Assert.assertTrue(AssetUtil.isNotEmptyDir(context, "png", "img"))
        Assert.assertTrue(AssetUtil.isNotEmptyDir(context, "png/", "img"))
        Assert.assertTrue(AssetUtil.isNotEmptyDir(context, "png\\", "img"))
    }

    @Test
    fun readLineTest() {
        // 访问的是 utils/src/androidTest/assets/**
        val context = InstrumentationRegistry.getInstrumentation().context
        val txtPath01 = "test_t01.txt"
        val txtPath02 = "doc/test_t02.txt"

        val txt1Lines = AssetUtil.readAllLines(context, txtPath01)
        Assert.assertTrue(txt1Lines.size >= 7)
        Assert.assertEquals(txt1Lines[0], "t_L1")
        Assert.assertTrue(AssetUtil.readAllLines(context, txtPath02).isNotEmpty())
    }

    @Test
    fun readBytesTest() {
        // 访问的是 utils/src/androidTest/assets/**
        val context = InstrumentationRegistry.getInstrumentation().context
        val txtPath01 = "test_t01.txt"
        val txtPath02 = "doc/test_t02.txt"

        val bytes = AssetUtil.readAllBytes(context, txtPath01)
        val txt1Lines = String(bytes)
        Assert.assertTrue(txt1Lines.length >= 7)
        Assert.assertTrue(txt1Lines.startsWith("t_L1"))
        Assert.assertTrue(AssetUtil.readAllBytes(context, txtPath02).isNotEmpty())
    }

    @Test
    fun copyTest() {
        // 访问的是 utils/src/androidTest/assets/**
        val context = InstrumentationRegistry.getInstrumentation().context
        val absPathCopy = "${context.getExternalFilesDir(null)?.absolutePath}/b/abc_copy.txt"
        FileUtil.delete(absPathCopy)
        Assert.assertFalse(FileUtil.isExist(absPathCopy))

        val txtPath01 = "test_t01.txt"
        val txtPath02 = "doc/test_t02.txt"

        Assert.assertTrue(AssetUtil.copy(context, absPathCopy, txtPath01))
        Assert.assertTrue(FileUtil.isExist(absPathCopy))

        val absCopyDirPath = "${context.getExternalFilesDir(null)?.absolutePath}/b/"
        Assert.assertTrue(AssetUtil.copy(context, absCopyDirPath, "emptyDir", true))
        Assert.assertTrue(FileUtil.isExist(absPathCopy))

        val imgDirDestPath = context.getExternalFilesDir(null)?.absolutePath ?: ""
        FileUtil.delete(imgDirDestPath)
        Assert.assertFalse(FileUtil.isExist(imgDirDestPath))

        // 复制非空目录(空子目录emptyDir会被忽略)
        Assert.assertTrue(AssetUtil.copy(context, imgDirDestPath, "img/", true))
        Assert.assertTrue(FileUtil.isExist(imgDirDestPath))

        // 直接复制空目录
        Assert.assertTrue(AssetUtil.copy(context, imgDirDestPath, "emptyDir/", true))
        Assert.assertTrue(FileUtil.isExist("$imgDirDestPath/emptyDir"))
    }
}