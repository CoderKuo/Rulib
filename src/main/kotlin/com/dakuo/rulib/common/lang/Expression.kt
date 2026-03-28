package com.dakuo.rulib.common.lang

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/**
 * 条件表达式引擎
 *
 * 支持:
 * - 数学运算: + - * / % ** ()
 * - 比较运算: > < >= <= == !=
 * - 逻辑运算: && || !
 * - 三元运算: condition ? then : else
 * - 空值合并: value ?? default
 * - 成员检测: x in [1, 2, 3] / 'sub' in str / x in 1..10
 * - 点号取值: player.stats.level（嵌套 Map）
 * - 字面量: 数字 / 字符串('hello') / 布尔(true/false) / null / 列表([1,2,3])
 * - 变量: 从 Map 或 Dict 中取值
 * - 内置函数: min, max, abs, round, floor, ceil, sqrt, pow, clamp,
 *            random, len, str, num, int, bool, upper, lower, trim,
 *            contains, startsWith, endsWith, replace, substr, between, if
 * - 自定义函数: Expression.registerFunction(name) { args -> ... }
 *
 * 使用示例:
 * // 条件判断
 * Expression.eval("level >= 10 && gold > 100", ctx)
 *
 * // 三元运算
 * Expression.eval("level >= 10 ? '高级' : '初级'", ctx)
 *
 * // 空值安全
 * Expression.eval("nickname ?? name ?? '未知'", ctx)
 *
 * // 成员检测
 * Expression.eval("type in ['warrior', 'mage']", ctx)
 * Expression.eval("'vip' in title", ctx)
 * Expression.eval("level in 10..50", ctx)
 *
 * // 嵌套取值
 * Expression.eval("player.stats.level > 10", mapOf("player" to mapOf("stats" to mapOf("level" to 15))))
 *
 * // 函数调用
 * Expression.eval("max(base * rate, min_damage)", ctx)
 * Expression.eval("clamp(damage, 0, 9999)", ctx)
 * Expression.eval("round(price * 0.8)", ctx)
 *
 * // 自定义函数
 * Expression.registerFunction("online_count") { Bukkit.getOnlinePlayers().size.toDouble() }
 */
object Expression {

    private val cache = ConcurrentHashMap<String, CompiledExpression>()
    private const val MAX_CACHE_SIZE = 500
    private val functions = ConcurrentHashMap<String, (List<Any?>) -> Any?>()

    init {
        // 数学函数
        functions["min"] = { args -> args.minOf { it.toNum() } }
        functions["max"] = { args -> args.maxOf { it.toNum() } }
        functions["abs"] = { args -> abs(args[0].toNum()) }
        functions["round"] = { args -> round(args[0].toNum()) }
        functions["floor"] = { args -> floor(args[0].toNum()) }
        functions["ceil"] = { args -> ceil(args[0].toNum()) }
        functions["sqrt"] = { args -> sqrt(args[0].toNum()) }
        functions["pow"] = { args -> args[0].toNum().pow(args[1].toNum()) }
        functions["clamp"] = { args -> args[0].toNum().coerceIn(args[1].toNum(), args[2].toNum()) }
        functions["random"] = { args ->
            val min = args[0].toNum().toInt()
            val max = args[1].toNum().toInt()
            kotlin.random.Random.nextInt(min, max + 1).toDouble()
        }
        functions["between"] = { args ->
            val x = args[0].toNum()
            x >= args[1].toNum() && x <= args[2].toNum()
        }

        // 类型转换
        functions["str"] = { args -> args[0].toString() }
        functions["num"] = { args -> args[0].toNum() }
        functions["int"] = { args -> args[0].toNum().toLong().toDouble() }
        functions["bool"] = { args -> args[0].toBool() }

        // 字符串函数
        functions["len"] = { args ->
            when (val v = args[0]) {
                is String -> v.length.toDouble()
                is Collection<*> -> v.size.toDouble()
                else -> v.toString().length.toDouble()
            }
        }
        functions["upper"] = { args -> args[0].toString().uppercase() }
        functions["lower"] = { args -> args[0].toString().lowercase() }
        functions["trim"] = { args -> args[0].toString().trim() }
        functions["contains"] = { args -> args[0].toString().contains(args[1].toString()) }
        functions["startsWith"] = { args -> args[0].toString().startsWith(args[1].toString()) }
        functions["endsWith"] = { args -> args[0].toString().endsWith(args[1].toString()) }
        functions["replace"] = { args -> args[0].toString().replace(args[1].toString(), args[2].toString()) }
        functions["substr"] = { args ->
            val str = args[0].toString()
            val start = args[1].toNum().toInt().coerceIn(0, str.length)
            if (args.size > 2) str.substring(start, args[2].toNum().toInt().coerceIn(start, str.length))
            else str.substring(start)
        }

        // 条件函数
        functions["if"] = { args -> if (args[0].toBool()) args[1] else args[2] }
    }

