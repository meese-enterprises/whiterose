package enterprises.meese.whiterose

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("whiterose_prefs", Application.MODE_PRIVATE)

    private val _intervalMinutes = MutableStateFlow(prefs.getInt("interval_minutes", 5))
    val intervalMinutes = _intervalMinutes.asStateFlow()

    fun setIntervalMinutes(minutes: Int) {
        viewModelScope.launch {
            _intervalMinutes.emit(minutes)
            prefs.edit().putInt("interval_minutes", minutes).apply()
        }
    }

    fun startTimerService() {
        IntervalTimerService.startService(getApplication(), intervalMinutes.value)
    }

    fun stopTimerService() {
        IntervalTimerService.stopService(getApplication())
    }
}