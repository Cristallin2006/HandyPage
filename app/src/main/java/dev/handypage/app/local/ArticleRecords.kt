package dev.handypage.app.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * One row per article ever opened in the reader (DESIGN.md §4.10). The EPUB
 * that [epubPath] points at is written by EpubPackager with a stable,
 * URL-derived file name, so a record doubles as the offline-cache index:
 * if the file still exists the article reopens without any network.
 */
@Entity(
    tableName = "article_records",
    indices = [Index(value = ["url"], unique = true)],
)
data class ArticleRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val title: String,
    val sourceId: String,
    val sourceName: String,
    val epubPath: String,
    val firstOpenedAt: Long,
    val lastOpenedAt: Long,
)

@Dao
interface ArticleRecordDao {

    /** @return the new row id, or -1 when the URL was already recorded. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: ArticleRecord): Long

    @Query(
        "UPDATE article_records SET title = :title, sourceId = :sourceId, " +
            "sourceName = :sourceName, epubPath = :epubPath, lastOpenedAt = :now " +
            "WHERE url = :url",
    )
    suspend fun updateOnOpen(
        url: String,
        title: String,
        sourceId: String,
        sourceName: String,
        epubPath: String,
        now: Long,
    ): Int

    /**
     * Upsert by URL. minSdk 24 ships SQLite < 3.24 on some devices, so the
     * INSERT ... ON CONFLICT DO UPDATE syntax is off the table — do the
     * insert-then-update dance inside one transaction instead.
     */
    @Transaction
    suspend fun recordOpen(
        url: String,
        title: String,
        sourceId: String,
        sourceName: String,
        epubPath: String,
        now: Long,
    ) {
        val id = insert(
            ArticleRecord(
                url = url, title = title, sourceId = sourceId, sourceName = sourceName,
                epubPath = epubPath, firstOpenedAt = now, lastOpenedAt = now,
            ),
        )
        if (id == -1L) {
            updateOnOpen(url, title, sourceId, sourceName, epubPath, now)
        }
    }

    @Query("SELECT * FROM article_records ORDER BY lastOpenedAt DESC")
    fun observeAll(): Flow<List<ArticleRecord>>

    /**
     * M20: reverse lookup, EPUB file → full record. Readium does not surface
     * the OPF dc:source we embed (not a first-class RWPM property), so the
     * reading record is the canonical url ↔ epubPath join for a book the app
     * itself packed. M21 also reads title/sourceId/sourceName from it for
     * the reader star.
     */
    @Query("SELECT * FROM article_records WHERE epubPath = :path")
    suspend fun recordForEpubPath(path: String): ArticleRecord?

    @Query("DELETE FROM article_records WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT COUNT(*) FROM article_records")
    suspend fun count(): Int
}
