package com.example.langalarm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.langalarm.data.WordEntity
import kotlinx.coroutines.launch

@Composable
fun DeckContentScreen(
    deckId: Int,
    deckName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var words by remember { mutableStateOf<List<WordEntity>>(emptyList()) }

    // Dialog States
    var showAddDialog by remember { mutableStateOf(false) }
    var wordToEdit by remember { mutableStateOf<WordEntity?>(null) }
    var wordToDelete by remember { mutableStateOf<WordEntity?>(null) }

    LaunchedEffect(deckId) {
        WordRepository.getDeckWords(context, deckId).collect {
            words = it
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Removed explicit Back button as requested
            Text(text = "Deck: $deckName", style = MaterialTheme.typography.headlineSmall)
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Note: Higher weight means the word will appear more frequently in quizzes. Weight must be between 0 and 10.",
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (words.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("This deck is empty.")
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(words) { word ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { wordToEdit = word },
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Q: ${word.question}", style = MaterialTheme.typography.titleMedium)
                                Text(text = "A: ${word.answer}", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "Weight: ${word.weight}", 
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { wordToDelete = word }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add New Word")
        }
    }

    if (showAddDialog) {
        WordDialog(
            title = "Add Word",
            initialQuestion = "",
            initialAnswer = "",
            initialWeight = 5,
            onDismiss = { showAddDialog = false },
            onSave = { q, a, w ->
                scope.launch { WordRepository.addWord(context, deckId, q, a, w) }
                showAddDialog = false
            }
        )
    }

    if (wordToEdit != null) {
        WordDialog(
            title = "Edit Word",
            initialQuestion = wordToEdit!!.question,
            initialAnswer = wordToEdit!!.answer,
            initialWeight = wordToEdit!!.weight,
            onDismiss = { wordToEdit = null },
            onSave = { q, a, w ->
                val updatedWord = wordToEdit!!.copy(question = q, answer = a, weight = w)
                scope.launch { WordRepository.updateWord(context, updatedWord) }
                wordToEdit = null
            }
        )
    }

    val currentWordToDelete = wordToDelete
    if (currentWordToDelete != null) {
        AlertDialog(
            onDismissRequest = { wordToDelete = null },
            title = { Text("Delete Word?") },
            text = { Text("Are you sure you want to delete this word? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch { WordRepository.deleteWord(context, currentWordToDelete) }
                        wordToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { wordToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun WordDialog(
    title: String,
    initialQuestion: String,
    initialAnswer: String,
    initialWeight: Int,
    onDismiss: () -> Unit,
    onSave: (String, String, Int) -> Unit
) {
    var question by remember { mutableStateOf(initialQuestion) }
    var answer by remember { mutableStateOf(initialAnswer) }
    var weightText by remember { mutableStateOf(initialWeight.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Question (e.g., German Word)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text("Answer (e.g., English Translation)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text("Weight (0-10)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Higher weight = more frequent. Range: 0-10.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (question.isNotBlank() && answer.isNotBlank()) {
                    // Enforce range 0-10
                    val w = (weightText.toIntOrNull() ?: 1).coerceIn(0, 10)
                    onSave(question, answer, w)
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}