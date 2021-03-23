# `ThreadSwitcher` 工具类说明

## 需求背景

对接sdk/库时经常需要设置监听器(observer, listener等),这些监听器为 `interface` 接口, 由于这些库可能是通过子线程触发回调监听器,
但业务层/展示层需要在特定线程中操作(如主线程更新UI),此时就需要进行线程切换 本工具类就是简化线程切换操作的

## 要求/限制

* 各监听器为 `interface` 接口(用于通过动态代理方式自动构建实例)
* sdk/库不通方法使用的监听器不同

## 使用

```kotlin
// 1. 根据上层指定的线程,创建switcher
val switcher = ThreadSwitcher.newInstance(Looper.getMainLooper())

// 2. 创建用于sdk注册的监听器, 记为: `innerObserver` ,其中 `ISimpleObserver` 为对应的接口类
// 并注册到sdk中, 如: xxxSdk.setObserver(innerObserver)
val innerObserver = switcher.generateInnerObserverImpl(ISimpleObserver::class.java)

// 3. 创建上层回调使用的监听器,记为: `outerObserver`
val outerObserver = object : ISimpleObserver {
    override fun onInvoke(msg: String?) {
        // 会运行在创建 switcher 时指定的线程中
    }
} 

// 4. 将 outerObserver 注册到 switcher 中
// 之后sdk回调 innerObserver 时, switcher 会搜索对应的 outerObserver, 并进行线程切换及触发  
switcher.registerOuterObserver(outerObserver, ISimpleObserver::class.java)

// 5. 停止switcher
switcher.deactive()

// 6. 吃饭持有的observer引用
switcher.release()
```