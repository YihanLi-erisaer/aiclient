package com.ikkoaudio.aiclient.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/**
 * Renders markdown-like content using AnnotatedString for reliable cross-platform display.
 * Handles **bold**, ***bold+italic***, *italic*, `inline code`, ```code blocks```, [links](url), and normalizes LLM output.
 */
@Composable
private fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier,
    baseColor: Color = Color.Unspecified
) {
    val normalized = normalizeMarkdownForRendering(content)
    val linkColor = MaterialTheme.colorScheme.primary
    val annotatedString = buildMarkdownAnnotatedString(normalized, linkColor)
    Text(
        text = annotatedString,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
        color = if (baseColor != Color.Unspecified) baseColor else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun normalizeMarkdownForRendering(content: String): String {
    return content
        .replace(Regex("""\*\*\*\s+"""), "***")
        .replace(Regex("""\s+\*\*\*"""), "***")
        .replace(Regex("""\*\*\s+"""), "**")
        .replace(Regex("""\s+\*\*"""), "**")
        .replace(Regex("""`\s+"""), "`")
        .replace(Regex("""\s+`"""), "`")
        .replace(Regex("""\*\*\*\*+"""), "***")
        .replace(Regex("""\]\s*\("""), "](")
        // Fix malformed link: [text](url)] -> [text](url) (extra ] after URL)
        .replace(Regex("""\]\([^)]+\)\]""")) { it.value.dropLast(1) }
        // Fix code block: ``` python -> ```python
        .replace(Regex("""```[ \t]+"""), "```")
}

/** Splits single-line code into multiple lines when LLM outputs code without newlines. */
private fun formatCodeBlockForReadability(code: String): String {
    if (code.contains("\n")) return code
    return code
        .replace(Regex("""(?<=[;:])\s*(?=[a-zA-Z#])"""), "\n") // Newline after : or ; (block start)
        .replace(Regex("""\b(def |class |for |while |if |elif |else:)\s*"""), "\n$1")
        .replace(Regex("""(print\s*\(|return |import |from )"""), "\n$1")
        .replace(Regex("""#"""), "\n#") // Newline before comments
        .replace(Regex("""\n+"""), "\n") // Collapse multiple newlines
        .trimStart()
}

private fun buildMarkdownAnnotatedString(content: String, linkColor: Color = Color.Unspecified): AnnotatedString = buildAnnotatedString {
    var i = 0
    val len = content.length
    while (i < len) {
        when {
            // Link [text](url) - must check before other patterns
            content.getOrNull(i) == '[' -> {
                val closeBracket = content.indexOf(']', i + 1)
                val openParen = if (closeBracket != -1 && content.getOrNull(closeBracket + 1) == '(') closeBracket + 1 else -1
                val closeParen = if (openParen != -1) {
                    var depth = 1
                    var idx = openParen + 1
                    while (idx < len && depth > 0) {
                        when (content[idx]) {
                            '(' -> depth++
                            ')' -> depth--
                            else -> {}
                        }
                        idx++
                    }
                    if (depth == 0) idx - 1 else -1
                } else -1
                if (closeParen != -1) {
                    val linkText = content.substring(i + 1, closeBracket)
                    val url = content.substring(openParen + 1, closeParen)
                    val displayText = if (linkText == url) url else linkText
                    if (linkColor != Color.Unspecified) {
                        pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                    }
                    append(displayText)
                    if (linkColor != Color.Unspecified) pop()
                    i = closeParen + 1
                } else {
                    append(content[i])
                    i += 1
                }
            }
            // Code block ```...``` (must check before single `)
            content.startsWith("```", i) -> {
                val lineEnd = content.indexOf("\n", i + 3)
                val codeStart = if (lineEnd != -1) lineEnd + 1 else i + 3
                val codeEnd = content.indexOf("```", codeStart)
                if (codeEnd != -1) {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
                    val code = content.substring(codeStart, codeEnd).trimEnd()
                    append(formatCodeBlockForReadability(code))
                    pop()
                    i = codeEnd + 3
                } else {
                    append(content.substring(i, minOf(i + 3, len)))
                    i += 3
                }
            }
            content.startsWith("***", i) -> {
                val end = content.indexOf("***", i + 3)
                if (end != -1 && end != i + 3) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                    append(buildMarkdownAnnotatedString(content.substring(i + 3, end), linkColor))
                    pop()
                    i = end + 3
                } else {
                    append(content.substring(i, minOf(i + 3, len)))
                    i += 3
                }
            }
            content.startsWith("**", i) -> {
                val closeBold = content.indexOf("**", i + 2)
                val lineEnd = content.indexOf("\n", i)
                val end = when {
                    closeBold != -1 && closeBold != i + 2 -> closeBold
                    lineEnd != -1 && lineEnd > i -> lineEnd
                    else -> len
                }
                if (end > i + 2) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(buildMarkdownAnnotatedString(content.substring(i + 2, end), linkColor))
                    pop()
                    i = if (closeBold != -1 && closeBold == end) closeBold + 2 else end
                } else {
                    append(content.substring(i, minOf(i + 2, len)))
                    i += 2
                }
            }
            content.startsWith("`", i) -> {
                val end = content.indexOf("`", i + 1)
                if (end != -1 && end != i + 1) {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
                    append(content.substring(i + 1, end))
                    pop()
                    i = end + 1
                } else {
                    append("`")
                    i += 1
                }
            }
            content.startsWith("*", i) -> {
                val end = content.indexOf("*", i + 1)
                if (end != -1 && end != i + 1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(buildMarkdownAnnotatedString(content.substring(i + 1, end), linkColor))
                    pop()
                    i = end + 1
                } else {
                    append("*")
                    i += 1
                }
            }
            else -> {
                append(content[i])
                i += 1
            }
        }
    }
}

@Composable
fun LlmPage(state: ChatState, viewModel: ChatViewModel) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        ErrorBanner(state.error) { viewModel.dispatch(ChatIntent.ClearError) }
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(state.messages, key = { it.id }) { msg ->
                MessageBubble(role = msg.role, content = msg.content, isStreaming = msg.isStreaming)
            }
            if (state.isLoading && state.messages.none { it.isStreaming }) {
                item { androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.padding(16.dp)) }
            }
        }
        LlmInputSection(
            inputText = state.inputText,
            isLoading = state.isLoading,
            onTextChange = { viewModel.dispatch(ChatIntent.UpdateInput(it)) },
            onSend = { viewModel.dispatch(ChatIntent.SendMessage(state.inputText)) }
        )
    }
}

@Composable
private fun MessageBubble(role: String, content: String, isStreaming: Boolean) {
    val isUser = role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 12.dp, topEnd = 12.dp,
                bottomStart = if (isUser) 12.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 12.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ) {
            val displayContent = content.ifEmpty { if (isStreaming) "..." else "" }
            if (isUser) {
                Text(
                    text = displayContent,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else if (isStreaming) {
                Text(
                    text = displayContent,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                MarkdownText(
                    content = displayContent,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun LlmInputSection(
    inputText: String,
    isLoading: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message...") },
                maxLines = 4
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onSend,
                enabled = inputText.isNotBlank() && !isLoading
            ) {
                Text("Send")
            }
        }
    }
}
