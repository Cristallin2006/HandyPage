package dev.handypage.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONArray

/**
 * M16: structured recommendation cards rendered inside Agent chat bubbles.
 * The model outputs a fenced ```cards block (JSON array) at the end of its
 * recommendation reply; [parseCards] extracts it, [RecommendCardList] renders
 * native Compose cards with actionable buttons.
 */

/** One parsed recommendation card (article or paper). */
data class RecommendCard(
    val type: String, // "article" or "paper"
    val title: String,
    val source: String, // source name (article) or authors (paper)
    val url: String, // article url or absUrl (paper)
    val summary: String,
    val category: String = "",
)

/**
 * M32: fences the model may use for the cards block. The contract says
 * ```cards, but thinking models sometimes emit ```json instead — both are
 * accepted rather than dumping raw JSON into the bubble.
 */
private val CARD_FENCES = listOf("```cards", "```json")

/** Strips trailing commas (`{"a":1,}` / `[1,]`) — the most common model JSON slip. */
private val TRAILING_COMMA = Regex(",\\s*([}\\]])")

/**
 * Splits an assistant message into the prose part (Markdown) and the
 * structured cards list. Returns null for cards when no usable block is
 * found (the caller then shows the full raw text as an honest fallback).
 *
 * M32 hardening: accepts ```json fences, an UNCLOSED fence (truncated
 * stream — the rest of the text is tried as JSON), trailing-comma repair,
 * and text after the closing fence is appended back to the prose instead of
 * being silently dropped.
 */
fun parseCards(text: String): Pair<String, List<RecommendCard>?> {
    var startIdx = -1
    var marker = ""
    for (fence in CARD_FENCES) {
        val i = text.indexOf(fence)
        if (i >= 0 && (startIdx < 0 || i < startIdx)) {
            startIdx = i
            marker = fence
        }
    }
    if (startIdx < 0) return text to null
    val jsonStart = startIdx + marker.length
    val endIdx = text.indexOf("```", jsonStart)
    val jsonStr = (if (endIdx >= 0) text.substring(jsonStart, endIdx) else text.substring(jsonStart)).trim()
    val cards = try {
        val arr = JSONArray(TRAILING_COMMA.replace(jsonStr, "$1"))
        (0 until arr.length()).mapNotNull { i ->
            val obj = arr.getJSONObject(i)
            val type = obj.optString("type", "")
            if (type != "article" && type != "paper") return@mapNotNull null
            RecommendCard(
                type = type,
                title = obj.optString("title", ""),
                source = if (type == "article") {
                    obj.optString("source", "")
                } else {
                    obj.optString("authors", "")
                },
                url = if (type == "article") {
                    obj.optString("url", "")
                } else {
                    obj.optString("absUrl", "")
                },
                summary = obj.optString("summary", ""),
                category = obj.optString("category", ""),
            )
        }
    } catch (_: Exception) {
        null
    }
    // Total parse failure: hide the broken block, keep the prose (legacy
    // behaviour) — the raw JSON never reaches the bubble.
    if (cards.isNullOrEmpty()) return text.substring(0, startIdx).trimEnd() to null
    val prose = buildString {
        append(text.substring(0, startIdx).trimEnd())
        if (endIdx >= 0) {
            val tail = text.substring(endIdx + 3).trim()
            if (tail.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append(tail)
            }
        }
    }
    return prose to cards
}

/**
 * M32: streaming counterpart of [parseCards] — while the answer is still
 * being generated, the raw cards JSON must not be typed into the bubble.
 * Returns the visible text up to the first cards fence, plus whether a
 * cards block is currently being generated (the UI shows a placeholder
 * line instead of the JSON).
 */
fun splitStreamingCards(text: String): Pair<String, Boolean> {
    val idx = CARD_FENCES.map { text.indexOf(it) }.filter { it >= 0 }.minOrNull()
        ?: return text to false
    return text.substring(0, idx).trimEnd() to true
}

/** Renders a vertical list of recommendation cards. */
@Composable
fun RecommendCardList(
    cards: List<RecommendCard>,
    onOpenArticle: (url: String, title: String, sourceName: String) -> Unit,
    onOpenPaper: (absUrl: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (card in cards) {
            RecommendCardItem(card = card, onOpenArticle = onOpenArticle, onOpenPaper = onOpenPaper)
        }
    }
}

@Composable
private fun RecommendCardItem(
    card: RecommendCard,
    onOpenArticle: (url: String, title: String, sourceName: String) -> Unit,
    onOpenPaper: (absUrl: String, title: String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(1.dp, editorialHairlineColor()),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Title
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Source / authors + category pill
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (card.source.isNotBlank()) {
                    Text(
                        text = card.source,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (card.category.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = card.category,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            // Summary
            if (card.summary.isNotBlank()) {
                Text(
                    text = card.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // Action button
            FilledTonalButton(
                onClick = {
                    if (card.type == "article") {
                        onOpenArticle(card.url, card.title, card.source)
                    } else {
                        onOpenPaper(card.url, card.title)
                    }
                },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(if (card.type == "article") "阅读" else "下载阅读")
            }
        }
    }
}