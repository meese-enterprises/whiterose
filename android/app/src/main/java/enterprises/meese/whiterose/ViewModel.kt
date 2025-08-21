package enterprises.meese.whiterose

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs: SharedPreferences = application.getSharedPreferences("whiterose_prefs", Application.MODE_PRIVATE)

    private val _intervalMinutes = MutableStateFlow(prefs.getInt("interval_minutes", 5))
    val intervalMinutes = _intervalMinutes.asStateFlow()

    private val _serviceRunning = MutableStateFlow(prefs.getBoolean("service_running", false))
    val serviceRunning = _serviceRunning.asStateFlow()

    private val _alignToClock = MutableStateFlow(prefs.getBoolean("align_to_clock", false))
    val alignToClock = _alignToClock.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "interval_minutes" -> _intervalMinutes.value = prefs.getInt("interval_minutes", 5)
            "service_running" -> _serviceRunning.value = prefs.getBoolean("service_running", false)
            "align_to_clock" -> _alignToClock.value = prefs.getBoolean("align_to_clock", false)
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        super.onCleared()
    }

    fun setIntervalMinutes(minutes: Int) {
        viewModelScope.launch {
            _intervalMinutes.emit(minutes)
            prefs.edit().putInt("interval_minutes", minutes).apply()
        }
    }

    fun setAlignToClock(enabled: Boolean) {
        viewModelScope.launch {
            _alignToClock.emit(enabled)
            prefs.edit().putBoolean("align_to_clock", enabled).apply()
        }
    }

    fun startTimerService() {
        IntervalTimerService.startService(getApplication(), intervalMinutes.value)
    }

    fun stopTimerService() {
        IntervalTimerService.stopService(getApplication())
    }
}