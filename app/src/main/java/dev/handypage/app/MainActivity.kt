package dev.handypage.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.handypage.app.ui.AgentScreen
import dev.handypage.app.ui.ArticleListScreen
import dev.handypage.app.ui.EditorialNavBar
import dev.handypage.app.ui.EditorialNavItem
import dev.handypage.app.ui.HandypageTheme
import dev.handypage.app.ui.AgentHistoryRoute
import dev.handypage.app.ui.HistoryScreen
import dev.handypage.app.ui.HpMotion
import dev.handypage.app.ui.LocalMastheadHost
import dev.handypage.app.ui.LocalScreen
import dev.handypage.app.ui.MastheadHost
import dev.handypage.app.ui.Routes
import dev.handypage.app.ui.SettingsScreen
import dev.handypage.app.ui.SourcesScreen

/**
 * Single-activity Compose host (DESIGN.md §5). M7 home shell: four
 * top-level tabs (阅读 / 本机 / Agent / 设置) in the editorial bottom bar
 * (design-system.md §4.5 — self-drawn, ink top rule, no colour blocks), with
 * the article list as a full-screen destination on top. The reader stays a
 * separate [ReaderActivity] on top of Readium.
 */
class MainActivity : ComponentActivity() {

    // isImeVisible (used to hide the bottom bar while the keyboard is up)
    // is still ExperimentalLayoutApi.
    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HandypageTheme {
                val nav = rememberNavController()
                val backStackEntry by nav.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                // IME fix: edge-to-edge keeps the window from resizing when the
                // keyboard opens, so if the bottom tab bar keeps its slot, the
                // chat input's imePadding() lifts it a whole bar-height above
                // the keyboard (input not flush with the IME). Drop the bar
                // while the IME is visible (the keyboard covers it anyway) so
                // the input row sits flush with the keyboard top edge.
                val imeVisible = WindowInsets.isImeVisible
                // M22-R3: tab screens register their mastheads into this host
                // (EditorialMastheadSlot) and the shell renders the active
                // route's slot here — the page header never participates in
                // tab transitions; only its text crossfades (§9).
                val mastheadHost = remember { MastheadHost() }

                CompositionLocalProvider(LocalMastheadHost provides mastheadHost) {
                Scaffold(
                    bottomBar = {
                        // M22: the bar is a bottom surface — it slides down
                        // and away on forward navigation (and on IME) instead
                        // of snapping out of existence.
                        AnimatedVisibility(
                            visible = currentRoute in Routes.TABS && !imeVisible,
                            enter = slideInVertically(tween(HpMotion.BarIn, easing = HpMotion.Decel)) { it } +
                                fadeIn(tween(HpMotion.BarIn, easing = HpMotion.Decel)),
                            exit = slideOutVertically(tween(HpMotion.BarOut, easing = HpMotion.Accel)) { it } +
                                fadeOut(tween(HpMotion.BarOut, easing = HpMotion.Accel)),
                        ) {
                            EditorialNavBar(
                                items = TAB_SPECS.map {
                                    EditorialNavItem(it.route, stringResource(it.labelRes), it.glyph)
                                },
                                currentRoute = currentRoute,
                                onSelect = { nav.navigateToTab(it) },
                            )
                        }
                    },
                ) { padding ->
                    Column(
                        // Consume the shell's insets as well as padding them:
                        // without this the nested per-screen Scaffolds re-apply
                        // the same status-bar inset and the masthead sits ~two
                        // status bars below the screen top.
                        modifier = Modifier
                            .padding(padding)
                            .consumeWindowInsets(padding),
                    ) {
                        AnimatedVisibility(
                            visible = currentRoute in Routes.TABS,
                            // The incoming page rises beneath it; the masthead
                            // collapses upward in the same beat (forward = up,
                            // back = down), so its slot never snaps shut.
                            enter = expandVertically(tween(HpMotion.AxisIn, easing = HpMotion.Decel)) +
                                fadeIn(tween(HpMotion.State, easing = HpMotion.Decel)),
                            exit = shrinkVertically(tween(HpMotion.AxisIn, easing = HpMotion.Accel)) +
                                fadeOut(tween(HpMotion.TabOut, easing = HpMotion.Accel)),
                        ) {
                            Crossfade(
                                targetState = currentRoute,
                                animationSpec = tween(HpMotion.State, easing = HpMotion.Standard),
                                label = "masthead",
                            ) { route ->
                                mastheadHost.slots[route]?.invoke()
                            }
                        }
                        NavHost(
                            navController = nav,
                            startDestination = Routes.SOURCES,
                            modifier = Modifier.weight(1f),
                            // M22: same-level tabs use fade-through; forward
                            // destinations below override with shared axis Y
                            // (docs/design-system.md §9, tokens in ui/Motion.kt).
                            enterTransition = { HpMotion.tabEnter() },
                            exitTransition = { HpMotion.tabExit() },
                            popEnterTransition = { HpMotion.tabEnter() },
                        popExitTransition = { HpMotion.tabExit() },
                    ) {
                        composable(Routes.SOURCES) {
                            SourcesScreen(
                                onOpenSource = { nav.navigate(Routes.articles(it)) },
                            )
                        }
                        composable(Routes.LOCAL) {
                            LocalScreen()
                        }
                        composable(Routes.AGENT) {
                            AgentScreen(
                                onOpenSettings = { nav.navigateToTab(Routes.SETTINGS) },
                                onOpenHistory = { nav.navigate(Routes.HISTORY) },
                            )
                        }
                        composable(
                            Routes.HISTORY,
                            // M22: forward destination — shared axis Y (§9).
                            enterTransition = { HpMotion.axisEnter() },
                            exitTransition = { HpMotion.axisExit() },
                            popEnterTransition = { HpMotion.axisPopEnter() },
                            popExitTransition = { HpMotion.axisPopExit() },
                        ) {
                            AgentHistoryRoute(
                                currentSessionKey = { (application as HandypageApp).currentGlobalSessionKey },
                                onSelectSession = { key: String ->
                                    (application as HandypageApp).currentGlobalSessionKey = key
                                    nav.popBackStack()
                                },
                                onNewSession = {
                                    val key = "global_${System.currentTimeMillis()}"
                                    (application as HandypageApp).currentGlobalSessionKey = key
                                    nav.popBackStack()
                                },
                                onBack = { nav.popBackStack() },
                            )
                        }
                        composable(
                            Routes.ARTICLES,
                            arguments = listOf(
                                navArgument(Routes.ARG_SOURCE_ID) { type = NavType.StringType },
                            ),
                            // M22: forward destination — shared axis Y (§9).
                            enterTransition = { HpMotion.axisEnter() },
                            exitTransition = { HpMotion.axisExit() },
                            popEnterTransition = { HpMotion.axisPopEnter() },
                            popExitTransition = { HpMotion.axisPopExit() },
                        ) { entry ->
                            ArticleListScreen(
                                sourceId = entry.arguments?.getString(Routes.ARG_SOURCE_ID).orEmpty(),
                                onBack = { nav.popBackStack() },
                            )
                        }
                        composable(Routes.SETTINGS) {
                            // M9: settings is a top-level tab now — no back arrow.
                            SettingsScreen()
                        }
                        }
                    }
                }
            }
        }
    }
}
}

private data class TabSpec(
    val route: String,
    val labelRes: Int,
    /** Pinyon Script letter rendered as the tab glyph (M23, §4.5 B). */
    val glyph: String,
)

private val TAB_SPECS = listOf(
    TabSpec(Routes.SOURCES, R.string.tab_sources, "R"),
    TabSpec(Routes.LOCAL, R.string.tab_local, "L"),
    TabSpec(Routes.AGENT, R.string.tab_agent, "A"),
    TabSpec(Routes.SETTINGS, R.string.tab_settings, "S"),
)

/** Standard tab navigation: one instance per tab, state kept across switches. */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
