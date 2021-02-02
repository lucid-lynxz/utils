package org.lynxz.utils

import android.app.Activity
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.widget.ScrollView
import java.nio.ByteBuffer

/**
 * 图片工具类
 */
object ImageUtil {
    fun eraseArrayBackground(
        roadEnlargeArrowTmp: Bitmap?,
        roadEnlargeBg: Bitmap?
    ): Bitmap? {
        if (roadEnlargeArrowTmp == null || roadEnlargeBg == null) {
            return null
        }
        val width = roadEnlargeArrowTmp.width
        val height = roadEnlargeArrowTmp.height
        val pixels = IntArray(width * height)
        val pixelsBack = IntArray(width * height)
        roadEnlargeArrowTmp.getPixels(pixels, 0, width, 0, 0, width, height)
        roadEnlargeBg.getPixels(pixelsBack, 0, width, 0, 0, width, height)
        val length = pixels.size
        for (i in 0 until length) {
            if (pixels[i] == -0xff01) {
                pixels[i] = pixelsBack[i]
            }
        }
        roadEnlargeArrowTmp.recycle()
        roadEnlargeBg.recycle()
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * 数组转图片
     */
    fun getImage(bytes: ByteArray?, opts: BitmapFactory.Options?): Bitmap? {
        return if (bytes != null) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } else null
    }

    /**
     * @param bytes  位图数据
     * @param width  图片宽
     * @param height 图片高
     * @param config 图片位深信息
     * @param mirror 镜像翻转
     */
    fun getImage(
        bytes: ByteArray,
        width: Int,
        height: Int,
        config: Bitmap.Config?,
        mirror: Boolean
    ): Bitmap? {
        val spic = Bitmap.createBitmap(width, height, config!!)
        var pic = spic
        return try {
            spic.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
            if (mirror) {
                val matrix = Matrix()
                matrix.postScale(1f, -1f)
                pic = Bitmap.createBitmap(spic, 0, 0, width, height, matrix, true)
            }
            pic
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 解析base64图片
     */
    fun getImage(base64Str: String?): Bitmap? = try {
        val bitmapArray: ByteArray = Base64.decode(base64Str, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bitmapArray, 0, bitmapArray.size)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }


    /**
     * 截取当前窗体的截图，根据[containStatusBar]判断是否包含当前窗体的状态栏
     * 原理是获取当前窗体decorView的缓存生成图片
     * P.S. 包含 surfaceView 的部分会是黑色
     */
    fun takeShotOfActivity(activity: Activity, containStatusBar: Boolean): Bitmap? {
        // 获取当前窗体的View对象
        val view = activity.window.decorView
        view.isDrawingCacheEnabled = true
        // 生成缓存
        view.buildDrawingCache()
        var result: Bitmap? = null
        result = if (containStatusBar) {
            // 绘制整个窗体，包括状态栏
            Bitmap.createBitmap(view.drawingCache, 0, 0, view.measuredWidth, view.measuredHeight)
        } else {
            val rect = Rect() // 获取状态栏高度
            view.getWindowVisibleDisplayFrame(rect)
            val defaultDisplay = activity.windowManager.defaultDisplay
            val sizePoint = Point()
            defaultDisplay.getSize(sizePoint)

            // 减去状态栏高度
//            result = Bitmap.createBitmap(view.getDrawingCache(), 0,
//                    rect.top, defaultDisplay.getWidth(), defaultDisplay.getHeight() - rect.top);
            Bitmap.createBitmap(
                view.drawingCache, 0,
                rect.top, sizePoint.x, sizePoint.y - rect.top
            )
        }
        view.isDrawingCacheEnabled = false
        view.destroyDrawingCache()
        return result
    }

    /**
     * 获取控件截图
     * 要求view已添加到界面上了
     */
    fun takeShotOfView(v: View): Bitmap =
        Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).apply {
            val c = Canvas(this)
            c.drawColor(Color.WHITE)
            v.draw(c)
        }


    /**
     * 对 surfaceView 控件进行截图, 需要api 24 (android 7.0+)
     */
    fun takeShotOfSurfaceView(surfaceView: SurfaceView, listener: onTakeShotListener?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val handler = Handler(Looper.getMainLooper())
            val bmp =
                Bitmap.createBitmap(surfaceView.width, surfaceView.height, Bitmap.Config.ARGB_8888)
            val onPixelCopyFinishedListener: (Int) -> Unit = { copyResult: Int ->
                listener?.onTakeShotFinished(copyResult == PixelCopy.SUCCESS, bmp, "")
            }
            PixelCopy.request(surfaceView, bmp, onPixelCopyFinishedListener, handler)
        } else {
            listener?.onTakeShotFinished(false, null, "系统版本低于7.0, 不支持当前 surfaceView 截图方法")
        }
    }

    /**
     * Scrollview截图
     */
    fun takeShotOfScrollView(scrollView: ScrollView): Bitmap {
        var h = 0
        for (i in 0 until scrollView.childCount) {
            h += scrollView.getChildAt(i).height
            scrollView.getChildAt(i).setBackgroundColor(Color.parseColor("#ffffff"))
        }

        return Bitmap.createBitmap(scrollView.width, h, Bitmap.Config.RGB_565).apply {
            val canvas = Canvas(this)
            scrollView.draw(canvas)
        }
    }

    /**
     * 获取图片深色部分占比
     * 过程:
     * 1. 把 RGB 模式转换成 YUV 模式,获得灰度值, 算法: gray = red * 0.3 + green * 0.59 + blue * 0.11
     * 2. 计算灰度值表示深色的像素占总图片的比例
     *
     * @param deepColorThreshold 深色灰度值阈值, 默认可填 192, 小于该值会被记为深色像素
     * @return 深色部分占比, 如0.2
     */
    fun gitDeepColorRatio(bitmap: Bitmap, deepColorThreshold: Int): Double {
        var deepColorPixelCount = 0.0
        val size = bitmap.width * bitmap.height
        val bitmapGrayValue = getBitmapGrayArr(bitmap)
        for (gray in bitmapGrayValue) {
            if (gray <= deepColorThreshold) {
                deepColorPixelCount += 1.0
            }
        }
        return deepColorPixelCount / size
    }

    /**
     * 获取图片灰度信息
     * 把 RGB 模式转换成 YUV 模式,获得灰度值, 算法: gray = red * 0.3 + green * 0.59 + blue * 0.11
     */
    fun getBitmapGrayArr(bitmap: Bitmap): DoubleArray {
        val width = bitmap.width
        val height = bitmap.height
        val size = width * height
        val grayArr = DoubleArray(size)
        val pixels = IntArray(size)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in 0 until size) {
            val pixel = pixels[i]
            val alpha = -0x1000000 and pixel
            if (alpha == 0) {
                grayArr[i] = 0.0
                continue
            }
            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)
            val gray = red * 0.3 + green * 0.59 + blue * 0.11
            grayArr[i] = gray
        }
        return grayArr
    }

    /**
     * 截图完成监听
     */
    interface onTakeShotListener {
        /**
         * @param success true-截图成功
         * @param bitmap  截图数据, success=true时非空
         */
        fun onTakeShotFinished(success: Boolean, bitmap: Bitmap?, errMsg: String?)
    }
}