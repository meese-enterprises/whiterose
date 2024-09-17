package enterprises.meese.whiterose

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class ClockViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("whiterose_prefs", Application.MODE_PRIVATE)

    private val _ticksEnabled = MutableStateFlow(prefs.getBoolean("ticks_enabled", true))
    val ticksEnabled = _ticksEnabled.asStateFlow()

    private val _speechEnabled = MutableStateFlow(prefs.getBoolean("speech_enabled", true))
    val speechEnabled = _speechEnabled.asStateFlow()

    init {
        updateWorkManager()
    }

    fun setTicksEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _ticksEnabled.emit(enabled)
            prefs.edit().putBoolean("ticks_enabled", enabled).apply()
            updateWorkManager()
        }
    }

    fun setSpeechEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _speechEnabled.emit(enabled)
            prefs.edit().putBoolean("speech_enabled", enabled).apply()
            updateWorkManager()
        }
    }

    private fun updateWorkManager() {
        val workManager = WorkManager.getInstance(getApplication())

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val inputData = workDataOf(
            "ticks_enabled" to ticksEnabled.value,
            "speech_enabled" to speechEnabled.value
        )

        val timeUpdateWork = PeriodicWorkRequestBuilder<TimeUpdateWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInputData(inputData)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "timeUpdate",
            ExistingPeriodicWorkPolicy.UPDATE,
            timeUpdateWork
        )
    }
}
