package dev.handypage.app

import android.content.Context
import dev.handypage.app.engine.SourceConfig
import org.json.JSONObject

/** Loads the bundled source configs from `assets/sources/`. */
object Sources {

    fun loadAll(context: Context): List<SourceConfig> {
        val names = context.assets.list("sources").orEmpty()
            .filter { it.endsWith(".json") }
            .sorted()
        return names.map { fileName ->
            val text = context.assets.open("sources/$fileName").bufferedReader().use { it.readText() }
            SourceConfig.fromJson(JSONObject(text))
        }
    }

    fun load(context: Context, id: String): SourceConfig =
        loadAll(context).firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("no bundled source with id '$id'")
}
