package dev.handypage.app.agent

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Agent session persistence (DESIGN.md §4.9), merged into handypage.db at
 * version 2. One [ChatSession] per article ([ChatSession.articleKey] is the
 * article URL/EPUB-relative key, unique), so reopening a reader continues
 * the same conversation.
 */
@Entity(
    tableName = "chat_sessions",
    indices = [Index(value = ["articleKey"], unique = true)],
)
data class ChatSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val articleKey: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** One persisted message of a session; tool exchanges keep their linkage. */
@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["sessionId"])],
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    /** user / assistant / tool */
    val role: String,
    val content: String,
    val toolName: String? = null,
    val toolCallId: String? = null,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val createdAt: Long,
)

/** Per-round token usage record backing the budget gate and the settings view. */
@Entity(tableName = "usage_logs")
data class UsageLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val promptTokens: Int,
    val completionTokens: Int,
    val createdAt: Long,
)

@Dao
interface ChatDao {

    @Query("SELECT * FROM chat_sessions WHERE articleKey = :articleKey LIMIT 1")
    suspend fun findByArticleKey(articleKey: String): ChatSession?

    /** @return the new row id, or -1 when the articleKey already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(session: ChatSession): Long

    /**
     * Returns the existing-or-new session for [articleKey] (one per
     * article). Not @Transaction-wrapped: find-insert-find is race-safe
     * enough for a single-user app thanks to the unique index.
     */
    suspend fun getOrCreateSession(articleKey: String, title: String, now: Long): ChatSession {
        findByArticleKey(articleKey)?.let { return it }
        insertSession(
            ChatSession(articleKey = articleKey, title = title, createdAt = now, updatedAt = now),
        )
        return checkNotNull(findByArticleKey(articleKey)) {
            "session insert failed for $articleKey"
        }
    }

    @Insert
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    fun observeMessages(sessionId: Long): Flow<List<ChatMessageEntity>>

    @Query("UPDATE chat_sessions SET updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun touchSession(sessionId: Long, updatedAt: Long)

    // --- M17: history conversation support ---

    /** All global Agent sessions (articleKey starts with "global"), newest first. */
    @Query("SELECT * FROM chat_sessions WHERE articleKey LIKE 'global%' ORDER BY updatedAt DESC")
    fun observeGlobalSessions(): Flow<List<ChatSession>>

    /** Message count for one session (displayed in history list meta). */
    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId AND role IN ('user','assistant')")
    suspend fun countMessages(sessionId: Long): Int

    /** Delete all messages of a session (cascade before session row). */
    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: Long)

    /** Delete the session row itself. */
    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    /** Full session delete: messages first, then the row. */
    suspend fun deleteSessionWithMessages(sessionId: Long) {
        deleteMessagesForSession(sessionId)
        deleteSession(sessionId)
    }
}

@Dao
interface UsageDao {

    @Insert
    suspend fun insert(log: UsageLog): Long

    /** Total tokens (prompt + completion) recorded at or after [since]. */
    @Query(
        "SELECT COALESCE(SUM(promptTokens + completionTokens), 0)" +
            " FROM usage_logs WHERE createdAt >= :since",
    )
    suspend fun sumSince(since: Long): Int
}
