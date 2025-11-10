package syntax

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

// I will use simple regex for this since the file will be small and it's easy to add more keywords
// for bigger files I would use a lexer
// or for more complex highlighting & soft keywords (can be a keyword or an identifier)

object SyntaxHighlighter {

    // colors here, it's easy to add themes later, just read these colors from a file for example
    private val KEYWORD_COLOR = Color(0xFF0033B3)
    private val STRING_COLOR = Color(0xFF067D17)
    private val COMMENT_COLOR = Color(0xFF8C8C8C)
    private val NUMBER_COLOR = Color(0xFF1750EB)
    private val FUNCTION_COLOR = Color(0xFF00627A)
    private val TYPE_COLOR = Color(0xFF000000)

    // only hard keywords added here, soft need some logic to see if they are actually keywords or ids
    private val KEYWORDS = setOf(
        "fun",
        "val",
        "var",
        "if",
        "else",
        "for",
        "while",
        "return",
        "class",
        "when",
        "is",
        "in",
        "object",
        "interface",
        "package",
        "as",
        "break",
        "continue",
        "do",
        "null",
        "true",
        "false",
        "this",
        "super",
        "throw",
        "try",
        "typealias",
        "typeof"
    )

    // I didn't find a list like for keywords so I added a few I know
    private val BUILTIN_FUNCTIONS = setOf(
        "println", "print", "readLine", "readln",
        "require", "check", "error", "repeat", "map", "filter"
    )

    // types
    private val TYPES = setOf(
        "Byte", "Short", "Int", "Long", "Float", "Double", "Boolean", "Char", "String",
    )

    fun highlight(code: String): AnnotatedString = buildAnnotatedString {
        append(code)
        highlightComments(code)
        highlightStrings(code)
        highlightNumbers(code)
        highlightKeywords(code)
        highlightBuiltinFunctions(code)
        highlightTypes(code)
    }

    private fun AnnotatedString.Builder.highlightComments(code: String) {
        // comment line
        val commentRegex = Regex("""//.*""")
        commentRegex.findAll(code).forEach { match ->
            addStyle(
                style = SpanStyle(color = COMMENT_COLOR),
                start = match.range.first,
                end = match.range.last + 1
            )
        }

        // comment block
        val multiCommentRegex = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        multiCommentRegex.findAll(code).forEach { match ->
            addStyle(
                style = SpanStyle(color = COMMENT_COLOR),
                start = match.range.first,
                end = match.range.last + 1
            )
        }
    }

    private fun AnnotatedString.Builder.highlightStrings(code: String) {
        // strings with 1 "
        val stringRegex = Regex(""""(?:[^"\\]|\\.)*"""")
        stringRegex.findAll(code).forEach { match ->
            if (!isInsideComment(code, match.range.first)) {
                addStyle(
                    style = SpanStyle(color = STRING_COLOR),
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }
        }
        // strings with 3 "
        val rawStringRegex = Regex(""""{3}.*?"{3}""", RegexOption.DOT_MATCHES_ALL)
        rawStringRegex.findAll(code).forEach { match ->
            if (!isInsideComment(code, match.range.first)) {
                addStyle(
                    style = SpanStyle(color = STRING_COLOR),
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }
        }
    }

    private fun AnnotatedString.Builder.highlightNumbers(code: String) {
        val numberRegex = Regex("""\b\d+\.?\d*([eE][+-]?\d+)?[fFdDlL]?\b|\b0[xX][0-9a-fA-F]+\b""")
        numberRegex.findAll(code).forEach { match ->
            if (!isInsideComment(code, match.range.first) &&
                !isInsideString(code, match.range.first)
            ) {
                addStyle(
                    style = SpanStyle(color = NUMBER_COLOR),
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }
        }
    }

    private fun AnnotatedString.Builder.highlightKeywords(code: String) {
        KEYWORDS.forEach { keyword ->
            val regex = Regex("""\b$keyword\b""")
            regex.findAll(code).forEach { match ->
                if (!isInsideComment(code, match.range.first) &&
                    !isInsideString(code, match.range.first)
                ) {
                    addStyle(
                        style = SpanStyle(color = KEYWORD_COLOR),
                        start = match.range.first,
                        end = match.range.last + 1
                    )
                }
            }
        }
    }

    private fun AnnotatedString.Builder.highlightBuiltinFunctions(code: String) {
        BUILTIN_FUNCTIONS.forEach { func ->
            val regex = Regex("""\b$func\b""")
            regex.findAll(code).forEach { match ->
                if (!isInsideComment(code, match.range.first) &&
                    !isInsideString(code, match.range.first)
                ) {
                    addStyle(
                        style = SpanStyle(color = FUNCTION_COLOR),
                        start = match.range.first,
                        end = match.range.last + 1
                    )
                }
            }
        }
    }

    private fun AnnotatedString.Builder.highlightTypes(code: String) {
        TYPES.forEach { type ->
            val regex = Regex("""\b$type\b""")
            regex.findAll(code).forEach { match ->
                if (!isInsideComment(code, match.range.first) &&
                    !isInsideString(code, match.range.first)
                ) {
                    addStyle(
                        style = SpanStyle(color = TYPE_COLOR),
                        start = match.range.first,
                        end = match.range.last + 1
                    )
                }
            }
        }
    }

    private fun isInsideComment(code: String, position: Int): Boolean {
        val lines = code.substring(0, position).lines()
        val lastLine = lines.lastOrNull() ?: return false
        if (lastLine.contains("//")) {
            val commentStart = lastLine.indexOf("//")
            val posInLine = lastLine.length
            if (posInLine > commentStart) return true
        }

        val beforePos = code.substring(0, position)
        val openCount = beforePos.count { it == '/' && beforePos.indexOf("/*", beforePos.indexOf(it)) != -1 }
        val closeCount = beforePos.count { it == '*' && beforePos.indexOf("*/", beforePos.indexOf(it)) != -1 }

        return openCount > closeCount
    }

    private fun isInsideString(code: String, position: Int): Boolean {
        val beforePos = code.substring(0, position)
        var quoteCount = 0
        var i = 0
        while (i < beforePos.length) {
            if (beforePos[i] == '"' && (i == 0 || beforePos[i - 1] != '\\')) {
                quoteCount++
            }
            i++
        }

        return quoteCount % 2 == 1
    }
}