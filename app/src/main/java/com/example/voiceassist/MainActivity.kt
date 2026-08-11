package com.example.voiceassist

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.voiceassist.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    private val micPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening() else {
            binding.statusText.text = "Microphone permission zaroori hai"
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startWakeWordService() else {
            binding.alwaysListenSwitch.isChecked = false
            binding.statusText.text = "Notification permission zaroori hai background sunne ke liye"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.enableAccessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.micButton.setOnClickListener {
            if (!isServiceEnabled()) {
                binding.statusText.text = getString(R.string.accessibility_needed)
                return@setOnClickListener
            }
            if (isListening) {
                stopListening()
            } else {
                checkMicPermissionAndListen()
            }
        }

        binding.alwaysListenSwitch.isChecked = WakeWordListenerService.isRunning
        binding.alwaysListenSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isServiceEnabled()) {
                binding.statusText.text = getString(R.string.accessibility_needed)
                binding.alwaysListenSwitch.isChecked = false
                return@setOnCheckedChangeListener
            }
            if (isChecked) {
                checkMicAndNotificationPermissionThenStart()
            } else {
                stopWakeWordService()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.accessibilityWarning.visibility =
            if (isServiceEnabled()) android.view.View.GONE else android.view.View.VISIBLE
        binding.enableAccessibilityButton.visibility =
            if (isServiceEnabled()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun isServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private fun checkMicAndNotificationPermissionThenStart() {
        val micGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!micGranted) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            binding.alwaysListenSwitch.isChecked = false
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!notifGranted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        startWakeWordService()
    }

    private fun startWakeWordService() {
        val intent = Intent(this, WakeWordListenerService::class.java)
        ContextCompat.startForegroundService(this, intent)
        binding.statusText.text = "Background mein sun raha hoon (\"Hey Assistant\" bolo)"
    }

    private fun stopWakeWordService() {
        val intent = Intent(this, WakeWordListenerService::class.java).apply {
            action = WakeWordListenerService.ACTION_STOP
        }
        startService(intent)
        binding.statusText.text = getString(R.string.mic_idle)
    }

    private fun checkMicPermissionAndListen() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) startListening() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            binding.statusText.text = "Speech recognition available nahi hai"
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    binding.statusText.text = getString(R.string.mic_listening)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val heard = matches?.firstOrNull().orEmpty()
                    binding.heardText.text = heard
                    if (heard.isNotBlank()) {
                        val response = VoiceAccessibilityService.instance?.executeCommand(heard)
                            ?: "Accessibility service chalu nahi hai"
                        binding.statusText.text = response
                    }
                    isListening = false
                }

                override fun onError(error: Int) {
                    isListening = false
                    binding.statusText.text = getString(R.string.mic_idle)
                }

                override fun onEndOfSpeech() { isListening = false }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Hindi ko primary rakha hai; agar device support kare to "hi-IN" try karega,
            // warna system default language use hogi.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        }

        speechRecognizer?.startListening(intent)
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        isListening = false
        binding.statusText.text = getString(R.string.mic_idle)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }
}
