package dev.handypage.app.vocab

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.handypage.app.agent.ChatDao
import dev.handypage.app.agent.ChatMessageEntity
import dev.handypage.app.agent.ChatSession
import dev.handypage.app.agent.UsageDao
import dev.handypage.app.agent.UsageLog
import dev.handypage.app.local.ArticleRecord
import dev.handypage.app.local.ArticleRecordDao
import kotlinx.coroutines.flow.Flow

/**
 * A saved vocabulary word (DESIGN.md §4.8).
 *
 * [articleUrl]/[sourceName] are identity fields, not gloss data: they default
 * to "" instead of null because SQLite treats NULLs as distinct in unique
 * indexes, which would silently defeat the dedup index below for words saved
 * from the demo book (no source article).
 */
@Entity(
    tableName = "vocab_words",
    indices = [Index(value = ["word", "articleUrl"], unique = true)],
)
data class VocabWord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val word: String,
    val lemma: String?,
    val phonetic: String?,
    val translation: String?,
    val definition: String?,
    /** Sentence the word was selected in (before+highlight+after, collapsed). */
    val sentence: String?,
    val articleUrl: String = "",
    val sourceName: String = "",
    val addedAt: Long,
)

/**
 * A saved sentence/paragraph (DESIGN.md §4.13, M10).
 *
 * [note] is the user's manual annotation; [aiNote] is the AI-generated
 * translation/breakdown. Both default to "" so the unique index below keeps
 * working (same NULL-dedup rationale as [VocabWord]).
 */
@Entity(
    tableName = "saved_sentences",
    indices = [Index(value = ["text", "articleUrl"], unique = true)],
)
data class SavedSentence(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Normalized sentence text ([SentenceText.normalize]). */
    val text: String,
    val note: String = "",
    val aiNote: String = "",
    val articleUrl: String = "",
    val articleTitle: String = "",
    val sourceName: String = "",
    val addedAt: Long,
)

/**
 * A starred web article (M21), the regular-article counterpart of [PaperStar].
 * [url] is the article URL — the same identity the reading record and the
 * cached EPUB file name derive from, so one key joins star ↔ record ↔ cache.
 * [sourceId] is stored (papers didn't need it: single arXiv source) so a
 * starred article whose cached EPUB was deleted can be refetched through its
 * source config.
 */
@Entity(tableName = "article_stars")
data class ArticleStar(
    @PrimaryKey
    val url: String,
    val title: String,
    val sourceId: String,
    val sourceName: String,
    val starredAt: Long,
)

@Dao
interface ArticleStarDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(star: ArticleStar): Long

    @Query("DELETE FROM article_stars WHERE url = :url")
    suspend fun deleteByUrl(url: String): Int

    /** Newest star first, merged with paper stars in the 收藏 section (M21). */
    @Query("SELECT * FROM article_stars ORDER BY starredAt DESC")
    fun observeAll(): Flow<List<ArticleStar>>

    /** Starred URLs only — cheap star-state lookup for article list rows. */
    @Query("SELECT url FROM article_stars")
    fun observeUrls(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM article_stars WHERE url = :url)")
    suspend fun exists(url: String): Boolean
}

/**
 * A starred arXiv paper (M13). [url] is the abs-page URL — the same identity
 * the reading record and the cached PDF/EPUB file names derive from, so one
 * key joins star ↔ record ↔ cache. [authors] is stored pre-joined (", ")
 * because the UI only ever displays the flat string.
 */
@Entity(tableName = "paper_stars")
data class PaperStar(
    @PrimaryKey
    val url: String,
    val title: String,
    val authors: String,
    val primaryCategory: String,
    val published: String,
    val starredAt: Long,
)

