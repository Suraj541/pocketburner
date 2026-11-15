package com.example.engine

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

enum class SyntaxTokenType {
    KEYWORD,
    TYPE_OR_HARDWARE,
    PREPROCESSOR,
    FUNCTION_CALL,
    NUMBER,
    STRING,
    COMMENT,
    OPERATOR,
}

data class SyntaxToken(
    val type: SyntaxTokenType,
    val start: Int,
    val endExclusive: Int,
)

enum class DiagnosticSeverity {
    ERROR,
    WARNING,
}

data class CodeDiagnostic(
    val lineIndex: Int,
    val column: Int,
    val startOffset: Int,
    val endOffset: Int,
    val severity: DiagnosticSeverity,
    val message: String,
    val ruleCode: String,
) {
    val oneBasedLine: Int get() = lineIndex + 1
    val oneBasedColumn: Int get() = column + 1
}

data class SyntaxAnalysisResult(
    val tokens: List<SyntaxToken>,
    val diagnostics: List<CodeDiagnostic>,
    val diagnosticsByLine: Map<Int, List<CodeDiagnostic>>,
    val errorCount: Int,
    val warningCount: Int,
)

data class SyntaxColorPalette(
    val keyword: Color = Color(0xFF7DD3FC),
    val typeOrHardware: Color = Color(0xFFC4B5FD),
    val preprocessor: Color = Color(0xFFF472B6),
    val functionCall: Color = Color(0xFF38BDF8),
    val number: Color = Color(0xFFFBBF24),
    val string: Color = Color(0xFF86EFAC),
    val comment: Color = Color(0xFF94A3B8),
    val operator: Color = Color(0xFFFB7185),
    val errorUnderline: Color = Color(0xFFF87171),
    val errorBackground: Color = Color(0x4DF87171),
    val warningUnderline: Color = Color(0xFFFBBF24),
    val warningBackground: Color = Color(0x38FBBF24),
    val searchBackground: Color = Color(0xFFF59E0B),
    val searchForeground: Color = Color(0xFF000000),
)

/**
 * Lexer, syntax highlighter, and lightweight static diagnostics engine for
 * IoT languages supported in the PocketBurner Nano Script Studio
 * (Arduino C++, MicroPython, Python, Lua, Bash, JavaScript, ESPHome YAML/JSON).
 */
object ScriptSyntaxEngine {

    private val cppKeywords = setOf(
        "alignas", "alignof", "auto", "break", "case", "catch", "class", "const", "constexpr",
        "continue", "default", "delete", "do", "else", "enum", "explicit", "extern", "for",
        "friend", "goto", "if", "inline", "namespace", "new", "noexcept", "nullptr", "operator",
        "override", "private", "protected", "public", "return", "sizeof", "static", "struct",
        "switch", "template", "this", "throw", "try", "typedef", "typename", "using", "virtual",
        "volatile", "while", "setup", "loop",
    )

    private val cppTypesAndHardware = setOf(
        "void", "bool", "boolean", "byte", "char", "int", "short", "long", "float", "double",
        "unsigned", "signed", "uint8_t", "uint16_t", "uint32_t", "uint64_t", "int8_t", "int16_t",
        "int32_t", "int64_t", "size_t", "String", "Serial", "Serial1", "Serial2", "Wire", "SPI",
        "WiFi", "M5", "ESP", "PROGMEM", "IRAM_ATTR", "LED_BUILTIN", "HIGH", "LOW", "INPUT",
        "INPUT_PULLUP", "OUTPUT", "OPEN_DRAIN", "true", "false", "pinMode", "digitalWrite",
        "digitalRead", "analogRead", "analogWrite", "delay", "delayMicroseconds", "millis",
        "micros", "attachInterrupt",
    )

    private val pythonKeywords = setOf(
        "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del",
        "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in",
        "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while",
        "with", "yield",
    )

    private val pythonHardwareAndBuiltins = setOf(
        "True", "False", "None", "self", "machine", "Pin", "I2C", "SPI", "UART", "PWM", "ADC",
        "Timer", "WDT", "RTC", "network", "WLAN", "STA_IF", "AP_IF", "time", "utime", "ticks_ms",
        "ticks_us", "ubinascii", "uos", "ujson", "esp", "esp32", "rp2", "gpiozero", "LED",
        "Button", "print", "len", "range", "hex", "str", "int", "float", "bytes", "bytearray",
        "open", "enumerate", "isinstance", "Exception",
    )

    private val luaKeywords = setOf(
        "and", "break", "do", "else", "elseif", "end", "for", "function", "goto", "if", "in",
        "local", "not", "or", "repeat", "return", "then", "until", "while",
    )

    private val luaHardwareAndBuiltins = setOf(
        "true", "false", "nil", "gpio", "tmr", "wifi", "net", "mqtt", "i2c", "spi", "adc", "pwm",
        "uart", "node", "file", "cjson", "sjson", "crypto", "rtctime", "sntp", "bit", "string",
        "table", "math", "print", "tostring", "tonumber", "pcall", "xpcall", "pairs", "ipairs",
        "OUTPUT", "INPUT", "HIGH", "LOW", "ALARM_AUTO", "ALARM_SINGLE", "STATION", "SOFTAP",
    )

