package com.example.langalarm

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SoundManagerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var customSounds by remember { mutableStateOf(SoundRepository.getCustomSounds(context)) }
    var activeSound by remember { mutableStateOf(SoundRepository.getActiveSoundUri(context)) }
    
    // State for media player and dialogs
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var currentlyPlaying by remember { mutableStateOf<String?>(null) }
    var showNameDialogForUri by remember { mutableStateOf<Uri?>(null) }
    var soundToRename by remember { mutableStateOf<Pair<String, String>?>(null) }
    var soundToDelete by remember { mutableStateOf<Pair<String, String>?>(null) }

    val soundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { showNameDialogForUri = it }
    }
    
    // Function to play a sound
    fun playSound(uriString: String) {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        
        val soundUri = if (uriString == SoundRepository.DEFAULT_SOUND) {
            Uri.parse("android.resource://${context.packageName}/${R.raw.alarm}")
        } else {
            Uri.parse(uriString)
        }

        try {
            mediaPlayer = MediaPlayer.create(context, soundUri).apply {
                setOnCompletionListener { 
                    currentlyPlaying = null 
                }
                start()
            }
            currentlyPlaying = uriString
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot play sound", Toast.LENGTH_SHORT).show()
        }
    }

    // Function to stop the sound
    fun stopSound() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        currentlyPlaying = null
    }

    // Clean up media player when the screen is left
    DisposableEffect(Unit) {
        onDispose { stopSound() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Removed explicit Back button
            Text("Manage Sounds", style = MaterialTheme.typography.headlineSmall)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sound List
        LazyColumn(modifier = Modifier.weight(1f)) {
            // Default Sound
            item {
                SoundItem(
                    name = "Default Alarm Sound",
                    uriString = SoundRepository.DEFAULT_SOUND,
                    isActive = activeSound == SoundRepository.DEFAULT_SOUND,
                    isPlaying = currentlyPlaying == SoundRepository.DEFAULT_SOUND,
                    onPlay = { playSound(SoundRepository.DEFAULT_SOUND) },
                    onStop = { stopSound() },
                    onSetActive = {
                        SoundRepository.setActiveSound(context, SoundRepository.DEFAULT_SOUND)
                        activeSound = SoundRepository.DEFAULT_SOUND
                    },
                    isCustom = false
                )
            }

            // Custom Sounds
            items(customSounds) { (name, uriString) ->
                SoundItem(
                    name = name,
                    uriString = uriString,
                    isActive = activeSound == uriString,
                    isPlaying = currentlyPlaying == uriString,
                    onPlay = { playSound(uriString) },
                    onStop = { stopSound() },
                    onSetActive = {
                        SoundRepository.setActiveSound(context, uriString)
                        activeSound = uriString
                    },
                    onRenameRequest = { soundToRename = (name to uriString) },
                    onDelete = { soundToDelete = (name to uriString) },
                    isCustom = true
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { soundPickerLauncher.launch(arrayOf("audio/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Custom Sound")
        }
    }

    // Dialog for naming new sound
    if (showNameDialogForUri != null) {
        NameSoundDialog(
            title = "Name Your Sound",
            initialName = showNameDialogForUri?.lastPathSegment?.substringBeforeLast('.') ?: "",
            onDismiss = { showNameDialogForUri = null },
            onSave = { name ->
                val uri = showNameDialogForUri!!
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                
                SoundRepository.addCustomSound(context, name, uri.toString())
                customSounds = SoundRepository.getCustomSounds(context)
                showNameDialogForUri = null
            }
        )
    }

    // Dialog for renaming sound
    if (soundToRename != null) {
        NameSoundDialog(
            title = "Rename Sound",
            initialName = soundToRename!!.first,
            onDismiss = { soundToRename = null },
            onSave = { newName ->
                SoundRepository.renameCustomSound(context, soundToRename!!.second, newName)
                customSounds = SoundRepository.getCustomSounds(context)
                soundToRename = null
            }
        )
    }

    // Dialog for confirming deletion
    val currentSoundToDelete = soundToDelete
    if (currentSoundToDelete != null) {
        AlertDialog(
            onDismissRequest = { soundToDelete = null },
            title = { Text("Delete Sound?") },
            text = { Text("Are you sure you want to delete '${currentSoundToDelete.first}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        val uriString = currentSoundToDelete.second
                        if (activeSound == uriString) { // If deleting active sound, reset to default
                            SoundRepository.setActiveSound(context, SoundRepository.DEFAULT_SOUND)
                            activeSound = SoundRepository.DEFAULT_SOUND
                        }
                        SoundRepository.removeCustomSound(context, uriString)
                        customSounds = SoundRepository.getCustomSounds(context)
                        soundToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { soundToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SoundItem(
    name: String,
    uriString: String,
    isActive: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onSetActive: () -> Unit,
    onRenameRequest: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    isCustom: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Play/Stop button
            IconButton(onClick = { if (isPlaying) onStop() else onPlay() }) {
                Icon(if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = "Play/Stop")
            }

            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            
            Row {
                if (!isActive) {
                    IconButton(onClick = onSetActive) {
                        Icon(Icons.Default.Check, contentDescription = "Set as Active")
                    }
                }
                if (isCustom) {
                    IconButton(onClick = { onRenameRequest?.invoke() }) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename")
                    }
                    IconButton(onClick = { onDelete?.invoke() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}

@Composable
fun NameSoundDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Sound Name") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { if (text.isNotBlank()) onSave(text) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}