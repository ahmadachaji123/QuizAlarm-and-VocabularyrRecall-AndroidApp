package com.example.langalarm

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object SoundRepository {

    private const val PREFS_NAME = "sound_prefs"
    private const val ACTIVE_SOUND_KEY = "active_sound_uri"
    private const val CUSTOM_SOUNDS_KEY = "custom_sounds"
    const val DEFAULT_SOUND = "default_sound"

    // --- Sound Preferences ---

    fun getActiveSoundUri(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(ACTIVE_SOUND_KEY, DEFAULT_SOUND) ?: DEFAULT_SOUND
    }

    fun setActiveSound(context: Context, uriString: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(ACTIVE_SOUND_KEY, uriString).apply()
    }

    fun getCustomSounds(context: Context): List<Pair<String, String>> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val soundSet = prefs.getStringSet(CUSTOM_SOUNDS_KEY, emptySet()) ?: emptySet()
        return soundSet.mapNotNull { 
            val parts = it.split("|")
            if (parts.size == 2) parts[0] to parts[1] else null // Pair of (DisplayName, UriString)
        }
    }

    fun addCustomSound(context: Context, name: String, uriString: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentSounds = prefs.getStringSet(CUSTOM_SOUNDS_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        currentSounds.add("$name|$uriString")
        prefs.edit().putStringSet(CUSTOM_SOUNDS_KEY, currentSounds).apply()
    }

    fun removeCustomSound(context: Context, uriString: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentSounds = prefs.getStringSet(CUSTOM_SOUNDS_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        val soundToRemove = currentSounds.find { it.endsWith("|$uriString") }
        if (soundToRemove != null) {
            currentSounds.remove(soundToRemove)
            prefs.edit().putStringSet(CUSTOM_SOUNDS_KEY, currentSounds).apply()
        }
    }

    fun renameCustomSound(context: Context, uriString: String, newName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentSounds = prefs.getStringSet(CUSTOM_SOUNDS_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        val soundToRename = currentSounds.find { it.endsWith("|$uriString") }
        if (soundToRename != null) {
            currentSounds.remove(soundToRename)
            currentSounds.add("$newName|$uriString")
            prefs.edit().putStringSet(CUSTOM_SOUNDS_KEY, currentSounds).apply()
        }
    }

    // --- File Management ---

    suspend fun copySoundToInternalStorage(context: Context, sourceUri: Uri): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return@withContext null
                val fileName = "custom_sound_${System.currentTimeMillis()}.ogg"
                val outputFile = File(context.filesDir, fileName)
                val outputStream = outputFile.outputStream()

                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }

                Uri.fromFile(outputFile)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}