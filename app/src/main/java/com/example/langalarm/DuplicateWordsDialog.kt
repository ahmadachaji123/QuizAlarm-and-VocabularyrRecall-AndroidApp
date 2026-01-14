package com.example.langalarm

import androidx.compose.foundation.layout.* 
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.* 
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.langalarm.data.WordEntity

@Composable
fun DuplicateWordsDialog(
    duplicates: List<DuplicateWord>,
    onDismiss: () -> Unit,
    onConfirm: (List<DuplicateWord>) -> Unit
) {
    val resolutions = remember { mutableStateListOf<DuplicateWord>().apply { addAll(duplicates) } }

    fun applyToAll(action: DuplicateAction) {
        val newResolutions = resolutions.map { it.copy(action = action) }
        resolutions.clear()
        resolutions.addAll(newResolutions)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Duplicates Found") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { applyToAll(DuplicateAction.Skip) }) { Text("Skip All") }
                    Button(onClick = { applyToAll(DuplicateAction.Overwrite) }) { Text("Overwrite All") }
                    Button(onClick = { applyToAll(DuplicateAction.KeepBoth) }) { Text("Keep All") }
                }
                LazyColumn {
                    itemsIndexed(resolutions) { index, resolution ->
                        DuplicateItem(resolution) { newResolution ->
                            resolutions[index] = newResolution
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(resolutions) }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DuplicateItem(resolution: DuplicateWord, onResolutionChange: (DuplicateWord) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Question: ${resolution.newWord.question}", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Existing: ${resolution.existingWord.answer} (Weight: ${resolution.existingWord.weight})")
            Text("New: ${resolution.newWord.answer} (Weight: ${resolution.newWord.weight})")
            Spacer(modifier = Modifier.height(8.dp))
            Box {
                OutlinedButton(onClick = { expanded = true }) {
                    Text(resolution.action.name)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DuplicateAction.values().forEach { action ->
                        DropdownMenuItem(onClick = {
                            onResolutionChange(resolution.copy(action = action))
                            expanded = false
                        }, text = { Text(action.name) })
                    }
                }
            }
        }
    }
}