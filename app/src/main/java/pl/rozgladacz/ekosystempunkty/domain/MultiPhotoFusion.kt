package pl.rozgladacz.ekosystempunkty.domain

/** Łączy odpowiadające sobie pola kilku zdjęć tego samego, niezmienionego układu. */
object MultiPhotoFusion {
    private const val MIN_VISUAL_QUALITY = 0.65f

    fun merge(
        shots: List<List<RecognitionCell>>,
        manualOverrides: Map<Int, CardType> = emptyMap(),
    ): List<RecognitionCell> {
        require(shots.isNotEmpty() && shots.all { it.size == BoardGrid.CELL_COUNT })
        require(manualOverrides.keys.all { it in 0 until BoardGrid.CELL_COUNT })
        return List(BoardGrid.CELL_COUNT) { index ->
            val observations = shots.map { it[index] }
            val scored = observations.filter { it.probabilities.size == CardType.entries.size }
            val clear = scored.filter { it.visualQuality >= MIN_VISUAL_QUALITY }
            val rankingSources = clear.ifEmpty { scored }
            val probabilities = if (rankingSources.isEmpty()) emptyList() else {
                val totalWeight = rankingSources.sumOf { it.visualQuality.coerceAtLeast(0.05f).toDouble() }
                List(CardType.entries.size) { typeIndex ->
                    (rankingSources.sumOf { observation ->
                        (observation.probabilities[typeIndex] * observation.visualQuality.coerceAtLeast(0.05f)).toDouble()
                    } / totalWeight).toFloat()
                }
            }
            val usable = observations.filter {
                it.type != null && it.confidence >= RecognitionCell.HIGH_CONFIDENCE &&
                    it.visualQuality >= MIN_VISUAL_QUALITY
            }
            val types = usable.mapNotNull { it.type }.toSet()
            val merged = if (types.size == 1) {
                val best = usable.maxBy { it.confidence * it.visualQuality }
                RecognitionCell(best.type, best.confidence, best.visualQuality, probabilities)
            } else {
                // Keep the best suggestion visible, while glare or disagreement
                // still requires review even if its numerical score is high.
                val bestIndex = probabilities.indices.maxByOrNull { probabilities[it] }
                    ?.takeIf { probabilities[it].isFinite() && probabilities[it] > 0f }
                val bestObservation = observations.filter {
                    it.type != null && it.confidence.isFinite() && it.confidence > 0f
                }.maxByOrNull { it.confidence * it.visualQuality }
                RecognitionCell(
                    type = bestIndex?.let { CardType.entries[it] } ?: bestObservation?.type,
                    confidence = bestIndex?.let { probabilities[it] } ?: bestObservation?.confidence ?: 0f,
                    visualQuality = rankingSources.maxOfOrNull { it.visualQuality }
                        ?: bestObservation?.visualQuality ?: 1f,
                    probabilities = probabilities,
                    reviewRequired = true,
                )
            }
            manualOverrides[index]?.let { merged.copy(type = it, confidence = 1f, reviewRequired = false) } ?: merged
        }
    }
}
