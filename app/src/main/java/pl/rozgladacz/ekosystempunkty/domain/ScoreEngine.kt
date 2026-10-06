package pl.rozgladacz.ekosystempunkty.domain

import java.util.ArrayDeque

object ScoreEngine {
    private val localCategories = listOf(
        ScoreCategory.RABBITS,
        ScoreCategory.FOXES,
        ScoreCategory.DRAGONFLIES,
        ScoreCategory.TROUT,
        ScoreCategory.BEARS,
        ScoreCategory.BEES,
        ScoreCategory.EAGLES,
        ScoreCategory.DEER,
        ScoreCategory.MEADOWS,
    )

    fun score(players: List<PlayerBoard>): GameScore {
        require(players.size in 2..6) { "Gra obsługuje od 2 do 6 graczy" }
        require(players.all { it.grid.isComplete }) { "Wszystkie plansze muszą być uzupełnione" }

        val baseScores = players.associate { it.id to localScore(it.grid) }
        val wolfCounts = players.associate { it.id to it.grid.cells.count { card -> card == CardType.WOLF } }
        val streamLengths = players.associate { it.id to connectedComponents(it.grid, CardType.STREAM).maxOfOrNull { c -> c.size }.orZero() }
        val wolfScores = competitionScores(wolfCounts, mapOf(1 to 12, 2 to 8, 3 to 4))
        val streamScores = competitionScores(streamLengths, mapOf(1 to 8, 2 to 5))

        return GameScore(players.map { player ->
            val scores = buildMap {
                putAll(baseScores.getValue(player.id))
                put(ScoreCategory.WOLVES, wolfScores.getValue(player.id))
                put(ScoreCategory.STREAMS, streamScores.getValue(player.id))
            }
            val gaps = ScoreCategory.entries
                .filterNot { it == ScoreCategory.GAPS }
                .count { scores.getValue(it) == 0 }
            val gapPoints = when {
                gaps >= 6 -> -5
                gaps == 5 -> 0
                gaps == 4 -> 3
                gaps == 3 -> 7
                else -> 12
            }
            val ordered = ScoreCategory.entries.map { category ->
                CategoryScore(category, if (category == ScoreCategory.GAPS) gapPoints else scores.getValue(category))
            }
            PlayerScore(player.id, player.name, ordered, ordered.sumOf { it.points })
        })
    }

    private fun localScore(grid: BoardGrid): Map<ScoreCategory, Int> {
        val streamComponents = connectedComponents(grid, CardType.STREAM)
        val streamComponentByCell = buildMap<Int, Set<Int>> {
            streamComponents.forEach { component -> component.forEach { put(it, component) } }
        }

        return buildMap {
            put(ScoreCategory.RABBITS, grid.cells.count { it == CardType.RABBIT })
            put(ScoreCategory.FOXES, indicesOf(grid, CardType.FOX).sumOf { index ->
                if (neighbors(index).none { grid.cells[it] == CardType.WOLF || grid.cells[it] == CardType.BEAR }) 3 else 0
            })
            put(ScoreCategory.DRAGONFLIES, indicesOf(grid, CardType.DRAGONFLY).sumOf { index ->
                neighbors(index)
                    .mapNotNull { streamComponentByCell[it] }
                    .distinctBy { it.minOrNull() }
                    .sumOf { it.size }
            })
            put(ScoreCategory.TROUT, indicesOf(grid, CardType.TROUT).sumOf { index ->
                neighbors(index).count { grid.cells[it] == CardType.STREAM || grid.cells[it] == CardType.DRAGONFLY } * 2
            })
            put(ScoreCategory.BEARS, indicesOf(grid, CardType.BEAR).sumOf { index ->
                neighbors(index).count { grid.cells[it] == CardType.BEE || grid.cells[it] == CardType.TROUT } * 2
            })
            put(ScoreCategory.BEES, indicesOf(grid, CardType.BEE).sumOf { index ->
                neighbors(index).count { grid.cells[it] == CardType.MEADOW } * 3
            })
            put(ScoreCategory.EAGLES, indicesOf(grid, CardType.EAGLE).sumOf { eagle ->
                grid.cells.indices.count { prey ->
                    (grid.cells[prey] == CardType.RABBIT || grid.cells[prey] == CardType.TROUT) &&
                        manhattanDistance(eagle, prey) <= 2
                } * 2
            })
            put(ScoreCategory.DEER, run {
                val deer = indicesOf(grid, CardType.DEER)
                (deer.map { it / BoardGrid.COLUMNS }.distinct().size +
                    deer.map { it % BoardGrid.COLUMNS }.distinct().size) * 2
            })
            put(ScoreCategory.MEADOWS, connectedComponents(grid, CardType.MEADOW).sumOf { component ->
                when (component.size) {
                    1 -> 0
                    2 -> 3
                    3 -> 6
                    4 -> 10
                    else -> 15
                }
            })
        }.also { require(it.keys.containsAll(localCategories)) }
    }

    private fun competitionScores(
        values: Map<String, Int>,
        pointsByRank: Map<Int, Int>,
    ): Map<String, Int> = values.mapValues { (_, value) ->
        if (value <= 0) return@mapValues 0
        val rank = 1 + values.values.count { it > value }
        pointsByRank[rank] ?: 0
    }

    private fun connectedComponents(grid: BoardGrid, type: CardType): List<Set<Int>> {
        val remaining = indicesOf(grid, type).toMutableSet()
        val result = mutableListOf<Set<Int>>()
        while (remaining.isNotEmpty()) {
            val start = remaining.first()
            val queue = ArrayDeque<Int>().apply { add(start) }
            val component = mutableSetOf<Int>()
            remaining.remove(start)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                component += current
                neighbors(current).filter { it in remaining }.forEach {
                    remaining.remove(it)
                    queue.add(it)
                }
            }
            result += component
        }
        return result
    }

    private fun indicesOf(grid: BoardGrid, type: CardType): List<Int> =
        grid.cells.indices.filter { grid.cells[it] == type }

    private fun neighbors(index: Int): List<Int> {
        val row = index / BoardGrid.COLUMNS
        val column = index % BoardGrid.COLUMNS
        return buildList {
            if (row > 0) add(index - BoardGrid.COLUMNS)
            if (row < BoardGrid.ROWS - 1) add(index + BoardGrid.COLUMNS)
            if (column > 0) add(index - 1)
            if (column < BoardGrid.COLUMNS - 1) add(index + 1)
        }
    }

    private fun manhattanDistance(first: Int, second: Int): Int =
        kotlin.math.abs(first / BoardGrid.COLUMNS - second / BoardGrid.COLUMNS) +
            kotlin.math.abs(first % BoardGrid.COLUMNS - second % BoardGrid.COLUMNS)

    private fun Int?.orZero(): Int = this ?: 0
}

