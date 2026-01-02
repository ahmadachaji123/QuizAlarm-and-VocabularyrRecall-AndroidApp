package com.example.langalarm

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.langalarm.data.Deck
import kotlinx.coroutines.launch

@Composable
fun DeckManagerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // State for decks
    var decks by remember { mutableStateOf<List<Deck>>(emptyList()) }
    
    // State for viewing a specific deck
    var viewingDeck by remember { mutableStateOf<Deck?>(null) }
    
    // Load decks
    LaunchedEffect(Unit) {
        WordRepository.getAllDecks(context).collect {
            decks = it
        }
    }

    // Handle back press when viewing a deck
    BackHandler(enabled = viewingDeck != null) {
        viewingDeck = null
    }

    if (viewingDeck != null) {
        DeckContentScreen(
            deckId = viewingDeck!!.id,
            deckName = viewingDeck!!.name,
            onBack = { viewingDeck = null }
        )
    } else {
        var showAddDialog by remember { mutableStateOf(false) }
        var deckToRename by remember { mutableStateOf<Deck?>(null) }
        var deckToDelete by remember { mutableStateOf<Deck?>(null) }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Removed explicit Back button
                Text("Manage Decks", style = MaterialTheme.typography.headlineSmall)
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(decks) { deck ->
                    DeckItem(
                        deck = deck,
                        onActivate = { 
                            scope.launch { WordRepository.setActiveDeck(context, deck.id) }
                        },
                        onDeleteRequest = {
                            deckToDelete = deck
                        },
                        onRenameRequest = {
                            deckToRename = deck
                        },
                        onViewContent = {
                            viewingDeck = deck
                        },
                        onImport = { uri ->
                            scope.launch { 
                                WordRepository.importCsvToDeck(context, deck.id, uri) 
                                Toast.makeText(context, "Imported!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onExport = {
                            // Handled inside DeckItem via launcher
                        }
                    )
                }
            }

            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create New Deck")
            }
        }

        if (showAddDialog) {
            AddDeckDialog(
                title = "New Deck",
                initialName = "",
                onDismiss = { showAddDialog = false },
                onSave = { name ->
                    scope.launch { WordRepository.createDeck(context, name) }
                    showAddDialog = false
                }
            )
        }

        if (deckToRename != null) {
            AddDeckDialog(
                title = "Rename Deck",
                initialName = deckToRename!!.name,
                onDismiss = { deckToRename = null },
                onSave = { newName ->
                    val currentDeck = deckToRename
                    if (currentDeck != null) {
                        scope.launch { WordRepository.renameDeck(context, currentDeck, newName) }
                    }
                    deckToRename = null
                }
            )
        }

        // Capture the deck to delete in a local variable to prevent race condition
        val currentDeckToDelete = deckToDelete
        if (currentDeckToDelete != null) {
            AlertDialog(
                onDismissRequest = { deckToDelete = null },
                title = { Text("Delete Deck?") },
                text = { Text("Are you sure you want to delete '${currentDeckToDelete.name}'? This action cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch { WordRepository.deleteDeck(context, currentDeckToDelete) }
                            deckToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deckToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun DeckItem(
    deck: Deck,
    onActivate: () -> Unit,
    onDeleteRequest: () -> Unit,
    onRenameRequest: () -> Unit,
    onViewContent: () -> Unit,
    onImport: (Uri) -> Unit,
    onExport: () -> Unit
) {
    val context = LocalContext.current
    val isDefault = deck.name == "Default Deck"
    
    // Launcher for Import CSV
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onImport(it) }
    }
    
    // Launcher for Export CSV (Create Document)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let { 
             val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
             scope.launch {
                 WordRepository.exportDeckToCsv(context, deck.id, it)
                 kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                     Toast.makeText(context, "Exported successfully!", Toast.LENGTH_SHORT).show()
                 }
             }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (deck.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = deck.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isDefault) FontWeight.Bold else FontWeight.Normal
                    )
                    
                    if (isDefault) {
                        Text(
                            text = " (Default)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    if (deck.isActive) {
                        Text(
                            text = " (Active)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    
                    // Rename (Edit) Icon is now here, next to the name
                    IconButton(onClick = onRenameRequest) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename")
                    }
                }

                if (!deck.isActive) {
                    IconButton(onClick = onActivate) {
                        Icon(Icons.Default.Check, contentDescription = "Activate")
                    }
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { importLauncher.launch(arrayOf("text/*", "text/csv")) }) {
                    Text("Import CSV")
                }
                
                TextButton(onClick = { exportLauncher.launch("${deck.name}.csv") }) {
                    Text("Export CSV")
                }
                
                // View Content (Info) Icon is now here in the button row
                IconButton(onClick = onViewContent) {
                    Icon(Icons.Default.Info, contentDescription = "View Content")
                }

                // Delete Button (Disabled for Default Deck)
                IconButton(
                    onClick = onDeleteRequest,
                    enabled = !isDefault
                ) {
                    Icon(
                        Icons.Default.Delete, 
                        contentDescription = "Delete",
                        tint = if (isDefault) Color.Gray else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun AddDeckDialog(
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
                label = { Text("Deck Name") }
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