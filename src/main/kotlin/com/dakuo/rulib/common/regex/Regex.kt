package com.dakuo.rulib.common.regex


/**
 * 正则表达式工具类
 *
 * 使用示例:
 * ```kotlin
 * // 1. 基础匹配
 * val text = "Hello 123 World"
 *
 * // 检查是否匹配
 * text.matchesRegex("\\w+\\s+\\d+\\s+\\w+") // true
 *
 * // 提取匹配结果
 * val match = text.matchRegex("(\\d+)")
 * println(match?.group(1)) // 123
 *
 * // 替换文本
 * text.replaceRegex("\\d+", "456") // "Hello 456 World"
 *
 * // 2. DSL构建正则表达式
 * val pattern = Regex.regex {
 *     startOfLine()
 *     words()
 *     space()
 *     group("number") {
 *         digits()
 *     }
 *     space()
 *     words()
 *     endOfLine()
 * }
 *
 * val result = pattern.matcher(text).toMatchResult()
 * println(result.group("number")) // 123
 *
 * // 3. 匹配多个结果
 * val text2 = "abc 123 def 456"
 * text2.matchAllRegex("\\d+").forEach {
 *     println(it.group()) // 打印: 123 456
 * }
 *
 * // 4. 命名组操作
 * val text3 = "name: John, age: 25"
 * val namedPattern = "name: (?<name>\\w+), age: (?<age>\\d+)"
 *
 * // 获取指定组的值
 * text3.matchRegex(namedPattern)?.getGroup("name") // "John"
 *
 * // 获取所有命名组及其值
 * text3.matchRegex(namedPattern)?.getGroups() // Map("name" to "John", "age" to "25")
 *
 * // 替换指定组的值
 * text3.replaceNamedGroup(namedPattern, "name", "Jane") // "name: Jane, age: 25"
 *
 * // 使用多个替换对同时替换多个组
 * text3.replaceNamedGroups(namedPattern, mapOf(
 *     "name" to "Jane",
 *     "age" to "30"
 * )) // "name: Jane, age: 30"
 * ```
 */
object Regex {
    // 缓存已编译的正则表达式
    private val patternCache = java.util.concurrent.ConcurrentHashMap<String, com.google.code.regexp.Pattern>()
    private const val MAX_CACHE_SIZE = 1000 // 最大缓存数量

    /**
     * 创建一个命名正则表达式,优先从缓存获取
     * @param pattern 正则表达式模式
     * @return 命名正则表达式对象
     */
    fun create(pattern: String): com.google.code.regexp.Pattern {
        if (patternCache.size > MAX_CACHE_SIZE) {
            patternCache.clear()
        }
        return patternCache.computeIfAbsent(pattern) {
            com.google.code.regexp.Pattern.compile(it)
        }
    }

    /**
     * 匹配字符串
     * @param pattern 正则表达式模式
     * @param input 输入字符串
     * @return 匹配结果
     */
    fun match(pattern: String, input: String): com.google.code.regexp.MatchResult? {
        val matcher = create(pattern).matcher(input)
        return if (matcher.find()) matcher.toMatchResult() else null
    }

    /**
     * 匹配所有结果
     * @param pattern 正则表达式模式
     * @param input 输入字符串
     * @return 所有匹配结果
     */
    fun matchAll(pattern: String, input: String): Sequence<com.google.code.regexp.MatchResult> = sequence {
        val matcher = create(pattern).matcher(input)
        while (matcher.find()) {
            yield(matcher.toMatchResult())
        }
    }

    /**
     * 替换字符串
     * @param pattern 正则表达式模式
     * @param input 输入字符串
     * @param replacement 替换字符串
     * @return 替换后的字符串
     */
    fun replace(pattern: String, input: String, replacement: String): String {
        return create(pattern).matcher(input).replaceAll(replacement)
    }
    /**
     * 替换命名组
     * @param pattern 正则表达式模式
     * @param input 输入字符串
     * @param groupName 组名
     * @param replacement 替换值
     * @return 替换后的字符串
     */
    fun replaceNamedGroup(pattern: String, input: String, groupName: String, replacement: String): String {
        val matcher = create(pattern).matcher(input)
        val buffer = StringBuffer(input.length) // 使用StringBuffer替代StringBuilder
        while (matcher.find()) {
            val group = matcher.group()
            val newGroup = group.replaceFirst(matcher.group(groupName), replacement)
            matcher.appendReplacement(buffer, newGroup)
        }
        matcher.appendTail(buffer)
        return buffer.toString()
    }

