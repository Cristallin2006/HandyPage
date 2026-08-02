package dev.handypage.app.agent

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Daily token budget gate (DESIGN.md §4.9). Pure Kotlin so the gate logic is
 * JVM-testable; android-side persistence lives in [DailyBudgetStore].
 *
 * A [limit] of 0 (or less) means unlimited. The gate is checked before each
 * provider round; actual usage is recorded after the round, so a run may
 * overshoot the limit once — the *next* call is then refused.
 */
class DailyBudget(
    val limit: Int = DEFAULT_LIMIT,
    var usedToday: Int = 0,
) {
    /** True while another provider call fits today's budget. */
    fun canSpend(): Boolean = limit <= 0 || usedToday < limit

    /** Adds [tokens] to today's usage; non-positive values are ignored. */
    fun record(tokens: Int) {
        if (tokens > 0) usedToday += tokens
    }

    companion object {
        /** Default daily cap: 200k tokens (DESIGN.md §4.9). */
        const val DEFAULT_LIMIT = 200_000
    }
}

/**
 * SharedPreferences persistence for [DailyBudget]. Usage is stored under a
 * key containing the date (`used_yyyy-MM-dd`), so a new day reads 0 without
 * any explicit reset.
 */
class DailyBudgetStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): DailyBudget = DailyBudget(
        limit = prefs.getInt(KEY_LIMIT, DailyBudget.DEFAULT_LIMIT),
        usedToday = prefs.getInt(usedKey(today()), 0),
    )

    fun save(budget: DailyBudget) {
        prefs.edit()
            .putInt(KEY_LIMIT, budget.limit)
            .putInt(usedKey(today()), budget.usedToday)
            .apply()
    }

    private fun usedKey(date: String): String = "$PREFIX_USED$date"

    private fun today(): String = DATE_FORMAT.get().format(Date())

    private companion object {
        const val PREFS_NAME = "agent_budget"
        const val KEY_LIMIT = "limit"
        const val PREFIX_USED = "used_"

        // SimpleDateFormat is not thread-safe; the store may be hit from
        // both the UI and the agent's IO work.
        val DATE_FORMAT: ThreadLocal<SimpleDateFormat> =
            ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    }
}
