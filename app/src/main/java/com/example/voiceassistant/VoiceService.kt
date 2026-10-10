package com.example.voiceassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import java.util.Locale

/**
 * Foreground service that:
 * 1. listens continuously for the wake word,
 * 2. when heard, opens the phone's default assistant (the one your power button opens,
 *    e.g. ChatGPT),
 * 3. pauses its own listening so the microphone is free for ChatGPT,
 * 4. resumes when you tap "Listen again" in the notification.
 */
class VoiceService : Service() {

    private val main = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var paused = false
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var wakeWord: String

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        wakeWord = normalize(
            prefs.getString("wake_word", "hey assistant").orEmpty().ifBlank { "hey assistant" }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RESUME) paused = false

        startAsForeground(statusText())
        if (wakeLock == null) {
            // Keeps the CPU awake so listening continues while the phone is locked.
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "voiceassistant:listening")
                .apply { acquire() }
        }
        running = true
        main.post { startListening() }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    // ---------- Speech recognition ----------

    private fun startListening() {
        if (!running || paused) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("Speech recognition is not available on this device", false)
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(listener)
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        recognizer?.startListening(intent)
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val candidates = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()
            // Check every alternative the recognizer offers, not just the first.
            val heard = candidates.any { normalize(it).contains(wakeWord) }
            if (heard) openAssistant() else startListening()
        }

        override fun onError(error: Int) {
            // Silence / no match is normal: just listen again after a short pause.
            main.postDelayed({ if (running && !paused) startListening() }, 700)
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    // ---------- Open the default assistant ----------

    private fun openAssistant() {
        // Free the microphone for the assistant app.
        paused = true
        recognizer?.destroy()
        recognizer = null

        val candidates = listOf(
            Intent(Intent.ACTION_ASSIST),                                 // power-button assistant
            Intent(Intent.ACTION_VOICE_COMMAND),                          // voice command app
            packageManager.getLaunchIntentForPackage("com.openai.chatgpt") // ChatGPT app itself
        )
        for (candidate in candidates) {
            if (candidate == null) continue
            candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(candidate)
                updateNotification(statusText(), true)
                return
            } catch (e: Exception) {
                // try the next option
            }
        }
        // Nothing could be opened: keep listening instead of going silent.
        paused = false
        updateNotification("Could not open the assistant. Check the overlay permission.", false)
        main.postDelayed({ startListening() }, 2000)
    }

    // ---------- Notification (required for foreground services) ----------

    private fun statusText(): String =
        if (paused) "Assistant is open. Tap Listen again when you are done."
        else "Listening for \"$wakeWord\""

    private fun buildNotification(text: String, showResume: Boolean): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Voice assistant", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Assistant")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)

        if (showResume) {
            val resume = PendingIntent.getService(
                this,
                0,
                Intent(this, VoiceService::class.java).setAction(ACTION_RESUME),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, "Listen again", resume)
        }
        return builder.build()
    }

    private fun startAsForeground(text: String) {
        val notification = buildNotification(text, paused)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun updateNotification(text: String, showResume: Boolean) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(text, showResume))
    }

    companion object {
        private const val CHANNEL_ID = "voice_assistant"
        private const val NOTIF_ID = 1
        private const val ACTION_RESUME = "com.example.voiceassistant.RESUME"
    }
}