    /**
     * 替换多个命名组
     * @param pattern 正则表达式模式
     * @param input 输入字符串
     * @param replacements 组名和替换值的映射
     * @return 替换后的字符串
     */
    fun replaceNamedGroups(pattern: String, input: String, replacements: Map<String, String>): String {
        // 一次性编译正则表达式
        val compiledPattern = create(pattern)
        var result = input
        replacements.forEach { (groupName, replacement) ->
            val matcher = compiledPattern.matcher(result)
            val buffer = StringBuffer(result.length) // 使用StringBuffer替代StringBuilder
            while (matcher.find()) {
                val group = matcher.group()
                val newGroup = group.replaceFirst(matcher.group(groupName), replacement)
                matcher.appendReplacement(buffer, newGroup)
            }
            matcher.appendTail(buffer)
            result = buffer.toString()
        }
        return result
    }

    /**
     * 检查是否匹配
     * @param pattern 正则表达式模式
     * @param input 输入字符串
     * @return 是否匹配
     */
    fun matches(pattern: String, input: String): Boolean {
        return create(pattern).matcher(input).matches()
    }

    // DSL支持
    class RegexBuilder {
        private val pattern = StringBuilder() // 使用StringBuilder提升性能

        // 基础构建方法
        fun pattern(value: String) {
            pattern.append(value)
        }

        // 常用正则模式
        fun digit() {
            pattern.append("\\d")
        }

        fun digits() {
            pattern.append("\\d+")
        }

        fun word() {
            pattern.append("\\w")
        }

        fun words() {
            pattern.append("\\w+")
        }

        fun space() {
            pattern.append("\\s")
        }

        fun spaces() {
            pattern.append("\\s+")
        }

        // 分组
        fun group(name: String, init: RegexBuilder.() -> Unit) {
            pattern.append("(?<").append(name).append(">")
            RegexBuilder().apply(init).also { pattern.append(it.pattern) }
            pattern.append(")")
        }

        // 数量词
        fun oneOrMore() {
            pattern.append("+")
        }

        fun zeroOrMore() {
            pattern.append("*")
        }

        fun optional() {
            pattern.append("?")
        }

        fun times(n: Int) {
            pattern.append("{").append(n).append("}")
        }

        fun times(min: Int, max: Int) {
            pattern.append("{").append(min).append(",").append(max).append("}")
        }

        // 特殊字符
        fun any() {
            pattern.append(".")
        }

        fun startOfLine() {
            pattern.append("^")
        }

        fun endOfLine() {
            pattern.append("$")
        }

        internal fun build() = pattern.toString()
    }

    /**
     * DSL入口方法
     */
    fun regex(init: RegexBuilder.() -> Unit): com.google.code.regexp.Pattern {
        return create(RegexBuilder().apply(init).build())
    }
}

// 扩展方法
fun String.matchesRegex(pattern: String): Boolean = Regex.matches(pattern, this)
fun String.matchRegex(pattern: String): com.google.code.regexp.MatchResult? = Regex.match(pattern, this)
fun String.matchAllRegex(pattern: String): Sequence<com.google.code.regexp.MatchResult> = Regex.matchAll(pattern, this)
fun String.replaceRegex(pattern: String, replacement: String): String = Regex.replace(pattern, this, replacement)

// 命名组扩展方法
fun com.google.code.regexp.MatchResult.getGroup(name: String): String? = group(name)

fun com.google.code.regexp.MatchResult.getGroups(): Map<String, String> {
    val result = mutableMapOf<String, String>()
    namedGroups().forEach { (name, _) ->
        group(name)?.let { value ->
            result[name] = value
        }
    }
    return result
}

fun String.getGroups(pattern: String): Map<String, String>? {
    return matchRegex(pattern)?.getGroups()
}

fun String.replaceNamedGroup(pattern: String, groupName: String, replacement: String): String =
    Regex.replaceNamedGroup(pattern, this, groupName, replacement)

fun String.replaceNamedGroups(pattern: String, replacements: Map<String, String>): String =
    Regex.replaceNamedGroups(pattern, this, replacements)