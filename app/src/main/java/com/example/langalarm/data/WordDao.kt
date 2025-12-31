package com.example.langalarm.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Query("SELECT * FROM words WHERE deckId = :deckId")
    fun getWordsByDeckId(deckId: Int): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE deckId = :deckId")
    suspend fun getWordsByDeckIdSync(deckId: Int): List<WordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: WordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<WordEntity>)

    @Query("DELETE FROM words WHERE deckId = :deckId")
    suspend fun deleteWordsByDeckId(deckId: Int)

    @Delete
    suspend fun deleteWord(word: WordEntity)

    @Update
    suspend fun updateWord(word: WordEntity)
}