    /**
     * 注册自定义函数
     */
    fun registerFunction(name: String, fn: (List<Any?>) -> Any?) {
        functions[name] = fn
        cache.clear()
    }

    /**
     * 注销自定义函数
     */
    fun unregisterFunction(name: String) {
        functions.remove(name)
    }

    internal fun getFunction(name: String): ((List<Any?>) -> Any?)? = functions[name]

    /**
     * 编译表达式（带缓存）
     */
    fun compile(expr: String): CompiledExpression {
        if (cache.size > MAX_CACHE_SIZE) cache.clear()
        return cache.computeIfAbsent(expr) {
            val tokens = Lexer(it).tokenize()
            val ast = Parser(tokens).parse()
            CompiledExpression(expr, ast)
        }
    }

    /**
     * 直接求值（编译+求值）
     */
    fun eval(expr: String, context: Map<String, Any?> = emptyMap()): Any? {
        return compile(expr).evaluate(context)
    }
}

/**
 * 编译后的表达式
 */
class CompiledExpression internal constructor(
    val source: String,
    private val ast: ExprNode
) {
    fun evaluate(context: Map<String, Any?> = emptyMap()): Any? = ast.eval(context)
}

// ===== AST 节点 =====

internal sealed class ExprNode {
    abstract fun eval(ctx: Map<String, Any?>): Any?
}

internal class NumberNode(val value: Double) : ExprNode() {
    override fun eval(ctx: Map<String, Any?>): Any = value
}

internal class StringNode(val value: String) : ExprNode() {
    override fun eval(ctx: Map<String, Any?>): Any = value
}

internal class BoolNode(val value: Boolean) : ExprNode() {
    override fun eval(ctx: Map<String, Any?>): Any = value
}

internal class NullNode : ExprNode() {
    override fun eval(ctx: Map<String, Any?>): Any? = null
}

/** 简单变量引用 */
internal class VarNode(val name: String) : ExprNode() {
    override fun eval(ctx: Map<String, Any?>): Any? = ctx[name]
}

/** 点号嵌套取值: player.stats.level */
internal class DotAccessNode(val path: List<String>) : ExprNode() {
    override fun eval(ctx: Map<String, Any?>): Any? {
        var current: Any? = ctx[path[0]]
        for (i in 1 until path.size) {
            current = when (current) {
                is Map<*, *> -> current[path[i]]
                else -> return null
            }
        }
        return current
    }
}

/** 函数调用 */
internal class FunctionCallNode(val name: String, val args: List<ExprNode>) : ExprNode() {
    override fun eval(ctx: Map<String, Any?>): Any? {
        val fn = Expression.getFunction(name)
            ?: throw IllegalArgumentException("未知函数: $name")
        val evaluatedArgs = args.map { it.eval(ctx) }
        return fn(evaluatedArgs)
    }
}

/** 列表字面量 [1, 2, 3] */
internal class ListNode(val elements: List<ExprNode>) : ExprNode() {
    override fun eval(ctx: Map<String, Any?>): Any = elements.map { it.eval(ctx) }
}

/** 一元运算 */
internal class UnaryNode(val op: String, val operand: ExprNode) : ExprNode() {
    override fun eval(ctx: Map<String, Any?>): Any? = when (op) {
        "!" -> !(operand.eval(ctx).toBool())
        "-" -> -(operand.eval(ctx).toNum())
        else -> throw IllegalArgumentException("未知一元运算符: $op")
    }
}