    private val jsKeywords = setOf(
        "async", "await", "break", "case", "catch", "class", "const", "continue", "default",
        "delete", "else", "export", "extends", "finally", "for", "function", "if", "import",
        "in", "instanceof", "let", "new", "return", "switch", "this", "throw", "try", "typeof",
        "var", "void", "while", "with", "yield",
    )

    private val jsHardwareAndBuiltins = setOf(
        "true", "false", "null", "undefined", "console", "JSON", "Math", "Promise", "fetch",
        "setTimeout", "setInterval", "clearInterval", "clearTimeout", "digitalWrite", "digitalRead",
        "pinMode", "LED1", "LED2", "I2C1", "SPI1", "Serial1",
    )

    private val bashKeywords = setOf(
        "if", "then", "else", "elif", "fi", "for", "in", "do", "done", "while", "until",
        "case", "esac", "function", "return", "exit", "export", "local", "set", "unset",
        "echo", "sudo", "systemctl", "grep", "curl", "cat", "chmod", "chown", "apt", "uname",
    )

    private val yamlKeywords = setOf(
        "esphome", "esp32", "esp8266", "rp2040", "wifi", "ssid", "password", "ota", "api",
        "logger", "sensor", "binary_sensor", "switch", "light", "output", "display", "i2c",
        "spi", "uart", "platform", "board", "pin", "name", "id", "true", "false", "null",
    )

    fun analyze(source: String, language: ScriptLanguage): SyntaxAnalysisResult {
        val tokens = mutableListOf<SyntaxToken>()
        val diagnostics = mutableListOf<CodeDiagnostic>()
        val lineStarts = computeLineStarts(source)

        tokenizeAndCheckLexicalDiagnostics(
            source = source,
            language = language,
            lineStarts = lineStarts,
            outTokens = tokens,
            outDiagnostics = diagnostics,
        )

        checkDelimiterBalance(source, tokens, lineStarts, diagnostics)
        checkLanguageSyntaxDiagnostics(source, language, tokens, lineStarts, diagnostics)

        val sortedDiagnostics = diagnostics
            .distinctBy { Triple(it.lineIndex, it.column, it.ruleCode) }
            .sortedWith(compareBy({ it.lineIndex }, { it.severity.ordinal }, { it.column }))

        val byLine = sortedDiagnostics.groupBy { it.lineIndex }
        return SyntaxAnalysisResult(
            tokens = tokens,
            diagnostics = sortedDiagnostics,
            diagnosticsByLine = byLine,
            errorCount = sortedDiagnostics.count { it.severity == DiagnosticSeverity.ERROR },
            warningCount = sortedDiagnostics.count { it.severity == DiagnosticSeverity.WARNING },
        )
    }

