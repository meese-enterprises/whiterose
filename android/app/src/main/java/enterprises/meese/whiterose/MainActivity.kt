package enterprises.meese.whiterose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import enterprises.meese.whiterose.ui.theme.WhiteroseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WhiteroseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WhiteroseApp()
                }
            }
        }
    }
}

@Composable
fun WhiteroseApp(viewModel: ViewModel = viewModel()) {
    val interval by viewModel.intervalMinutes.collectAsState()
    val serviceRunning by viewModel.serviceRunning.collectAsState()
    val alignToClock by viewModel.alignToClock.collectAsState()
    val playSound by viewModel.playSound.collectAsState()
    val vibrate by viewModel.vibrate.collectAsState()
    val widgetMaterial by viewModel.widgetMaterial.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val nextTriggerMs by viewModel.nextTriggerMs.collectAsState()
    val currentPhase by viewModel.currentPhase.collectAsState()

    /* -------------------------------- Permission Launcher ----------------------------- */
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startTimerService()
            scope.launch { snackbarHostState.showSnackbar("Timer started") }
        }
    }

    WhiteroseTheme() {
        var showSettings by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxSize()) {
            SnackbarHost(hostState = snackbarHostState)

            /* --------------------------- Top bar --------------------------- */
            TextButton(
                onClick = { showSettings = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Text("Settings")
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ClockAndStatus(
                    mode = mode,
                    currentPhase = currentPhase,
                    nextTriggerMs = nextTriggerMs,
                    serviceRunning = serviceRunning
                )
            }

            /* --------------------------- Bottom controls --------------------------- */
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(onClick = {
                        // Request notification permission on Android 13+
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.startTimerService()
                            scope.launch { snackbarHostState.showSnackbar("Timer started") }
                        }
                    }, enabled = !serviceRunning) {
                        Text("Start")
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.stopTimerService()
                            scope.launch { snackbarHostState.showSnackbar("Timer stopped") }
                        },
                        enabled = serviceRunning
                    ) {
                        Text("Stop")
                    }
                }
            }

            /* ------------------------- Settings Overlay ------------------------- */
            if (showSettings) {
                SettingsScreen(
                    viewModel = viewModel,
                    onClose = { showSettings = false }
                )
            }
        }
    }
}

@Composable
fun Clock() {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = System.currentTimeMillis()
        }
    }

    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    Text(
        text = timeFormat.format(Date(currentTime)),
        fontSize = 48.sp
    )
}

