package com.dakuo.rulib.common.lang

object StrUtils {


    fun format(message: String, vararg args: Pair<String, Any?>): String {
        var result = message
        for (arg in args) {
            result = result.replace("{%s}".format(arg.first), arg.second.toString())
        }
        return result
    }


}