package enterprises.meese.whiterose

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import enterprises.meese.whiterose.ui.theme.WhiteroseTheme

class MainActivity : ComponentActivity() {

    private val requestScheduleExactAlarm =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Handle the result if necessary
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (alarmManager.canScheduleExactAlarms()) {
                    setupRepeatingAlarm()
                } else {
                    // Optionally, handle the case where permission is not granted
                }
            } else {
                setupRepeatingAlarm()
            }
        }

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                showRequestExactAlarmPermissionDialog()
            } else {
                setupRepeatingAlarm()
            }
        } else {
            setupRepeatingAlarm()
        }
    }

    private fun showRequestExactAlarmPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Exact Alarm Permission Required")
            .setMessage("This app requires permission to schedule exact alarms for precise timing.")
            .setPositiveButton("Allow") { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    requestScheduleExactAlarm.launch(intent)
                } else {
                    // Fallback behavior for older Android versions
                    setupRepeatingAlarm()
                }
            }
            .setNegativeButton("Deny") { _, _ -> }
            .show()
    }

    private fun setupRepeatingAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = Intent(this, TimeUpdateReceiver::class.java).let { intent ->
            PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val calendar = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, 1)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                alarmIntent
            )
        } else {
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                alarmIntent
            )
        }
    }
}

@Composable
fun WhiteroseApp(viewModel: ClockViewModel = viewModel()) {
    val ticksEnabled by viewModel.ticksEnabled.collectAsState()
    val speechEnabled by viewModel.speechEnabled.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    WhiteroseTheme() {
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
                    ticksEnabled = ticksEnabled,
                    speechEnabled = speechEnabled,
                    onDismiss = { showSettings = false },
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
    ticksEnabled: Boolean,
    speechEnabled: Boolean,
    onDismiss: () -> Unit,
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
