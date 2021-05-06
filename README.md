# 个人常用工具类

> 抽离整理个人平时工作中用到工具类,方便不同项目使用
>
> 使用kotlin
>
> 尽量不依赖其他库(由于封装长流程fragment,仅依赖了 androidx.appcompat:appcompat 库)

## 使用说明

### 导包
```gradle
// 1. 修改项目根目录 build.gradle 文件,添加仓库
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}

// 2. 在模块下的 build.gradle 中添加依赖
dependencies {
    implementation 'com.github.lucid-lynxz:utils:0.1.10'
}
```

### 各工具类方法说明

* [FileUtil](doc/README_Fileutil.md) 文件的创建/删除/复制/移动/读/写等功能
* [NumberExt](doc/README_NumberExt.md) 字符串与数字的转换扩展函数
* [ThreadSwitcher](doc/README_ThreadSwitcher.md) observer线程自动切换工具类
* AssetUtil 待添加