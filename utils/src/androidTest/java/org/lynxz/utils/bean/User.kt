package org.lynxz.utils.bean

import org.lynxz.utils.annotations.Require
import org.lynxz.utils.annotations.RequireType
import org.lynxz.utils.annotations.StringInfo

class User {
    companion object {
        const val MALE = "male"
        const val FEMALE = "female"
    }

    var age: Int = 0

    @StringInfo(MALE, FEMALE)
    @Require(require = RequireType.TRUE)
    var gender: String = MALE

    fun updateGender(
        @StringInfo(MALE, FEMALE)
        @Require(require = RequireType.TRUE)
        gender: String,

        @Require(require = RequireType.FALSE)
        name: String
    ) {
        this.gender = gender
    }
}