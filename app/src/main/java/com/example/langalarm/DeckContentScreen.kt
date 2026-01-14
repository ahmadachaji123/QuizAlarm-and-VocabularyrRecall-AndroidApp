package com.example.langalarm

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
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
import java.text.Collator
import java.util.Locale

enum class SortType {
    Alphabetical,
    AlphabeticalReverse,
    Weight,
    WeightReverse,
    Newest,
    Oldest
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeckContentScreen(
    deckId: Int,
    deckName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var words by remember { mutableStateOf<List<WordEntity>>(emptyList()) }
    var sortType by remember { mutableStateOf(SortType.Newest) }
    var selectionMode by remember { mutableStateOf(false) }
    val selectedWords = remember { mutableStateListOf<WordEntity>() }

    // Dialog States
    var showAddDialog by remember { mutableStateOf(false) }
    var wordToEdit by remember { mutableStateOf<WordEntity?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(deckId) {
        WordRepository.getDeckWords(context, deckId).collect {
            words = it
        }
    }

    val sortedWords = remember(words, sortType) {
        val collator = Collator.getInstance(Locale.getDefault())
        when (sortType) {
            SortType.Alphabetical -> words.sortedWith(compareBy(collator) { it.question })
            SortType.AlphabeticalReverse -> words.sortedWith(compareByDescending(collator) { it.question })
            SortType.Weight -> words.sortedBy { it.weight }
            SortType.WeightReverse -> words.sortedByDescending { it.weight }
            SortType.Newest -> words.sortedByDescending { it.createdAt }
            SortType.Oldest -> words.sortedBy { it.createdAt }
        }
    }

    fun clearSelection() {
        selectedWords.clear()
        selectionMode = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (selectionMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { clearSelection() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close selection")
                    }
                    Text("${selectedWords.size} selected")
                }
                Row {
                    IconButton(onClick = { 
                        selectedWords.clear()
                        selectedWords.addAll(words)
                    }) {
                        Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                    }
                    IconButton(onClick = { 
                        if (selectedWords.isNotEmpty()) {
                            showDeleteConfirmDialog = true
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                    }
                }
            } else {
                Text(text = "Deck: $deckName", style = MaterialTheme.typography.headlineSmall)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        if (!selectionMode) {
            Text(
                text = "Note: Higher weight means the word will appear more frequently in quizzes. Weight must be between 0 and 10.",
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        SortDropdown(sortType) { sortType = it }

        Spacer(modifier = Modifier.height(16.dp))

        if (sortedWords.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("This deck is empty.")
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(sortedWords) { word ->
                    val isSelected = selectedWords.contains(word)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .combinedClickable(
                                onClick = {
                                    if (selectionMode) {
                                        if (isSelected) selectedWords.remove(word) else selectedWords.add(word)
                                    } else {
                                        wordToEdit = word
                                    }
                                },
                                onLongClick = {
                                    if (!selectionMode) {
                                        selectionMode = true
                                        selectedWords.add(word)
                                    }
                                }
                            ),
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
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

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Confirm Deletion") },
            text = { Text("Are you sure you want to delete ${selectedWords.size} selected words? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        val wordsToDelete = selectedWords.toList()
                        scope.launch {
                            WordRepository.deleteWords(context, wordsToDelete)
                        }
                        showDeleteConfirmDialog = false
                        clearSelection()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SortDropdown(sortType: SortType, onSortChange: (SortType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("Sort by: ${sortType.name}")
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Sort")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortType.values().forEach { type ->
                DropdownMenuItem(onClick = { 
                    onSortChange(type)
                    expanded = false
                }, text = { Text(type.name) })
            }
        }
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
