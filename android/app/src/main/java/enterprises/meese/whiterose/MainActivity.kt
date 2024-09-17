package enterprises.meese.whiterose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
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
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val ticksEnabled by viewModel.ticksEnabled.collectAsState()
    val speechEnabled by viewModel.speechEnabled.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    WhiteroseTheme(darkTheme = isDarkTheme) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Clock()
            }

            IconButton(
                onClick = { showSettings = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }

            if (showSettings) {
                SettingsDialog(
                    isDarkTheme = isDarkTheme,
                    ticksEnabled = ticksEnabled,
                    speechEnabled = speechEnabled,
                    onDismiss = { showSettings = false },
                    onThemeChanged = viewModel::setDarkTheme,
                    onTicksChanged = viewModel::setTicksEnabled,
                    onSpeechChanged = viewModel::setSpeechEnabled
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
fun SettingsDialog(
    isDarkTheme: Boolean,
    ticksEnabled: Boolean,
    speechEnabled: Boolean,
    onDismiss: () -> Unit,
    onThemeChanged: (Boolean) -> Unit,
    onTicksChanged: (Boolean) -> Unit,
    onSpeechChanged: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dark Theme", modifier = Modifier.weight(1f))
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = onThemeChanged
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Ticks", modifier = Modifier.weight(1f))
                    Switch(
                        checked = ticksEnabled,
                        onCheckedChange = onTicksChanged
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Speech", modifier = Modifier.weight(1f))
                    Switch(
                        checked = speechEnabled,
                        onCheckedChange = onSpeechChanged
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
