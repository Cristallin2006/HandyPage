package dev.handypage.app

import dev.handypage.app.agent.AgentPreferences
import dev.handypage.app.agent.GetPreferencesTool
import dev.handypage.app.agent.PreferencesProvider
import dev.handypage.app.agent.SavePreferenceTool
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM tests for [SavePreferenceTool] and [GetPreferencesTool]: verifies
 * set/clear/partial-update semantics and the human-readable receipt text.
 */
class SavePreferenceToolTest {

    private lateinit var store: FakePreferences
    private lateinit var saveTool: SavePreferenceTool
    private lateinit var getTool: GetPreferencesTool

    @Before
    fun setUp() {
        store = FakePreferences()
        saveTool = SavePreferenceTool(preferencesStore = store)
        getTool = GetPreferencesTool(preferencesStore = store)
    }

    @Test
    fun `set difficulty and topics persists both`() = runBlocking {
        val result = saveTool.execute(
            JSONObject()
                .put("difficulty", "intermediate")
                .put("topics", "AI, linguistics"),
        )
        assertTrue(result.contains("已更新偏好"))
        assertTrue(result.contains("intermediate"))
        assertTrue(result.contains("AI, linguistics"))
        val saved = store.load()
        assertEquals("intermediate", saved.difficulty)
        assertEquals("AI, linguistics", saved.topics)
        assertTrue(saved.updatedAt > 0)
    }

    @Test
    fun `partial update keeps existing fields`() = runBlocking {
        store.save(AgentPreferences(difficulty = "advanced", topics = "physics", updatedAt = 1L))
        // Only update topics; difficulty should remain "advanced".
        saveTool.execute(JSONObject().put("topics", "biology"))
        val saved = store.load()
        assertEquals("advanced", saved.difficulty)
        assertEquals("biology", saved.topics)
    }

    @Test
    fun `clear action removes all preferences`() = runBlocking {
        store.save(AgentPreferences(difficulty = "beginner", topics = "math", updatedAt = 1L))
        val result = saveTool.execute(JSONObject().put("action", "clear"))
        assertTrue(result.contains("已清除"))
        val saved = store.load()
        assertEquals("", saved.difficulty)
        assertEquals("", saved.topics)
    }

    @Test
    fun `get returns description when set`() = runBlocking {
        store.save(AgentPreferences(difficulty = "advanced", topics = "NLP", updatedAt = 1L))
        val result = getTool.execute(JSONObject())
        assertTrue(result.contains("advanced"))
        assertTrue(result.contains("NLP"))
    }

    @Test
    fun `get returns not-set message when empty`() = runBlocking {
        val result = getTool.execute(JSONObject())
        assertEquals("尚未设置偏好", result)
    }

    @Test
    fun `difficulty is normalized to lowercase`() = runBlocking {
        saveTool.execute(JSONObject().put("difficulty", "BEGINNER"))
        assertEquals("beginner", store.load().difficulty)
    }

    private class FakePreferences : PreferencesProvider {
        private var prefs = AgentPreferences()
        override fun load(): AgentPreferences = prefs
        override fun save(preferences: AgentPreferences) { prefs = preferences }
        override fun clear() { prefs = AgentPreferences() }
    }
}