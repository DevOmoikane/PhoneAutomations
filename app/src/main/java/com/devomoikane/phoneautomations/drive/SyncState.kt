package com.devomoikane.phoneautomations.drive

import org.json.JSONArray
import org.json.JSONObject

class SyncState private constructor(
    private val entries: MutableMap<String, EntryMeta>
) {

    data class EntryMeta(
        val localVersion: Long,
        val remoteMtime: Long,
        val driveId: String?,
        val isDir: Boolean
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("localVersion", localVersion)
            .put("remoteMtime", remoteMtime)
            .put("driveId", driveId)
            .put("dir", isDir)

        companion object {
            fun fromJson(json: JSONObject): EntryMeta =
                EntryMeta(
                    localVersion = json.optLong("localVersion", -1L),
                    remoteMtime = json.optLong("remoteMtime", -1L),
                    driveId = json.takeUnless { it.isNull("driveId") }?.optString("driveId"),
                    isDir = json.optBoolean("dir", false)
                )
        }
    }

    operator fun get(path: String): EntryMeta? = entries[path]

    operator fun set(path: String, meta: EntryMeta) {
        entries[path] = meta
    }

    fun remove(path: String) {
        entries.remove(path)
    }

    fun toJson(): JSONObject {
        val array = JSONArray()
        entries.forEach { (path, meta) ->
            array.put(JSONObject().put("path", path).put("meta", meta.toJson()))
        }
        return JSONObject().put("entries", array)
    }

    companion object {
        fun empty(): SyncState = SyncState(mutableMapOf())

        fun fromJson(json: JSONObject): SyncState {
            val entries = mutableMapOf<String, EntryMeta>()
            val array = json.optJSONArray("entries") ?: JSONArray()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val path = item.optString("path", "") ?: ""
                val metaJson = item.optJSONObject("meta") ?: continue
                entries[path] = EntryMeta.fromJson(metaJson)
            }
            return SyncState(entries)
        }
    }
}
