package org.lynxz.utils.annotations

import androidx.annotation.IntDef

/**
 * 用于表示bool变量的三种情况
 */
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.VALUE_PARAMETER
)
@Retention(AnnotationRetention.SOURCE)
@IntDef(RequireType.TRUE, RequireType.FALSE, RequireType.UNSPECIAL)
annotation class RequireType {
    companion object {
        const val FALSE = 0x000
        const val TRUE = 0x001
        const val UNSPECIAL = 0x100
    }
}

@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.VALUE_PARAMETER
)
@Retention(AnnotationRetention.RUNTIME)
annotation class Require(@RequireType val require: Int = RequireType.UNSPECIAL)