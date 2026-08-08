package dev.handypage.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.handypage.app.R
import dev.handypage.app.ai.AIFactory
import dev.handypage.app.ai.AIProviderConfig
import dev.handypage.app.ai.AIProviderPreset
import dev.handypage.app.ai.AISettingsStore
import dev.handypage.app.ai.OpenAICompatProvider
import dev.handypage.app.reader.ReaderSettings
import dev.handypage.app.reader.ReaderSettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * Settings tab (M19, hub-and-spoke): the flat one-page list became a hub of
 * category rows ([SettingsCategoryRow] — 15sp title + live status summary +
 * chevron, hairline-separated, no cards), each opening a detail page under
 * its own masthead with a back arrow. [initialSection] lets the standalone
 * [dev.handypage.app.SettingsActivity] deep-link straight into a page (the
 * readers' "去设置" lands on [SettingsSection.Ai]); the bottom-tab host
 * starts at the hub. System back returns hub-wards before leaving.
 *
 * Detail content is the M15 set, plus the M16 highlight palette now also
 * lives on the reading page (shared [HighlightSwatchRow] with the Aa panel).
 * AI behaviour unchanged: field values load per provider, the selected
 * provider is what reader features use, probe errors shown verbatim.
 */
@Composable
fun SettingsScreen(
    onBack: (() -> Unit)? = null,
    initialSection: SettingsSection? = null,
) {
    var section by remember { mutableStateOf(initialSection) }
    BackHandler(enabled = section != null) { section = null }
    when (section) {
        SettingsSection.Reading -> ReadingSettingsPage(onBack = { section = null })
        SettingsSection.Appearance -> AppearanceSettingsPage(onBack = { section = null })
        SettingsSection.Ai -> AiSettingsPage(onBack = { section = null })
        null -> SettingsHub(onBack = onBack, onOpen = { section = it })
    }
}

enum class SettingsSection { Reading, Appearance, Ai }

// ------------------------------------------------------------------ hub

@Composable
private fun SettingsHub(onBack: (() -> Unit)?, onOpen: (SettingsSection) -> Unit) {
    val context = LocalContext.current
    val readerSettings = remember { ReaderSettingsStore(context).load() }
    val aiConfig = remember { AISettingsStore(context).selectedConfig() }
    // M38: live summary — the row reflects a theme switch immediately.
    val appTheme by AppThemeController.theme.collectAsState()

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // M22: hoisted to the shell via MastheadHost when present (tab
            // context); inline in the standalone SettingsActivity.
            EditorialMastheadSlot(Routes.SETTINGS) {
                EditorialMasthead(
                    title = stringResource(R.string.tab_settings),
                    titleEn = "SETTINGS",
                    meta = stringResource(R.string.settings_meta),
                    kickerEnd = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back),
                                )
                            }
                        }
                    },
                )
            }
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = EditorialSpacing.lg)) {
                SettingsCategoryRow(
                    title = stringResource(R.string.reader_settings_title),
                    summary = readingSummary(readerSettings),
                    onClick = { onOpen(SettingsSection.Reading) },
                )
                EditorialHairline()
                SettingsCategoryRow(
                    title = stringResource(R.string.settings_appearance_title),
                    summary = stringResource(appTheme.labelRes) + " · " + appTheme.labelEn,
                    onClick = { onOpen(SettingsSection.Appearance) },
                )
                EditorialHairline()
                SettingsCategoryRow(
                    title = stringResource(R.string.ai_config_section),
                    summary = if (aiConfig.isUsable) {
                        "${AIProviderPreset.fromId(aiConfig.presetId).label} · ${aiConfig.effectiveModel}"
                    } else {
                        stringResource(R.string.settings_ai_not_configured)
                    },
                    onClick = { onOpen(SettingsSection.Ai) },
                )
                EditorialHairline()
            }
        }
    }
}

