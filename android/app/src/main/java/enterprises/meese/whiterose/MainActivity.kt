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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
    val nextTriggerMs by viewModel.nextTriggerMs.collectAsState()

    /* -------------------------------- Permission Launcher ----------------------------- */
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startTimerService()
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.msg_started)) }
        }
    }

    WhiteroseTheme() {
        Box(modifier = Modifier.fillMaxSize()) {
            SnackbarHost(hostState = snackbarHostState)
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Clock()
                
                // Countdown timer display
                CountdownDisplay(nextTriggerMs)
            }

            /* --------------------------- Bottom controls --------------------------- */
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Mode selection
                Text(
                    text = context.getString(R.string.label_mode),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RadioButton(
                        selected = mode == IntervalTimerService.MODE_SIMPLE,
                        onClick = { viewModel.setMode(IntervalTimerService.MODE_SIMPLE) },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        text = context.getString(R.string.mode_simple),
                        modifier = Modifier
                            .clickable { viewModel.setMode(IntervalTimerService.MODE_SIMPLE) }
                            .padding(start = 4.dp, end = 8.dp)
                    )
                    
                    RadioButton(
                        selected = mode == IntervalTimerService.MODE_POMODORO_SIMPLE,
                        onClick = { viewModel.setMode(IntervalTimerService.MODE_POMODORO_SIMPLE) },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        text = context.getString(R.string.mode_pomodoro_simple),
                        modifier = Modifier
                            .clickable { viewModel.setMode(IntervalTimerService.MODE_POMODORO_SIMPLE) }
                            .padding(start = 4.dp, end = 8.dp)
                    )
                    
                    RadioButton(
                        selected = mode == IntervalTimerService.MODE_POMODORO_ADVANCED,
                        onClick = { viewModel.setMode(IntervalTimerService.MODE_POMODORO_ADVANCED) },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        text = context.getString(R.string.mode_pomodoro_advanced),
                        modifier = Modifier
                            .clickable { viewModel.setMode(IntervalTimerService.MODE_POMODORO_ADVANCED) }
                            .padding(start = 4.dp)
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Sound and vibration toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(context.getString(R.string.label_sound))
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = playSound,
                            onCheckedChange = { viewModel.setPlaySound(it) }
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(context.getString(R.string.label_vibrate))
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = vibrate,
                            onCheckedChange = { viewModel.setVibrate(it) }
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))

                var text by remember(interval) { mutableStateOf(interval.toString()) }

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
                    label = { Text(context.getString(R.string.label_interval)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                /* Quick preset chips */
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

                /* Align-to-clock switch */
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(context.getString(R.string.label_align))
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = alignToClock,
                        onCheckedChange = { viewModel.setAlignToClock(it) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(onClick = {
                        // Request notification permission on Android 13+
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.startTimerService()
                            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.msg_started)) }
                        }
                    }, enabled = !serviceRunning) {
                        Text(context.getString(R.string.btn_start))
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.stopTimerService()
                            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.msg_stopped)) }
                        },
                        enabled = serviceRunning
                    ) {
                        Text(context.getString(R.string.btn_stop))
                    }
                }
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
fun CountdownDisplay(nextTriggerMs: Long) {
    val context = LocalContext.current
    
    // Derived state that updates when the current time changes
    val remainingTime by produceState(initialValue = "00:00") {
        while (true) {
            val now = System.currentTimeMillis()
            val remainingMs = maxOf(0L, nextTriggerMs - now)
            
            if (remainingMs > 0) {
                val seconds = (remainingMs / 1000) % 60
                val minutes = (remainingMs / (1000 * 60)) % 60
                value = String.format(Locale.US, "%02d:%02d", minutes, seconds)
            } else {
                value = "00:00"
            }
            
            delay(1000) // Update every second
        }
    }
    
    if (nextTriggerMs > 0) {
        Text(
            text = context.getString(R.string.next_in_label, remainingTime),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
