package com.example.langalarm

import android.content.Context
import android.net.Uri
import com.example.langalarm.data.AppDatabase
import com.example.langalarm.data.Deck
import com.example.langalarm.data.WordEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object WordRepository {

    // --- Legacy Support for UI ---
    suspend fun loadActiveWords(context: Context): List<WordItem> {
        val db = AppDatabase.getDatabase(context)
        val activeDeck = db.deckDao().getActiveDeck() ?: return loadLegacyWords(context)
        
        val entities = db.wordDao().getWordsByDeckIdSync(activeDeck.id)
        if (entities.isEmpty()) return loadLegacyWords(context)

        return entities.map { entity ->
            WordItem(
                question = entity.question,
                answer = entity.answer,
                weight = entity.weight
            )
        }
    }

    private suspend fun loadLegacyWords(context: Context): List<WordItem> {
        return withContext(Dispatchers.IO) {
            try {
                val json = context.assets.open("words.json").bufferedReader().use { it.readText() }
                val type = object : TypeToken<List<WordItem>>() {}.type
                val items: List<WordItem> = Gson().fromJson(json, type)
                
                val db = AppDatabase.getDatabase(context)
                if (db.deckDao().getActiveDeck() == null) {
                    val deckId = db.deckDao().insertDeck(Deck(name = "Default Deck", isActive = true)).toInt()
                    val entities = items.map { 
                        // Set default weight to 5 for legacy words
                        WordEntity(deckId = deckId, question = it.question, answer = it.answer, weight = 5) 
                    }
                    db.wordDao().insertWords(entities)
                }
                items.onEach { it.weight = 5 } // Ensure legacy items also have the default weight
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun saveProgress(context: Context, words: List<WordItem>) {
        val db = AppDatabase.getDatabase(context)
        val activeDeck = db.deckDao().getActiveDeck() ?: return
        
        val currentEntities = db.wordDao().getWordsByDeckIdSync(activeDeck.id)
        
        currentEntities.forEach { entity ->
            val updatedWord = words.find { it.question == entity.question }
            if (updatedWord != null && updatedWord.weight != entity.weight) {
                db.wordDao().updateWord(entity.copy(weight = updatedWord.weight))
            }
        }
    }
    
    // --- Deck Management ---

    fun getAllDecks(context: Context): Flow<List<Deck>> {
        return AppDatabase.getDatabase(context).deckDao().getAllDecks()
    }

    suspend fun createDeck(context: Context, name: String) {
        AppDatabase.getDatabase(context).deckDao().insertDeck(Deck(name = name))
    }
    
    suspend fun deleteDeck(context: Context, deck: Deck) {
        val db = AppDatabase.getDatabase(context)
        db.deckDao().deleteDeck(deck)
    }

    suspend fun setActiveDeck(context: Context, deckId: Int) {
        AppDatabase.getDatabase(context).deckDao().setActiveDeck(deckId)
    }

    suspend fun renameDeck(context: Context, deck: Deck, newName: String) {
        AppDatabase.getDatabase(context).deckDao().updateDeck(deck.copy(name = newName))
    }

    fun getDeckWords(context: Context, deckId: Int): Flow<List<WordEntity>> {
        return AppDatabase.getDatabase(context).wordDao().getWordsByDeckId(deckId)
    }

    // --- Word Management ---

    suspend fun addWord(context: Context, deckId: Int, question: String, answer: String, weight: Int = 5) {
        AppDatabase.getDatabase(context).wordDao().insertWord(
            WordEntity(deckId = deckId, question = question, answer = answer, weight = weight)
        )
    }

    suspend fun deleteWord(context: Context, word: WordEntity) {
        AppDatabase.getDatabase(context).wordDao().deleteWord(word)
    }

    suspend fun updateWord(context: Context, word: WordEntity) {
        AppDatabase.getDatabase(context).wordDao().updateWord(word)
    }

    // --- CSV Import/Export ---

    suspend fun importCsvToDeck(context: Context, deckId: Int, uri: Uri) {
        withContext(Dispatchers.IO) {
            val contentResolver = context.contentResolver
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val words = mutableListOf<WordEntity>()
                    reader.lineSequence().forEach { line ->
                        val parts = line.split(",")
                        if (parts.size >= 2) {
                            val question = parts[0].trim()
                            val answer = parts[1].trim()
                            
                            // Check for weight in the 3rd column, default to 5
                            var weight = 5
                            if (parts.size >= 3) {
                                weight = parts[2].trim().toIntOrNull() ?: 5
                            }
                            // Enforce weight limits (0-10)
                            weight = weight.coerceIn(0, 10)

                            if (question.isNotEmpty() && answer.isNotEmpty()) {
                                words.add(WordEntity(deckId = deckId, question = question, answer = answer, weight = weight))
                            }
                        }
                    }
                    AppDatabase.getDatabase(context).wordDao().insertWords(words)
                }
            }
        }
    }

    suspend fun exportDeckToCsv(context: Context, deckId: Int, uri: Uri) {
        withContext(Dispatchers.IO) {
            val words = AppDatabase.getDatabase(context).wordDao().getWordsByDeckIdSync(deckId)
            val contentResolver = context.contentResolver
            
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.bufferedWriter().use { writer ->
                    words.forEach { word ->
                        writer.write("${word.question},${word.answer},${word.weight}\n")
                    }
                }
            }
        }
    }
}