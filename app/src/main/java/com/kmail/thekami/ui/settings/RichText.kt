package com.kmail.thekami.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.kmail.thekami.data.prefs.SignatureHtml
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** What a span of text can look like. ALIGN covers whole lines: num 1 = center, 2 = right. */
enum class RichKind { BOLD, ITALIC, UNDERLINE, STRIKE, COLOR, SIZE, LINK, ALIGN }

/** [num] is the ARGB color for COLOR, the size in px for SIZE, and the side for ALIGN. */
data class RichStyle(val kind: RichKind, val num: Int = 0, val url: String = "")

data class RichSpan(
    val kind: RichKind,
    val start: Int,
    val end: Int,
    val num: Int = 0,
    val url: String = "",
) {
    fun style() = RichStyle(kind, num, url)
}

/** Plain text plus the formatting laid over it. Lists and quotes are real text prefixes: "• ", "1. ", "> ". */
data class RichDoc(val text: String, val spans: List<RichSpan>) {
    companion object {
        val Empty = RichDoc("", emptyList())
    }
}

data class EditResult(val doc: RichDoc, val cursor: Int?)

object RichOps {
    const val BULLET = "• "
    const val QUOTE = "> "
    private val numbered = Regex("^(\\d+)\\. ")
    private val blockTags = setOf("div", "p", "ul", "ol", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6")
    private val colorRe = Regex("(?:^|;|\\s)color:\\s*#([0-9a-fA-F]{6})")
    private val sizeRe = Regex("font-size:\\s*(\\d+)px")
    private val alignRe = Regex("text-align:\\s*(center|right)")

    enum class ListKind { BULLET, NUMBER, QUOTE }

    // ---- span bookkeeping ----

    /** Moves spans after the text in [p, oldEnd) was replaced by [inserted] characters. */
    fun remap(spans: List<RichSpan>, p: Int, oldEnd: Int, inserted: Int): List<RichSpan> {
        val delta = inserted - (oldEnd - p)
        return spans.mapNotNull { sp ->
            val s = when {
                sp.start < p -> sp.start
                sp.start >= oldEnd -> sp.start + delta
                else -> p + inserted
            }
            val e = when {
                sp.end <= p -> sp.end
                sp.end >= oldEnd -> sp.end + delta
                else -> p
            }
            if (e > s) sp.copy(start = s, end = e) else null
        }
    }

    /** Joins touching spans of the same look and drops empty ones. */
    fun normalize(spans: List<RichSpan>): List<RichSpan> {
        val out = mutableListOf<RichSpan>()
        spans.filter { it.end > it.start }
            .sortedWith(compareBy({ it.kind }, { it.num }, { it.url }, { it.start }))
            .forEach { sp ->
                val last = out.lastOrNull()
                if (last != null && last.kind == sp.kind && last.num == sp.num && last.url == sp.url && sp.start <= last.end) {
                    out[out.size - 1] = last.copy(end = maxOf(last.end, sp.end))
                } else {
                    out += sp
                }
            }
        return out
    }

    fun replace(doc: RichDoc, p: Int, oldEnd: Int, ins: String): RichDoc =
        RichDoc(doc.text.replaceRange(p, oldEnd, ins), normalize(remap(doc.spans, p, oldEnd, ins.length)))

    private fun covered(spans: List<RichSpan>, kind: RichKind, a: Int, b: Int): Boolean {
        var pos = a
        spans.filter { it.kind == kind && it.end > a && it.start < b }.sortedBy { it.start }.forEach { sp ->
            if (sp.start > pos) return false
            pos = maxOf(pos, sp.end)
        }
        return pos >= b
    }

    private fun cut(spans: List<RichSpan>, kind: RichKind, a: Int, b: Int): List<RichSpan> =
        spans.flatMap { sp ->
            if (sp.kind != kind || sp.end <= a || sp.start >= b) {
                listOf(sp)
            } else {
                buildList {
                    if (sp.start < a) add(sp.copy(end = a))
                    if (sp.end > b) add(sp.copy(start = b))
                }
            }
        }

    fun isActive(doc: RichDoc, kind: RichKind, a: Int, b: Int): Boolean = covered(doc.spans, kind, a, b)

    fun toggle(doc: RichDoc, kind: RichKind, a: Int, b: Int): RichDoc {
        val base = cut(doc.spans, kind, a, b)
        val next = if (covered(doc.spans, kind, a, b)) base else base + RichSpan(kind, a, b)
        return doc.copy(spans = normalize(next))
    }

    /** Sets COLOR / SIZE / ALIGN over a range. A [num] of 0 removes it. */
    fun setValue(doc: RichDoc, kind: RichKind, num: Int, a: Int, b: Int): RichDoc {
        val base = cut(doc.spans, kind, a, b)
        return doc.copy(spans = normalize(if (num == 0) base else base + RichSpan(kind, a, b, num)))
    }

    fun clear(doc: RichDoc, a: Int, b: Int): RichDoc {
        val spans = RichKind.values().filter { it != RichKind.ALIGN }
            .fold(doc.spans) { acc, kind -> cut(acc, kind, a, b) }
        return doc.copy(spans = normalize(spans))
    }

    /** The looks the next typed character gets when the cursor is at [pos]. */
    fun stylesAt(doc: RichDoc, pos: Int): List<RichStyle> =
        doc.spans.filter { it.kind != RichKind.ALIGN && it.kind != RichKind.LINK && it.start < pos && it.end >= pos }
            .map { it.style() }
            .distinct()

    // ---- lines ----

    private fun lineStart(text: String, i: Int): Int = if (i <= 0) 0 else text.lastIndexOf('\n', i - 1) + 1

    private fun lineEnd(text: String, i: Int): Int {
        val e = text.indexOf('\n', i)
        return if (e < 0) text.length else e
    }

    private fun lineRange(text: String, a: Int, b: Int): Pair<Int, Int> {
        val probe = if (b > a && b > 0 && text[b - 1] == '\n') b - 1 else b
        return lineStart(text, a) to lineEnd(text, probe)
    }

    fun align(doc: RichDoc, num: Int, a: Int, b: Int): RichDoc {
        val (ls, le) = lineRange(doc.text, a, b)
        return setValue(doc, RichKind.ALIGN, num, ls, le)
    }

    private fun prefixLength(line: String): Int = when {
        line.startsWith(BULLET) || line.startsWith(QUOTE) -> 2
        else -> numbered.find(line)?.value?.length ?: 0
    }

    private fun hasKind(line: String, kind: ListKind): Boolean = when (kind) {
        ListKind.BULLET -> line.startsWith(BULLET)
        ListKind.QUOTE -> line.startsWith(QUOTE)
        ListKind.NUMBER -> numbered.containsMatchIn(line)
    }

    /** Turns a list or quote on for the selected lines, or off if they all have it already. */
    fun togglePrefix(doc: RichDoc, kind: ListKind, a: Int, b: Int): EditResult {
        val (ls, le) = lineRange(doc.text, a, b)
        val lines = doc.text.substring(ls, le).split("\n")
        val allHave = lines.all { hasKind(it, kind) }
        val starts = mutableListOf<Int>()
        var pos = ls
        lines.forEach {
            starts += pos
            pos += it.length + 1
        }
        var result = doc
        var delta = 0
        for (i in lines.indices.reversed()) {
            val oldLen = prefixLength(lines[i])
            val newPrefix = if (allHave) {
                ""
            } else {
                when (kind) {
                    ListKind.BULLET -> BULLET
                    ListKind.QUOTE -> QUOTE
                    ListKind.NUMBER -> "${i + 1}. "
                }
            }
            result = replace(result, starts[i], starts[i] + oldLen, newPrefix)
            delta += newPrefix.length - oldLen
        }
        return EditResult(result, le + delta)
    }

    fun insertLink(doc: RichDoc, a: Int, b: Int, display: String, url: String): EditResult {
        val replaced = replace(doc, a, b, display)
        val end = a + display.length
        val spans = cut(replaced.spans, RichKind.LINK, a, end) + RichSpan(RichKind.LINK, a, end, url = url)
        return EditResult(replaced.copy(spans = normalize(spans)), end)
    }

    // ---- typing ----

    /** The text changed from the keyboard: keep the spans in place and give new text the typing look. */
    fun onTextChanged(doc: RichDoc, newText: String, typing: List<RichStyle>): EditResult {
        val old = doc.text
        val minLen = minOf(old.length, newText.length)
        var p = 0
        while (p < minLen && old[p] == newText[p]) p++
        var s = 0
        while (s < minLen - p && old[old.length - 1 - s] == newText[newText.length - 1 - s]) s++
        val oldEnd = old.length - s
        val newEnd = newText.length - s
        val inserted = newEnd - p

        var spans = remap(doc.spans, p, oldEnd, inserted)
        if (inserted > 0) {
            typing.forEach { st -> spans = spans + RichSpan(st.kind, p, newEnd, st.num, st.url) }
        }
        var result = RichDoc(newText, normalize(spans))
        var cursor: Int? = null

        // Enter inside a list or quote continues it. Enter on an empty item leaves it.
        if (oldEnd == p && inserted == 1 && newText[p] == '\n') {
            val ls = lineStart(newText, p)
            val line = newText.substring(ls, p)
            val num = numbered.find(line)
            val plen = prefixLength(line)
            if (plen > 0) {
                if (line.length == plen) {
                    result = replace(result, ls, p + 1, "")
                    cursor = ls
                } else {
                    val prefix = when {
                        line.startsWith(BULLET) -> BULLET
                        line.startsWith(QUOTE) -> QUOTE
                        else -> "${(num?.groupValues?.get(1)?.toIntOrNull() ?: 0) + 1}. "
                    }
                    result = replace(result, p + 1, p + 1, prefix)
                    cursor = p + 1 + prefix.length
                }
            }
        }
        return EditResult(result, cursor)
    }

    // ---- rendering in the editor ----

    fun annotate(text: String, spans: List<RichSpan>, linkColor: Color): AnnotatedString = buildAnnotatedString {
        append(text)
        spans.forEach { sp ->
            val s = sp.start.coerceIn(0, text.length)
            val e = sp.end.coerceIn(0, text.length)
            if (e <= s) return@forEach
            val style = when (sp.kind) {
                RichKind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                RichKind.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                RichKind.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                RichKind.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                RichKind.COLOR -> SpanStyle(color = Color(sp.num))
                RichKind.SIZE -> SpanStyle(fontSize = sp.num.sp)
                RichKind.LINK -> SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                RichKind.ALIGN -> null
            }
            if (style != null) addStyle(style, s, e)
        }
        var pos = 0
        for (line in text.split("\n")) {
            val ls = pos
            val le = ls + line.length
            pos = le + 1
            val side = spans.firstOrNull { it.kind == RichKind.ALIGN && it.start < le && it.end > ls }?.num ?: 0
            if (side != 0 && le > ls) {
                addStyle(ParagraphStyle(textAlign = if (side == 1) TextAlign.Center else TextAlign.End), ls, le)
            }
        }
    }

    // ---- to HTML ----

    private fun hex(argb: Int): String = String.format("#%06X", argb and 0xFFFFFF)

    private fun openTag(name: String): String = when (name) {
        "blockquote" -> "<blockquote style=\"margin:0;padding-left:12px;border-left:3px solid #c4c7c5;color:#5f6368\">"
        else -> "<$name style=\"margin:0;padding-left:24px\">"
    }

    private fun inline(doc: RichDoc, a: Int, b: Int): String {
        if (b <= a) return ""
        val cuts = sortedSetOf(a, b)
        doc.spans.forEach { sp ->
            if (sp.start in (a + 1) until b) cuts += sp.start
            if (sp.end in (a + 1) until b) cuts += sp.end
        }
        val points = cuts.toList()
        val sb = StringBuilder()
        for (i in 0 until points.size - 1) {
            val x = points[i]
            val y = points[i + 1]
            var html = SignatureHtml.escape(doc.text.substring(x, y))
            doc.spans.filter { it.kind != RichKind.ALIGN && it.start <= x && it.end >= y }
                .sortedBy { it.kind }
                .forEach { sp ->
                    html = when (sp.kind) {
                        RichKind.BOLD -> "<b>$html</b>"
                        RichKind.ITALIC -> "<i>$html</i>"
                        RichKind.UNDERLINE -> "<u>$html</u>"
                        RichKind.STRIKE -> "<s>$html</s>"
                        RichKind.COLOR -> "<span style=\"color:${hex(sp.num)}\">$html</span>"
                        RichKind.SIZE -> "<span style=\"font-size:${sp.num}px\">$html</span>"
                        RichKind.LINK -> "<a href=\"${SignatureHtml.escape(sp.url)}\">$html</a>"
                        RichKind.ALIGN -> html
                    }
                }
            sb.append(html)
        }
        return sb.toString()
    }

    fun toHtml(doc: RichDoc): String {
        val text = doc.text.trimEnd('\n', ' ')
        if (text.isBlank()) return ""
        val out = StringBuilder()
        var open: String? = null
        var pos = 0
        for (line in text.split("\n")) {
            val ls = pos
            val le = ls + line.length
            pos = le + 1
            val bullet = line.startsWith(BULLET)
            val quote = line.startsWith(QUOTE)
            val num = numbered.find(line)
            val plen = when {
                bullet || quote -> 2
                num != null -> num.value.length
                else -> 0
            }
            val container = when {
                bullet -> "ul"
                num != null -> "ol"
                quote -> "blockquote"
                else -> null
            }
            if (container != open) {
                val previous = open
                if (previous != null) out.append("</").append(previous).append(">")
                if (container != null) out.append(openTag(container))
                open = container
            }
            val inner = inline(doc, ls + plen, le)
            val side = doc.spans.firstOrNull { it.kind == RichKind.ALIGN && it.start < le && it.end > ls }?.num ?: 0
            if (container == "ul" || container == "ol") {
                out.append("<li>").append(inner).append("</li>")
            } else {
                val style = when (side) {
                    1 -> " style=\"text-align:center\""
                    2 -> " style=\"text-align:right\""
                    else -> ""
                }
                out.append("<div").append(style).append(">")
                    .append(if (inner.isEmpty()) "<br>" else inner)
                    .append("</div>")
            }
        }
        val last = open
        if (last != null) out.append("</").append(last).append(">")
        return out.toString()
    }

    // ---- from HTML ----

    private class Parsed {
        val sb = StringBuilder()
        val spans = mutableListOf<RichSpan>()
    }

    fun fromHtml(html: String): RichDoc {
        if (html.isBlank()) return RichDoc.Empty
        val body = Jsoup.parseBodyFragment(html).body()
        val parsed = Parsed()
        blocks(body, parsed)
        val text = parsed.sb.toString().trimEnd('\n')
        val spans = parsed.spans
            .filter { it.start < text.length }
            .map { it.copy(end = minOf(it.end, text.length)) }
        return RichDoc(text, normalize(spans))
    }

    private fun alignOf(el: Element): Int =
        when (alignRe.find(el.attr("style"))?.groupValues?.get(1)) {
            "center" -> 1
            "right" -> 2
            else -> 0
        }

    private fun blocks(parent: Element, out: Parsed) {
        val pending = mutableListOf<Node>()
        fun flush() {
            if (pending.isNotEmpty()) {
                line(pending.toList(), "", 0, out)
                pending.clear()
            }
        }
        for (node in parent.childNodes()) {
            if (node is Element && node.tagName() in blockTags) {
                flush()
                when (node.tagName()) {
                    "ul", "ol" -> {
                        var n = 1
                        for (li in node.children().filter { it.tagName() == "li" }) {
                            val prefix = if (node.tagName() == "ol") "${n++}. " else BULLET
                            line(li.childNodes(), prefix, alignOf(li), out)
                        }
                    }
                    "blockquote" -> {
                        val inner = node.children().filter { it.tagName() in blockTags }
                        if (inner.isEmpty()) {
                            line(node.childNodes(), QUOTE, alignOf(node), out)
                        } else {
                            inner.forEach { line(it.childNodes(), QUOTE, alignOf(it), out) }
                        }
                    }
                    else -> {
                        if (node.children().any { it.tagName() in blockTags }) {
                            blocks(node, out)
                        } else {
                            line(node.childNodes(), "", alignOf(node), out)
                        }
                    }
                }
            } else if (node is TextNode && node.isBlank && pending.isEmpty()) {
                // spaces between blocks
            } else {
                pending += node
            }
        }
        flush()
    }

    private fun line(nodes: List<Node>, prefix: String, align: Int, out: Parsed) {
        val start = out.sb.length
        out.sb.append(prefix)
        val onlyBreak = nodes.size == 1 && (nodes[0] as? Element)?.tagName() == "br"
        if (!onlyBreak) inlineNodes(nodes, emptyList(), out)
        val end = out.sb.length
        if (align != 0 && end > start) out.spans += RichSpan(RichKind.ALIGN, start, end, align)
        out.sb.append('\n')
    }

    private fun inlineNodes(nodes: List<Node>, styles: List<RichStyle>, out: Parsed) {
        for (node in nodes) {
            if (node is TextNode) {
                val t = node.wholeText.replace(Regex("\\s+"), " ")
                if (t.isNotEmpty()) {
                    val s = out.sb.length
                    out.sb.append(t)
                    styles.forEach { out.spans += RichSpan(it.kind, s, out.sb.length, it.num, it.url) }
                }
            } else if (node is Element) {
                if (node.tagName() == "br") {
                    out.sb.append('\n')
                } else {
                    inlineNodes(node.childNodes(), styles + styleOf(node), out)
                }
            }
        }
    }

    private fun styleOf(el: Element): List<RichStyle> {
        val list = mutableListOf<RichStyle>()
        when (el.tagName()) {
            "b", "strong" -> list += RichStyle(RichKind.BOLD)
            "i", "em" -> list += RichStyle(RichKind.ITALIC)
            "u" -> list += RichStyle(RichKind.UNDERLINE)
            "s", "strike", "del" -> list += RichStyle(RichKind.STRIKE)
            "a" -> {
                val href = el.attr("href")
                if (href.isNotBlank()) list += RichStyle(RichKind.LINK, url = href)
            }
        }
        val css = el.attr("style")
        val color = colorRe.find(css)
        if (color != null) list += RichStyle(RichKind.COLOR, 0xFF000000.toInt() or color.groupValues[1].toInt(16))
        val size = sizeRe.find(css)
        if (size != null) list += RichStyle(RichKind.SIZE, size.groupValues[1].toInt())
        return list
    }
}
