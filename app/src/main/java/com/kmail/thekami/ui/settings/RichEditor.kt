package com.kmail.thekami.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kmail.thekami.R

/** The text of the editor, its formatting, and what the toolbar does to it. */
@Stable
class RichEditorState(initial: RichDoc) {
    var doc by mutableStateOf(initial)
        private set
    var field by mutableStateOf(TextFieldValue(initial.text, TextRange(initial.text.length)))
        private set
    private var typing by mutableStateOf(emptyList<RichStyle>())

    fun onValueChange(v: TextFieldValue) {
        if (v.text != doc.text) {
            val r = RichOps.onTextChanged(doc, v.text, typing)
            doc = r.doc
            val cursor = r.cursor
            field = if (cursor != null) TextFieldValue(r.doc.text, TextRange(cursor)) else v
        } else {
            val moved = v.selection != field.selection
            field = v
            if (moved && v.selection.collapsed) typing = RichOps.stylesAt(doc, v.selection.start)
        }
    }

    fun isActive(kind: RichKind): Boolean {
        val s = field.selection
        return if (s.collapsed) typing.any { it.kind == kind } else RichOps.isActive(doc, kind, s.min, s.max)
    }

    /** The current COLOR or SIZE at the cursor or selection, 0 for none. */
    fun valueOf(kind: RichKind): Int {
        val s = field.selection
        return if (s.collapsed) {
            typing.firstOrNull { it.kind == kind }?.num ?: 0
        } else {
            doc.spans.firstOrNull { it.kind == kind && it.start <= s.min && it.end >= s.max }?.num ?: 0
        }
    }

    fun toggle(kind: RichKind) {
        val s = field.selection
        if (s.collapsed) {
            typing = if (typing.any { it.kind == kind }) typing.filter { it.kind != kind } else typing + RichStyle(kind)
        } else {
            doc = RichOps.toggle(doc, kind, s.min, s.max)
        }
    }

    fun setValue(kind: RichKind, num: Int) {
        val s = field.selection
        if (s.collapsed) {
            val rest = typing.filter { it.kind != kind }
            typing = if (num == 0) rest else rest + RichStyle(kind, num)
        } else {
            doc = RichOps.setValue(doc, kind, num, s.min, s.max)
        }
    }

    fun align(side: Int) {
        val s = field.selection
        doc = RichOps.align(doc, side, s.min, s.max)
    }

    fun prefix(kind: RichOps.ListKind) {
        val s = field.selection
        apply(RichOps.togglePrefix(doc, kind, s.min, s.max))
    }

    fun clear() {
        val s = field.selection
        if (s.collapsed) typing = emptyList() else doc = RichOps.clear(doc, s.min, s.max)
    }

    fun selectedText(): String = field.text.substring(field.selection.min, field.selection.max)

    fun insertLink(display: String, url: String) {
        val s = field.selection
        apply(RichOps.insertLink(doc, s.min, s.max, display, url))
    }

    private fun apply(r: EditResult) {
        doc = r.doc
        field = TextFieldValue(r.doc.text, TextRange(r.cursor ?: r.doc.text.length))
    }

    fun html(): String = RichOps.toHtml(doc)

    fun plain(): String = doc.text.trimEnd()
}

@Composable
fun RichEditor(state: RichEditorState, placeholder: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val linkColor = scheme.primary
    val spans = state.doc.spans
    val transformation = remember(spans, linkColor) {
        object : VisualTransformation {
            override fun filter(text: AnnotatedString): TransformedText =
                TransformedText(RichOps.annotate(text.text, spans, linkColor), OffsetMapping.Identity)
        }
    }
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = scheme.onSurface,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )
    BasicTextField(
        value = state.field,
        onValueChange = state::onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = textStyle,
        cursorBrush = SolidColor(scheme.primary),
        visualTransformation = transformation,
        decorationBox = { inner ->
            Box(Modifier.padding(16.dp).heightIn(min = 140.dp)) {
                if (state.field.text.isEmpty()) {
                    Text(placeholder, style = textStyle, color = scheme.onSurfaceVariant)
                }
                inner()
            }
        },
    )
}

private val sizes = listOf(
    12 to R.string.sig_size_small,
    0 to R.string.sig_size_normal,
    18 to R.string.sig_size_large,
    24 to R.string.sig_size_huge,
)

private val swatches = listOf(
    0xFFE53935, 0xFFFB8C00, 0xFF43A047, 0xFF00897B, 0xFF1E88E5,
    0xFF8E24AA, 0xFFD81B60, 0xFF6D4C41, 0xFF757575, 0xFF000000,
).map { it.toInt() }

