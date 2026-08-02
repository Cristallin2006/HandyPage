package dev.handypage.app.agent

import android.content.Context

/**
 * User's reading preferences for the Agent recommendation feature (M16).
 * Declared in conversation via the `save_preference` tool; consulted by
 * `search_articles` and `search_papers` as soft filtering signals.
 */
data class AgentPreferences(
    /** "beginner" / "intermediate" / "advanced", or empty when unset. */
    val difficulty: String = "",
    /** Comma-separated topic keywords, e.g. "machine learning, linguistics". */
    val topics: String = "",
    /** Epoch millis of the last update; 0 when never set. */
    val updatedAt: Long = 0,
) {
    val isSet: Boolean get() = difficulty.isNotEmpty() || topics.isNotEmpty()

    /** Human-readable summary for tool receipts and the system prompt. */
    fun describe(): String = buildString {
        if (difficulty.isNotEmpty()) append("难度=").append(difficulty)
        if (topics.isNotEmpty()) {
            if (isNotEmpty()) append(", ")
            append("主题=").append(topics)
        }
        if (isEmpty()) append("尚未设置偏好")
    }
}

/**
 * Abstraction over preference persistence so tools stay JVM-testable
 * without an Android Context. Production uses [AgentPreferencesStore].
 */
interface PreferencesProvider {
    fun load(): AgentPreferences
    fun save(preferences: AgentPreferences)
    fun clear()
}

/**
 * SharedPreferences-backed store for [AgentPreferences].
 * Thread-safe via apply() (async write); reads are synchronous.
 */
class AgentPreferencesStore(context: Context) : PreferencesProvider {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): AgentPreferences = AgentPreferences(
        difficulty = prefs.getString(KEY_DIFFICULTY, "").orEmpty(),
        topics = prefs.getString(KEY_TOPICS, "").orEmpty(),
        updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L),
    )

    override fun save(preferences: AgentPreferences) {
        prefs.edit()
            .putString(KEY_DIFFICULTY, preferences.difficulty)
            .putString(KEY_TOPICS, preferences.topics)
            .putLong(KEY_UPDATED_AT, preferences.updatedAt)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "agent_preferences"
        const val KEY_DIFFICULTY = "difficulty"
        const val KEY_TOPICS = "topics"
        const val KEY_UPDATED_AT = "updated_at"
    }
}