@Composable
fun ClockAndStatus(
    mode: String,
    currentPhase: String,
    nextTriggerMs: Long,
    serviceRunning: Boolean
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    Text(
        text = timeFormat.format(Date(now)),
        fontSize = 48.sp
    )

    /* -------- Phase & countdown -------- */
    if (mode != IntervalTimerService.MODE_SIMPLE) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = when (currentPhase) {
                "work" -> "Phase: Work"
                else -> "Phase: Break"
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }

    /* Countdown until next trigger */
    if (serviceRunning) {
        val remainingMs = (nextTriggerMs - now).coerceAtLeast(0L)
        // use ceiling so current clock seconds + countdown always sums to 60
        val remainingSecTotal = kotlin.math.ceil(remainingMs / 1000.0).toLong()
        val mins = (remainingSecTotal / 60).toInt()
        val secs = (remainingSecTotal % 60).toInt()
        val remainingStr = String.format("%02d:%02d", mins, secs)

        Spacer(Modifier.height(2.dp))
        Text(
            text = "Next in $remainingStr",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/* -------------------------------------------------------------------------- */
@Composable
fun SettingsScreen(viewModel: ViewModel, onClose: () -> Unit) {
    val interval by viewModel.intervalMinutes.collectAsState()
    val alignToClock by viewModel.alignToClock.collectAsState()
    val playSound by viewModel.playSound.collectAsState()
    val vibrate by viewModel.vibrate.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val pomoWorkMin by viewModel.pomoWorkMin.collectAsState()
    val pomoBreakMin by viewModel.pomoBreakMin.collectAsState()
    val pomoLongMin by viewModel.pomoLongMin.collectAsState()
    val pomoLongEvery by viewModel.pomoLongEvery.collectAsState()
    val widgetMaterial by viewModel.widgetMaterial.collectAsState()

    var text by remember(interval) { mutableStateOf(interval.toString()) }
    var workText by remember(pomoWorkMin) { mutableStateOf(pomoWorkMin.toString()) }
    var breakText by remember(pomoBreakMin) { mutableStateOf(pomoBreakMin.toString()) }
    var longText by remember(pomoLongMin) { mutableStateOf(pomoLongMin.toString()) }
    var everyText by remember(pomoLongEvery) { mutableStateOf(pomoLongEvery.toString()) }

    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.97f),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            /* Close button */
            TextButton(onClick = onClose) {
                Text("Close")
            }

            Spacer(Modifier.height(8.dp))

            /* Interval (Simple mode only) */
            if (mode == IntervalTimerService.MODE_SIMPLE) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        val sanitized = it.filter { ch -> ch.isDigit() }.take(3)
                        text = sanitized
                        val minutes = sanitized.toIntOrNull() ?: 0
                        if (minutes in 1..120) {
                            viewModel.setIntervalMinutes(minutes)
                        }
                    },
                    label = { Text("Interval (minutes)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                /* Presets */
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 5, 15).forEach { preset ->
                        AssistChip(
                            onClick = { viewModel.setIntervalMinutes(preset) },
                            label = { Text("$preset") },
                            colors = AssistChipDefaults.assistChipColors()
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            /* Pomodoro settings (non-simple modes) */
            if (mode != IntervalTimerService.MODE_SIMPLE) {
                Text("Pomodoro Settings", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                
                // Work duration
                OutlinedTextField(
                    value = workText,
                    onValueChange = {
                        val sanitized = it.filter { ch -> ch.isDigit() }.take(3)
                        workText = sanitized
                        val minutes = sanitized.toIntOrNull() ?: pomoWorkMin
                        if (minutes in 1..120) {
                            viewModel.setPomodoroWork(minutes)
                        }
                    },
                    label = { Text("Work minutes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(8.dp))
                
                // Break duration
                OutlinedTextField(
                    value = breakText,
                    onValueChange = {
                        val sanitized = it.filter { ch -> ch.isDigit() }.take(3)
                        breakText = sanitized
                        val minutes = sanitized.toIntOrNull() ?: pomoBreakMin
                        if (minutes in 1..120) {
                            viewModel.setPomodoroBreak(minutes)
                        }
                    },
                    label = { Text("Break minutes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(8.dp))
                
                /* ----- Advanced-only (long break) settings ----- */
                if (mode == IntervalTimerService.MODE_POMODORO_ADVANCED) {
                    // Long break duration
                    OutlinedTextField(
                        value = longText,
                        onValueChange = {
                            val sanitized = it.filter { ch -> ch.isDigit() }.take(3)
                            longText = sanitized
                            val minutes = sanitized.toIntOrNull() ?: pomoLongMin
                            if (minutes in 1..120) {
                                viewModel.setPomodoroLong(minutes)
                            }
                        },
                        label = { Text("Long break minutes") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // Long break frequency
                    OutlinedTextField(
                        value = everyText,
                        onValueChange = {
                            val sanitized = it.filter { ch -> ch.isDigit() }.take(2)
                            everyText = sanitized
                            val cycles = sanitized.toIntOrNull() ?: pomoLongEvery
                            if (cycles in 1..12) {
                                viewModel.setPomodoroLongEvery(cycles)
                            }
                        },
                        label = { Text("Long break every N cycles") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Work, short breaks, and long breaks will follow your custom durations.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Work and short breaks will follow your custom durations.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Spacer(Modifier.height(16.dp))
            }

            /* Align switch */
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Align to clock")
                Spacer(Modifier.width(8.dp))
                Switch(checked = alignToClock, onCheckedChange = { viewModel.setAlignToClock(it) })
            }

            /* Sound toggle */
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Play sound")
                Spacer(Modifier.width(8.dp))
                Switch(checked = playSound, onCheckedChange = { viewModel.setPlaySound(it) })
            }

            /* Vibrate toggle */
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Vibrate")
                Spacer(Modifier.width(8.dp))
                Switch(checked = vibrate, onCheckedChange = { viewModel.setVibrate(it) })
            }

            /* Widget Material You toggle */
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Widget uses Material You")
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = widgetMaterial,
                    onCheckedChange = { viewModel.setWidgetMaterial(it) }
                )
            }

            /* Mode selection – vertical */
            Spacer(Modifier.height(24.dp))
            Text("Mode", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Column {
                listOf(
                    IntervalTimerService.MODE_SIMPLE to "Simple",
                    IntervalTimerService.MODE_POMODORO_SIMPLE to "Pomodoro (25/5)",
                    IntervalTimerService.MODE_POMODORO_ADVANCED to "Pomodoro (25/5 + 15)"
                ).forEach { (value, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = mode == value,
                            onClick = { viewModel.setMode(value) }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(label)
                    }
                }
            }
        }
    }
}
