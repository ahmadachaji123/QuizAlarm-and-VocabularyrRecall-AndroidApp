package com.example.langalarm.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "words",
    foreignKeys = [
        ForeignKey(
            entity = Deck::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["deckId"])]
)
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val deckId: Int,
    val question: String,
    val answer: String,
    var weight: Int = 1
)