/** Hub row: 15sp/500 title + live status summary + trailing chevron (§4.3 行式,无卡片). */
@Composable
private fun SettingsCategoryRow(title: String, summary: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null, // the row's title announces the destination
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun readingSummary(s: ReaderSettings): String {
    val align = stringResource(
        if (s.justified) R.string.settings_align_justify else R.string.settings_align_left,
    )
    val theme = stringResource(
        when (s.normalizedThemeName) {
            ReaderSettings.THEME_DARK -> R.string.settings_theme_dark
            ReaderSettings.THEME_SEPIA -> R.string.settings_theme_sepia
            else -> R.string.settings_theme_light
        },
    )
    return "$align · ${(s.clampedFontScale * 100).roundToInt()}% · $theme"
}

// ------------------------------------------------------------------ detail scaffold

@Composable
private fun SettingsDetailPage(
    titleRes: Int,
    titleEn: String,
    metaRes: Int,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            EditorialMasthead(
                title = stringResource(titleRes),
                titleEn = titleEn,
                meta = stringResource(metaRes),
                kickerEnd = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(EditorialSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

// ------------------------------------------------------------------ reading page

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    // Shared store with the in-reader Aa panel; changes apply to the next
    // article open, the panel re-reads it.
    val readerStore = remember { ReaderSettingsStore(context) }
    var readerSettings by remember { mutableStateOf(readerStore.load()) }

    fun applyReaderSettings(s: ReaderSettings) {
        readerSettings = s
        readerStore.save(s)
    }

    SettingsDetailPage(
        titleRes = R.string.reader_settings_title,
        titleEn = "READING",
        metaRes = R.string.settings_reading_meta,
        onBack = onBack,
    ) {
        Text(stringResource(R.string.settings_align))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !readerSettings.justified,
                onClick = { applyReaderSettings(readerSettings.copy(justified = false)) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = 0, count = 2,
                    baseShape = MaterialTheme.shapes.medium, // M15-R2: 4dp (§4.10)
                ),
            ) { Text(stringResource(R.string.settings_align_left)) }
            SegmentedButton(
                selected = readerSettings.justified,
                onClick = { applyReaderSettings(readerSettings.copy(justified = true)) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = 1, count = 2,
                    baseShape = MaterialTheme.shapes.medium,
                ),
            ) { Text(stringResource(R.string.settings_align_justify)) }
        }
        Text(
            stringResource(R.string.settings_justify_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.settings_font_size))
            Text(
                String.format(
                    java.util.Locale.US, "%d%%",
                    (readerSettings.clampedFontScale * 100).roundToInt(),
                ),
            )
        }
        Slider(
            value = readerSettings.fontScale,
            onValueChange = { applyReaderSettings(readerSettings.copy(fontScale = it)) },
            valueRange = ReaderSettings.FONT_SCALE_MIN.toFloat()..
                ReaderSettings.FONT_SCALE_MAX.toFloat(),
            steps = 33, // 0.05 granularity, same as the in-reader panel
        )

        Text(stringResource(R.string.settings_theme))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val themes = listOf(
                ReaderSettings.THEME_LIGHT to R.string.settings_theme_light,
                ReaderSettings.THEME_DARK to R.string.settings_theme_dark,
                ReaderSettings.THEME_SEPIA to R.string.settings_theme_sepia,
            )
            themes.forEachIndexed { index, (themeName, labelRes) ->
                SegmentedButton(
                    selected = readerSettings.normalizedThemeName == themeName,
                    onClick = {
                        applyReaderSettings(readerSettings.copy(themeName = themeName))
                    },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index, count = themes.size,
                        baseShape = MaterialTheme.shapes.medium, // M15-R2: 4dp (§4.10)
                    ),
                ) { Text(stringResource(labelRes)) }
            }
        }

        // M19: the M16 palette also lives here (Aa panel shares the row).
        Text(stringResource(R.string.settings_highlight_color))
        HighlightSwatchRow(
            settings = readerSettings,
            onSelect = { applyReaderSettings(readerSettings.copy(highlightName = it)) },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.settings_page_margins))
            Text(
                String.format(
                    java.util.Locale.US, "%.2f", readerSettings.clampedPageMargins,
                ),
            )
        }
        Slider(
            value = readerSettings.pageMargins,
            onValueChange = { applyReaderSettings(readerSettings.copy(pageMargins = it)) },
            valueRange = ReaderSettings.PAGE_MARGINS_MIN.toFloat()..
                ReaderSettings.PAGE_MARGINS_MAX.toFloat(),
            steps = 29, // 0.05 granularity
        )
    }
}