@Dao
interface PaperStarDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(star: PaperStar): Long

    @Query("DELETE FROM paper_stars WHERE url = :url")
    suspend fun deleteByUrl(url: String): Int

    /** Newest star first, for the 收藏 section of the 本机 tab. */
    @Query("SELECT * FROM paper_stars ORDER BY starredAt DESC")
    fun observeAll(): Flow<List<PaperStar>>

    @Query("SELECT EXISTS(SELECT 1 FROM paper_stars WHERE url = :url)")
    suspend fun exists(url: String): Boolean

    /** Starred URLs only — cheap star-state lookup for paper list rows. */
    @Query("SELECT url FROM paper_stars")
    fun observeUrls(): Flow<List<String>>
}

@Dao
interface SavedSentenceDao {

    /** @return the new row id, or -1 when the (text, articleUrl) pair already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sentence: SavedSentence): Long

    /**
     * M20: normalized texts of sentences saved from one article (dc:source of
     * the open book), for in-article underline decorations.
     */
    @Query("SELECT text FROM saved_sentences WHERE articleUrl = :articleUrl")
    suspend fun textsForArticle(articleUrl: String): List<String>

    @Query("SELECT * FROM saved_sentences ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<SavedSentence>>

    @Query("DELETE FROM saved_sentences WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("UPDATE saved_sentences SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String): Int

    @Query("UPDATE saved_sentences SET aiNote = :aiNote WHERE id = :id")
    suspend fun updateAiNote(id: Long, aiNote: String): Int
}

@Dao
interface VocabWordDao {

    /** @return the new row id, or -1 when the (word, articleUrl) pair already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(word: VocabWord): Long

    @Query("SELECT * FROM vocab_words ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<VocabWord>>

    @Query("DELETE FROM vocab_words WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT COUNT(*) FROM vocab_words")
    suspend fun count(): Int

    /**
     * M8: distinct surface forms worth weak-highlighting in articles — every
     * saved word plus its lemma (dedup via UNION). [VocabHighlight] filters
     * blanks/short tokens further.
     */
    @Query(
        "SELECT word FROM vocab_words " +
            "UNION SELECT lemma FROM vocab_words WHERE lemma IS NOT NULL",
    )
    suspend fun highlightTerms(): List<String>

    /** Whether (word, articleUrl) is already in the book — the panel's saved state. */
    @Query("SELECT EXISTS(SELECT 1 FROM vocab_words WHERE word = :word AND articleUrl = :articleUrl)")
    suspend fun exists(word: String, articleUrl: String): Boolean
}

/**
 * A cached paragraph translation of an arXiv paper's HTML version
 * (bilingual reading). Identity is (paperKey, paraHash, model): paperKey is
 * the urlHash16 of the abs URL, paraHash the SHA-1 of the normalized
 * paragraph text, model the "preset:effectiveModel" fingerprint — so a
 * paper's new version only refetches changed paragraphs and switching
 * models never clobbers existing translations.
 */
@Entity(
    tableName = "paper_translations",
    indices = [Index(value = ["paperKey", "paraHash", "model"], unique = true)],
)
data class PaperTranslation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val paperKey: String,
    val paraHash: String,
    val model: String,
    val translatedText: String,
    val createdAt: Long,
)

@Dao
interface PaperTranslationDao {

    /** Cached translations of [hashes] for one paper under one model. */
    @Query(
        "SELECT * FROM paper_translations " +
            "WHERE paperKey = :paperKey AND model = :model AND paraHash IN (:hashes)",
    )
    suspend fun cachedFor(paperKey: String, model: String, hashes: List<String>): List<PaperTranslation>

    /** REPLACE: re-translating a paragraph overwrites its cached text. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<PaperTranslation>)

    /** Drops every cached translation of one paper (「重译」). */
    @Query("DELETE FROM paper_translations WHERE paperKey = :paperKey")
    suspend fun deleteByPaper(paperKey: String): Int
}

