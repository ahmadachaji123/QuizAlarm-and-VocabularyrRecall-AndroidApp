package com.example.langalarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "decks")
data class Deck(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val isActive: Boolean = false // Only one deck can be active at a time
)