/** 二元运算 */
internal class BinaryNode(val left: ExprNode, val op: String, val right: ExprNode) : ExprNode() {
    override fun eval(ctx: Map<String, Any?>): Any? {
        // 短路求值
        if (op == "&&") return left.eval(ctx).toBool() && right.eval(ctx).toBool()
        if (op == "||") return left.eval(ctx).toBool() || right.eval(ctx).toBool()

        // 空值合并：左侧非 null 直接返回，否则求值右侧
        if (op == "??") {
            return left.eval(ctx) ?: right.eval(ctx)
        }

        val lv = left.eval(ctx)
        val rv = right.eval(ctx)

        return when (op) {
            "+" -> {
                if (lv is String || rv is String) lv.toString() + rv.toString()
                else lv.toNum() + rv.toNum()
            }
            "-" -> lv.toNum() - rv.toNum()
            "*" -> lv.toNum() * rv.toNum()
            "/" -> {
                val r = rv.toNum()
                if (r == 0.0) throw ArithmeticException("除以零")
                lv.toNum() / r
            }
            "%" -> {
                val r = rv.toNum()
                if (r == 0.0) throw ArithmeticException("模零")
                lv.toNum() % r
            }
            "**" -> lv.toNum().pow(rv.toNum())
            ">" -> lv.toNum() > rv.toNum()
            "<" -> lv.toNum() < rv.toNum()
            ">=" -> lv.toNum() >= rv.toNum()
            "<=" -> lv.toNum() <= rv.toNum()
            "==" -> lv == rv || lv.toString() == rv.toString()
            "!=" -> lv != rv && lv.toString() != rv.toString()
            else -> throw IllegalArgumentException("未知运算符: $op")
        }
    }
}

/** 三元运算: condition ? thenExpr : elseExpr */
internal class TernaryNode(
    val condition: ExprNode,
    val thenBranch: ExprNode,
    val elseBranch: ExprNode
) : ExprNode() {
    override fun eval(ctx: Map<String, Any?>): Any? {
        return if (condition.eval(ctx).toBool()) thenBranch.eval(ctx) else elseBranch.eval(ctx)
    }
}

/** in 列表: x in [1, 2, 3] */
internal class InListNode(val value: ExprNode, val list: ExprNode) : ExprNode() {
    override fun eval(ctx: Map<String, Any?>): Any {
        val v = value.eval(ctx)
        val l = list.eval(ctx)
        return when (l) {
            is List<*> -> l.any { it == v || it.toString() == v.toString() }
            is String -> v.toString() in l  // 子串检查
            else -> false
        }
    }
}

/** in 范围: x in 1..10 */
internal class InRangeNode(
    val value: ExprNode,
    val start: ExprNode,
    val end: ExprNode
) : ExprNode() {
    override fun eval(ctx: Map<String, Any?>): Any {
        val v = value.eval(ctx).toNum()
        return v >= start.eval(ctx).toNum() && v <= end.eval(ctx).toNum()
    }
}

// ===== 类型转换辅助 =====

private fun Any?.toNum(): Double = when (this) {
    is Number -> this.toDouble()
    is String -> this.toDoubleOrNull() ?: 0.0
    is Boolean -> if (this) 1.0 else 0.0
    null -> 0.0
    else -> this.toString().toDoubleOrNull() ?: 0.0
}

private fun Any?.toBool(): Boolean = when (this) {
    is Boolean -> this
    is Number -> this.toDouble() != 0.0
    is String -> this.isNotEmpty() && this != "false" && this != "0"
    null -> false
    else -> true
}

// ===== 词法分析器 =====

internal enum class ExprTokenType {
    NUMBER, STRING, BOOL, NULL, IDENT,
    PLUS, MINUS, STAR, SLASH, PERCENT, DOUBLE_STAR,
    GT, LT, GTE, LTE, EQ, NEQ,
    AND, OR, NOT, IN,
    DOUBLE_QUESTION, QUESTION, COLON,
    LPAREN, RPAREN, LBRACKET, RBRACKET,
    COMMA, DOT, RANGE,
    EOF
}

