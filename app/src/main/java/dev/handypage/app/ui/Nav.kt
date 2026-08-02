package dev.handypage.app.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.handypage.app.R

/** Navigation routes for the single-activity Compose host (DESIGN.md §5). */
object Routes {
    /** Top-level tabs shown in the bottom NavigationBar (M7; M9 adds SETTINGS). */
    const val SOURCES = "sources"
    const val LOCAL = "local"
    const val AGENT = "agent"
    const val SETTINGS = "settings"
    val TABS = listOf(SOURCES, LOCAL, AGENT, SETTINGS)

    const val HISTORY = "history"

    const val ARG_SOURCE_ID = "sourceId"
    const val ARTICLES = "articles/{$ARG_SOURCE_ID}"

    fun articles(sourceId: String) = "articles/$sourceId"
}

/**
 * Shared top bar: title plus an optional back arrow (everywhere except the
 * start destination) and per-screen actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandypageTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            }
        },
        actions = actions,
    )
}
