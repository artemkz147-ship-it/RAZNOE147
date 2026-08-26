package ru.vibecut.editor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object ProjectBackup {
    private const val PREFS = "vibecut_projects_v1"
    private const val INDEX_KEY = "project_index"
    private const val PROJECT_PREFIX = "project_"

    fun exportProject(context: Context, projectId: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PROJECT_PREFIX + projectId, null)

    fun importProject(context: Context, raw: String): String? {
        val source = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (!source.has("clips")) return null

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val newId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val originalName = source.optString("name", "Импортированный проект").ifBlank { "Импортированный проект" }
        source.put("id", newId)
        source.put("name", "$originalName · импорт")
        source.put("createdAt", now)
        source.put("updatedAt", now)
        source.put("selectedId", JSONObject.NULL)

        prefs.edit().putString(PROJECT_PREFIX + newId, source.toString()).apply()

        val index = runCatching {
            JSONArray(prefs.getString(INDEX_KEY, "[]") ?: "[]")
        }.getOrElse { JSONArray() }
        val ids = mutableListOf<String>()
        for (i in 0 until index.length()) {
            index.optString(i).takeIf { it.isNotBlank() }?.let(ids::add)
        }
        if (newId !in ids) ids += newId
        val newIndex = JSONArray().apply { ids.distinct().forEach(::put) }
        prefs.edit().putString(INDEX_KEY, newIndex.toString()).apply()

        return ProjectStore.load(context, newId)?.id ?: run {
            prefs.edit().remove(PROJECT_PREFIX + newId).apply()
            val rollback = JSONArray().apply { ids.filterNot { it == newId }.forEach(::put) }
            prefs.edit().putString(INDEX_KEY, rollback.toString()).apply()
            null
        }
    }
}
