package dev.handypage.app.vocab

/**
 * M40: vocab-book grouping/search/sort — pure JVM logic, no Android deps, so
 * the whole contract is unit-testable (VocabGroupingTest).
 *
 * One "word" the user thinks of can be several rows: the (word, articleUrl)
 * unique index lets the same lemma be saved once per source article. The
 * screen therefore lists GROUPS keyed by lemma (falling back to the surface
 * form), not raw rows.
 */

/** Mastery levels (VocabWord.mastery). */
const val MASTERY_NEW = 0
const val MASTERY_LEARNING = 1
const val MASTERY_MASTERED = 2

/** Sort orders offered by the vocab-book header menu. */
enum class VocabSort { RECENT, FREQUENCY, ALPHABETICAL }

/**
 * All rows sharing one lemma/surface form, collapsed for display. [rows] is
 * newest-first. [headword]/[phonetic]/[translation]/[definition] describe the
 * group as a whole (see [groupVocabWords]); [mastery] is the MIN of the rows
 * — least-mastered wins, so a group only reads 已掌握 once every saved
 * occurrence is mastered.
 */
data class VocabGroup(
    val key: String,
    val headword: String,
    val phonetic: String?,
    val translation: String?,
    val definition: String?,
    val occurrences: Int,
    val latestAt: Long,
    val mastery: Int,
    val rows: List<VocabWord>,
)

/** Grouping key: the lemma when present, else the surface form; normalized. */
fun groupKeyOf(w: VocabWord): String =
    (w.lemma?.takeIf { it.isNotBlank() } ?: w.word).trim().lowercase()

/**
 * Groups [rows] by [groupKeyOf]. Display fields come from the latest row
 * (max addedAt), except phonetic/translation/definition which fall back to
 * the latest non-blank value when the latest row's is blank (a re-save from
 * another article may carry no gloss).
 */
fun groupVocabWords(rows: List<VocabWord>): List<VocabGroup> =
    rows.groupBy(::groupKeyOf).map { (key, groupRows) ->
        val sorted = groupRows.sortedByDescending { it.addedAt }
        val latest = sorted.first()
        fun latestNonBlank(select: (VocabWord) -> String?): String? =
            select(latest)?.takeIf { it.isNotBlank() }
                ?: sorted.firstNotNullOfOrNull { select(it)?.takeIf { v -> v.isNotBlank() } }
        VocabGroup(
            key = key,
            headword = latest.word,
            phonetic = latestNonBlank { it.phonetic },
            translation = latestNonBlank { it.translation },
            definition = latestNonBlank { it.definition },
            occurrences = sorted.size,
            latestAt = latest.addedAt,
            mastery = sorted.minOf { it.mastery },
            rows = sorted,
        )
    }

/**
 * Filters [groups]: blank [query] matches all; otherwise a case-insensitive
 * contains on headword/translation/definition. [masteryFilter] null = all
 * levels; otherwise only groups at exactly that level.
 */
fun filterGroups(
    groups: List<VocabGroup>,
    query: String,
    masteryFilter: Int?,
): List<VocabGroup> {
    val q = query.trim().lowercase()
    return groups.filter { g ->
        (masteryFilter == null || g.mastery == masteryFilter) &&
            (
                q.isEmpty() ||
                    g.headword.lowercase().contains(q) ||
                    g.translation?.lowercase()?.contains(q) == true ||
                    g.definition?.lowercase()?.contains(q) == true
                )
    }
}

/** RECENT: latest save first; FREQUENCY: occurrence count then recency; ALPHABETICAL: headword A→Z. */
fun sortGroups(groups: List<VocabGroup>, sort: VocabSort): List<VocabGroup> =
    when (sort) {
        VocabSort.RECENT -> groups.sortedByDescending { it.latestAt }
        VocabSort.FREQUENCY ->
            groups.sortedWith(compareByDescending<VocabGroup> { it.occurrences }.thenByDescending { it.latestAt })
        VocabSort.ALPHABETICAL -> groups.sortedBy { it.headword.lowercase() }
    }
