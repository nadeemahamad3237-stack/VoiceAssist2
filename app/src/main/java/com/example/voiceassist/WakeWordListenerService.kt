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
        var isRunning = false
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isActive = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopListening()
            isRunning = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stopSelf()
            return
        }

        startForeground(NOTIFICATION_ID, buildNotification("Sunraaha hoon..."))

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val heard = matches?.firstOrNull()?.lowercase()?.trim().orEmpty()
                    
                    if (heard.contains("hey assistant") || heard.contains("hey asistant")) {
                        val remainder = heard
                            .replace("hey assistant", "")
                            .replace("hey asistant", "")
                            .trim()
                        
                        if (remainder.isNotBlank()) {
                            val response = VoiceAccessibilityService.instance?.executeCommand(remainder)
                                ?: "Error"
                            updateNotification(response)
                        } else {
                            updateNotification("Awaiting command...")
                        }
                    }
                    
                    restartListening()
                }

                override fun onError(error: Int) {
                    restartListening()
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
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun restartListening() {
        android.os.Handler(mainLooper).postDelayed({ startListening() }, 300)
    }

    private fun stopListening() {
        speechRecognizer?.destroy()
        isRunning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VoiceAssist",
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
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