    fun buildHighlightedText(
        source: String,
        language: ScriptLanguage,
        searchQuery: String = "",
        analysis: SyntaxAnalysisResult = analyze(source, language),
        palette: SyntaxColorPalette = SyntaxColorPalette(),
    ): AnnotatedString = buildAnnotatedString {
        append(source)
        val len = source.length

        for (token in analysis.tokens) {
            val s = token.start.coerceIn(0, len)
            val e = token.endExclusive.coerceIn(s, len)
            if (e <= s) continue
            val style = when (token.type) {
                SyntaxTokenType.KEYWORD -> SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold)
                SyntaxTokenType.TYPE_OR_HARDWARE -> SpanStyle(color = palette.typeOrHardware, fontWeight = FontWeight.Medium)
                SyntaxTokenType.PREPROCESSOR -> SpanStyle(color = palette.preprocessor, fontWeight = FontWeight.Medium)
                SyntaxTokenType.FUNCTION_CALL -> SpanStyle(color = palette.functionCall)
                SyntaxTokenType.NUMBER -> SpanStyle(color = palette.number)
                SyntaxTokenType.STRING -> SpanStyle(color = palette.string)
                SyntaxTokenType.COMMENT -> SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic)
                SyntaxTokenType.OPERATOR -> SpanStyle(color = palette.operator)
            }
            addStyle(style, s, e)
        }

        for (diag in analysis.diagnostics) {
            val s = diag.startOffset.coerceIn(0, len)
            var e = diag.endOffset.coerceIn(s, len)
            if (e <= s && s < len) e = (s + 1).coerceAtMost(len)
            if (e > s) {
                val style = if (diag.severity == DiagnosticSeverity.ERROR) {
                    SpanStyle(
                        background = palette.errorBackground,
                        textDecoration = TextDecoration.Underline,
                    )
                } else {
                    SpanStyle(
                        background = palette.warningBackground,
                        textDecoration = TextDecoration.Underline,
                    )
                }
                addStyle(style, s, e)
            }
        }

        if (searchQuery.isNotBlank()) {
            Regex(Regex.escape(searchQuery), RegexOption.IGNORE_CASE).findAll(source).forEach { m ->
                addStyle(
                    SpanStyle(background = palette.searchBackground, color = palette.searchForeground),
                    m.range.first,
                    m.range.last + 1,
                )
            }
        }
    }

    private fun tokenizeAndCheckLexicalDiagnostics(
        source: String,
        language: ScriptLanguage,
        lineStarts: IntArray,
        outTokens: MutableList<SyntaxToken>,
        outDiagnostics: MutableList<CodeDiagnostic>,
    ) {
        val n = source.length
        var i = 0
        var lineStart = true

        while (i < n) {
            val ch = source[i]

            if (ch == '\n') {
                lineStart = true
                i++
                continue
            }
            if (ch == ' ' || ch == '\t' || ch == '\r') {
                i++
                continue
            }

            // 1. Preprocessor / Shebang / Decorators
            if (ch == '#' && (language == ScriptLanguage.ARDUINO_CPP)) {
                val lineEnd = findLineEnd(source, i)
                // Directive word e.g. #include, #define, #ifdef
                var wordEnd = i + 1
                while (wordEnd < lineEnd && (source[wordEnd].isLetterOrDigit() || source[wordEnd] == '_')) {
                    wordEnd++
                }
                outTokens.add(SyntaxToken(SyntaxTokenType.PREPROCESSOR, i, wordEnd))
                // Angle header <Arduino.h> inside #include
                val rest = source.substring(wordEnd, lineEnd)
                val lt = rest.indexOf('<')
                val gt = rest.indexOf('>')
                if (lt >= 0 && gt > lt) {
                    outTokens.add(SyntaxToken(SyntaxTokenType.STRING, wordEnd + lt, wordEnd + gt + 1))
                }
                i = wordEnd
                lineStart = false
                continue
            }
            if ((language == ScriptLanguage.MICROPYTHON || language == ScriptLanguage.PYTHON) &&
                ch == '@' && lineStart
            ) {
                val endDec = findIdentifierEnd(source, i + 1)
                outTokens.add(SyntaxToken(SyntaxTokenType.PREPROCESSOR, i, endDec))
                i = endDec
                lineStart = false
                continue
            }
            if (ch == '#' && source.startsWith("#!", i)) {
                val lineEnd = findLineEnd(source, i)
                outTokens.add(SyntaxToken(SyntaxTokenType.PREPROCESSOR, i, lineEnd))
                i = lineEnd
                continue
            }

            // 2. Comments (single-line & multi-line)
            if ((language == ScriptLanguage.ARDUINO_CPP || language == ScriptLanguage.JAVASCRIPT) &&
                source.startsWith("//", i)
            ) {
                val lineEnd = findLineEnd(source, i)
                outTokens.add(SyntaxToken(SyntaxTokenType.COMMENT, i, lineEnd))
                i = lineEnd
                continue
            }
            if ((language == ScriptLanguage.ARDUINO_CPP || language == ScriptLanguage.JAVASCRIPT) &&
                source.startsWith("/*", i)
            ) {
                val closeIdx = source.indexOf("*/", i + 2)
                if (closeIdx < 0) {
                    outTokens.add(SyntaxToken(SyntaxTokenType.COMMENT, i, n))
                    val line = offsetToLine(lineStarts, i)
                    outDiagnostics.add(
                        CodeDiagnostic(
                            lineIndex = line,
                            column = i - lineStarts[line],
                            startOffset = i,
                            endOffset = (i + 2).coerceAtMost(n),
                            severity = DiagnosticSeverity.ERROR,
                            message = "Unterminated block comment '/* ... */'",
                            ruleCode = "UNCLOSED_BLOCK_COMMENT",
                        )
                    )
                    i = n
                } else {
                    outTokens.add(SyntaxToken(SyntaxTokenType.COMMENT, i, closeIdx + 2))
                    i = closeIdx + 2
                }
                lineStart = false
                continue
            }
            if (language == ScriptLanguage.LUA && source.startsWith("--[[", i)) {
                val closeIdx = source.indexOf("]]", i + 4)
                if (closeIdx < 0) {
                    outTokens.add(SyntaxToken(SyntaxTokenType.COMMENT, i, n))
                    val line = offsetToLine(lineStarts, i)
                    outDiagnostics.add(
                        CodeDiagnostic(
                            lineIndex = line,
                            column = i - lineStarts[line],
                            startOffset = i,
                            endOffset = (i + 4).coerceAtMost(n),
                            severity = DiagnosticSeverity.ERROR,
                            message = "Unterminated Lua block comment '--[[ ... ]]'",
                            ruleCode = "UNCLOSED_LUA_COMMENT",
                        )
                    )
                    i = n
                } else {
                    outTokens.add(SyntaxToken(SyntaxTokenType.COMMENT, i, closeIdx + 2))
                    i = closeIdx + 2
                }
                lineStart = false
                continue
            }
            if (language == ScriptLanguage.LUA && source.startsWith("--", i)) {
                val lineEnd = findLineEnd(source, i)
                outTokens.add(SyntaxToken(SyntaxTokenType.COMMENT, i, lineEnd))
                i = lineEnd
                continue
            }
            if ((language == ScriptLanguage.MICROPYTHON ||
                    language == ScriptLanguage.PYTHON ||
                    language == ScriptLanguage.BASH ||
                    language == ScriptLanguage.YAML_JSON) && ch == '#'
            ) {
                val lineEnd = findLineEnd(source, i)
                outTokens.add(SyntaxToken(SyntaxTokenType.COMMENT, i, lineEnd))
                i = lineEnd
                continue
            }

            // 3. Multi-line strings (Python triple quotes / Lua [[ ]])
            if ((language == ScriptLanguage.MICROPYTHON || language == ScriptLanguage.PYTHON) &&
                (source.startsWith("\"\"\"", i) || source.startsWith("'''", i))
            ) {
                val quote = source.substring(i, i + 3)
                val closeIdx = source.indexOf(quote, i + 3)
                if (closeIdx < 0) {
                    outTokens.add(SyntaxToken(SyntaxTokenType.STRING, i, n))
                    val line = offsetToLine(lineStarts, i)
                    outDiagnostics.add(
                        CodeDiagnostic(
                            lineIndex = line,
                            column = i - lineStarts[line],
                            startOffset = i,
                            endOffset = (i + 3).coerceAtMost(n),
                            severity = DiagnosticSeverity.ERROR,
                            message = "Unterminated triple-quoted string ($quote)",
                            ruleCode = "UNCLOSED_MULTILINE_STRING",
                        )
                    )
                    i = n
                } else {
                    outTokens.add(SyntaxToken(SyntaxTokenType.STRING, i, closeIdx + 3))
                    i = closeIdx + 3
                }
                lineStart = false
                continue
            }
            if (language == ScriptLanguage.LUA && source.startsWith("[[", i)) {
                val closeIdx = source.indexOf("]]", i + 2)
                if (closeIdx < 0) {
                    outTokens.add(SyntaxToken(SyntaxTokenType.STRING, i, n))
                    val line = offsetToLine(lineStarts, i)
                    outDiagnostics.add(
                        CodeDiagnostic(
                            lineIndex = line,
                            column = i - lineStarts[line],
                            startOffset = i,
                            endOffset = (i + 2).coerceAtMost(n),
                            severity = DiagnosticSeverity.ERROR,
                            message = "Unterminated Lua long string '[[ ... ]]'",
                            ruleCode = "UNCLOSED_LUA_STRING",
                        )
                    )
                    i = n
                } else {
                    outTokens.add(SyntaxToken(SyntaxTokenType.STRING, i, closeIdx + 2))
                    i = closeIdx + 2
                }
                lineStart = false
                continue
            }

            // 4. Single-line quoted strings (and template literals for JS)
            if (ch == '"' || ch == '\'' || (ch == '`' && language == ScriptLanguage.JAVASCRIPT)) {
                val quote = ch
                var j = i + 1
                var closed = false
                while (j < n) {
                    val c = source[j]
                    if (c == '\\') {
                        j += 2
                        continue
                    }
                    if (c == '\n' && quote != '`') {
                        break
                    }
                    if (c == quote) {
                        j++
                        closed = true
                        break
                    }
                    j++
                }
                outTokens.add(SyntaxToken(SyntaxTokenType.STRING, i, j))
                if (!closed) {
                    val line = offsetToLine(lineStarts, i)
                    outDiagnostics.add(
                        CodeDiagnostic(
                            lineIndex = line,
                            column = i - lineStarts[line],
                            startOffset = i,
                            endOffset = j,
                            severity = DiagnosticSeverity.ERROR,
                            message = "Unterminated string literal ($quote...$quote)",
                            ruleCode = "UNCLOSED_STRING",
                        )
                    )
                }
                i = j
                lineStart = false
                continue
            }

            // 5. Numbers (hex 0x..., binary 0b..., float/int)
            if (ch.isDigit() || (ch == '.' && i + 1 < n && source[i + 1].isDigit())) {
                var j = i
                if (source.startsWith("0x", i, ignoreCase = true) || source.startsWith("0b", i, ignoreCase = true)) {
                    j += 2
                    while (j < n && (source[j].isLetterOrDigit() || source[j] == '_')) j++
                } else {
                    while (j < n && (source[j].isDigit() || source[j] == '.' || source[j] == '_')) j++
                    if (j < n && (source[j] == 'u' || source[j] == 'U' || source[j] == 'l' || source[j] == 'L' || source[j] == 'f' || source[j] == 'F')) {
                        j++
                    }
                }
                outTokens.add(SyntaxToken(SyntaxTokenType.NUMBER, i, j))
                i = j
                lineStart = false
                continue
            }

            // 6. Identifiers, Keywords, Hardware APIs, Function Calls, YAML Keys
            if (ch.isLetter() || ch == '_') {
                val j = findIdentifierEnd(source, i)
                val word = source.substring(i, j)
                val isFollowedByParen = skipInlineSpaces(source, j).let { nextIdx ->
                    nextIdx < n && source[nextIdx] == '('
                }
                val isYamlKey = language == ScriptLanguage.YAML_JSON &&
                    skipInlineSpaces(source, j).let { nextIdx -> nextIdx < n && source[nextIdx] == ':' }

                val tokenType = when {
                    isYamlKey -> SyntaxTokenType.PREPROCESSOR
                    isKeyword(word, language) -> SyntaxTokenType.KEYWORD
                    isTypeOrHardware(word, language) -> SyntaxTokenType.TYPE_OR_HARDWARE
                    isFollowedByParen -> SyntaxTokenType.FUNCTION_CALL
                    else -> null
                }
                if (tokenType != null) {
                    outTokens.add(SyntaxToken(tokenType, i, j))
                }
                i = j
                lineStart = false
                continue
            }

            // 7. Multi-char & single-char operators
            val twoChar = if (i + 1 < n) source.substring(i, i + 2) else ""
            if (twoChar in setOf("==", "!=", "<=", ">=", "->", "::", "..", "&&", "||", "<<", ">>", "+=", "-=", "*=", "/=")) {
                outTokens.add(SyntaxToken(SyntaxTokenType.OPERATOR, i, i + 2))
                i += 2
                lineStart = false
                continue
            }
            if (ch in setOf('+', '-', '*', '/', '=', '<', '>', '&', '|', '^', '!', '%', '~', '?')) {
                outTokens.add(SyntaxToken(SyntaxTokenType.OPERATOR, i, i + 1))
            }

            lineStart = false
            i++
        }
    }

    private fun checkDelimiterBalance(
        source: String,
        tokens: List<SyntaxToken>,
        lineStarts: IntArray,
        outDiagnostics: MutableList<CodeDiagnostic>,
    ) {
        val ignoredRanges = tokens
            .filter { it.type == SyntaxTokenType.COMMENT || it.type == SyntaxTokenType.STRING }
            .sortedBy { it.start }

        var tokIdx = 0
        fun isIgnored(pos: Int): Boolean {
            while (tokIdx < ignoredRanges.size && ignoredRanges[tokIdx].endExclusive <= pos) {
                tokIdx++
            }
            return tokIdx < ignoredRanges.size &&
                pos >= ignoredRanges[tokIdx].start &&
                pos < ignoredRanges[tokIdx].endExclusive
        }

        data class OpenBracket(val ch: Char, val offset: Int)
        val stack = ArrayDeque<OpenBracket>()

        for (i in source.indices) {
            val ch = source[i]
            if (ch !in charArrayOf('(', '[', '{', ')', ']', '}')) continue
            if (isIgnored(i)) continue

            when (ch) {
                '(', '[', '{' -> stack.addLast(OpenBracket(ch, i))
                ')', ']', '}' -> {
                    val expectedOpen = when (ch) {
                        ')' -> '('
                        ']' -> '['
                        else -> '{'
                    }
                    if (stack.isEmpty()) {
                        val line = offsetToLine(lineStarts, i)
                        outDiagnostics.add(
                            CodeDiagnostic(
                                lineIndex = line,
                                column = i - lineStarts[line],
                                startOffset = i,
                                endOffset = i + 1,
                                severity = DiagnosticSeverity.ERROR,
                                message = "Unexpected closing '$ch' without matching '$expectedOpen'",
                                ruleCode = "UNMATCHED_CLOSING_$ch",
                            )
                        )
                    } else if (stack.last().ch != expectedOpen) {
                        val top = stack.last()
                        val line = offsetToLine(lineStarts, i)
                        outDiagnostics.add(
                            CodeDiagnostic(
                                lineIndex = line,
                                column = i - lineStarts[line],
                                startOffset = i,
                                endOffset = i + 1,
                                severity = DiagnosticSeverity.ERROR,
                                message = "Mismatched delimiter: expected '${matchingClose(top.ch)}' for '${top.ch}' at line ${offsetToLine(lineStarts, top.offset) + 1}, found '$ch'",
                                ruleCode = "MISMATCHED_DELIMITER_$ch",
                            )
                        )
                    } else {
                        stack.removeLast()
                    }
                }
            }
        }

        while (stack.isNotEmpty()) {
            val unclosed = stack.removeLast()
            val line = offsetToLine(lineStarts, unclosed.offset)
            outDiagnostics.add(
                CodeDiagnostic(
                    lineIndex = line,
                    column = unclosed.offset - lineStarts[line],
                    startOffset = unclosed.offset,
                    endOffset = unclosed.offset + 1,
                    severity = DiagnosticSeverity.ERROR,
                    message = "Unclosed '${unclosed.ch}' (missing '${matchingClose(unclosed.ch)}')",
                    ruleCode = "UNCLOSED_DELIMITER_${unclosed.ch}_${unclosed.offset}",
                )
            )
        }
    }

    private fun checkLanguageSyntaxDiagnostics(
        source: String,
        language: ScriptLanguage,
        tokens: List<SyntaxToken>,
        lineStarts: IntArray,
        outDiagnostics: MutableList<CodeDiagnostic>,
    ) {
        val commentOrStringSpans = tokens.filter { it.type == SyntaxTokenType.COMMENT || it.type == SyntaxTokenType.STRING }
        val lines = source.lines()

        when (language) {
            ScriptLanguage.ARDUINO_CPP -> checkArduinoCppLines(lines, lineStarts, commentOrStringSpans, outDiagnostics)
            ScriptLanguage.MICROPYTHON, ScriptLanguage.PYTHON -> checkPythonLines(lines, lineStarts, commentOrStringSpans, outDiagnostics)
            ScriptLanguage.LUA -> checkLuaLines(source, lines, lineStarts, commentOrStringSpans, outDiagnostics)
            ScriptLanguage.YAML_JSON -> checkYamlLines(lines, lineStarts, outDiagnostics)
            else -> Unit
        }
    }

    private fun checkArduinoCppLines(
        lines: List<String>,
        lineStarts: IntArray,
        commentOrStringSpans: List<SyntaxToken>,
        outDiagnostics: MutableList<CodeDiagnostic>,
    ) {
        val hasAssignmentInCondition = Regex("""\b(?:if|while)\s*\(\s*[a-zA-Z_][a-zA-Z0-9_]*\s*=\s*[^=].*\)""")
        val functionHeaderRegex = Regex("""^[a-zA-Z_][a-zA-Z0-9_:<>\s*&]+\s+[a-zA-Z_][a-zA-Z0-9_]*\s*\([^;]*\)\s*(const)?\s*$""")
        var parenDepth = 0

        lines.forEachIndexed { idx, rawLine ->
            val lineStartOffset = lineStarts.getOrElse(idx) { 0 }
            val stripped = stripCommentsFromLine(rawLine, lineStartOffset, commentOrStringSpans).trim()
            if (stripped.isEmpty()) return@forEachIndexed

            if (hasAssignmentInCondition.containsMatchIn(stripped)) {
                outDiagnostics.add(
                    CodeDiagnostic(
                        lineIndex = idx,
                        column = rawLine.indexOf('=').coerceAtLeast(0),
                        startOffset = lineStartOffset,
                        endOffset = lineStartOffset + rawLine.length,
                        severity = DiagnosticSeverity.WARNING,
                        message = "Assignment '=' inside conditional expression; did you mean '=='?",
                        ruleCode = "CPP_ASSIGN_IN_COND_$idx",
                    )
                )
            }

            val openParens = stripped.count { it == '(' }
            val closeParens = stripped.count { it == ')' }
            val nextDepth = (parenDepth + openParens - closeParens).coerceAtLeast(0)

            val skipSemicolon = stripped.startsWith("#") ||
                stripped.endsWith(";") ||
                stripped.endsWith("{") ||
                stripped.endsWith("}") ||
                stripped.endsWith(":") ||
                stripped.endsWith(",") ||
                nextDepth > 0

            parenDepth = nextDepth
            if (skipSemicolon) return@forEachIndexed

            val startsControl = Regex("""^(if|else\s+if|else|for|while|switch|do|try|catch)\b""").containsMatchIn(stripped)
            if (startsControl || functionHeaderRegex.matches(stripped)) return@forEachIndexed

            // If inside multi-line paren/array literal continuation, don't false-flag
            val nextNonBlank = lines.drop(idx + 1).firstOrNull {
                stripCommentsFromLine(it, 0, emptyList()).trim().isNotEmpty()
            }?.trim().orEmpty()
            if (nextNonBlank.startsWith("{") || nextNonBlank.startsWith(".") || nextNonBlank.startsWith(")")) {
                return@forEachIndexed
            }

            val trimmedRightLen = rawLine.trimEnd().length
            outDiagnostics.add(
                CodeDiagnostic(
                    lineIndex = idx,
                    column = (trimmedRightLen - 1).coerceAtLeast(0),
                    startOffset = lineStartOffset + rawLine.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0),
                    endOffset = lineStartOffset + trimmedRightLen.coerceAtLeast(1),
                    severity = DiagnosticSeverity.ERROR,
                    message = "Missing trailing ';' in C++ statement",
                    ruleCode = "CPP_MISSING_SEMICOLON_$idx",
                )
            )
        }
    }

    private fun checkPythonLines(
        lines: List<String>,
        lineStarts: IntArray,
        commentOrStringSpans: List<SyntaxToken>,
        outDiagnostics: MutableList<CodeDiagnostic>,
    ) {
        val blockHeaderRegex = Regex("""^(def|class|if|elif|else|for|while|try|except|finally|with|async\s+def|async\s+for|async\s+with)\b.*""")
        val assignInIfRegex = Regex("""^(if|elif|while)\s+[a-zA-Z_][a-zA-Z0-9_]*\s*=\s*[^=].*:$""")
        var parenBalance = 0

        lines.forEachIndexed { idx, rawLine ->
            val lineStartOffset = lineStarts.getOrElse(idx) { 0 }
            if (rawLine.contains("\t") && rawLine.trimStart().length < rawLine.length && rawLine.contains(" ")) {
                outDiagnostics.add(
                    CodeDiagnostic(
                        lineIndex = idx,
                        column = rawLine.indexOf('\t').coerceAtLeast(0),
                        startOffset = lineStartOffset,
                        endOffset = lineStartOffset + rawLine.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(1),
                        severity = DiagnosticSeverity.ERROR,
                        message = "Mixed tabs and spaces in indentation",
                        ruleCode = "PY_MIXED_INDENT_$idx",
                    )
                )
            }

            val codeOnly = stripCommentsFromLine(rawLine, lineStartOffset, commentOrStringSpans)
            val trimmed = codeOnly.trim()
            if (trimmed.isEmpty()) return@forEachIndexed

            if (parenBalance == 0 && blockHeaderRegex.matches(trimmed) && !trimmed.endsWith(":") && !trimmed.endsWith("\\")) {
                val opensCount = trimmed.count { it == '(' || it == '[' || it == '{' }
                val closesCount = trimmed.count { it == ')' || it == ']' || it == '}' }
                if (opensCount == closesCount) {
                    outDiagnostics.add(
                        CodeDiagnostic(
                            lineIndex = idx,
                            column = (rawLine.trimEnd().length - 1).coerceAtLeast(0),
                            startOffset = lineStartOffset + rawLine.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0),
                            endOffset = lineStartOffset + rawLine.trimEnd().length.coerceAtLeast(1),
                            severity = DiagnosticSeverity.ERROR,
                            message = "Expected ':' at end of Python block statement",
                            ruleCode = "PY_MISSING_COLON_$idx",
                        )
                    )
                }
            }

            if (assignInIfRegex.matches(trimmed)) {
                outDiagnostics.add(
                    CodeDiagnostic(
                        lineIndex = idx,
                        column = rawLine.indexOf('=').coerceAtLeast(0),
                        startOffset = lineStartOffset + rawLine.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0),
                        endOffset = lineStartOffset + rawLine.trimEnd().length.coerceAtLeast(1),
                        severity = DiagnosticSeverity.ERROR,
                        message = "Invalid syntax: assignment '=' inside condition (use '==')",
                        ruleCode = "PY_ASSIGN_IN_COND_$idx",
                    )
                )
            }

            parenBalance += codeOnly.count { it == '(' || it == '[' || it == '{' }
            parenBalance = (parenBalance - codeOnly.count { it == ')' || it == ']' || it == '}' }).coerceAtLeast(0)
        }
    }

    private fun checkLuaLines(
        source: String,
        lines: List<String>,
        lineStarts: IntArray,
        commentOrStringSpans: List<SyntaxToken>,
        outDiagnostics: MutableList<CodeDiagnostic>,
    ) {
        val ignoredSpans = commentOrStringSpans.sortedBy { it.start }
        fun insideCommentOrString(offset: Int): Boolean =
            ignoredSpans.any { offset >= it.start && offset < it.endExclusive }

        data class LuaBlock(val keyword: String, val lineIndex: Int, val offset: Int)
        val blockStack = ArrayDeque<LuaBlock>()

        val wordRegex = Regex("""\b[a-zA-Z_][a-zA-Z0-9_]*\b""")
        wordRegex.findAll(source).forEach { match ->
            val start = match.range.first
            if (insideCommentOrString(start)) return@forEach
            val line = offsetToLine(lineStarts, start)
            when (val w = match.value) {
                "function", "if", "for", "while", "repeat" -> {
                    blockStack.addLast(LuaBlock(w, line, start))
                }
                "do" -> {
                    // 'for ... do' or 'while ... do' shares one block with its 'for'/'while'; standalone 'do' pushes a block
                    val top = blockStack.lastOrNull()
                    if (top == null || (top.keyword != "for" && top.keyword != "while") || top.lineIndex != line) {
                        blockStack.addLast(LuaBlock("do", line, start))
                    }
                }
                "end" -> {
                    if (blockStack.isEmpty() || blockStack.last().keyword == "repeat") {
                        outDiagnostics.add(
                            CodeDiagnostic(
                                lineIndex = line,
                                column = start - lineStarts[line],
                                startOffset = start,
                                endOffset = match.range.last + 1,
                                severity = DiagnosticSeverity.ERROR,
                                message = "Unexpected Lua 'end' without matching 'function'/'if'/'for'/'while'/'do'",
                                ruleCode = "LUA_EXTRA_END_$start",
                            )
                        )
                    } else {
                        blockStack.removeLast()
                    }
                }
                "until" -> {
                    val idx = blockStack.indexOfLast { it.keyword == "repeat" }
                    if (idx < 0) {
                        outDiagnostics.add(
                            CodeDiagnostic(
                                lineIndex = line,
                                column = start - lineStarts[line],
                                startOffset = start,
                                endOffset = match.range.last + 1,
                                severity = DiagnosticSeverity.ERROR,
                                message = "Unexpected Lua 'until' without matching 'repeat'",
                                ruleCode = "LUA_EXTRA_UNTIL_$start",
                            )
                        )
                    } else {
                        while (blockStack.size > idx) blockStack.removeLast()
                    }
                }
            }
        }

        while (blockStack.isNotEmpty()) {
            val unclosed = blockStack.removeLast()
            val closingWord = if (unclosed.keyword == "repeat") "until" else "end"
            outDiagnostics.add(
                CodeDiagnostic(
                    lineIndex = unclosed.lineIndex,
                    column = unclosed.offset - lineStarts[unclosed.lineIndex],
                    startOffset = unclosed.offset,
                    endOffset = unclosed.offset + unclosed.keyword.length,
                    severity = DiagnosticSeverity.ERROR,
                    message = "Unclosed Lua '${unclosed.keyword}' block (missing '$closingWord')",
                    ruleCode = "LUA_UNCLOSED_${unclosed.offset}",
                )
            )
        }

        val ifAssignRegex = Regex("""^(if|elseif)\s+[a-zA-Z_][a-zA-Z0-9_]*\s*=\s*[^=].*""")
        lines.forEachIndexed { idx, raw ->
            val startOffset = lineStarts.getOrElse(idx) { 0 }
            val trimmed = stripCommentsFromLine(raw, startOffset, commentOrStringSpans).trim()
            if (trimmed.isEmpty()) return@forEachIndexed

            if (Regex("""^(if|elseif)\b""").containsMatchIn(trimmed) && !Regex("""\bthen\b""").containsMatchIn(trimmed)) {
                outDiagnostics.add(
                    CodeDiagnostic(
                        lineIndex = idx,
                        column = 0,
                        startOffset = startOffset,
                        endOffset = startOffset + raw.trimEnd().length.coerceAtLeast(1),
                        severity = DiagnosticSeverity.ERROR,
                        message = "Expected 'then' in Lua conditional statement",
                        ruleCode = "LUA_MISSING_THEN_$idx",
                    )
                )
            }
            if (ifAssignRegex.matches(trimmed)) {
                outDiagnostics.add(
                    CodeDiagnostic(
                        lineIndex = idx,
                        column = raw.indexOf('=').coerceAtLeast(0),
                        startOffset = startOffset,
                        endOffset = startOffset + raw.trimEnd().length.coerceAtLeast(1),
                        severity = DiagnosticSeverity.ERROR,
                        message = "Assignment '=' inside Lua condition; use '=='",
                        ruleCode = "LUA_ASSIGN_IN_COND_$idx",
                    )
                )
            }
        }
    }

    private fun checkYamlLines(
        lines: List<String>,
        lineStarts: IntArray,
        outDiagnostics: MutableList<CodeDiagnostic>,
    ) {
        lines.forEachIndexed { idx, raw ->
            if (raw.contains('\t')) {
                val startOffset = lineStarts.getOrElse(idx) { 0 }
                val col = raw.indexOf('\t')
                outDiagnostics.add(
                    CodeDiagnostic(
                        lineIndex = idx,
                        column = col,
                        startOffset = startOffset + col,
                        endOffset = startOffset + col + 1,
                        severity = DiagnosticSeverity.ERROR,
                        message = "YAML does not allow tab characters for indentation; use spaces",
                        ruleCode = "YAML_TAB_INDENT_$idx",
                    )
                )
            }
        }
    }

    private fun stripCommentsFromLine(
        rawLine: String,
        lineStartOffset: Int,
        spans: List<SyntaxToken>,
    ): String {
        val sb = StringBuilder(rawLine.length)
        for (i in rawLine.indices) {
            val abs = lineStartOffset + i
            val inComment = spans.any { it.type == SyntaxTokenType.COMMENT && abs >= it.start && abs < it.endExclusive }
            sb.append(if (inComment) ' ' else rawLine[i])
        }
        return sb.toString()
    }

    private fun isKeyword(word: String, language: ScriptLanguage): Boolean = when (language) {
        ScriptLanguage.ARDUINO_CPP -> word in cppKeywords
        ScriptLanguage.MICROPYTHON, ScriptLanguage.PYTHON -> word in pythonKeywords
        ScriptLanguage.LUA -> word in luaKeywords
        ScriptLanguage.JAVASCRIPT -> word in jsKeywords
        ScriptLanguage.BASH -> word in bashKeywords
        ScriptLanguage.YAML_JSON -> word in yamlKeywords
    }

    private fun isTypeOrHardware(word: String, language: ScriptLanguage): Boolean = when (language) {
        ScriptLanguage.ARDUINO_CPP -> word in cppTypesAndHardware
        ScriptLanguage.MICROPYTHON, ScriptLanguage.PYTHON -> word in pythonHardwareAndBuiltins
        ScriptLanguage.LUA -> word in luaHardwareAndBuiltins
        ScriptLanguage.JAVASCRIPT -> word in jsHardwareAndBuiltins
        ScriptLanguage.BASH -> false
        ScriptLanguage.YAML_JSON -> word in setOf("GPIO0", "GPIO2", "GPIO4", "GPIO5", "GPIO12", "GPIO21", "GPIO22", "ESP32", "ESP8266")
    }

    private fun matchingClose(open: Char): Char = when (open) {
        '(' -> ')'
        '[' -> ']'
        else -> '}'
    }

    private fun computeLineStarts(source: String): IntArray {
        val starts = mutableListOf(0)
        for (i in source.indices) {
            if (source[i] == '\n') starts.add(i + 1)
        }
        return starts.toIntArray()
    }

    private fun offsetToLine(lineStarts: IntArray, offset: Int): Int {
        var lo = 0
        var hi = lineStarts.lastIndex
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lineStarts[mid] <= offset) {
                if (mid == lineStarts.lastIndex || lineStarts[mid + 1] > offset) return mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return 0
    }

    private fun findLineEnd(source: String, from: Int): Int {
        val nl = source.indexOf('\n', from)
        return if (nl < 0) source.length else nl
    }

    private fun findIdentifierEnd(source: String, from: Int): Int {
        var j = from
        while (j < source.length && (source[j].isLetterOrDigit() || source[j] == '_')) {
            j++
        }
        return j
    }

    private fun skipInlineSpaces(source: String, from: Int): Int {
        var j = from
        while (j < source.length && (source[j] == ' ' || source[j] == '\t')) {
            j++
        }
        return j
    }
}
