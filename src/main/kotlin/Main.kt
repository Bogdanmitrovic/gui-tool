import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import syntax.SyntaxHighlighter
import java.io.File
import java.io.BufferedReader

data class ErrorLocation(val line: Int, val column: Int, val message: String)

fun parseErrorLocation(text: String): ErrorLocation? {
    val regex = """(?:script(?:\.kts)?:)?(\d+):(\d+):""".toRegex()
    val match = regex.find(text) ?: return null
    val line = match.groupValues[1].toIntOrNull() ?: return null
    val column = match.groupValues[2].toIntOrNull() ?: return null
    return ErrorLocation(line, column, text)
}

@Composable
@Preview
fun App() {
    var scriptText by remember { mutableStateOf(TextFieldValue("println(\"Simple kotlin script!\")\n\n// Basic loop:\nfor (i in 1..5) {\n    println(\"Count: \$i\")\n    Thread.sleep(500)\n}")) }
    var output by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var lastExitCode by remember { mutableStateOf<Int?>(null) }
    var currentProcess by remember { mutableStateOf<Process?>(null) }
    val focusRequester = remember { FocusRequester() }

    val scope = rememberCoroutineScope()

    fun navigateToError(line: Int, column: Int) {
        focusRequester.requestFocus()
        val lines = scriptText.text.split("\n")
        if (line <= 0 || line > lines.size) return

        var pos = 0
        for (i in 0 until line - 1) {
            pos += lines[i].length + 1
        }
        pos += (column - 1).coerceAtLeast(0)
        scriptText = scriptText.copy(
            selection = androidx.compose.ui.text.TextRange(pos)
        )
    }

    fun runScript() {
        scope.launch {
            isRunning = true
            output = ""
            lastExitCode = null
            currentProcess = null

            try {
                val result = executeScript(scriptText.text,
                    onOutput = { line ->
                        output += line + "\n"
                    },
                    onProcessCreated = { process ->
                        currentProcess = process
                    }
                )
                lastExitCode = result
            } catch (e: Exception) {
                output += "\nError: ${e.message}\n"
                lastExitCode = -1
            } finally {
                isRunning = false
                currentProcess = null
            }
        }
    }

    fun stopScript() {
        currentProcess?.let { process ->
            scope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        process.destroy()
                        Thread.sleep(100)
                        if (process.isAlive) {
                            process.destroyForcibly()
                        }
                        output += "\n[Script terminated by user]\n"
                        lastExitCode = 143
                    } catch (e: Exception) {
                        output += "\nError stopping script: ${e.message}\n"
                    }
                }
            }
        }
    }

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
            Toolbar(
                isRunning = isRunning,
                lastExitCode = lastExitCode,
                onRunStopClick = { if (isRunning) stopScript() else runScript() }
            )
            Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    elevation = 2.dp,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    EditorPane(
                        scriptText = scriptText,
                        onScriptTextChange = { scriptText = it },
                        focusRequester = focusRequester,
                    )
                }
                Surface(
                    elevation = 2.dp,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    OutputPane(
                        output = output,
                        isRunning = isRunning,
                        lastExitCode = lastExitCode,
                        onErrorClick = ::navigateToError
                    )
                }
            }
        }
    }
}

suspend fun executeScript(
    script: String,
    onOutput: (String) -> Unit,
    onProcessCreated: (Process) -> Unit
): Int = withContext(Dispatchers.IO) {
    val tempFile = File.createTempFile("script", ".kts").apply {
        writeText(script)
        deleteOnExit()
    }

    try {
        val processBuilder = ProcessBuilder(
            "kotlinc", "-script", tempFile.absolutePath
        )

        processBuilder.redirectErrorStream(true)
        val process = processBuilder.start()

        withContext(Dispatchers.Main) {
            onProcessCreated(process)
        }

        val reader = BufferedReader(process.inputStream.reader())
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            withContext(Dispatchers.Main) {
                onOutput(line!!)
            }
        }

        process.waitFor()
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            onOutput("Error executing script: ${e.message}")
        }
        -1
    } finally {
        tempFile.delete()
    }
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Kotlin Script Runner",
        state = rememberWindowState(width = 1200.dp, height = 800.dp)
    ) {
        App()
    }
}

@Composable
fun RunStopButton(isRunning: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (isRunning) Color(0xFFF44336) else Color(0xFF4CAF50),
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(8.dp)
    ) {
        Icon(
            imageVector = if (isRunning) Icons.Default.Close else Icons.Default.PlayArrow,
            contentDescription = if (isRunning) "Stop" else "Run",
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun Toolbar(
    isRunning: Boolean,
    lastExitCode: Int?,
    onRunStopClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RunStopButton(
                isRunning = isRunning,
                onClick = onRunStopClick
            )
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
                Text("Script is running...")
            }

            lastExitCode?.let { code ->
                Surface(
                    color = when (code) {
                        0 -> Color(0xFF4CAF50)
                        143 -> Color(0xFFFF9800)
                        else -> Color(0xFFF44336)
                    },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.height(40.dp)
                ) {
                    Text(
                        text = when (code) {
                            0 -> "✓ Success (exit: 0)"
                            143 -> "⊗ Terminated (exit: $code)"
                            else -> "✗ Failed (exit: $code)"
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun EditorPane(
    scriptText: TextFieldValue,
    onScriptTextChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester
) {

    Column(modifier = Modifier.padding(8.dp)) {
        Text(
            "Script Editor",
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        val highlightedText = remember(scriptText.text) {
            SyntaxHighlighter.highlight(scriptText.text)
        }

        BasicTextField(
            value = scriptText,
            onValueChange = onScriptTextChange,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .focusRequester(focusRequester),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = Color.Transparent
            ),
            cursorBrush = SolidColor(Color.Black),
            decorationBox = { innerTextField ->
                Box {
                    Text(
                        text = highlightedText,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                    )
                    innerTextField()
                }
            }
        )
    }
}

@Composable
fun OutputPane(
    output: String,
    isRunning: Boolean,
    lastExitCode: Int?,
    onErrorClick: (Int, Int) -> Unit
) {

    Column(modifier = Modifier.padding(8.dp)) {
        Text(
            "Output",
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF1E1E1E)
        ) {
            val scrollState = rememberScrollState()

            LaunchedEffect(output) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }

            val outputLines = remember(output, isRunning, lastExitCode) {
                val text = if (output.isEmpty()) {
                    "Output will appear here..."
                } else if (isRunning) {
                    output
                } else {
                    output + "\nProcess finished with exit code ${lastExitCode ?: "..."}"
                }
                text.split("\n")
            }

            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .verticalScroll(scrollState)
            ) {
                outputLines.forEach { line ->
                    val errorLocation = parseErrorLocation(line)
                    if (errorLocation != null) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    style = SpanStyle(
                                        color = Color(0xFFFF6B6B),
                                        background = Color(0xFF3D1F1F)
                                    )
                                ) {
                                    append(line)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onErrorClick(errorLocation.line, errorLocation.column)
                                }
                                .padding(vertical = 2.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                    } else {
                        Text(
                            text = line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = if (output.isEmpty()) Color.Gray else Color(0xFF00FF00)
                        )
                    }
                }
            }
        }
    }
}