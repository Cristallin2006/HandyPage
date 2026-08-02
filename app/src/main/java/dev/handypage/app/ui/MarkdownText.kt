package dev.handypage.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon

/**
 * Finished Markdown rendered via Markwon on a wrapped TextView (chat bubbles,
 * sentence-book AI notes). Streaming text should NOT go through this — a
 * partial token stream can hold unclosed markers; render it as plain Text
 * and swap to [MarkdownText] once the stream completes (ChatPanel pattern).
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Unspecified,
) {
    val context = LocalContext.current
    val markwon = remember { Markwon.create(context) }
    val color = if (textColor == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        textColor
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            android.widget.TextView(ctx).apply {
                setTextColor(color.toArgb())
                textSize = 14f
            }
        },
        update = { view -> markwon.setMarkdown(view, markdown) },
    )
}
