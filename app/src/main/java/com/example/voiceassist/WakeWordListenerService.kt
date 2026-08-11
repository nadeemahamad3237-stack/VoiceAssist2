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
        const val CHANNEL_ID = "voiceassist_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.example.voiceassist.STOP_LISTENING"
        var isRunning = false
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isDestroying = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        isDestroying = false
        createNotificationChannel()
        showPersistentNotification()
        initSpeechRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            isDestroying = true
            speechRecognizer?.destroy()
            isRunning = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun initSpeechRecognizer() {
        if (isDestroying || !SpeechRecognizer.isRecognitionAvailable(this)) return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    if (isDestroying) return

                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val heard = matches?.firstOrNull()?.lowercase()?.trim().orEmpty()

                    if (heard.isNotEmpty() && 
                        (heard.contains("hey assistant") || heard.contains("hey asistant"))) {
                        handleWakeWord(heard)
                    }
                    
                    // Smoothly restart without destroying
                    restartListeningSmooth()
                }

                override fun onError(error: Int) {
                    if (!isDestroying) {
                        restartListeningSmooth()
                    }
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

        startListeningWithLongTimeout()
    }

    private fun startListeningWithLongTimeout() {
        if (isDestroying || speechRecognizer == null) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            if (!isDestroying) restartListeningSmooth()
        }
    }

    private fun restartListeningSmooth() {
        if (isDestroying) return
        // Small delay taaki recognizer recover kar sake
        android.os.Handler(mainLooper).postDelayed({
            if (!isDestroying) {
                startListeningWithLongTimeout()
            }
        }, 100)
    }

    private fun handleWakeWord(heard: String) {
        val command = heard.replace("hey assistant", "")
            .replace("hey asistant", "")
            .trim()

        val response = if (command.isNotEmpty()) {
            VoiceAccessibilityService.instance?.executeCommand(command) ?: "Error"
        } else {
            "Awaiting command... (bolo)"
        }

        updateNotification(response)
    }

    private fun showPersistentNotification() {
        val stopIntent = Intent(this, WakeWordListenerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VoiceAssist Active")
            .setContentText("Listening... (Say 'Hey Assistant')")
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .addAction(0, "Stop", stopPendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(text: String) {
        val stopIntent = Intent(this, WakeWordListenerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VoiceAssist")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .addAction(0, "Stop", stopPendingIntent)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VoiceAssist",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        isDestroying = true
        isRunning = false
        speechRecognizer?.destroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