internal data class ExprToken(val type: ExprTokenType, val value: String)

internal class Lexer(private val source: String) {
    private var pos = 0

    private fun peek(): Char = if (pos < source.length) source[pos] else '\u0000'
    private fun peekNext(): Char = if (pos + 1 < source.length) source[pos + 1] else '\u0000'

    fun tokenize(): List<ExprToken> {
        val tokens = mutableListOf<ExprToken>()
        while (pos < source.length) {
            when {
                peek().isWhitespace() -> pos++
                peek().isDigit() -> tokens.add(readNumber())
                peek() == '\'' -> tokens.add(readString())
                peek().isLetter() || peek() == '_' -> tokens.add(readIdentOrKeyword())
                else -> tokens.add(readOperator())
            }
        }
        tokens.add(ExprToken(ExprTokenType.EOF, ""))
        return tokens
    }

    private fun readNumber(): ExprToken {
        val start = pos
        while (pos < source.length && source[pos].isDigit()) pos++
        if (pos < source.length && source[pos] == '.' && pos + 1 < source.length && source[pos + 1].isDigit()) {
            pos++ // consume '.'
            while (pos < source.length && source[pos].isDigit()) pos++
        }
        return ExprToken(ExprTokenType.NUMBER, source.substring(start, pos))
    }

    private fun readString(): ExprToken {
        pos++ // skip opening quote
        val start = pos
        while (pos < source.length && source[pos] != '\'') pos++
        val value = source.substring(start, pos)
        if (pos < source.length) pos++ // skip closing quote
        return ExprToken(ExprTokenType.STRING, value)
    }

    private fun readIdentOrKeyword(): ExprToken {
        val start = pos
        while (pos < source.length && (source[pos].isLetterOrDigit() || source[pos] == '_')) pos++
        val text = source.substring(start, pos)
        return when (text) {
            "true", "false" -> ExprToken(ExprTokenType.BOOL, text)
            "null" -> ExprToken(ExprTokenType.NULL, text)
            "in" -> ExprToken(ExprTokenType.IN, text)
            else -> ExprToken(ExprTokenType.IDENT, text)
        }
    }

    private fun readOperator(): ExprToken {
        val c = peek()
        val next = peekNext()
        return when {
            c == '&' && next == '&' -> { pos += 2; ExprToken(ExprTokenType.AND, "&&") }
            c == '|' && next == '|' -> { pos += 2; ExprToken(ExprTokenType.OR, "||") }
            c == '=' && next == '=' -> { pos += 2; ExprToken(ExprTokenType.EQ, "==") }
            c == '!' && next == '=' -> { pos += 2; ExprToken(ExprTokenType.NEQ, "!=") }
            c == '>' && next == '=' -> { pos += 2; ExprToken(ExprTokenType.GTE, ">=") }
            c == '<' && next == '=' -> { pos += 2; ExprToken(ExprTokenType.LTE, "<=") }
            c == '*' && next == '*' -> { pos += 2; ExprToken(ExprTokenType.DOUBLE_STAR, "**") }
            c == '?' && next == '?' -> { pos += 2; ExprToken(ExprTokenType.DOUBLE_QUESTION, "??") }
            c == '.' && next == '.' -> { pos += 2; ExprToken(ExprTokenType.RANGE, "..") }
            c == '>' -> { pos++; ExprToken(ExprTokenType.GT, ">") }
            c == '<' -> { pos++; ExprToken(ExprTokenType.LT, "<") }
            c == '+' -> { pos++; ExprToken(ExprTokenType.PLUS, "+") }
            c == '-' -> { pos++; ExprToken(ExprTokenType.MINUS, "-") }
            c == '*' -> { pos++; ExprToken(ExprTokenType.STAR, "*") }
            c == '/' -> { pos++; ExprToken(ExprTokenType.SLASH, "/") }
            c == '%' -> { pos++; ExprToken(ExprTokenType.PERCENT, "%") }
            c == '!' -> { pos++; ExprToken(ExprTokenType.NOT, "!") }
            c == '?' -> { pos++; ExprToken(ExprTokenType.QUESTION, "?") }
            c == ':' -> { pos++; ExprToken(ExprTokenType.COLON, ":") }
            c == '(' -> { pos++; ExprToken(ExprTokenType.LPAREN, "(") }
            c == ')' -> { pos++; ExprToken(ExprTokenType.RPAREN, ")") }
            c == '[' -> { pos++; ExprToken(ExprTokenType.LBRACKET, "[") }
            c == ']' -> { pos++; ExprToken(ExprTokenType.RBRACKET, "]") }
            c == ',' -> { pos++; ExprToken(ExprTokenType.COMMA, ",") }
            c == '.' -> { pos++; ExprToken(ExprTokenType.DOT, ".") }
            else -> throw IllegalArgumentException("未知字符: '$c' 在位置 $pos")
        }
    }
}

