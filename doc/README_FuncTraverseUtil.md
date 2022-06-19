# `FunTraverseUtil` 接口随机压测

## 主要功能

对已知的对象进行接口随机压测

## 用法

可参考 [测试用例FuncTraverseTest](../utils/src/androidTest/java/org/lynxz/utils/reflect/FuncTraverseTest.kt)

```kotlin
FuncTraverseUtil.create(mainObj) // 必传待测对象
     .setTargetClz(Main::class.java) // 可选, 设置待验证方法所在的Class
     .setMethodNamePattern(".*") // 可选, 待测试方法名匹配规则, 正则
     .addExcludeMethodNames("release") // 可选, 不进行测试的方法名,可多次添加或一次添加多条
     .setSpecialMethodList(null) // 可选, 指定待测试的方法列表,此时 setRandomSortMethods 无效
     .setRandomSortMethods(false) // 可选, 是否随机运行方法,默认为false
     .addArgTypeValue(Int::class.java, 666) // 可选, 额外添加参数类型对应的值, 可多条,内置给定了默认值
     .enableDefaultMultiArtTypeValues() // 可选, 启用内置的多默认值
     .setMaxArgValueGroupSize(3) // 可选, 候选形参值组合过多时,可设置最大组合数, 负数表示不限制,默认为10个
     .setMethodArgGroupIndexMap(null) // 可选, 设置某些方法调用时所用的实参组合序号列表
     .setMethodArgGroupIndexList("xxxx",null) // 可选,单独设置某方法调用时所使用的的实参组合序号列表
     .addBeforeFuncInvokeAction(null) // 可选,可多条,方法执行前回调
     .addAfterFuncInvokeAction(null) // 可选,可多条,方法执行后回调
     .invokeAllPublic() // 必须, 触发执行符合条件的所有方法
```