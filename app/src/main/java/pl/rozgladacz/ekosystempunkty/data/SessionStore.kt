package pl.rozgladacz.ekosystempunkty.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import pl.rozgladacz.ekosystempunkty.domain.BoardGrid
import pl.rozgladacz.ekosystempunkty.domain.CardType
import pl.rozgladacz.ekosystempunkty.domain.PlayerBoard

private val Context.sessionDataStore by preferencesDataStore("current_game")

class SessionStore(private val context: Context) {
    private val sessionKey = stringPreferencesKey("session")

    suspend fun load(): List<PlayerBoard>? {
        val raw = context.sessionDataStore.data.first()[sessionKey] ?: return null
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val encodedGrid = item.optJSONArray("grid")
                PlayerBoard(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    grid = if (encodedGrid == null) BoardGrid.empty() else BoardGrid(
                        List(BoardGrid.CELL_COUNT) { cell ->
                            encodedGrid.optString(cell).takeIf { it.isNotBlank() }?.let(CardType::valueOf)
                        },
                    ),
                )
            }
        }.getOrNull()
    }

    suspend fun save(players: List<PlayerBoard>) {
        val array = JSONArray()
        players.forEach { player ->
            array.put(JSONObject().apply {
                put("id", player.id)
                put("name", player.name)
                put("grid", JSONArray().apply {
                    player.grid.cells.forEach { put(it?.name ?: "") }
                })
            })
        }
        context.sessionDataStore.edit { it[sessionKey] = array.toString() }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.remove(sessionKey) }
    }
}