// ===== 语法分析器（递归下降） =====
//
// 优先级（低→高）:
// 1. 三元     ? :
// 2. 空值合并  ??
// 3. 逻辑或   ||
// 4. 逻辑与   &&
// 5. 等值     == !=
// 6. 比较/成员 > < >= <= in
// 7. 加减     + -
// 8. 乘除模   * / %
// 9. 幂       **（右结合）
// 10. 一元    ! -
// 11. 基本    字面量 / 变量 / 函数 / 点号 / 括号 / 列表

internal class Parser(private val tokens: List<ExprToken>) {
    private var pos = 0

    private fun peek(): ExprToken = tokens[pos]
    private fun advance(): ExprToken = tokens[pos++]
    private fun match(type: ExprTokenType): Boolean {
        if (peek().type == type) { advance(); return true }
        return false
    }
    private fun expect(type: ExprTokenType): ExprToken {
        val t = advance()
        if (t.type != type) throw IllegalArgumentException("期望 $type 但得到 ${t.type}(${t.value})")
        return t
    }

    fun parse(): ExprNode {
        val node = parseTernary()
        if (peek().type != ExprTokenType.EOF) {
            throw IllegalArgumentException("表达式未完全解析，剩余: ${peek().value}")
        }
        return node
    }

    // condition ? thenExpr : elseExpr
    private fun parseTernary(): ExprNode {
        val condition = parseNullCoalesce()
        if (match(ExprTokenType.QUESTION)) {
            val thenBranch = parseTernary() // 右结合
            expect(ExprTokenType.COLON)
            val elseBranch = parseTernary()
            return TernaryNode(condition, thenBranch, elseBranch)
        }
        return condition
    }

    // left ?? right
    private fun parseNullCoalesce(): ExprNode {
        var left = parseOr()
        while (peek().type == ExprTokenType.DOUBLE_QUESTION) {
            advance()
            left = BinaryNode(left, "??", parseOr())
        }
        return left
    }

    private fun parseOr(): ExprNode {
        var left = parseAnd()
        while (peek().type == ExprTokenType.OR) {
            advance()
            left = BinaryNode(left, "||", parseAnd())
        }
        return left
    }

    private fun parseAnd(): ExprNode {
        var left = parseEquality()
        while (peek().type == ExprTokenType.AND) {
            advance()
            left = BinaryNode(left, "&&", parseEquality())
        }
        return left
    }

    private fun parseEquality(): ExprNode {
        var left = parseComparison()
        while (peek().type in listOf(ExprTokenType.EQ, ExprTokenType.NEQ)) {
            val op = advance().value
            left = BinaryNode(left, op, parseComparison())
        }
        return left
    }

    // 比较 + in
    private fun parseComparison(): ExprNode {
        var left = parseAddSub()

        // in 操作
        if (peek().type == ExprTokenType.IN) {
            advance()
            return parseInTarget(left)
        }

        while (peek().type in listOf(ExprTokenType.GT, ExprTokenType.LT, ExprTokenType.GTE, ExprTokenType.LTE)) {
            val op = advance().value
            left = BinaryNode(left, op, parseAddSub())
        }
        return left
    }

