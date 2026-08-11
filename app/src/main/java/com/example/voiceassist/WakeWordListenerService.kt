package com.example.voiceassist

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat

class WakeWordListenerService : Service() {

    companion object {
        const val CHANNEL_ID = "wake_word_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.example.voiceassist.STOP_LISTENING"
        private val WAKE_WORDS = listOf("hey assistant", "ok assistant", "hey asistant", "he assistant")

        var isRunning = false
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var awaitingCommand = false
    private var shouldKeepListening = true

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Sun raha hoon... bolo \"Hey Assistant\""))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelfService()
            return START_NOT_STICKY
        }
        shouldKeepListening = true
        listenLoop()
        return START_STICKY
    }

    private fun listenLoop() {
        if (!shouldKeepListening) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val heard = matches?.firstOrNull()?.lowercase()?.trim().orEmpty()
                    handleHeard(heard)
                    restart()
                }

                override fun onError(error: Int) {
                    restart()
                }

                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun handleHeard(heard: String) {
        if (heard.isBlank()) return

        val matchedWakeWord = WAKE_WORDS.firstOrNull { heard.contains(it) }

        when {
            awaitingCommand -> {
                awaitingCommand = false
                runCommand(heard)
            }
            matchedWakeWord != null -> {
                val remainder = heard.replace(matchedWakeWord, "").trim()
                if (remainder.isNotBlank()) {
                    runCommand(remainder)
                } else {
                    awaitingCommand = true
                    updateNotification("Sun raha hoon, ab command bolo...")
                }
            }
        }
    }

    private fun runCommand(command: String) {
        val response = VoiceAccessibilityService.instance?.executeCommand(command)
            ?: "Accessibility service chalu nahi hai"
        updateNotification(response)
    }

    private fun restart() {
        android.os.Handler(mainLooper).postDelayed({ listenLoop() }, 400)
    }

    private fun stopSelfService() {
        shouldKeepListening = false
        speechRecognizer?.destroy()
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        shouldKeepListening = false
        speechRecognizer?.destroy()
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VoiceAssist Wake Word",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, WakeWordListenerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VoiceAssist")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .addAction(0, "Band Karo", stopPendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
