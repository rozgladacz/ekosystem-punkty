package pl.rozgladacz.ekosystempunkty.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreEngineTest {
    @Test
    fun `local adjacency categories and eagle use official distances`() {
        val first = grid(
            0 to CardType.EAGLE,
            1 to CardType.RABBIT,
            2 to CardType.TROUT,
            5 to CardType.FOX,
            6 to CardType.BEAR,
            7 to CardType.BEE,
            8 to CardType.MEADOW,
            9 to CardType.MEADOW,
            10 to CardType.DRAGONFLY,
            11 to CardType.STREAM,
            12 to CardType.STREAM,
            13 to CardType.TROUT,
            15 to CardType.DEER,
            19 to CardType.DEER,
        )
        val score = ScoreEngine.score(listOf(player("a", first), player("b", filled(CardType.RABBIT))))
            .players.first()
            .categories.associate { it.category to it.points }

        assertEquals(4, score[ScoreCategory.EAGLES])
        assertEquals(2, score[ScoreCategory.DRAGONFLIES])
        assertEquals(2, score[ScoreCategory.TROUT])
        assertEquals(0, score[ScoreCategory.FOXES])
        assertEquals(2, score[ScoreCategory.BEARS])
        assertEquals(3, score[ScoreCategory.BEES])
        assertEquals(6, score[ScoreCategory.DEER])
        assertEquals(3, score[ScoreCategory.MEADOWS])
    }

    @Test
    fun `dragonfly counts the same adjacent stream component once`() {
        val board = grid(
            0 to CardType.DRAGONFLY,
            1 to CardType.STREAM,
            5 to CardType.STREAM,
            6 to CardType.STREAM,
        )
        val score = ScoreEngine.score(listOf(player("a", board), player("b", filled(CardType.RABBIT))))
            .players.first().categories.single { it.category == ScoreCategory.DRAGONFLIES }
        assertEquals(3, score.points)
    }

    @Test
    fun `wolf ties use competition ranking and consume places`() {
        val players = listOf(
            player("a", withCount(CardType.WOLF, 3)),
            player("b", withCount(CardType.WOLF, 3)),
            player("c", withCount(CardType.WOLF, 2)),
            player("d", withCount(CardType.WOLF, 1)),
        )
        val scores = ScoreEngine.score(players).players.associate { score ->
            score.playerId to score.categories.single { it.category == ScoreCategory.WOLVES }.points
        }
        assertEquals(mapOf("a" to 12, "b" to 12, "c" to 4, "d" to 0), scores)
    }

    @Test
    fun `stream first-place tie removes second place`() {
        val players = listOf(
            player("a", streamOfLength(4)),
            player("b", streamOfLength(4)),
            player("c", streamOfLength(3)),
        )
        val scores = ScoreEngine.score(players).players.associate { score ->
            score.playerId to score.categories.single { it.category == ScoreCategory.STREAMS }.points
        }
        assertEquals(mapOf("a" to 8, "b" to 8, "c" to 0), scores)
    }

    @Test
    fun `gaps count categories that exist but score zero`() {
        val players = listOf(player("a", filled(CardType.FOX)), player("b", filled(CardType.WOLF)))
        val result = ScoreEngine.score(players).players.first()
        assertEquals(-5, result.categories.single { it.category == ScoreCategory.GAPS }.points)
    }

    @Test
    fun `winners include every player tied on total`() {
        val same = filled(CardType.RABBIT)
        val result = ScoreEngine.score(listOf(player("a", same), player("b", same)))
        assertEquals(setOf("a", "b"), result.winningPlayerIds)
        assertTrue(result.players.all { it.total == result.players.first().total })
    }

    @Test
    fun `complete board matches manually calculated score sheet`() {
        val board = BoardGrid.complete(
            listOf(
                CardType.RABBIT, CardType.EAGLE, CardType.RABBIT, CardType.FOX, CardType.WOLF,
                CardType.TROUT, CardType.STREAM, CardType.DRAGONFLY, CardType.BEAR, CardType.BEE,
                CardType.MEADOW, CardType.MEADOW, CardType.DEER, CardType.STREAM, CardType.STREAM,
                CardType.MEADOW, CardType.WOLF, CardType.DEER, CardType.BEE, CardType.FOX,
            ),
        )
        val result = ScoreEngine.score(listOf(player("a", board), player("b", filled(CardType.RABBIT))))
            .players.first()
        val scores = result.categories.associate { it.category to it.points }

        assertEquals(2, scores[ScoreCategory.RABBITS])
        assertEquals(3, scores[ScoreCategory.FOXES])
        assertEquals(1, scores[ScoreCategory.DRAGONFLIES])
        assertEquals(2, scores[ScoreCategory.TROUT])
        assertEquals(2, scores[ScoreCategory.BEARS])
        assertEquals(0, scores[ScoreCategory.BEES])
        assertEquals(6, scores[ScoreCategory.EAGLES])
        assertEquals(6, scores[ScoreCategory.DEER])
        assertEquals(12, scores[ScoreCategory.WOLVES])
        assertEquals(8, scores[ScoreCategory.STREAMS])
        assertEquals(6, scores[ScoreCategory.MEADOWS])
        assertEquals(12, scores[ScoreCategory.GAPS])
        assertEquals(60, result.total)
    }

    @Test
    fun `five connected meadows reach the group cap`() {
        val board = grid(
            0 to CardType.MEADOW,
            1 to CardType.MEADOW,
            2 to CardType.MEADOW,
            3 to CardType.MEADOW,
            4 to CardType.MEADOW,
        )
        val points = ScoreEngine.score(listOf(player("a", board), player("b", filled(CardType.RABBIT))))
            .players.first().categories.single { it.category == ScoreCategory.MEADOWS }.points
        assertEquals(15, points)
    }

    @Test
    fun `eagle ignores prey outside Manhattan range two`() {
        val cells = MutableList(BoardGrid.CELL_COUNT) { CardType.DEER }
        cells[0] = CardType.EAGLE
        cells[2] = CardType.TROUT
        cells[3] = CardType.RABBIT
        val board = BoardGrid.complete(cells)
        val points = ScoreEngine.score(listOf(player("a", board), player("b", filled(CardType.DEER))))
            .players.first().categories.single { it.category == ScoreCategory.EAGLES }.points
        assertEquals(2, points)
    }

    @Test
    fun `correcting a card recalculates the game`() {
        val before = ScoreEngine.score(
            listOf(player("a", filled(CardType.RABBIT)), player("b", filled(CardType.RABBIT))),
        ).players.first()
        val corrected = filled(CardType.RABBIT).withCell(0, CardType.WOLF)
        val after = ScoreEngine.score(
            listOf(player("a", corrected), player("b", filled(CardType.RABBIT))),
        ).players.first()

        assertEquals(20, before.categories.single { it.category == ScoreCategory.RABBITS }.points)
        assertEquals(19, after.categories.single { it.category == ScoreCategory.RABBITS }.points)
        assertEquals(12, after.categories.single { it.category == ScoreCategory.WOLVES }.points)
        assertTrue(before.total != after.total)
    }

    private fun player(id: String, grid: BoardGrid) = PlayerBoard(id, id, grid)

    private fun filled(type: CardType): BoardGrid = BoardGrid.complete(List(BoardGrid.CELL_COUNT) { type })

    private fun grid(vararg cards: Pair<Int, CardType>): BoardGrid {
        val cells = MutableList(BoardGrid.CELL_COUNT) { CardType.RABBIT }
        cards.forEach { (index, type) -> cells[index] = type }
        return BoardGrid.complete(cells)
    }

    private fun withCount(type: CardType, count: Int): BoardGrid =
        BoardGrid.complete(List(BoardGrid.CELL_COUNT) { if (it < count) type else CardType.RABBIT })

    private fun streamOfLength(length: Int): BoardGrid =
        BoardGrid.complete(List(BoardGrid.CELL_COUNT) { if (it < length) CardType.STREAM else CardType.RABBIT })
}
