package enterprises.meese.whiterose

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.speech.tts.TextToSpeech
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.*

class TimeUpdateWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var soundPool: SoundPool? = null
    private var tickSoundId: Int = 0
    private var isSoundLoaded: Boolean = false

    init {
        // Initialize TTS and SoundPool once
        tts = TextToSpeech(applicationContext, this)
        setupSoundPool()
    }

    override fun doWork(): Result {
        val ticksEnabled = inputData.getBoolean("ticks_enabled", true)
        val speechEnabled = inputData.getBoolean("speech_enabled", true)

        if (ticksEnabled || speechEnabled) {
            val calendar = Calendar.getInstance()
            val minute = calendar.get(Calendar.MINUTE)

            if (speechEnabled && minute % 15 == 0) {
                speakTime(calendar)
            } else if (ticksEnabled) {
                // Only play the sound if it's loaded
                if (isSoundLoaded) {
                    playTickSound()
                }
            }
        }

        return Result.success()
    }

    private fun setupSoundPool() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(attributes)
            .build()

        soundPool?.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) {
                isSoundLoaded = true
            }
        }

        tickSoundId = soundPool?.load(applicationContext, R.raw.tick_sound, 1) ?: 0
    }

    private fun speakTime(calendar: Calendar) {
        val hour = calendar.get(Calendar.HOUR)
        val minute = calendar.get(Calendar.MINUTE)
        val amPm = if (calendar.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        val timeString = "$hour ${if (minute == 0) "o'clock" else minute} $amPm"
        tts?.speak(timeString, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun playTickSound() {
        soundPool?.play(tickSoundId, 1f, 1f, 1, 0, 1f)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    override fun onStopped() {
        tts?.shutdown()
        soundPool?.release()
    }
}
