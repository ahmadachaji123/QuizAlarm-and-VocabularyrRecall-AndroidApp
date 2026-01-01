package com.example.langalarm

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun FlashCardSetupScreen(
    onStartSession: (Int, Int) -> Unit, // Int is target, -1 for open ended, second Int is max attempts
    onBack: () -> Unit
) {
    var goalText by remember { mutableStateOf("10") }
    var isOpenEnded by remember { mutableStateOf(false) }
    var maxAttemptsText by remember { mutableStateOf("2") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Flash Cards Practice", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        if (!isOpenEnded) {
            OutlinedTextField(
                value = goalText,
                onValueChange = { goalText = it },
                label = { Text("Target Correct Answers") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = isOpenEnded,
                onCheckedChange = { isOpenEnded = it }
            )
            Text("Open Goal (Practice indefinitely)")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = maxAttemptsText,
            onValueChange = { maxAttemptsText = it },
            label = { Text("Max Wrong Attempts (Before Skip)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Min: 1",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Start).padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                val target = if (isOpenEnded) -1 else (goalText.toIntOrNull() ?: 10)
                val maxAttempts = (maxAttemptsText.toIntOrNull() ?: 2).coerceAtLeast(1)
                onStartSession(target, maxAttempts)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Session")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = onBack) {
            Text("Back")
        }
    }
}

@Composable
fun FlashCardSessionScreen(
    words: List<WordItem>,
    targetCorrect: Int, // -1 for open ended
    maxWrongAttempts: Int,
    onSessionCompleted: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val recentWords = remember { mutableStateListOf<WordItem>() }
    var correctCount by remember { mutableStateOf(0) }
    var currentWord by remember { mutableStateOf(pickWeightedWord(words, recentWords)) }
    
    var userAnswer by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    
    var consecutiveWrongCount by remember { mutableStateOf(0) }

    if (targetCorrect != -1 && correctCount >= targetCorrect) {
        // Goal Achieved Screen
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Congrats!", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("You achieved your goal of $targetCorrect correct answers.")
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onSessionCompleted) {
                Text("Finish")
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("Exit") }
                if (targetCorrect != -1) {
                    Text("Progress: $correctCount / $targetCorrect")
                } else {
                    Text("Score: $correctCount")
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            if (consecutiveWrongCount > 0) {
                val remaining = maxWrongAttempts - consecutiveWrongCount
                val remainingText = if (remaining > 0) "$remaining trial(s) left" else "Last trial!"
                Text(
                    text = remainingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            Text(
                text = currentWord.question,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            OutlinedTextField(
                value = userAnswer,
                onValueChange = { userAnswer = it },
                label = { Text("Your answer") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (userAnswer.trim().equals(currentWord.answer, ignoreCase = true)) {
                        result = "Correct ✅"
                        correctCount++
                        
                        currentWord.weight = (currentWord.weight - 1).coerceAtLeast(0)
                        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                        scope.launch { WordRepository.saveProgress(context, words) }

                        recentWords.add(currentWord)
                        if (recentWords.size > 3) recentWords.removeAt(0)
                        
                        consecutiveWrongCount = 0

                        if (targetCorrect == -1 || correctCount < targetCorrect) {
                            currentWord = pickWeightedWord(words, recentWords)
                            userAnswer = ""
                        }
                    } else {
                        consecutiveWrongCount++
                        
                        if (consecutiveWrongCount >= maxWrongAttempts) {
                            // Max Attempts reached
                            // Penalize score only if in Open Goal mode (targetCorrect == -1)
                            if (targetCorrect == -1) {
                                correctCount--
                                // Ensure score doesn't drop below 0
                                if (correctCount < 0) correctCount = 0
                            }

                            // Skip logic
                            val skippedAnswer = currentWord.answer
                            val skippedQuestion = currentWord.question
                            
                            currentWord.weight = (currentWord.weight + 1).coerceAtMost(10)
                            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                            scope.launch { WordRepository.saveProgress(context, words) }
                            
                            recentWords.add(currentWord)
                            if (recentWords.size > 3) recentWords.removeAt(0)
                            
                            // Retry logic to pick new word
                            val nextWord = pickWeightedWord(words, recentWords)
                            var retries = 0
                            var tempWord = nextWord
                            while (tempWord == currentWord && words.size > 1 && retries < 10) {
                                tempWord = pickWeightedWord(words, recentWords)
                                retries++
                            }
                            
                            currentWord = tempWord
                            userAnswer = ""
                            consecutiveWrongCount = 0
                            result = "Skipped: '$skippedQuestion' was '$skippedAnswer'"
                        } else {
                            result = "Wrong ❌ Try again"
                            currentWord.weight = (currentWord.weight + 2).coerceAtMost(10)
                            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                            scope.launch { WordRepository.saveProgress(context, words) }
                        }
                    }
                },
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth()
            ) {
                Text("Submit")
            }

            result?.let {
                Text(
                    text = it,
                    color = if (it.contains("Correct") || it.contains("Skipped")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}
