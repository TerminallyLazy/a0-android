package com.terminallylazy.a0android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.terminallylazy.a0android.web.ServerOrigin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.profileData by preferencesDataStore("servers")

data class ServerProfile(val id: String, val name: String, val origin: ServerOrigin) {
    val browserId: String get() = "a0_$id"
}
data class ProfileState(val profiles: List<ServerProfile> = emptyList(), val selectedId: String? = null) {
    val selected: ServerProfile? get() = profiles.find { it.id == selectedId }
}

class ServerProfileStore(context: Context) {
    private val data = context.applicationContext.profileData
    private val profilesKey = stringPreferencesKey("profiles_v1")
    private val selectedKey = stringPreferencesKey("selected_v1")
    val state: Flow<ProfileState> = data.data.map { preferences ->
        val profiles = try {
            val array = JSONArray(preferences[profilesKey] ?: "[]")
            (0 until array.length()).mapNotNull { index ->
                val entry = array.getJSONObject(index)
                val id = entry.getString("id")
                val origin = ServerOrigin.parse(entry.getString("origin")) ?: return@mapNotNull null
                if (!id.matches(Regex("[a-f0-9-]{36}"))) return@mapNotNull null
                ServerProfile(id, entry.getString("name").take(80), origin)
            }
        } catch (_: org.json.JSONException) { emptyList() }
        ProfileState(profiles, preferences[selectedKey])
    }

    suspend fun save(name: String, origin: ServerOrigin, existing: ServerProfile? = null): ServerProfile {
        // Changing origins creates a fresh cookie/storage boundary.
        val profile = ServerProfile(
            if (existing?.origin == origin) existing.id else UUID.randomUUID().toString(),
            name.trim().take(80).ifEmpty { origin.url.host }, origin,
        )
        data.edit { preferences ->
            val array = JSONArray(preferences[profilesKey] ?: "[]")
            val updated = JSONArray()
            for (i in 0 until array.length()) {
                val entry = array.getJSONObject(i)
                if (entry.getString("id") != existing?.id) updated.put(entry)
            }
            updated.put(JSONObject().put("id", profile.id).put("name", profile.name).put("origin", origin.value))
            preferences[profilesKey] = updated.toString()
            preferences[selectedKey] = profile.id
        }
        return profile
    }

    suspend fun select(id: String) { data.edit { it[selectedKey] = id } }

    suspend fun forget(id: String) {
        data.edit { preferences ->
            val array = JSONArray(preferences[profilesKey] ?: "[]")
            val remaining = JSONArray()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                if (item.getString("id") != id) remaining.put(item)
            }
            preferences[profilesKey] = remaining.toString()
            if (preferences[selectedKey] == id) preferences.remove(selectedKey)
        }
    }
}
