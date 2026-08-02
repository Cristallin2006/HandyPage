package dev.handypage.app

import dev.handypage.app.ai.AIProviderConfig
import dev.handypage.app.ai.AIProviderPreset
import dev.handypage.app.ai.AISettingsCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the pure BYOK settings logic: preset defaults, effective
 * value fallbacks, usability gate, and the map codec round-trip. The android
 * SharedPreferences wrapper is thin and device-verified later.
 */
class AISettingsTest {

    @Test
    fun `presets carry their documented endpoints`() {
        assertEquals("https://api.deepseek.com", AIProviderPreset.DEEPSEEK.defaultBaseUrl)
        assertEquals("deepseek-chat", AIProviderPreset.DEEPSEEK.defaultModel)
        assertEquals("国内直连", AIProviderPreset.DEEPSEEK.tag)
        assertEquals("https://api.openai.com/v1", AIProviderPreset.OPENAI.defaultBaseUrl)
        assertEquals("gpt-4o-mini", AIProviderPreset.OPENAI.defaultModel)
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/openai/",
            AIProviderPreset.GEMINI.defaultBaseUrl,
        )
        assertEquals("gemini-2.5-flash-lite", AIProviderPreset.GEMINI.defaultModel)
        assertEquals("", AIProviderPreset.CUSTOM.defaultBaseUrl)
    }

    @Test
    fun `fromId falls back to DeepSeek for unknown ids`() {
        assertEquals(AIProviderPreset.DEEPSEEK, AIProviderPreset.fromId(null))
        assertEquals(AIProviderPreset.DEEPSEEK, AIProviderPreset.fromId("bogus"))
        assertEquals(AIProviderPreset.GEMINI, AIProviderPreset.fromId("gemini"))
    }

    @Test
    fun `effective values fall back to preset defaults when blank`() {
        val config = AIProviderConfig(presetId = "deepseek", apiKey = "k")
        assertEquals("https://api.deepseek.com", config.effectiveBaseUrl)
        assertEquals("deepseek-chat", config.effectiveModel)
        val overridden = config.copy(baseUrl = "https://proxy.example.com/v1", model = "m1")
        assertEquals("https://proxy.example.com/v1", overridden.effectiveBaseUrl)
        assertEquals("m1", overridden.effectiveModel)
    }

    @Test
    fun `usability requires key, url and model`() {
        assertFalse(AIProviderConfig(presetId = "deepseek").isUsable)
        assertTrue(AIProviderConfig(presetId = "deepseek", apiKey = "k").isUsable)
        // Custom has no defaults: blank url/model make it unusable even with a key.
        assertFalse(AIProviderConfig(presetId = "custom", apiKey = "k").isUsable)
        assertTrue(
            AIProviderConfig(
                presetId = "custom",
                apiKey = "k",
                baseUrl = "https://x.example.com",
                model = "m",
            ).isUsable,
        )
    }

    @Test
    fun `codec round-trips per-provider overrides and the selection`() {
        var map = emptyMap<String, String>()
        // Default selection is DeepSeek.
        assertEquals("deepseek", AISettingsCodec.selectedId(map))

        map = AISettingsCodec.withConfig(
            map,
            AIProviderConfig(presetId = "deepseek", apiKey = "sk-deep"),
            select = true,
        )
        map = AISettingsCodec.withConfig(
            map,
            AIProviderConfig(
                presetId = "custom",
                apiKey = "sk-custom",
                baseUrl = "https://proxy.example.com/v1",
                model = "my-model",
            ),
            select = true,
        )

        assertEquals("custom", AISettingsCodec.selectedId(map))
        val custom = AISettingsCodec.configFor(map, "custom")
        assertEquals("sk-custom", custom.apiKey)
        assertEquals("https://proxy.example.com/v1", custom.baseUrl)
        assertEquals("my-model", custom.model)
        // The other provider's key survived the second write.
        assertEquals("sk-deep", AISettingsCodec.configFor(map, "deepseek").apiKey)
        // Untouched provider decodes to blanks.
        assertEquals("", AISettingsCodec.configFor(map, "openai").apiKey)
    }
}
