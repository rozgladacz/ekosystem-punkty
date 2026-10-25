package pl.rozgladacz.ekosystempunkty.domain

/** Ranks every choice, including suggestions that still require review. */
object CardSuggestions {
    fun orderedTypes(cell: RecognitionCell): List<CardType> {
        if (cell.probabilities.size == CardType.entries.size) {
            return CardType.entries.sortedWith(
                compareByDescending<CardType> { cell.probabilities[it.ordinal] }
                    .thenBy { it.ordinal },
            )
        }
        val selected = cell.type ?: return CardType.entries.toList()
        return listOf(selected) + CardType.entries.filterNot { it == selected }
    }
}
