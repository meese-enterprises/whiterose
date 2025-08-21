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
    val mode by viewModel.mode.collectAsState()

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
                Clock()
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

/* -------------------------------------------------------------------------- */
@Composable
fun SettingsScreen(viewModel: ViewModel, onClose: () -> Unit) {
    val interval by viewModel.intervalMinutes.collectAsState()
    val alignToClock by viewModel.alignToClock.collectAsState()
    val playSound by viewModel.playSound.collectAsState()
    val vibrate by viewModel.vibrate.collectAsState()
    val mode by viewModel.mode.collectAsState()

    var text by remember(interval) { mutableStateOf(interval.toString()) }

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

            /* Pomodoro helper text (non-simple modes) */
            if (mode != IntervalTimerService.MODE_SIMPLE) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Pomodoro intervals are managed automatically (Work 25 • Break 5 • Long break 15).",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