    // in 的右侧: [list] / start..end / expr（子串检查）
    private fun parseInTarget(left: ExprNode): ExprNode {
        // [1, 2, 3]
        if (peek().type == ExprTokenType.LBRACKET) {
            val list = parseList()
            return InListNode(left, list)
        }
        // start..end 或普通表达式
        val start = parseAddSub()
        if (peek().type == ExprTokenType.RANGE) {
            advance()
            val end = parseAddSub()
            return InRangeNode(left, start, end)
        }
        // 子串检查: 'x' in someString
        return InListNode(left, start)
    }

    private fun parseAddSub(): ExprNode {
        var left = parseMulDiv()
        while (peek().type in listOf(ExprTokenType.PLUS, ExprTokenType.MINUS)) {
            val op = advance().value
            left = BinaryNode(left, op, parseMulDiv())
        }
        return left
    }

    private fun parseMulDiv(): ExprNode {
        var left = parsePower()
        while (peek().type in listOf(ExprTokenType.STAR, ExprTokenType.SLASH, ExprTokenType.PERCENT)) {
            val op = advance().value
            left = BinaryNode(left, op, parsePower())
        }
        return left
    }

    // 右结合: 2 ** 3 ** 2 = 2 ** (3 ** 2)
    private fun parsePower(): ExprNode {
        val left = parseUnary()
        if (peek().type == ExprTokenType.DOUBLE_STAR) {
            advance()
            return BinaryNode(left, "**", parsePower())
        }
        return left
    }

    private fun parseUnary(): ExprNode {
        if (peek().type == ExprTokenType.NOT) {
            advance()
            return UnaryNode("!", parseUnary())
        }
        if (peek().type == ExprTokenType.MINUS) {
            advance()
            return UnaryNode("-", parseUnary())
        }
        return parsePrimary()
    }

    private fun parsePrimary(): ExprNode {
        val token = peek()
        return when (token.type) {
            ExprTokenType.NUMBER -> { advance(); NumberNode(token.value.toDouble()) }
            ExprTokenType.STRING -> { advance(); StringNode(token.value) }
            ExprTokenType.BOOL -> { advance(); BoolNode(token.value == "true") }
            ExprTokenType.NULL -> { advance(); NullNode() }

            ExprTokenType.IDENT -> {
                advance()
                when {
                    // 函数调用: name(args...)
                    peek().type == ExprTokenType.LPAREN -> parseFunctionCall(token.value)
                    // 点号取值: a.b.c
                    peek().type == ExprTokenType.DOT -> parseDotAccess(token.value)
                    // 普通变量
                    else -> VarNode(token.value)
                }
            }

            ExprTokenType.LPAREN -> {
                advance()
                val node = parseTernary()
                expect(ExprTokenType.RPAREN)
                node
            }

            ExprTokenType.LBRACKET -> parseList()

            else -> throw IllegalArgumentException("意外的 token: ${token.type}(${token.value})")
        }
    }

    // name(arg1, arg2, ...)
    private fun parseFunctionCall(name: String): ExprNode {
        expect(ExprTokenType.LPAREN)
        val args = mutableListOf<ExprNode>()
        if (peek().type != ExprTokenType.RPAREN) {
            args.add(parseTernary())
            while (match(ExprTokenType.COMMA)) {
                args.add(parseTernary())
            }
        }
        expect(ExprTokenType.RPAREN)
        return FunctionCallNode(name, args)
    }

    // a.b.c
    private fun parseDotAccess(firstName: String): ExprNode {
        val path = mutableListOf(firstName)
        while (match(ExprTokenType.DOT)) {
            val next = expect(ExprTokenType.IDENT)
            path.add(next.value)
        }
        return DotAccessNode(path)
    }

    // [elem1, elem2, ...]
    private fun parseList(): ExprNode {
        expect(ExprTokenType.LBRACKET)
        val elements = mutableListOf<ExprNode>()
        if (peek().type != ExprTokenType.RBRACKET) {
            elements.add(parseTernary())
            while (match(ExprTokenType.COMMA)) {
                elements.add(parseTernary())
            }
        }
        expect(ExprTokenType.RBRACKET)
        return ListNode(elements)
    }
}
