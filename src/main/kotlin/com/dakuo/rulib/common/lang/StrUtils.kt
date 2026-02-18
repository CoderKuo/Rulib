package com.dakuo.rulib.common.lang

object StrUtils {


    fun format(message: String, vararg args: Pair<String, Any?>): String {
        var result = message
        for ((key, value) in args) {
            result = result.replace("{$key}", value.toString())
        }
        return result
    }


}