// ------------------------------------------------------------------ AI page

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AISettingsStore(context) }
    val scope = rememberCoroutineScope()

    var preset by remember {
        mutableStateOf(AIProviderPreset.fromId(store.selectedConfig().presetId))
    }
    var baseUrl by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var testJob by remember { mutableStateOf<Job?>(null) }

    // Refill whenever the preset changes, including the initial composition;
    // unsaved edits of the previous provider are discarded, as before.
    LaunchedEffect(preset) {
        val stored = store.configFor(preset.id)
        baseUrl = stored.effectiveBaseUrl
        model = stored.effectiveModel
        apiKey = stored.apiKey
        status = ""
    }

    fun configFromFields(): AIProviderConfig = AIProviderConfig(
        presetId = preset.id,
        apiKey = apiKey.trim(),
        baseUrl = baseUrl.trim(),
        model = model.trim(),
    )

    /**
     * Real chat/completions round-trip; the error body is shown verbatim —
     * it is the fastest way to spot a 401 or a wrong base URL.
     */
    fun testConnection() {
        val config = configFromFields()
        val provider = AIFactory.fromConfig(config) as? OpenAICompatProvider
        if (provider == null) {
            status = context.getString(R.string.ai_test_need_key)
            return
        }
        testJob?.cancel()
        status = context.getString(R.string.ai_testing)
        testJob = scope.launch {
            try {
                val reply = withTimeoutOrNull(20_000) {
                    var text = ""
                    provider.testConnection().collect { text = it }
                    text
                }
                status = if (reply == null) {
                    context.getString(R.string.ai_test_timeout)
                } else {
                    context.getString(
                        R.string.ai_test_ok, provider.name, config.effectiveModel, reply,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = context.getString(R.string.ai_test_failed, e.message ?: e.toString())
            }
        }
    }

    // Leaving the page cancels an in-flight probe (old onStop behaviour).
    DisposableEffect(Unit) {
        onDispose { testJob?.cancel() }
    }

    SettingsDetailPage(
        titleRes = R.string.ai_config_section,
        titleEn = "AI",
        metaRes = R.string.settings_ai_meta,
        onBack = onBack,
    ) {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = preset.label,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.ai_provider)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                AIProviderPreset.entries.forEach { entry ->
                    DropdownMenuItem(
                        text = { Text(entry.label) },
                        onClick = {
                            preset = entry
                            expanded = false
                        },
                    )
                }
            }
        }
        if (preset.tag.isNotBlank()) {
            Text(
                preset.tag,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text(stringResource(R.string.ai_base_url)) },
            placeholder = { Text(stringResource(R.string.ai_base_url_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text(stringResource(R.string.ai_model)) },
            placeholder = { Text(stringResource(R.string.ai_model_hint)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text(stringResource(R.string.ai_api_key)) },
            placeholder = { Text(stringResource(R.string.ai_api_key_hint)) },
            singleLine = true,
            visualTransformation =
                if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(R.string.ai_show_key),
                    )
                }
            },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    store.save(configFromFields(), select = true)
                    Toast.makeText(context, R.string.ai_saved, Toast.LENGTH_SHORT).show()
                },
                shape = MaterialTheme.shapes.medium, // ink 填充 4dp (§4.7)
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.ai_save))
            }
            OutlinedButton(
                onClick = { testConnection() },
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.ai_test))
            }
        }

        if (status.isNotBlank()) {
            SelectionContainer {
                Text(status, modifier = Modifier.fillMaxWidth())
            }
        }
        Text(
            stringResource(R.string.ai_key_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ------------------------------------------------------------------ appearance page (M38)

/**
 * App colour-theme picker: one row per [AppTheme], dual-tone dots (accent +
 * soft container, rendered from the palette of the current light/dark mode),
 * zh label + decorative English subscript, check on the active row. Tapping
 * switches instantly via [AppThemeController] — no recreate, every Compose
 * host repaints. Reader themes are a separate setting (Reading page).
 */
@Composable
private fun AppearanceSettingsPage(onBack: () -> Unit) {
    val active by AppThemeController.theme.collectAsState()
    val dark = isSystemInDarkTheme()

    SettingsDetailPage(
        titleRes = R.string.settings_appearance_title,
        titleEn = "APPEARANCE",
        metaRes = R.string.settings_appearance_meta,
        onBack = onBack,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AppTheme.entries.forEachIndexed { index, theme ->
                AppThemeRow(
                    theme = theme,
                    dark = dark,
                    selected = theme == active,
                    onClick = { AppThemeController.set(theme) },
                )
                if (index < AppTheme.entries.size - 1) EditorialHairline()
            }
        }
    }
}

/** Theme row: dual-tone dots + 15sp/500 label + en subscript + trailing check. */
@Composable
private fun AppThemeRow(
    theme: AppTheme,
    dark: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = theme.palette(dark)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.padding(end = 14.dp)) {
            Box(
                Modifier
                    .size(16.dp)
                    .background(Color(palette.accent), CircleShape),
            )
            Box(
                Modifier
                    .padding(start = 4.dp)
                    .size(16.dp)
                    .background(Color(palette.accentSoft), CircleShape),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(theme.labelRes),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = theme.labelEn,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null, // row state is announced by the label
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
