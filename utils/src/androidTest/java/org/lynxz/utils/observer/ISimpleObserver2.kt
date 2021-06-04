package org.lynxz.utils.observer

interface ISimpleObserver2 {
    fun onInvoke2(msg: String?): Boolean
    fun onInvokeByte(): Byte?
    fun onInvokeChar(): Char
    fun onInvokeInt(): Int
    fun onInvokeLong(): Long
    fun onInvokeDouble(): Double
    fun onInvokeFloat(): Float
}