@Composable
private fun ToolButton(
    description: String,
    onClick: () -> Unit,
    active: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) scheme.secondaryContainer else Color.Transparent)
            .semantics { contentDescription = description }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Size, color, bold, italic, underline, strikethrough, and a "more" menu. */
@Composable
fun RichToolbar(state: RichEditorState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    var sizeMenu by remember { mutableStateOf(false) }
    var colorMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var linkDialog by remember { mutableStateOf(false) }
    val currentColor = state.valueOf(RichKind.COLOR)
    val shown = if (currentColor != 0) Color(currentColor) else scheme.onSurface

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            ToolButton(stringResource(R.string.sig_size), { sizeMenu = true }, active = state.valueOf(RichKind.SIZE) != 0) {
                Text("Aa", style = MaterialTheme.typography.titleSmall)
            }
            DropdownMenu(expanded = sizeMenu, onDismissRequest = { sizeMenu = false }) {
                sizes.forEach { (px, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(label),
                                fontWeight = if (state.valueOf(RichKind.SIZE) == px) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            state.setValue(RichKind.SIZE, px)
                            sizeMenu = false
                        },
                    )
                }
            }
        }
        Box {
            ToolButton(stringResource(R.string.sig_color), { colorMenu = true }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("A", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = shown)
                    Box(Modifier.width(16.dp).height(3.dp).background(shown))
                }
            }
            DropdownMenu(expanded = colorMenu, onDismissRequest = { colorMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sig_color_default)) },
                    onClick = {
                        state.setValue(RichKind.COLOR, 0)
                        colorMenu = false
                    },
                )
                swatches.chunked(5).forEach { row ->
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { argb ->
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .then(
                                        if (currentColor == argb) {
                                            Modifier.border(2.dp, scheme.onSurface, CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable {
                                        state.setValue(RichKind.COLOR, argb)
                                        colorMenu = false
                                    },
                            )
                        }
                    }
                }
            }
        }
        ToolButton(stringResource(R.string.sig_bold), { state.toggle(RichKind.BOLD) }, state.isActive(RichKind.BOLD)) {
            Text("B", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        ToolButton(stringResource(R.string.sig_italic), { state.toggle(RichKind.ITALIC) }, state.isActive(RichKind.ITALIC)) {
            Text("I", style = MaterialTheme.typography.titleMedium, fontStyle = FontStyle.Italic)
        }
        ToolButton(stringResource(R.string.sig_underline), { state.toggle(RichKind.UNDERLINE) }, state.isActive(RichKind.UNDERLINE)) {
            Text("U", style = MaterialTheme.typography.titleMedium, textDecoration = TextDecoration.Underline)
        }
        ToolButton(stringResource(R.string.sig_strike), { state.toggle(RichKind.STRIKE) }, state.isActive(RichKind.STRIKE)) {
            Text("S", style = MaterialTheme.typography.titleMedium, textDecoration = TextDecoration.LineThrough)
        }
        Box {
            ToolButton(stringResource(R.string.sig_more), { moreMenu = true }) {
                Text("\u22EF", style = MaterialTheme.typography.titleLarge)
            }
            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                MoreItem(R.string.sig_bulleted) { state.prefix(RichOps.ListKind.BULLET); moreMenu = false }
                MoreItem(R.string.sig_numbered) { state.prefix(RichOps.ListKind.NUMBER); moreMenu = false }
                MoreItem(R.string.sig_quote) { state.prefix(RichOps.ListKind.QUOTE); moreMenu = false }
                HorizontalDivider()
                MoreItem(R.string.sig_align_left) { state.align(0); moreMenu = false }
                MoreItem(R.string.sig_align_center) { state.align(1); moreMenu = false }
                MoreItem(R.string.sig_align_right) { state.align(2); moreMenu = false }
                HorizontalDivider()
                MoreItem(R.string.sig_link) { linkDialog = true; moreMenu = false }
                MoreItem(R.string.sig_clear) { state.clear(); moreMenu = false }
            }
        }
    }

    if (linkDialog) {
        var text by remember { mutableStateOf(state.selectedText()) }
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { linkDialog = false },
            title = { Text(stringResource(R.string.sig_link)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(stringResource(R.string.sig_link_text)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.sig_link_url)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = url.isNotBlank(),
                    onClick = {
                        val clean = url.trim()
                        val full = if (clean.contains("://") || clean.startsWith("mailto:")) clean else "https://$clean"
                        if (full.startsWith("https://") || full.startsWith("http://") || full.startsWith("mailto:")) {
                            state.insertLink(text.ifBlank { clean }, full)
                        }
                        linkDialog = false
                    },
                ) { Text(stringResource(R.string.sig_link_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { linkDialog = false }) { Text(stringResource(R.string.settings_cancel)) }
            },
        )
    }
}

@Composable
private fun MoreItem(label: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick)
}
