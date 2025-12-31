package com.example.langalarm

fun pickWeightedWord(words: List<WordItem>, recentWords: List<WordItem> = emptyList()): WordItem {
    // Filter out recently shown words
    val availableWords = words.filter { it !in recentWords }
    if (availableWords.isEmpty()) return words.random() // fallback if all words are in buffer

    val totalWeight = availableWords.sumOf { it.weight + 1 } // +1 to avoid zero weight
    var r = (1..totalWeight).random()

    for (word in availableWords) {
        r -= (word.weight + 1)
        if (r <= 0) return word
    }

    return availableWords.last() // fallback
}

