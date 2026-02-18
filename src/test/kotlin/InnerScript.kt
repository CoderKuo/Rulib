// 基础抽象语法树节点
sealed class ASTNode

// 表示整个程序
data class Program(val statements: List<Statement>) : ASTNode()

// 语句的基类
sealed class Statement : ASTNode()

// 函数声明
data class FunctionDeclaration(
    val name: String,
    val params: List<String>,
    val body: Block
) : Statement()

// 代码块，表示一组语句
data class Block(val statements: List<Statement>) : ASTNode()

// if 语句
data class IfStatement(
    val condition: Expression,
    val thenBlock: Block,
    val elseBlock: Block? = null
) : Statement()

// while 语句
data class WhileStatement(
    val condition: Expression,
    val body: Block
) : Statement()

// 表达式的基类
sealed class Expression : ASTNode()

// 变量引用或值
data class VariableExpression(val name: String) : Expression()

// 常量
data class ConstantExpression(val value: Any) : Expression()

// 函数调用
data class FunctionCall(val name: String, val arguments: List<Expression>) : Expression()

// 运算表达式
data class BinaryExpression(val left: Expression, val operator: String, val right: Expression) : Expression()

class Parser(private val tokens: List<String>) {
    private var currentIndex = 0

    private fun currentToken(): String? = if (currentIndex < tokens.size) tokens[currentIndex] else null
    private fun advance() {
        if (currentIndex < tokens.size) currentIndex++
    }

    fun parse(): Program {
        val statements = mutableListOf<Statement>()
        while (currentToken() != null) {
            statements.add(parseStatement())
        }
        return Program(statements)
    }

    private fun parseStatement(): Statement {
        val token = currentToken() ?: throw IllegalArgumentException("Unexpected end of input.")
        return when {
            token == "func" -> parseFunctionDeclaration()
            token == "if" -> parseIfStatement()
            token == "while" -> parseWhileStatement()
            else -> throw IllegalArgumentException("Unknown statement: $token")
        }
    }

    private fun parseFunctionDeclaration(): FunctionDeclaration {
        advance() // skip "func"
        val functionName = currentToken() ?: throw IllegalArgumentException("Expected function name.")
        advance()
        val params = parseParameters()
        val body = parseBlock()
        return FunctionDeclaration(functionName, params, body)
    }

    private fun parseParameters(): List<String> {
        advance() // skip "("
        val params = mutableListOf<String>()

        // 如果下一个 token 不是右括号，就开始解析参数
        if (currentToken() != ")") {
            while (currentToken() != ")") {
                val param = currentToken() ?: throw IllegalArgumentException("Expected parameter")
                params.add(param)
                advance()

                // 处理逗号
                if (currentToken() == ",") {
                    advance()
                }
            }
        }
        advance() // skip ")"
        return params
    }

    private fun parseBlock(): Block {
        advance() // skip "{"
        val statements = mutableListOf<Statement>()
        while (currentToken() != "}") {
            statements.add(parseStatement())
        }
        advance() // skip "}"
        return Block(statements)
    }

    private fun parseIfStatement(): IfStatement {
        advance() // skip "if"
        val condition = parseExpression()
        val thenBlock = parseBlock()
        val elseBlock = if (currentToken() == "else") {
            advance()
            parseBlock()
        } else {
            null
        }
        return IfStatement(condition, thenBlock, elseBlock)
    }

    private fun parseWhileStatement(): WhileStatement {
        advance() // skip "while"
        val condition = parseExpression()
        val body = parseBlock()
        return WhileStatement(condition, body)
    }

    private fun parseExpression(): Expression {
        // 对表达式的简单处理（这里只支持变量和常量）
        val token = currentToken() ?: throw IllegalArgumentException("Expected expression.")
        return if (token.toIntOrNull() != null) {
            ConstantExpression(token.toInt())
        } else {
            VariableExpression(token)
        }
    }
}

class Interpreter {

    private val variables = mutableMapOf<String, Any>()
    private val functions = mutableMapOf<String, FunctionDeclaration>()

    fun interpret(program: Program) {
        program.statements.forEach { executeStatement(it) }
    }

    private fun executeStatement(statement: Statement) {
        when (statement) {
            is FunctionDeclaration -> {
                functions[statement.name] = statement
            }
            is IfStatement -> {
                if (evaluateExpression(statement.condition)) {
                    interpretBlock(statement.thenBlock)
                } else {
                    statement.elseBlock?.let { interpretBlock(it) }
                }
            }
            is WhileStatement -> {
                while (evaluateExpression(statement.condition)) {
                    interpretBlock(statement.body)
                }
            }
            else -> throw IllegalArgumentException("Unknown statement type: $statement")
        }
    }

    private fun interpretBlock(block: Block) {
        block.statements.forEach { executeStatement(it) }
    }

    private fun evaluateExpression(expression: Expression): Boolean {
        return when (expression) {
            is ConstantExpression -> expression.value as Boolean
            is VariableExpression -> variables[expression.name] as Boolean
            else -> throw IllegalArgumentException("Unsupported expression type: $expression")
        }
    }
}

fun main() {
    val script = """
        func add(x, y) {
            return x + y
        }

        if (true) {
            println("This is true")
        } else {
            println("This is false")
        }

        while (true) {
            println("Looping...")
            break
        }
    """

    val tokens = script.split("\\s+".toRegex()).filter { it.isNotBlank() }
    val parser = Parser(tokens)
    val program = parser.parse()

    val interpreter = Interpreter()
    interpreter.interpret(program)
}
