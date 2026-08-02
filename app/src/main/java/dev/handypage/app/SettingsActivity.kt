package dev.handypage.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.handypage.app.ui.HandypageTheme
import dev.handypage.app.ui.SettingsScreen
import dev.handypage.app.ui.SettingsSection
import dev.handypage.app.ui.applyHpAxisCloseTransition
import dev.handypage.app.ui.applyHpAxisOpenTransition

/**
 * Thin standalone host for [SettingsScreen]. Kept because the (frozen)
 * ReaderFragment deep-links here via an explicit Intent when no BYOK key is
 * configured; in-app navigation uses the NavHost settings destination in
 * [MainActivity] instead. [EXTRA_SECTION] picks the landing detail page
 * (M19: the readers' "去设置" lands on the AI page, not the hub).
 */
class SettingsActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SECTION = "dev.handypage.app.SETTINGS_SECTION"
        const val SECTION_AI = "ai"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // M22: axis-Y open transition (§9; close twin in finish()).
        applyHpAxisOpenTransition()
        val initial = if (intent.getStringExtra(EXTRA_SECTION) == SECTION_AI) {
            SettingsSection.Ai
        } else {
            null
        }
        setContent {
            HandypageTheme {
                SettingsScreen(onBack = { finish() }, initialSection = initial)
            }
        }
    }

    override fun finish() {
        applyHpAxisCloseTransition()
        super.finish()
    }
}
