package dev.handypage.app.ai

import android.content.Context
import dev.handypage.app.handypageHttpClient
import okhttp3.OkHttpClient

/** Builds providers from persisted settings; null when no usable key exists. */
object AIFactory {

    /** Shared base client; providers derive per-call timeouts from it. */
    private val baseClient: OkHttpClient by lazy { handypageHttpClient() }

    fun fromSettings(context: Context): AIProvider? {
        val config = AISettingsStore(context).selectedConfig()
        if (!config.isUsable) return null
        return OpenAICompatProvider(config, baseClient)
    }

    /** Builds a provider from ad-hoc (possibly unsaved) field values. */
    fun fromConfig(config: AIProviderConfig): AIProvider? {
        if (!config.isUsable) return null
        return OpenAICompatProvider(config, baseClient)
    }
}
