抽离整理个人平时工作中用到工具类,方便不同项目使用
使用kotlin
尽量不依赖其他库

## 功能
* [x] `FileUtil` 文件操作工具类(读写,删除,添加等)
* [x] `AssetUtil` asset文件工具类
* [x] `LoggerUtil` 日志工具类, 可自定义进行持久化
* [x] `ReflectUtil` 反射工具类
* [x] `ProxyUtil` 接口动态代理实现工具类
* [x] `NumberExt` 字符串转数值扩展类
* [x] `BooleanExt` 布尔类型扩展类
...

## 使用说明

### 1. 导入gitpack
```gradle
// 在项目 build.gradle 下添加仓库地址
allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```

### 2. 在module下的 build.gradle 中导入依赖
```gradle
dependencies {
	 implementation 'com.github.lucid-lynxz:utils:0.1.0'
}
```