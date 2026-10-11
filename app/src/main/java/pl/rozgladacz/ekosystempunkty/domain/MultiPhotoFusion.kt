package pl.rozgladacz.ekosystempunkty.domain

/** Łączy odpowiadające sobie pola kilku zdjęć tego samego, niezmienionego układu. */
object MultiPhotoFusion {
    private const val MIN_CONFIDENCE = 0.85f
    private const val MIN_VISUAL_QUALITY = 0.65f

    fun merge(
        shots: List<List<RecognitionCell>>,
        manualOverrides: Map<Int, CardType> = emptyMap(),
    ): List<RecognitionCell> {
        require(shots.isNotEmpty() && shots.all { it.size == BoardGrid.CELL_COUNT })
        require(manualOverrides.keys.all { it in 0 until BoardGrid.CELL_COUNT })
        return List(BoardGrid.CELL_COUNT) { index ->
            manualOverrides[index]?.let { return@List RecognitionCell(it, 1f) }
            val observations = shots.map { it[index] }
            val usable = observations.filter {
                it.type != null && it.confidence >= MIN_CONFIDENCE &&
                    it.visualQuality >= MIN_VISUAL_QUALITY
            }
            val types = usable.mapNotNull { it.type }.toSet()
            if (types.size == 1) {
                val best = usable.maxBy { it.confidence * it.visualQuality }
                RecognitionCell(best.type, best.confidence, best.visualQuality)
            } else {
                // Brak wiarygodnego ujęcia albo sprzeczne odczyty: decyzja należy do użytkownika.
                RecognitionCell(null, 0f)
            }
        }
    }
}
