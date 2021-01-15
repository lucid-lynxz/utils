package org.lynxz.utils

import java.io.Closeable
import java.lang.Exception

fun Closeable?.closeSafety() = try {
    this?.close()
} catch (e: Exception) {
    e.printStackTrace()
}