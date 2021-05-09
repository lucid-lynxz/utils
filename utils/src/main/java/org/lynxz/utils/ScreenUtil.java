package org.lynxz.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.ColorInt;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import org.lynxz.utils.log.LoggerUtil;
import org.lynxz.utils.observers.EmptyActivityLifecycleCallback;
import org.lynxz.utils.reflect.ProxyUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * V1.0 2019.03.12 通过修改系统参数来适配android设备尺寸,并提供了 dp/px 互转方法 以及 获取状态栏/导航条高度及沉浸式等方法
 * V1.1 2021.05.09 增加禁止字体随系统字体缩放而变化功能, 支持剔除指定页面的适配
 * 注意: 若项目中引用了第三方UI库,且UI库的设计尺寸与当前项目不同,则可能会导致适配问题(本适配工具未处理这种情况)
 * <p>
 * 另外,本工具类还提供了状态栏透明及切换状态栏文字图案颜色模式的方法
 * </p>
 * <p>
 * 今日头条屏幕适配方案:
 * 一. 原理:
 * app中使用的 dp, sp 等单位最终都是通过 {@link android.util.TypedValue#applyDimension(int, float, DisplayMetrics)} 方法进行转换的,
 * 因此我们可以通过修改其转换系数来达到适配不通屏幕尺寸的目的, 其中:
 * - dp: 通过 {@link DisplayMetrics#density} 系数
 * - sp: 通过 {@link DisplayMetrics#scaledDensity} 系数
 * <p>
 * 二. 使用方法: <br>
 * 1. 在 application 类的 onCreate() 中执行: {@link #init(Application)},app就会以默认的
 * 360dp*667dp 来适配布局;<br>
 * 若需要指定其他尺寸,则请执行:{@link #init(Application, float, float)}
 * ,依次传入宽高设计尺寸(单位:dp)即可;<br>
 * 另外,提供了"严格"模式,此模式下,只有实现了接口 {@link Adaptable} 的activity才会进行适配
 * <p>
 * 2.[可选,已默认指定宽度适配] 若某个activity不想使用默认的适配参数,则请实现接口 {@link Adaptable} 接口;<br>
 * 3.若想指定某个Activity不做屏幕适配,则请实现接口 {@link DonotAdapt} 接口;<br>
 * 4.若不适配依赖库中的页面,则在对应页面创建前,调用 {@link #addExcludeActivity(String...)} 进行套剔除;<br>
 * <p>
 * 以下为状态栏相关操作: <br>
 * 1. 设置状态栏背景色透明: {@link #setStatusBarTranslucent(Activity)};<br>
 * 2. 修改状态栏背景色: {@link #setStatusBarColor(Activity, int)};<br>
 * 3.
 * 切换状态栏内容颜色模式(深色/浅色调):{@link #setStatusBarTextColorMode(Activity, boolean)};<br>
 * 4. 获取状态栏高度:{@link #getStatusBarHeightPx(Context)};<br>
 * 5. 获取底部导航栏高度: {@link #getNavigationBarHeightPx(Context)}<br>
 * <p>
 * 其他方法说明:<br>
 * 1. dp转px: {@link #dp2px(Context, int)};<br>
 * 2. px转dp: {@link #px2dp(Context, float)}<br>
 * 3. 获取view在屏幕上的坐标: {@link #getViewScreenLocation(View)};<br>
 * 4. 获取屏幕宽高(px):{@link #getScreenWidthPx(Context)}, {@link #getScreenHeightPx(Context, boolean)};<br>
 * 5. 屏幕截图: {@link #snapShot(Activity, boolean)}<br>
 * <p>
 * 参考文章:
 * <ol>
 * <li>[今日头条屏幕适配](https://mp.weixin.qq.com/s/d9QCoBP6kV9VSWvVldVVwA)</li>
 * <li>[今日头条适配方案优化](https://www.jianshu.com/p/4254ea9d1b27)</li>
 * <li>[屏幕适配扫盲](https://www.jianshu.com/p/ec5a1a30694b)</li>
 * <li>[今日头条适配方案升级(增加对第三方库的适配)](https://juejin.im/post/5b7a29736fb9a019d53e7ee2?utm_source=gold_browser_extension)</li>
 * <li>[一个Activity中多个Fragment实现沉浸式状态栏](https://blog.csdn.net/impure/article/details/53965082)</li>
 * <li>[Android透明状态栏与状态栏文字颜色更改](https://www.jianshu.com/p/7392237bc1de)</li>
 * </ol>
 * <p>
 * 其他知识:
 * <pre>
 *      dpi = Math.sqrt(w*w+h*h)/屏幕尺寸 (屏幕宽高单位:px,尺寸单位:inch)
 *      density = dpi / 160;
 *      px = density * dp;
 *      px = dp * (dpi / 160);
 *
 * 查看屏幕尺寸和分辨率密度的adb命令
 *  adb shell wm size
 *  adb shell wm density
 * </pre>
 */
@Keep
public class ScreenUtil {
    private static final int MIUI_V60_VERSION_CODE = 4;
    private static final int MIUI_V70_VERSION_CODE = 5;

    // 默认的设计稿适配信息, 宽度适配,宽 360dp, 高 667dp
    private static final ScreenOrientation DEFAULT_ORIENTATION = ScreenOrientation.WIDTH;
    private static final float DEFAULT_DESIGN_WIDTH = 360f; // 默认的设计稿宽度,单位:dp
    private static final float DEFAULT_DESIGN_HEIGHT = 667f; // 默认的设计稿高度,单位:dp

    // 实际设计图的适配方向及宽高尺寸,单位dp,作为各 activity 的默认适配参数
    private static ScreenOrientation designOrientation = DEFAULT_ORIENTATION;
    private static float designWidthDp = DEFAULT_DESIGN_WIDTH;
    private static float designHeightDp = DEFAULT_DESIGN_HEIGHT;

    // 系统比例参数
    private static DisplayMetrics appDisplayMetrics;
    private static float appDensity;
    private static float appScaledDensity; // 字体缩放比例
    private static boolean enableScaleDensityChanged = true; // 是否允许字体大小随系统缩放设置而缩放
    private static int barHeight; // 状态栏高度
    private static Set<String> excludeAdaptActivities; // 不进行适配的页面,用于三方库剔除三方库页面等场景

    /**
     * 是否是严格模式
     * true-严格模式: 只有实现接口 {@link Adaptable} 的 activity 才进行适配
     * false-宽松模式(默认): 不实现接口 {@link DonotAdapt} 的 activity 都进行适配
     */
    private static boolean isStrictMode = false;
    /**
     * 小米系统判定 MIUI V6 为: 4 MIUI V7 为: 5
     */
    private static int sMiUIVersionCode = -1;

    /**
     * 初始化,使用默认适配参数: 360dp*667dp/宽度适配/非严格模式/允许字体缩放
     * 请参考: {@link #init(Application, ScreenOrientation, float, float, boolean, boolean)}
     */
    public static void init(@NonNull Application application) {
        init(application, DEFAULT_ORIENTATION, DEFAULT_DESIGN_WIDTH, DEFAULT_DESIGN_HEIGHT, false, enableScaleDensityChanged);
    }

    /**
     * 指定要适配的分辨率, 宽度适配/非严格模式
     * 请参考: {@link #init(Application, ScreenOrientation, float, float, boolean, boolean)}
     */
    public static void init(@NonNull Application application, float widthDp, float heightDp) {
        init(application, DEFAULT_ORIENTATION, widthDp, heightDp, false, enableScaleDensityChanged);
    }

    /**
     * 设置适配参数
     *
     * @param widthDp    美工设计图屏幕宽度,单位:dp
     * @param heightDp   美工设计图屏幕高度,单位:dp
     * @param strictMode 是否使用严格模式
     *                   true-严格模式: 只有实现接口 {@link Adaptable} 的 activity 才进行适配
     *                   false-宽松模式(默认): 不实现接口 {@link DonotAdapt} 的 activity 都进行适配
     */
    public static void init(@NonNull final Application application,
                            ScreenOrientation orientation,
                            float widthDp,
                            float heightDp,
                            boolean strictMode,
                            boolean scaleDensityChanged) {
        designOrientation = orientation;
        designWidthDp = widthDp;
        designHeightDp = heightDp;
        isStrictMode = strictMode;

        // 获取application的DisplayMetrics
        appDisplayMetrics = application.getResources().getDisplayMetrics();
        // 获取状态栏高度
        barHeight = getStatusBarHeightPx(application);
        if (appDensity == 0) {
            // 初始化的时候赋值
            appDensity = appDisplayMetrics.density;
            appScaledDensity = appDisplayMetrics.scaledDensity; // 默认是跟 density 相等, 可通过系统设置字体缩放大小进行改变
            enableFontScaleChange(scaleDensityChanged);

            // 自动在activity的 onCreate() 中设置默认方向(宽度)适配,若有需要改为高度适配请在 activity 中修改
            application.registerActivityLifecycleCallbacks(new EmptyActivityLifecycleCallback() {
                @Override
                public void onActivityCreated(@NotNull Activity activity, @Nullable Bundle savedInstanceState) {
                    super.onActivityCreated(activity, savedInstanceState);
                    if (shouldAdapted(activity)) {
                        setActivityAdaptParam(activity);
                    }
                }
            });

            // 添加字体变化的监听
            application.registerComponentCallbacks(new ComponentCallbacks() {
                @Override
                public void onConfigurationChanged(@NonNull Configuration newConfig) {
                    // 字体改变后,将appScaledDensity重新赋值
                    if (newConfig.fontScale > 0 && scaleDensityChanged) {
                        appScaledDensity = application.getResources().getDisplayMetrics().scaledDensity;
                    }
                }

                @Override
                public void onLowMemory() {
                }
            });
        }
    }

    /**
     * 添加不进行屏幕适配的页面信息,需要在页面创建前进行设置
     * 可填写activity完整路径,或者添加其所在的包路径(该包下所有页面都不适配)
     * 如: org.lynxz.ui.SomeActivity  或者 org.lynxz.ui
     *
     * @param activityPath 页面路径,填写完整路径
     */
    public static void addExcludeActivity(@NotNull String... activityPath) {
        if (excludeAdaptActivities == null) {
            synchronized (ScreenUtil.class) {
                if (excludeAdaptActivities == null) {
                    excludeAdaptActivities = new HashSet<>();
                }
            }
        }

        for (String path : activityPath) {
            excludeAdaptActivities.add(path.trim());
        }
    }

    /**
     * 判断页面是否需要适配
     */
    private static boolean shouldAdapted(Activity activity) {
        if (activity instanceof DonotAdapt) {
            return false;
        }
        // 若指定了不适配的页面信息, 则遍历查找
        int excludeSize = excludeAdaptActivities == null ? 0 : excludeAdaptActivities.size();
        if (excludeSize > 0) {
            String fullClassName = ProxyUtil.INSTANCE.getFullClassName(activity); // 完整类路径(包名+类名)
            int length = fullClassName == null ? 0 : fullClassName.length();
            if (length >= 1) {
                String simpleClassName = ProxyUtil.INSTANCE.getSimpleClassName(activity);
                int endIndex = length - simpleClassName.length() - 1; // 删除最后的点
                String pkgName = fullClassName.substring(0, endIndex); // 包名
                // 优先按照完成路径进行一次查找,未命中再进行全量遍历搜索
                if (excludeAdaptActivities.contains(fullClassName) || excludeAdaptActivities.contains(pkgName)) {
                    return false;
                }
                for (String excludeName : excludeAdaptActivities) {
                    if (fullClassName.startsWith(excludeName)) {
                        return false;
                    }
                }
            }
        }

        return !isStrictMode || activity instanceof Adaptable;
    }

    /**
     * 启用系统字体缩放设置
     * 请在 {@link #init(Application)} 初始化前设置
     *
     * @param enable 是否允许字体随系统设置进行缩放
     */
    public static void enableFontScaleChange(boolean enable) {
        enableScaleDensityChanged = enable;
        if (!enable) {
            appScaledDensity = appDensity;
        }
    }

    private static float getActivityDensity(Activity activity) {
        float width = designWidthDp;
        float height = designHeightDp;
        ScreenOrientation orientation = designOrientation;

        if (activity instanceof Adaptable) {
            orientation = ((Adaptable) activity).getAdaptOrientation();
            if (orientation == null) {
                orientation = designOrientation;
            }
            int adaptWidth = ((Adaptable) activity).getAdaptWidthDp();
            int adaptHeight = ((Adaptable) activity).getAdaptHeightDp();
            if (adaptWidth > 0) {
                width = adaptWidth;
            }

            if (adaptHeight > 0) {
                height = adaptHeight;
            }
        }

        float targetDensity;
        if (ScreenOrientation.WIDTH == orientation) {
            targetDensity = appDisplayMetrics.widthPixels / width;
        } else {
            // 由于设计图是包含标题栏和导航栏,因此高度应计算全屏幕高度
            // TODO: lynxz 2018/12/5 导航条不显示时才包含其高度
            // targetDensity = (appDisplayMetrics.heightPixels - barHeight) / height;
            targetDensity = appDisplayMetrics.heightPixels / height;
            // targetDensity = getScreenHeightWithNavigationBar(activity) / height;
        }
        return targetDensity;
    }

    /**
     * 设置activity的适配参数 默认都统一使用 init() 初始化时指定的设计图参数
     * 若想单独指定某个Activity的适配参数,则让该 Activity 实现接口 {@link Adaptable} 即可
     * targetDensity targetScaledDensity targetDensityDpi 这三个参数是统一修改过后的值
     */
    private static void setActivityAdaptParam(@NotNull Activity activity) {
        float targetDensity = getActivityDensity(activity);
        float targetScaledDensity = targetDensity * (appScaledDensity / appDensity);
        int targetDensityDpi = (int) (160 * targetDensity);
        // 最后在这里将修改过后的值赋给系统参数 只修改Activity的density值
        Resources resources = activity.getResources();
        DisplayMetrics activityDisplayMetrics = resources.getDisplayMetrics();

        activityDisplayMetrics.density = targetDensity;
        activityDisplayMetrics.densityDpi = targetDensityDpi;
        activityDisplayMetrics.scaledDensity = targetScaledDensity;
//        activityDisplayMetrics.scaledDensity = enableScaleDensityChanged ? targetScaledDensity : appDensity;
//        LoggerUtil.w("xxx", "targetDensity=" + targetDensity + "," + targetScaledDensity + "," + appScaledDensity + "," + appDensity);
        // 如果不希望字体大小随系统设置的字体大小变化而变化,可以将其指定为确定值
        // activityDisplayMetrics.scaledDensity = appScaledDensity;

//        Configuration configuration = resources.getConfiguration();
//        if (!enableScaleDensityChanged) {
//            configuration.fontScale = 1.0f;
//        }
//        resources.updateConfiguration(configuration, activityDisplayMetrics);
    }

    public static int dp2px(Context context, int dp) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }

    /**
     * 根据手机的分辨率从 px 的单位 转成为 dp
     */
    public static int px2dp(Context context, float pxValue) {
        final float density = context.getResources().getDisplayMetrics().density;
        return (int) (pxValue / density + 0.5f);
    }

    /**
     * 获取屏幕宽度(px)
     */
    public static int getScreenWidthPx(Context context) {
        WindowManager windowmanager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowmanager == null) {
            return 0;
        }
        Display display = windowmanager.getDefaultDisplay();
        DisplayMetrics outMetrics = new DisplayMetrics();
        display.getMetrics(outMetrics);
        return outMetrics.widthPixels;
    }

    /**
     * 得到屏幕高度(px,不包含底部导航条高度)
     *
     * @param withNavigationBar 是否包含底部导航条高度
     */
    public static int getScreenHeightPx(Context context, boolean withNavigationBar) {
        WindowManager windowmanager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowmanager == null) {
            return 0;
        }
        DisplayMetrics dm = new DisplayMetrics();
        if (withNavigationBar) {
            windowmanager.getDefaultDisplay().getRealMetrics(dm); // 包含底部导航条高度
        } else {
            windowmanager.getDefaultDisplay().getMetrics(dm); // 不包含导航栏
        }
        return dm.heightPixels;
    }

    /**
     * 获取 View 的坐标
     */
    public static RectF getViewScreenLocation(View view) {
        int[] location = new int[2];
        // 获取控件在屏幕中的位置，返回的数组分别为控件左顶点的 x、y 的值
        view.getLocationOnScreen(location);
        return new RectF(location[0], location[1], location[0] + view.getWidth(), location[1] + view.getHeight());
    }

    /**
     * 获取当前屏幕截图
     *
     * @param withoutStatusBar true-不包含状态栏 false-包含状态栏截图
     */
    public static Bitmap snapShot(Activity activity, boolean withoutStatusBar) {
        View view = activity.getWindow().getDecorView();
        view.setDrawingCacheEnabled(true);
        view.buildDrawingCache();
        Bitmap bmp = view.getDrawingCache();
        int width = getScreenWidthPx(activity);
        int height = getScreenHeightPx(activity, false);
        int y = 0;
        if (withoutStatusBar) {
            Rect frame = new Rect();
            activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(frame);
            y = frame.top; // 扣掉状态栏高度
        }
        Bitmap bp = Bitmap.createBitmap(bmp, 0, y, width, height - y);
        view.destroyDrawingCache();
        return bp;
    }

    // ------- 状态栏修改相关操作 -------

    /**
     * 获取状态栏高度,单位:px
     */
    public static int getStatusBarHeightPx(Context context) {
        int result = 0;
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = context.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    /**
     * 获取手机底部导航条高度,单位:px
     */
    public static int getNavigationBarHeightPx(Context context) {
        int var1 = 0;
        int var2 = context.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (var2 > 0) {
            var1 = context.getResources().getDimensionPixelSize(var2);
        }
        return var1 + dp2px(context, 5);
    }

    /**
     * 给activity的状态栏设置颜色
     *
     * @param activity activity
     * @param color    颜色值
     */
    public static void setStatusBarColor(Activity activity, @ColorInt int color) {
        Window window = activity.getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(color);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            View view = new View(activity);
            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    getStatusBarHeightPx(activity));
            view.setLayoutParams(params);
            view.setBackgroundColor(color);

            ViewGroup decorView = (ViewGroup) window.getDecorView();
            decorView.addView(view);

            ViewGroup contentView = activity.findViewById(android.R.id.content);
            contentView.setPadding(0, getStatusBarHeightPx(activity), 0, 0);
        }
    }

    /**
     * 设置activity全屏，状态栏透明，内容填充到状态栏中
     */
    public static void setStatusBarTranslucent(Activity activity) {
        Window window = activity.getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            View decorView = window.getDecorView();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }
    }

    private static void setNavTrans(Activity activity, boolean isNavTrans) {
        if (!isNavTrans && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            // 要重新add
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }
    }

    /**
     * 修改状态栏文字颜色，这里小米，魅族区别对待
     */
    public static void setStatusBarTextColorMode(Activity activity, boolean darkMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            RomType romType = getStatusBarAvailableRomType();
            switch (romType) {
                case MIUI:
                    setMIUIStatusBarColorMode(activity, darkMode);
                    break;
                case FLYME:
                    setFlymeStatusBarColorMode(activity, darkMode);
                    break;
                case ANDROID_NATIVE:
                    setAndroidNativeStatusBarColorMode(activity, darkMode);
                    break;
            }
        }
    }

    private static RomType getStatusBarAvailableRomType() {
        // 判断是否是 miui V6.0以上
        if (getMiUIVersionCode() >= MIUI_V60_VERSION_CODE) {
            return RomType.MIUI;
        }

        if (isFlymeV4OrAbove()) {
            return RomType.FLYME;
        }

        if (isAndroidMOrAbove()) {
            return RomType.ANDROID_NATIVE;
        }

        return RomType.NA;
    }

    /**
     * Android Api 23以上 系统版本判定: 原生系统在M以上才支持状态栏文字颜色切换
     */
    private static boolean isAndroidMOrAbove() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    /**
     * 获取miui系统版本号,V6.x以上支持状态条颜色模式切换
     */
    private static int getMiUIVersionCode() {
        if (sMiUIVersionCode >= 0) {
            return sMiUIVersionCode;
        }
        FileInputStream fis = null;
        sMiUIVersionCode = -1;
        try {
            final Properties properties = new Properties();
            fis = new FileInputStream(new File(Environment.getRootDirectory(), "build.prop"));
            properties.load(fis);
            String uiCode = properties.getProperty("ro.miui.ui.version.code", null);
            if (uiCode != null) {
                sMiUIVersionCode = Integer.parseInt(uiCode);
            }
        } catch (Exception e) {
            LoggerUtil.d("" + e.getMessage());
            // e.printStackTrace();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return sMiUIVersionCode;
    }

    /**
     * 魅族系统判断 Flyme V4的displayId格式为 [Flyme OS 4.x.x.xA] Flyme V5的displayId格式为 [Flyme
     * 5.x.x.x beta]
     */
    private static boolean isFlymeV4OrAbove() {
        String displayId = Build.DISPLAY;
        if (!TextUtils.isEmpty(displayId) && displayId.contains("Flyme")) {
            String[] displayIdArray = displayId.split(" ");
            for (String temp : displayIdArray) {
                // 版本号4以上，形如4.x.
                if (temp.matches("^[4-9]\\.(\\d+\\.)+\\S*")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 原生系统修改状态栏字体颜色模式
     */
    public static void setAndroidNativeStatusBarColorMode(Activity activity, boolean darkMode) {
        View decor = activity.getWindow().getDecorView();
        if (darkMode) {
            if (isAndroidMOrAbove()) {
                decor.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    /**
     * 小米系统下状态栏文字颜色的修改(在深色和浅色之间切换)
     */
    @SuppressLint("PrivateApi")
    private static boolean setMIUIStatusBarColorMode(Activity activity, boolean darkMode) {
        boolean result = false;
        Window window = activity.getWindow();
        if (window != null) {
            Class clazz = window.getClass();
            try {
                Class layoutParams = Class.forName("android.view.MiuiWindowManager$LayoutParams");
                Field field = layoutParams.getField("EXTRA_FLAG_STATUS_BAR_DARK_MODE");
                int darkModeFlag = field.getInt(layoutParams);
                Method extraFlagField = clazz.getMethod("setExtraFlags", int.class, int.class);
                if (darkMode) {
                    extraFlagField.invoke(window, darkModeFlag, darkModeFlag);// 状态栏透明且黑色字体
                } else {
                    extraFlagField.invoke(window, 0, darkModeFlag);// 清除黑色字体
                }
                result = true;

                // 开发版 7.7.13 及以后版本采用了系统API，旧方法无效但不会报错，所以两个方式都要加上
                if (isAndroidMOrAbove() && getMiUIVersionCode() >= MIUI_V70_VERSION_CODE) {
                    if (darkMode) {
                        activity.getWindow().getDecorView().setSystemUiVisibility(
                                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                    } else {
                        activity.getWindow().getDecorView().setSystemUiVisibility(
                                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    /**
     * 魅族系统状态栏文字颜色修改
     */
    private static boolean setFlymeStatusBarColorMode(Activity activity, boolean darkMode) {
        boolean result = false;
        if (activity != null) {
            try {
                WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
                Field darkFlag = WindowManager.LayoutParams.class.getDeclaredField("MEIZU_FLAG_DARK_STATUS_BAR_ICON");
                Field meizuFlags = WindowManager.LayoutParams.class.getDeclaredField("meizuFlags");
                darkFlag.setAccessible(true);
                meizuFlags.setAccessible(true);
                int bit = darkFlag.getInt(null);
                int value = meizuFlags.getInt(lp);
                if (darkMode) {
                    value |= bit;
                } else {
                    value &= ~bit;
                }
                meizuFlags.setInt(lp, value);
                activity.getWindow().setAttributes(lp);
                result = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public enum ScreenOrientation {
        WIDTH, HEIGHT
    }

    /**
     * 几种可能的系统
     */
    enum RomType {
        MIUI(1), FLYME(2), ANDROID_NATIVE(3), NA(4);
        private int romType = 0;

        RomType(int type) {
            this.romType = type;
        }

        public int getRomType() {
            return romType;
        }
    }

    /**
     * 严格模式下,只有实现本接口的页面才进行屏幕适配 另外,可通过实现该接口来指定activity要特殊适配的尺寸和方向
     */
    public interface Adaptable {

        /**
         * 指定当前页面的屏幕适配方向,可以跟application中设置的不同 若返回null则表示使用默认适配方向
         */
        ScreenOrientation getAdaptOrientation();

        /**
         * 指定适配的设计图宽度尺寸(dp),若返回0或负数,则表示使用app默认适配宽度尺寸
         * 请参考:{@link #init(Application, ScreenOrientation, float, float, boolean, boolean)}
         */
        int getAdaptWidthDp();

        /**
         * 指定适配的设计图高度尺寸(dp),若返回0或负数,则表示使用app默认适配高度尺寸
         * 请参考:{@link #init(Application, ScreenOrientation, float, float, boolean, boolean)}
         */
        int getAdaptHeightDp();
    }

    /**
     * 空接口, 实现该接口的页面将不做适配,保持系统比例
     */
    public interface DonotAdapt {

    }
}