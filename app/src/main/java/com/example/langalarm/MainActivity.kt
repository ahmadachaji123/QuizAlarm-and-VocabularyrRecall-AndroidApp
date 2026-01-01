package com.example.langalarm

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.langalarm.ui.theme.LangalarmTheme
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private val alarms = mutableStateListOf<Alarm>()
    private var wordsState = mutableStateOf<List<WordItem>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure screen turns on and stays on for the alarm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        
        // This is still needed for older versions or as a fallback
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
        scope.launch {
            wordsState.value = WordRepository.loadActiveWords(this@MainActivity)
        }

        enableEdgeToEdge()
        loadAlarms()
        checkNotificationPermission()

        // Handle the intent that started this activity
        handleIntent(intent)

        setContent {
            LangalarmTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.systemBars
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        // Check if we are in "Quiz Mode" from the intent extras
                        val startQuizNow = intent.getBooleanExtra("START_QUIZ", false)
                        
                        if (startQuizNow) {
                            startQuiz()
                        } else {
                            var showSettings by remember { mutableStateOf(false) }
                            var showDeckManager by remember { mutableStateOf(false) }
                            var showSoundManager by remember { mutableStateOf(false) }
                            var showAlarmEditor by remember { mutableStateOf(false) }
                            var editingAlarm by remember { mutableStateOf<Alarm?>(null) }
                            
                            // State for Flash Cards
                            var showFlashCardSetup by remember { mutableStateOf(false) }
                            var showFlashCardSession by remember { mutableStateOf(false) }
                            var flashCardTarget by remember { mutableStateOf(10) }
                            var flashCardMaxAttempts by remember { mutableStateOf(2) }
                            
                            val scope = rememberCoroutineScope()
                            
                            // Back Navigation Logic
                            BackHandler(enabled = showSettings || showDeckManager || showSoundManager || showAlarmEditor || showFlashCardSetup || showFlashCardSession) {
                                if (showAlarmEditor) {
                                    showAlarmEditor = false
                                } else if (showSettings) {
                                    showSettings = false
                                } else if (showDeckManager) {
                                    showDeckManager = false
                                    // Reload words when coming back from deck manager in case deck changed
                                    scope.launch { wordsState.value = WordRepository.loadActiveWords(this@MainActivity) }
                                } else if (showSoundManager) {
                                    showSoundManager = false
                                } else if (showFlashCardSession) {
                                    showFlashCardSession = false
                                    showFlashCardSetup = true // Go back to setup, or main? Usually setup.
                                } else if (showFlashCardSetup) {
                                    showFlashCardSetup = false
                                }
                            }

                            when {
                                showDeckManager -> DeckManagerScreen(onBack = { 
                                    showDeckManager = false 
                                    // Reload words when coming back from deck manager
                                    scope.launch { wordsState.value = WordRepository.loadActiveWords(this@MainActivity) }
                                }) 
                                showSoundManager -> SoundManagerScreen(onBack = { showSoundManager = false })
                                showFlashCardSession -> FlashCardSessionScreen(
                                    words = wordsState.value,
                                    targetCorrect = flashCardTarget,
                                    maxWrongAttempts = flashCardMaxAttempts,
                                    onSessionCompleted = { showFlashCardSession = false; showFlashCardSetup = false },
                                    onBack = { showFlashCardSession = false; showFlashCardSetup = true }
                                )
                                showFlashCardSetup -> FlashCardSetupScreen(
                                    onStartSession = { target, maxAttempts ->
                                        flashCardTarget = target
                                        flashCardMaxAttempts = maxAttempts
                                        showFlashCardSession = true
                                    },
                                    onBack = { showFlashCardSetup = false }
                                )
                                else -> {
                                    MainScreen(
                                        alarms = alarms,
                                        onAddAlarmClick = {
                                            if (checkExactAlarmPermission()) {
                                                editingAlarm = null
                                                showAlarmEditor = true
                                            }
                                        },
                                        onEditAlarmClick = { alarm ->
                                            if (checkExactAlarmPermission()) {
                                                editingAlarm = alarm
                                                showAlarmEditor = true
                                            }
                                        },
                                        onToggleAlarm = { alarm, isEnabled ->
                                            val updatedAlarm = alarm.copy(isEnabled = isEnabled)
                                            updateAlarmInList(updatedAlarm)
                                            AlarmScheduler.schedule(this@MainActivity, updatedAlarm)
                                            AlarmRepository.updateAlarm(this@MainActivity, updatedAlarm)
                                        },
                                        onDeleteAlarmClick = { alarm -> deleteAlarm(alarm) },
                                        onSettingsClick = { showSettings = true },
                                        onDecksClick = { showDeckManager = true },
                                        onSoundsClick = { showSoundManager = true },
                                        onFlashCardsClick = { 
                                            // Reload words before starting flash cards to ensure latest deck
                                            scope.launch { wordsState.value = WordRepository.loadActiveWords(this@MainActivity) }
                                            showFlashCardSetup = true 
                                        }
                                    )

                                    if (showSettings) {
                                        SettingsDialog(
                                            onDismiss = { showSettings = false },
                                            context = this@MainActivity,
                                            onForceStop = {
                                                stopAlarmService()
                                                Toast.makeText(this@MainActivity, "Alarm sound stopped", Toast.LENGTH_SHORT).show()
                                                showSettings = false
                                            }
                                        )
                                    }

                                    if (showAlarmEditor) {
                                        AlarmEditorDialog(
                                            initialAlarm = editingAlarm,
                                            onDismiss = { showAlarmEditor = false },
                                            onConfirm = { alarm ->
                                                showAlarmEditor = false
                                                if (editingAlarm == null) {
                                                    // New Alarm
                                                    alarms.add(alarm)
                                                    AlarmScheduler.schedule(this@MainActivity, alarm)
                                                    AlarmRepository.saveAlarms(this@MainActivity, alarms)
                                                } else {
                                                    // Edit Existing
                                                    updateAlarmInList(alarm)
                                                    AlarmScheduler.schedule(this@MainActivity, alarm)
                                                    AlarmRepository.updateAlarm(this@MainActivity, alarm)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Update the intent for the activity
        handleIntent(intent)
        // Re-compose the content to reflect the new intent state (e.g. switch to quiz mode)
        setContent {
            LangalarmTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.systemBars
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        val startQuizNow = intent.getBooleanExtra("START_QUIZ", false)
                        if (startQuizNow) {
                             startQuiz()
                        } else {
                            // Fallback to normal UI if needed, or recreate the MainScreen logic here
                            // For simplicity, reusing the logic from onCreate but we must ensure state is preserved
                             var showSettings by remember { mutableStateOf(false) }
                             // ... rest of the main screen logic ...
                             // A better approach for a real app would be to hoist this state to a ViewModel
                             // For this fix, we will just reload the content
                             
                             // Since we are inside onNewIntent, we can just trigger a recomposition or rely on state.
                             // However, Compose doesn't automatically recompose on intent change unless we observe something.
                             // The simplest way here is to let setContent re-run.
                             val mainActivity = this@MainActivity
                             mainActivity.recreate() // Force recreation to handle the new intent cleanly in onCreate
                        }
                    }
                }
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("START_QUIZ", false) == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            }
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }
    
    private fun updateAlarmInList(updatedAlarm: Alarm) {
        val index = alarms.indexOfFirst { it.id == updatedAlarm.id }
        if (index != -1) {
            alarms[index] = updatedAlarm
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun checkExactAlarmPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(this, "Permission required: Alarms & Reminders", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
                return false
            }
        }
        return true
    }

    private fun deleteAlarm(alarm: Alarm) {
        AlarmScheduler.cancel(this, alarm)
        alarms.remove(alarm)
        AlarmRepository.saveAlarms(this, alarms)
    }

    private fun loadAlarms() {
        val loaded = AlarmRepository.loadAlarms(this)
        alarms.clear()
        alarms.addAll(loaded)
    }

    private fun stopAlarmService() {
        val intent = Intent(this, AlarmService::class.java)
        intent.action = "STOP_ALARM"
        startService(intent)
    }

    @Composable
    private fun startQuiz() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val requiredCorrect = prefs.getInt("required_correct", 3) 
        val bufferSize = prefs.getInt("buffer_size", 3)
        // Load max trials from settings, default 2
        val maxTrials = prefs.getInt("max_trials", 2)

        val currentWords = wordsState.value
        if (currentWords.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
                LaunchedEffect(Unit) {
                    wordsState.value = WordRepository.loadActiveWords(this@MainActivity)
                }
            }
        } else {
            LanguageQuestionScreen(
                words = currentWords,
                requiredCorrect = requiredCorrect,
                bufferSize = bufferSize,
                maxWrongAttempts = maxTrials,
                onQuizCompleted = {
                    stopAlarmService()
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    // If we were started just for the quiz, finish. Otherwise go back to main.
                    // Checking if we are the task root is a heuristic.
                    finish()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorDialog(
    initialAlarm: Alarm?,
    onDismiss: () -> Unit,
    onConfirm: (Alarm) -> Unit
) {
    val calendar = Calendar.getInstance()
    var initialHour = initialAlarm?.hour ?: calendar.get(Calendar.HOUR_OF_DAY)
    var initialMinute = initialAlarm?.minute ?: calendar.get(Calendar.MINUTE)

    val timeState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )
    
    val selectedDays = remember { mutableStateListOf<Int>().apply {
        if (initialAlarm != null) addAll(initialAlarm.days)
    }}

    val allDays = setOf(
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
        Calendar.SATURDAY,
        Calendar.SUNDAY
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { 
                val alarm = initialAlarm?.copy(
                    hour = timeState.hour,
                    minute = timeState.minute,
                    days = selectedDays.toSet()
                ) ?: Alarm(
                    hour = timeState.hour,
                    minute = timeState.minute,
                    days = selectedDays.toSet()
                )
                onConfirm(alarm) 
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Used TimePicker (Clock Face) instead of TimeInput to provide a graphical interface
                TimePicker(state = timeState)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text("Repeat", style = MaterialTheme.typography.titleSmall, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(8.dp))
                
                // Everyday button
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selectedDays.size == 7,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                selectedDays.clear()
                                selectedDays.addAll(allDays)
                            } else {
                                selectedDays.clear()
                            }
                        }
                    )
                    Text(text = "Everyday")
                }

                // Days selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val days = listOf(
                        "M" to Calendar.MONDAY,
                        "T" to Calendar.TUESDAY,
                        "W" to Calendar.WEDNESDAY,
                        "T" to Calendar.THURSDAY,
                        "F" to Calendar.FRIDAY,
                        "S" to Calendar.SATURDAY,
                        "S" to Calendar.SUNDAY
                    )
                    
                    days.forEach { (label, dayConst) ->
                        val isSelected = selectedDays.contains(dayConst)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    if (isSelected) selectedDays.remove(dayConst)
                                    else selectedDays.add(dayConst)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit, 
    context: Context,
    onForceStop: () -> Unit
) {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    var requiredCorrectText by remember { mutableStateOf(prefs.getInt("required_correct", 3).toString()) }
    var bufferSizeText by remember { mutableStateOf(prefs.getInt("buffer_size", 3).toString()) }
    var maxTrialsText by remember { mutableStateOf(prefs.getInt("max_trials", 2).toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                // Required Correct Answers
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = requiredCorrectText,
                        onValueChange = { requiredCorrectText = it },
                        label = { Text("Required Correct Answers") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Min: 1",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Max Trials (Moved to 2nd Position)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = maxTrialsText,
                        onValueChange = { maxTrialsText = it },
                        label = { Text("Max Wrong Attempts") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Min: 1",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Text(
                    text = "Number of wrong attempts allowed before skipping to the next word.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start).padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Buffer Size (Moved to 3rd Position)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = bufferSizeText,
                        onValueChange = { bufferSizeText = it },
                        label = { Text("Word Buffer Size") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Min: 3",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Text(
                    text = "The buffer size determines how many unique words must be shown before a word can repeat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start).padding(top = 4.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Contact info
                Text(
                    text = "Contact: ahmadachaji188@gmail.com",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(onClick = {
                        val correct = requiredCorrectText.toIntOrNull() ?: 3
                        val buffer = bufferSizeText.toIntOrNull() ?: 3
                        val trials = maxTrialsText.toIntOrNull() ?: 2
                        
                        val finalCorrect = correct.coerceAtLeast(1)
                        val finalBuffer = buffer.coerceAtLeast(3)
                        val finalTrials = trials.coerceAtLeast(1)

                        prefs.edit()
                            .putInt("required_correct", finalCorrect)
                            .putInt("buffer_size", finalBuffer)
                            .putInt("max_trials", finalTrials)
                            .apply()
                        
                        onDismiss()
                    }) {
                        Text("Save")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onForceStop,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Force Stop Alarm")
                }
            }
        }
    }
}

@Composable
fun LanguageQuestionScreen(
    words: List<WordItem>,
    requiredCorrect: Int,
    bufferSize: Int,
    maxWrongAttempts: Int,
    onQuizCompleted: () -> Unit
) {
    val context = LocalContext.current
    val recentWords = remember { mutableStateListOf<WordItem>() }
    var correctCount by remember { mutableStateOf(0) }

    var currentWord by remember {
        mutableStateOf(pickWeightedWord(words, recentWords))
    }

    var userAnswer by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    
    // New state for tracking consecutive wrong attempts for the current word
    var consecutiveWrongCount by remember { mutableStateOf(0) }

    Column(modifier = Modifier.padding(24.dp)) {
        Text(text = "Target: $requiredCorrect correct answers", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        // Show remaining trials if user has started making mistakes
        if (consecutiveWrongCount > 0) {
            val remaining = maxWrongAttempts - consecutiveWrongCount
            val remainingText = if (remaining > 0) "$remaining trial(s) left" else "Last trial!"
            Text(
                text = remainingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        
        // Removed "What is the meaning of:" phrase
        Text(
            text = currentWord.question,
            style = MaterialTheme.typography.headlineSmall,
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
                    currentWord.weight = (currentWord.weight - 1).coerceAtLeast(1)
                    
                    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                    scope.launch {
                         WordRepository.saveProgress(context, words)
                    }

                    recentWords.add(currentWord)
                    if (recentWords.size > bufferSize) recentWords.removeAt(0)
                    
                    consecutiveWrongCount = 0 // Reset wrong count

                    if (correctCount >= requiredCorrect) {
                        onQuizCompleted()
                    } else {
                        currentWord = pickWeightedWord(words, recentWords)
                        userAnswer = ""
                    }
                } else {
                    consecutiveWrongCount++
                    
                    if (consecutiveWrongCount >= maxWrongAttempts) {
                        // Max attempts reached: skip this word and don't penalize or count as correct
                        val skippedAnswer = currentWord.answer
                        val skippedQuestion = currentWord.question
                        
                        // Increase weight slightly, but cap at 10
                        currentWord.weight = (currentWord.weight + 1).coerceAtMost(10)
                        
                        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                        scope.launch {
                             WordRepository.saveProgress(context, words)
                        }
                        
                        // Add to recent words to prevent immediate re-appearance if buffer allows
                        recentWords.add(currentWord)
                        if (recentWords.size > bufferSize) recentWords.removeAt(0)
                        
                        // Pick a new word
                        val nextWord = pickWeightedWord(words, recentWords)
                        
                        // Force retry loop to find a different word
                        var retries = 0
                        var tempWord = nextWord
                        // Check against the OLD currentWord, not the new 'nextWord' assigned to tempWord initially
                        // We want to make sure tempWord != currentWord (the skipped word)
                        while (tempWord == currentWord && words.size > 1 && retries < 10) {
                            tempWord = pickWeightedWord(words, recentWords)
                            retries++
                        }
                        
                        currentWord = tempWord
                        userAnswer = ""
                        consecutiveWrongCount = 0
                        result = "Skipped: '$skippedQuestion' was '$skippedAnswer'"
                    } else {
                        // Normal wrong answer
                        result = "Wrong ❌ Try again"
                        // Increase weight but cap at 10
                        currentWord.weight = (currentWord.weight + 2).coerceAtMost(10)
                        
                        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                        scope.launch {
                             WordRepository.saveProgress(context, words)
                        }
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

        Text(
            text = "Progress: $correctCount / $requiredCorrect",
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
fun MainScreen(
    alarms: List<Alarm>,
    onAddAlarmClick: () -> Unit,
    onEditAlarmClick: (Alarm) -> Unit,
    onToggleAlarm: (Alarm, Boolean) -> Unit,
    onDeleteAlarmClick: (Alarm) -> Unit,
    onSettingsClick: () -> Unit,
    onDecksClick: () -> Unit,
    onSoundsClick: () -> Unit,
    onFlashCardsClick: () -> Unit // New callback
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Your Alarms", style = MaterialTheme.typography.titleLarge)
            Row {
                IconButton(onClick = onFlashCardsClick) { // Added Flash Cards Icon
                    Icon(Icons.Default.School, contentDescription = "Flash Cards")
                }
                IconButton(onClick = onDecksClick) {
                    Icon(Icons.Default.List, contentDescription = "Manage Decks")
                }
                IconButton(onClick = onSoundsClick) {
                    Icon(Icons.Default.MusicNote, contentDescription = "Manage Sounds")
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (alarms.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No alarms set")
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmItem(
                        alarm = alarm,
                        onEditClick = { onEditAlarmClick(alarm) },
                        onToggle = { isEnabled -> onToggleAlarm(alarm, isEnabled) },
                        onDeleteClick = { onDeleteAlarmClick(alarm) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        FloatingActionButton(
            onClick = onAddAlarmClick,
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Alarm")
        }
    }
}

@Composable
fun AlarmItem(
    alarm: Alarm,
    onEditClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onEditClick() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = String.format("%02d:%02d", alarm.hour, alarm.minute),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                
                val daysText = if (alarm.days.isEmpty()) {
                    "Once"
                } else if (alarm.days.size == 7) {
                    "Every day"
                } else {
                    // Simple representation
                    val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                    alarm.days.sorted().joinToString(", ") { day ->
                        // Calendar.SUNDAY is 1
                        dayNames.getOrElse(day - 1) { "" }
                    }
                }
                
                Text(
                    text = daysText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = onToggle
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}