@Database(
    entities = [
        VocabWord::class, ChatSession::class, ChatMessageEntity::class, UsageLog::class,
        ArticleRecord::class, SavedSentence::class, PaperStar::class, ArticleStar::class,
        PaperTranslation::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class VocabDatabase : RoomDatabase() {
    abstract fun vocabWordDao(): VocabWordDao
    abstract fun savedSentenceDao(): SavedSentenceDao
    abstract fun paperStarDao(): PaperStarDao
    abstract fun articleStarDao(): ArticleStarDao
    abstract fun chatDao(): ChatDao
    abstract fun usageDao(): UsageDao
    abstract fun articleRecordDao(): ArticleRecordDao
    abstract fun paperTranslationDao(): PaperTranslationDao

    companion object {
        /**
         * 1 → 2 adds the M5 agent tables (DESIGN.md §4.9). Pure CREATE
         * TABLE/INDEX: existing vocab_words data is untouched. Column and
         * index names must match what Room expects for the new entities.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_sessions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`articleKey` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_chat_sessions_articleKey`" +
                        " ON `chat_sessions` (`articleKey`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_messages` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` INTEGER NOT NULL, " +
                        "`role` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, " +
                        "`toolName` TEXT, " +
                        "`toolCallId` TEXT, " +
                        "`promptTokens` INTEGER NOT NULL, " +
                        "`completionTokens` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId`" +
                        " ON `chat_messages` (`sessionId`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `usage_logs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`promptTokens` INTEGER NOT NULL, " +
                        "`completionTokens` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
            }
        }

        /**
         * 2 → 3 adds the M7 reading-record table (DESIGN.md §4.10): one row
         * per opened article, doubling as the offline-cache index. Pure
         * CREATE TABLE/INDEX; existing data untouched.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `article_records` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`url` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`sourceId` TEXT NOT NULL, " +
                        "`sourceName` TEXT NOT NULL, " +
                        "`epubPath` TEXT NOT NULL, " +
                        "`firstOpenedAt` INTEGER NOT NULL, " +
                        "`lastOpenedAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_article_records_url`" +
                        " ON `article_records` (`url`)",
                )
            }
        }

        /**
         * 3 → 4 adds the M10 saved-sentence table (DESIGN.md §4.13). Pure
         * CREATE TABLE/INDEX; existing data untouched. note/aiNote and the
         * provenance columns are NOT NULL with no DB default — inserts always
         * go through the entity, which defaults them to "".
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_sentences` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`text` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`aiNote` TEXT NOT NULL, " +
                        "`articleUrl` TEXT NOT NULL, " +
                        "`articleTitle` TEXT NOT NULL, " +
                        "`sourceName` TEXT NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_sentences_text_articleUrl`" +
                        " ON `saved_sentences` (`text`, `articleUrl`)",
                )
            }
        }

        /**
         * 4 → 5 adds the M13 paper-star table (DESIGN.md §4.14). Pure CREATE
         * TABLE; existing data untouched.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `paper_stars` (" +
                        "`url` TEXT PRIMARY KEY NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`authors` TEXT NOT NULL, " +
                        "`primaryCategory` TEXT NOT NULL, " +
                        "`published` TEXT NOT NULL, " +
                        "`starredAt` INTEGER NOT NULL)",
                )
            }
        }

        /**
         * 5 → 6 adds the M21 article-star table (DESIGN.md §4.23). Pure
         * CREATE TABLE; existing data untouched.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `article_stars` (" +
                        "`url` TEXT PRIMARY KEY NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`sourceId` TEXT NOT NULL, " +
                        "`sourceName` TEXT NOT NULL, " +
                        "`starredAt` INTEGER NOT NULL)",
                )
            }
        }

        /**
         * 6 → 7 adds the paper-translation cache for the bilingual HTML
         * reader. Pure CREATE TABLE/INDEX; existing data untouched.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `paper_translations` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`paperKey` TEXT NOT NULL, " +
                        "`paraHash` TEXT NOT NULL, " +
                        "`model` TEXT NOT NULL, " +
                        "`translatedText` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS" +
                        " `index_paper_translations_paperKey_paraHash_model`" +
                        " ON `paper_translations` (`paperKey`, `paraHash`, `model`)",
                )
            }
        }
    }
}
