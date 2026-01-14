package com.example.langalarm.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {
    @Transaction
    @Query("SELECT d.*, (SELECT COUNT(*) FROM words WHERE deckId = d.id) as wordCount FROM decks d")
    fun getDecksWithWordCount(): Flow<List<DeckWithWordCount>>

    @Query("SELECT * FROM decks")
    fun getAllDecks(): Flow<List<Deck>>

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun getDeckById(id: Int): Deck?

    @Query("SELECT * FROM decks WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveDeck(): Deck?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeck(deck: Deck): Long

    @Query("UPDATE decks SET isActive = 0")
    suspend fun deactivateAllDecks()

    @Query("UPDATE decks SET isActive = 1 WHERE id = :deckId")
    suspend fun activateDeck(deckId: Int)

    @Transaction
    suspend fun setActiveDeck(deckId: Int) {
        deactivateAllDecks()
        activateDeck(deckId)
    }

    @Delete
    suspend fun deleteDeck(deck: Deck)

    @Update
    suspend fun updateDeck(deck: Deck)
}