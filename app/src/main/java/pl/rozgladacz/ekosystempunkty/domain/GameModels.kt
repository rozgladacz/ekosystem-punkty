package pl.rozgladacz.ekosystempunkty.domain

enum class CardType(val polishName: String, val shortName: String) {
    RABBIT("Zając", "ZA"),
    FOX("Lis", "LI"),
    DRAGONFLY("Ważka", "WA"),
    TROUT("Pstrąg", "PS"),
    BEAR("Niedźwiedź", "NI"),
    BEE("Pszczoła", "PZ"),
    EAGLE("Bielik", "BI"),
    DEER("Jeleń", "JE"),
    WOLF("Wilk", "WI"),
    STREAM("Potok", "PO"),
    MEADOW("Łąka", "ŁĄ"),
}

data class BoardGrid(val cells: List<CardType?>) {
    init {
        require(cells.size == CELL_COUNT) { "Plansza musi zawierać dokładnie $CELL_COUNT pól" }
    }

    operator fun get(row: Int, column: Int): CardType? = cells[row * COLUMNS + column]

    fun withCell(index: Int, type: CardType?): BoardGrid =
        copy(cells = cells.toMutableList().also { it[index] = type })

    val isComplete: Boolean get() = cells.none { it == null }

    companion object {
        const val ROWS = 4
        const val COLUMNS = 5
        const val CELL_COUNT = ROWS * COLUMNS

        fun empty(): BoardGrid = BoardGrid(List(CELL_COUNT) { null })
        fun complete(cells: List<CardType>): BoardGrid = BoardGrid(cells)
    }
}

data class PlayerBoard(
    val id: String,
    val name: String,
    val grid: BoardGrid,
)

data class RecognitionCell(
    val type: CardType?,
    val confidence: Float,
)

data class NormalizedPoint(val x: Float, val y: Float)

data class RecognitionResult(
    val cells: List<RecognitionCell>,
    val corners: List<NormalizedPoint>,
    val boardConfidence: Float,
) {
    init {
        require(cells.size == BoardGrid.CELL_COUNT)
        require(corners.size == 4)
    }

    val grid: BoardGrid get() = BoardGrid(cells.map { it.type })
}

enum class ScoreCategory(val polishName: String) {
    RABBITS("Zające"),
    FOXES("Lisy"),
    DRAGONFLIES("Ważki"),
    TROUT("Pstrągi"),
    BEARS("Niedźwiedzie"),
    BEES("Pszczoły"),
    EAGLES("Bieliki"),
    DEER("Jelenie"),
    WOLVES("Wilki"),
    STREAMS("Potoki"),
    MEADOWS("Łąki"),
    GAPS("Luki w ekosystemie"),
}

data class CategoryScore(val category: ScoreCategory, val points: Int)

data class PlayerScore(
    val playerId: String,
    val playerName: String,
    val categories: List<CategoryScore>,
    val total: Int,
)

data class GameScore(val players: List<PlayerScore>) {
    val winningPlayerIds: Set<String> by lazy {
        val maximum = players.maxOfOrNull { it.total } ?: return@lazy emptySet()
        players.filter { it.total == maximum }.mapTo(mutableSetOf()) { it.playerId }
    }
}

