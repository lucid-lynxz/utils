package org.lynxz.utils

import java.io.Closeable

fun Closeable?.closeSafety() = try {
    this?.close()
} catch (e: Exception) {
    e.printStackTrace()
}