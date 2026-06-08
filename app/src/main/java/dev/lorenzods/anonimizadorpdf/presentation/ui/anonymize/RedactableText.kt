package dev.lorenzods.anonimizadorpdf.presentation.ui.anonymize

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle

/**
 * Renders document text where every occurrence of a selected term is highlighted, and tapping any
 * word toggles that word as a redaction term. The highlight + hit-testing run fully on-device; no
 * text ever leaves the composable.
 */
@Composable
fun RedactableText(
    text: String,
    selectedTerms: List<String>,
    highlightBackground: Color,
    highlightForeground: Color,
    style: TextStyle,
    onToggleWord: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val annotated = remember(text, selectedTerms, highlightBackground, highlightForeground) {
        buildHighlightedText(text, selectedTerms, highlightBackground, highlightForeground)
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val currentToggle by rememberUpdatedState(onToggleWord)

    Text(
        text = annotated,
        style = style,
        onTextLayout = { layout = it },
        modifier = modifier.pointerInput(text) {
            detectTapGestures { pos ->
                val lr = layout ?: return@detectTapGestures
                val offset = lr.getOffsetForPosition(pos)
                wordRangeAt(text, offset)?.let { range ->
                    currentToggle(text.substring(range.first, range.last + 1))
                }
            }
        },
    )
}

/** Maximal run of letter/digit characters covering [index]; null if the tap missed any word. */
private fun wordRangeAt(text: String, index: Int): IntRange? {
    if (text.isEmpty()) return null
    var i = index.coerceIn(0, text.length - 1)
    if (!text[i].isWordChar()) {
        // Tapped on whitespace/punctuation — try the character just before the tap.
        if (i > 0 && text[i - 1].isWordChar()) i-- else return null
    }
    var start = i
    var end = i
    while (start > 0 && text[start - 1].isWordChar()) start--
    while (end < text.length - 1 && text[end + 1].isWordChar()) end++
    return start..end
}

private fun Char.isWordChar(): Boolean = isLetterOrDigit() || this == '@' || this == '.' || this == '-'

/** Highlights all case-insensitive occurrences of any [terms] within [text]. */
fun buildHighlightedText(
    text: String,
    terms: List<String>,
    background: Color,
    foreground: Color,
): AnnotatedString {
    val cleaned = terms.map { it.trim() }.filter { it.isNotEmpty() }
    if (cleaned.isEmpty()) return AnnotatedString(text)

    val lower = text.lowercase()
    val ranges = ArrayList<IntRange>()
    for (term in cleaned) {
        val needle = term.lowercase()
        if (needle.length > lower.length) continue
        var from = lower.indexOf(needle)
        while (from >= 0) {
            ranges.add(from until from + needle.length)
            from = lower.indexOf(needle, from + needle.length)
        }
    }
    if (ranges.isEmpty()) return AnnotatedString(text)

    ranges.sortBy { it.first }
    val merged = ArrayList<IntRange>()
    for (r in ranges) {
        val last = merged.lastOrNull()
        if (last != null && r.first <= last.last + 1) {
            if (r.last > last.last) merged[merged.lastIndex] = last.first..r.last
        } else {
            merged.add(r)
        }
    }

    return buildAnnotatedString {
        var cursor = 0
        for (r in merged) {
            if (r.first > cursor) append(text.substring(cursor, r.first))
            withStyle(SpanStyle(background = background, color = foreground)) {
                append(text.substring(r.first, r.last + 1))
            }
            cursor = r.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}
