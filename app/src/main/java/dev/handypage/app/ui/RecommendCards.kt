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
 * Splits an assistant message into the prose part (Markdown) and the
 * structured cards list. Returns null for cards when no valid ```cards
 * block is found.
 */
fun parseCards(text: String): Pair<String, List<RecommendCard>?> {
    val startMarker = "```cards"
    val startIdx = text.indexOf(startMarker)
    if (startIdx < 0) return text to null
    val jsonStart = startIdx + startMarker.length
    val endIdx = text.indexOf("```", jsonStart)
    if (endIdx < 0) return text to null
    val prose = text.substring(0, startIdx).trimEnd()
    val jsonStr = text.substring(jsonStart, endIdx).trim()
    val cards = try {
        val arr = JSONArray(jsonStr)
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
    return prose to cards?.takeIf { it.isNotEmpty() }
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