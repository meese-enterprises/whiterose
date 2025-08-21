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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
        Box(modifier = Modifier.fillMaxSize()) {
            SnackbarHost(hostState = snackbarHostState)
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
                    label = { Text("Interval (minutes)") },
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
                    Text("Align to clock")
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
