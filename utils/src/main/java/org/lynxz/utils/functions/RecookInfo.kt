package org.lynxz.utils.functions

/**
 * @version 1.0
 * description: 对对象进行二次处理
 */
interface RecookInfo<A, B> {
    /**
     * 对指定对象进行二次处理
     */
    fun recook(objA: A, objB: B): B
}