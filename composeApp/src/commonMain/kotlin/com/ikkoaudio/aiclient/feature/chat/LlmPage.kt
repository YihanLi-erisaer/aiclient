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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.sp

private sealed class MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock()
    data class CodeBlock(val code: String) : MarkdownBlock()
    data class Table(val rows: List<List<String>>) : MarkdownBlock()
    object HorizontalRule : MarkdownBlock()
}

private fun parseMarkdownBlocks(content: String): List<MarkdownBlock> {
    val normalized = normalizeMarkdownForRendering(content)
    val lines = normalized.split("\n")
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.matches(Regex("""^[-*_]{3,}\s*\$""")) -> {
                blocks.add(MarkdownBlock.HorizontalRule)
                i++
            }
            line.trim().startsWith("```") -> {
                val lang = line.trim().removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                if (i < lines.size) i++
                blocks.add(MarkdownBlock.CodeBlock(formatCodeBlockForReadability(codeLines.joinToString("\n").trimEnd())))
            }
            line.contains("|") && line.trim().startsWith("|") -> {
                val tableRows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].contains("|")) {
                    val row = lines[i]
                    val cells = row.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    val isSeparator = cells.isNotEmpty() && cells.all { it.matches(Regex("""^[-:\s]+$""")) }
                    if (!isSeparator && cells.isNotEmpty()) {
                        tableRows.add(cells)
                    }
                    i++
                }
                if (tableRows.isNotEmpty()) {
                    blocks.add(MarkdownBlock.Table(tableRows))
                }
            }
            else -> {
                val paraLines = mutableListOf<String>()
                while (i < lines.size) {
                    val l = lines[i]
                    val isTableRow = l.contains("|") && l.trim().startsWith("|")
                    if (l.matches(Regex("""^[-*_]{3,}\s*\$""")) ||
                        l.trim().startsWith("```") ||
                        isTableRow) break
                    paraLines.add(l)
                    i++
                }
                val para = paraLines.joinToString("\n").trim()
                if (para.isNotEmpty()) {
                    blocks.add(MarkdownBlock.Paragraph(para))
                }
            }
        }
    }
    return blocks
}

/** Renders markdown with block support: tables, horizontal rules, headers, etc. */
@Composable
private fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
    linkColor: Color = MaterialTheme.colorScheme.primary
) {
    val blocks = remember(content) { parseMarkdownBlocks(content) }
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> {
                    val annotated = buildMarkdownAnnotatedString(block.text, linkColor)
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                is MarkdownBlock.CodeBlock -> {
                    Text(
                        text = block.code,
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace,
                        color = textColor,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                is MarkdownBlock.Table -> {
                    val rows = block.rows
                    if (rows.isNotEmpty()) {
                        val colCount = rows.maxOfOrNull { it.size } ?: 1
                        Column(
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .padding(bottom = 12.dp)
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            rows.forEachIndexed { rowIdx, cells ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp, horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    cells.forEachIndexed { colIdx, cell ->
                                        Box(
                                            modifier = Modifier.weight(1f),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                text = buildMarkdownAnnotatedString(cell, linkColor),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = if (rowIdx == 0) FontWeight.Bold else FontWeight.Normal,
                                                color = if (rowIdx == 0) {
                                                    MaterialTheme.colorScheme.primary
                                                } else textColor
                                            )
                                        }
                                    }
                                    (cells.size until colCount).forEach { _ ->
                                        Box(modifier = Modifier.weight(1f))
                                    }
                                }
                                if (rowIdx == 0) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        thickness = 1.dp
                                    )
                                }
                            }
                        }
                    }
                }
                is MarkdownBlock.HorizontalRule -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

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
        .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
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
        // Fix header: #标题 -> # 标题 (need space after # for Markdown)
        .replace(Regex("""(^|\n)(#{1,6})([^\s\n#][^\n]*)""")) { mr ->
            mr.groupValues[1] + mr.groupValues[2] + " " + mr.groupValues[3]
        }
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
            // Markdown header # ## ### (at line start only)
            (i == 0 || content.getOrNull(i - 1) == '\n') && content.getOrNull(i) == '#' -> {
                var hashCount = 0
                var j = i
                while (j < len && content[j] == '#' && hashCount < 6) {
                    hashCount++
                    j++
                }
                if (hashCount > 0 && j < len && content[j] in " \t") {
                    val afterSpace = content.indexOf('\n', j).takeIf { it != -1 } ?: len
                    val headerText = content.substring(j, afterSpace).trimStart()
                    val fontSize = when (hashCount) {
                        1 -> 22.sp
                        2 -> 20.sp
                        3 -> 18.sp
                        else -> 16.sp
                    }
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = fontSize))
                    append(buildMarkdownAnnotatedString(headerText, linkColor))
                    pop()
                    if (afterSpace < len) append("\n")
                    i = afterSpace
                } else {
                    append(content[i])
                    i += 1
                }
            }
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
                MarkdownContent(
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
