package com.voiceaction.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.voiceaction.app.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    // Parsed intent details
    private var parsedApp = ""
    private var parsedRecipient = ""
    private var parsedMessage = ""
    private var parsedPackage = ""

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup button listeners
        binding.btnEnableService.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        binding.btnMic.setOnClickListener {
            if (isListening) {
                stopSpeechRecognition()
            } else {
                checkPermissionAndStartSpeech()
            }
        }

        binding.btnCancelSend.setOnClickListener {
            binding.cardConfirmation.visibility = View.GONE
            logMessage("Action cancelled by user.")
        }

        binding.btnConfirmSend.setOnClickListener {
            executeAutomatedAction()
        }
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
    }

    private fun updateAccessibilityStatus() {
        val isEnabled = isAccessibilityServiceEnabled(
            this,
            VoiceActionAccessibilityService::class.java
        )
        if (isEnabled) {
            binding.viewStatusDot.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            binding.tvServiceStatus.text = "Accessibility Service: Enabled"
            binding.btnEnableService.visibility = View.GONE
        } else {
            binding.viewStatusDot.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
            binding.tvServiceStatus.text = "Accessibility Service: Disabled"
            binding.btnEnableService.visibility = View.VISIBLE
        }
    }

    private fun checkPermissionAndStartSpeech() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        } else {
            startSpeechRecognition()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSpeechRecognition()
            } else {
                Toast.makeText(this, "Microphone permission required for voice action.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startSpeechRecognition() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            
            // Set language based on selection
            val languageCode = when {
                binding.rbSpanish.isChecked -> "es-ES"
                binding.rbHindi.isChecked -> "hi-IN"
                else -> "en-US"
            }
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, languageCode)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                binding.tvMicStatus.text = "Listening... Speak now"
                binding.btnMic.setImageResource(android.R.drawable.presence_audio_online)
                logMessage("Microphone active. Listening...")
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                binding.tvMicStatus.text = "Processing..."
            }

            override fun onError(error: Int) {
                isListening = false
                binding.tvMicStatus.text = "Error occurred. Tap to retry."
                binding.btnMic.setImageResource(android.R.drawable.ic_btn_speak_now)
                logMessage("Speech recognition error code: $error")
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                binding.btnMic.setImageResource(android.R.drawable.ic_btn_speak_now)
                binding.tvMicStatus.text = "Tap microphone to speak"

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val spokenText = matches[0]
                    logMessage("Transcribed: \"$spokenText\"")
                    parseVoiceIntent(spokenText)
                } else {
                    logMessage("No speech captured. Please try again.")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun stopSpeechRecognition() {
        speechRecognizer?.stopListening()
        isListening = false
        binding.btnMic.setImageResource(android.R.drawable.ic_btn_speak_now)
        binding.tvMicStatus.text = "Tap microphone to speak"
    }

    // Multilingual Intent Parser Engine
    private fun parseVoiceIntent(text: String) {
        val lowercaseText = text.lowercase(Locale.getDefault())

        // Default settings
        parsedApp = "WhatsApp"
        parsedPackage = "com.whatsapp"
        parsedRecipient = "Unknown"
        parsedMessage = text

        // 1. Detect target application
        if (lowercaseText.contains("whatsapp") || lowercaseText.contains("व्हाट्सएप") || lowercaseText.contains("व्हाट्सप्प")) {
            parsedApp = "WhatsApp"
            parsedPackage = "com.whatsapp"
        } else if (lowercaseText.contains("gmail") || lowercaseText.contains("email") || lowercaseText.contains("correo") || lowercaseText.contains("मेल") || lowercaseText.contains("ईमेल")) {
            parsedApp = "Gmail"
            parsedPackage = "com.google.android.gm"
        } else if (lowercaseText.contains("sms") || lowercaseText.contains("message") || lowercaseText.contains("messages") || lowercaseText.contains("मैसेज") || lowercaseText.contains("संदेश")) {
            parsedApp = "SMS"
            parsedPackage = "com.google.android.apps.messaging"
        }

        // 2. Multilingual recipient and message extraction
        if (binding.rbSpanish.isChecked) {
            // Spanish parse: "Enviar WhatsApp a [Juan] diciendo [mensaje]"
            val aIndex = lowercaseText.indexOf(" a ")
            val diciendoIndex = lowercaseText.indexOf(" diciendo ")
            val queDiceIndex = lowercaseText.indexOf(" que dice ")

            val splitIndex = if (diciendoIndex != -1) diciendoIndex else queDiceIndex

            if (aIndex != -1 && splitIndex != -1 && splitIndex > aIndex) {
                parsedRecipient = text.substring(aIndex + 3, splitIndex).trim()
                parsedMessage = text.substring(splitIndex + if (diciendoIndex != -1) 10 else 10).trim()
            }
        } else if (binding.rbHindi.isChecked) {
            // Hindi parse: "[Rohit] को [whatsapp] पर मैसेज भेजो कि [मैसेज]"
            // E.g., "रोहित को व्हाट्सएप पर संदेश भेजो कि मैं घर आ रहा हूं"
            val koIndex = lowercaseText.indexOf(" को ")
            val kiIndex = lowercaseText.indexOf(" कि ")
            val sendIndex = lowercaseText.indexOf(" भेजो ")

            if (koIndex != -1) {
                parsedRecipient = text.substring(0, koIndex).trim()
                if (kiIndex != -1 && kiIndex > koIndex) {
                    parsedMessage = text.substring(kiIndex + 4).trim()
                } else if (sendIndex != -1 && sendIndex > koIndex) {
                    // Extract message before "भेजो" but after app name
                    val appWords = listOf("व्हाट्सएप", "व्हाट्सप्प", "whatsapp", "मैसेज", "संदेश", "पर")
                    var cleanMsg = text.substring(koIndex + 4, sendIndex).trim()
                    for (word in appWords) {
                        cleanMsg = cleanMsg.replace(word, "", ignoreCase = true)
                    }
                    parsedMessage = cleanMsg.trim()
                }
            }
        } else {
            // English parse: "Send WhatsApp to [John] saying [message]"
            val toIndex = lowercaseText.indexOf(" to ")
            val sayingIndex = lowercaseText.indexOf(" saying ")
            val tellingIndex = lowercaseText.indexOf(" telling ")
            val messageIndex = lowercaseText.indexOf(" message ")

            val splitIndex = when {
                sayingIndex != -1 -> sayingIndex
                tellingIndex != -1 -> tellingIndex
                else -> messageIndex
            }

            if (toIndex != -1 && splitIndex != -1 && splitIndex > toIndex) {
                parsedRecipient = text.substring(toIndex + 4, splitIndex).trim()
                parsedMessage = text.substring(splitIndex + if (sayingIndex != -1) 8 else if (tellingIndex != -1) 9 else 9).trim()
            }
        }

        // Display Confirmation UI
        binding.tvConfirmRecipient.text = parsedRecipient
        binding.tvConfirmApp.text = parsedApp
        binding.etConfirmMessage.setText(parsedMessage)
        binding.cardConfirmation.visibility = View.VISIBLE
        
        logMessage("Parsed Intent -> App: $parsedApp, Recipient: $parsedRecipient, Msg: \"$parsedMessage\"")
    }

    private fun executeAutomatedAction() {
        val isServiceEnabled = isAccessibilityServiceEnabled(this, VoiceActionAccessibilityService::class.java)
        if (!isServiceEnabled) {
            Toast.makeText(this, "Please enable Accessibility Service first.", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            return
        }

        // Get edited message from user input
        val finalMessage = binding.etConfirmMessage.text.toString()
        if (TextUtils.isEmpty(finalMessage)) {
            Toast.makeText(this, "Message cannot be empty.", Toast.LENGTH_SHORT).show()
            return
        }

        logMessage("Executing action... Launching $parsedApp for $parsedRecipient")

        // 1. Package instructions for the accessibility service
        VoiceActionAccessibilityService.pendingAction = VoiceActionAccessibilityService.PendingAction(
            appPackage = parsedPackage,
            recipient = parsedRecipient,
            message = finalMessage
        )

        // 2. Launch the target app
        try {
            val intent = packageManager.getLaunchIntentForPackage(parsedPackage)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                binding.cardConfirmation.visibility = View.GONE
            } else {
                Toast.makeText(this, "$parsedApp is not installed on this device.", Toast.LENGTH_SHORT).show()
                logMessage("Error: Launcher intent for $parsedPackage not found.")
            }
        } catch (e: Exception) {
            logMessage("Error opening app: ${e.localizedMessage}")
        }
    }

    private fun logMessage(msg: String) {
        val currentLogs = binding.tvLogs.text.toString()
        binding.tvLogs.text = "$currentLogs\n> $msg"
        binding.scrollLog.post {
            binding.scrollLog.fullScroll(View.FOCUS_DOWN)
        }
    }

    // Helper: Check if Accessibility Service is running
    private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<out AccessibilityService>): Boolean {
        val expectedComponentName = android.content.ComponentName(context, serviceClass)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = android.content.ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedComponentName) {
                return true
            }
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }
}
