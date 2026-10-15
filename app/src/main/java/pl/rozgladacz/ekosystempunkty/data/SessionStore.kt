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
                    developmentGroupId = item.optString("developmentGroupId").takeIf { it.isNotBlank() },
                    cardProbabilities = item.optJSONArray("cardProbabilities")?.let { scores ->
                        if (scores.length() != BoardGrid.CELL_COUNT) emptyList() else {
                            List(BoardGrid.CELL_COUNT) { cell ->
                                scores.optJSONArray(cell)?.takeIf { it.length() == CardType.entries.size }?.let { row ->
                                    List(CardType.entries.size) { type ->
                                        row.optDouble(type, 0.0).toFloat().takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
                                    }
                                }.orEmpty()
                            }
                        }
                    }.orEmpty(),
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
                player.developmentGroupId?.let { put("developmentGroupId", it) }
                if (player.cardProbabilities.size == BoardGrid.CELL_COUNT) {
                    put("cardProbabilities", JSONArray().apply {
                        player.cardProbabilities.forEach { scores ->
                            put(JSONArray().apply { scores.forEach { put(it.toDouble()) } })
                        }
                    })
                }
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

