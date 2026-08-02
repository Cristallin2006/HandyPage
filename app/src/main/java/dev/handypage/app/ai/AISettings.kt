package dev.handypage.app.ai

import android.content.Context

/**
 * BYOK provider presets (DESIGN.md §4.7). Values are the OpenAI-compatible
 * chat-completions endpoints; [CUSTOM] starts blank and is fully editable.
 */
enum class AIProviderPreset(
    val id: String,
    val label: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    /** Short hint shown in the settings UI, e.g. 国内直连. */
    val tag: String,
) {
    DEEPSEEK("deepseek", "DeepSeek", "https://api.deepseek.com", "deepseek-chat", "国内直连"),
    OPENAI("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", ""),
    GEMINI(
        "gemini", "Gemini",
        "https://generativelanguage.googleapis.com/v1beta/openai/",
        "gemini-2.5-flash-lite", "",
    ),
    CUSTOM("custom", "Custom", "", "", "");

    companion object {
        fun fromId(id: String?): AIProviderPreset =
            entries.firstOrNull { it.id == id } ?: DEEPSEEK
    }
}

/**
 * Effective settings for one provider: preset identity plus the user's
 * overrides (blank baseUrl/model fall back to the preset defaults).
 */
data class AIProviderConfig(
    val presetId: String,
    val apiKey: String = "",
    val baseUrl: String = "",
    val model: String = "",
) {
    val preset: AIProviderPreset
        get() = AIProviderPreset.fromId(presetId)

    /** Base URL actually used: override when set, else the preset default. */
    val effectiveBaseUrl: String
        get() = baseUrl.ifBlank { preset.defaultBaseUrl }

    /** Model actually used: override when set, else the preset default. */
    val effectiveModel: String
        get() = model.ifBlank { preset.defaultModel }

    /** A provider is only callable once it has a key, URL, and model. */
    val isUsable: Boolean
        get() = apiKey.isNotBlank() && effectiveBaseUrl.isNotBlank() &&
            effectiveModel.isNotBlank()
}

/**
 * Pure map (de)serialization of the whole settings state, kept free of
 * android classes so it is JVM-testable. Map layout:
 * `provider` = selected preset id; `key_<id>` / `baseurl_<id>` / `model_<id>`
 * hold the per-provider values.
 */
object AISettingsCodec {

    private const val KEY_PROVIDER = "provider"
    private const val PREFIX_KEY = "key_"
    private const val PREFIX_BASE_URL = "baseurl_"
    private const val PREFIX_MODEL = "model_"

    fun selectedId(map: Map<String, String>): String =
        map[KEY_PROVIDER] ?: AIProviderPreset.DEEPSEEK.id

    fun configFor(map: Map<String, String>, presetId: String): AIProviderConfig =
        AIProviderConfig(
            presetId = presetId,
            apiKey = map[PREFIX_KEY + presetId].orEmpty(),
            baseUrl = map[PREFIX_BASE_URL + presetId].orEmpty(),
            model = map[PREFIX_MODEL + presetId].orEmpty(),
        )

    /** Returns a new map with [config] stored under its preset id. */
    fun withConfig(
        map: Map<String, String>,
        config: AIProviderConfig,
        select: Boolean = false,
    ): Map<String, String> = map.toMutableMap().apply {
        put(PREFIX_KEY + config.presetId, config.apiKey)
        put(PREFIX_BASE_URL + config.presetId, config.baseUrl)
        put(PREFIX_MODEL + config.presetId, config.model)
        if (select) put(KEY_PROVIDER, config.presetId)
    }
}

/**
 * Android-side persistence for [AISettingsCodec]'s map.
 *
 * Plain SharedPreferences (MODE_PRIVATE), NOT EncryptedSharedPreferences:
 * androidx.security:security-crypto's latest stable (1.1.0, 2025-07-30)
 * deprecates the EncryptedSharedPreferences.create(...) factory methods in
 * favour of using Tink directly (verified in the published AAR), and pulling
 * in Tink for one BYOK field is not worth it. The device-local, sandboxed
 * SharedPreferences file is acceptable for a personal BYOK key.
 */
class AISettingsStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadMap(): Map<String, String> =
        prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    fun selectedConfig(): AIProviderConfig =
        loadMap().let { AISettingsCodec.configFor(it, AISettingsCodec.selectedId(it)) }

    fun configFor(presetId: String): AIProviderConfig =
        AISettingsCodec.configFor(loadMap(), presetId)

    fun save(config: AIProviderConfig, select: Boolean = true) {
        val updated = AISettingsCodec.withConfig(loadMap(), config, select)
        prefs.edit().apply {
            updated.forEach { (k, v) -> putString(k, v) }
        }.apply()
    }

    private companion object {
        const val PREFS_NAME = "ai_settings"
    }
}
