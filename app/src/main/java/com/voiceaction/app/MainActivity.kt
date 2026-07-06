package com.voiceaction.app

import android.Manifest
import android.accessibilityservice.AccessibilityService
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
import androidx.activity.result.contract.ActivityResultContracts
import com.voiceaction.app.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isListening = false

    private var speechRecognizer: SpeechRecognizer? = null

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

        binding.btnCancelOverlay.setOnClickListener {
            stopSpeechRecognition()
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
        cleanupSpeechRecognizer()
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            
            // Adjust length settings to prevent cutting off early
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)

            val languageCode = when {
                binding.rbSpanish.isChecked -> "es-ES"
                binding.rbTamil.isChecked -> "ta-IN"
                else -> "en-US"
            }
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
        }

        speechRecognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                binding.tvMicStatus.text = "Listening... Speak now"
                binding.btnMic.setImageResource(android.R.drawable.presence_audio_online)
                logMessage("Microphone active. Listening...")
                
                // Show Custom Listening Overlay
                binding.layoutListeningOverlay.visibility = View.VISIBLE
                binding.tvOverlayTitle.text = "Listening..."
                binding.tvOverlaySubtitle.text = "Speak your command clearly"
                binding.btnCancelOverlay.text = "CANCEL"

                // Haptic feedback
                try {
                    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(100)
                    }
                } catch (e: Exception) {}
            }

            override fun onBeginningOfSpeech() {}
            
            override fun onRmsChanged(rmsdB: Float) {
                // Animate voice wave ring size in real time based on audio input volume!
                val scale = 1.0f + (rmsdB.coerceAtLeast(0f) / 8f)
                binding.viewVoiceWave.scaleX = scale
                binding.viewVoiceWave.scaleY = scale
            }
            
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                binding.tvMicStatus.text = "Processing..."
                binding.tvOverlayTitle.text = "Processing..."
                binding.tvOverlaySubtitle.text = "Converting your voice to text..."
            }

            override fun onError(error: Int) {
                isListening = false
                binding.btnMic.setImageResource(android.R.drawable.ic_btn_speak_now)
                binding.tvMicStatus.text = "Error occurred. Tap to retry."
                logMessage("Speech recognition error code: $error")
                
                // Keep overlay visible but display helpful diagnostics, changing button to CLOSE
                binding.tvOverlayTitle.text = "Speech Error"
                binding.btnCancelOverlay.text = "CLOSE"
                
                if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                    binding.tvOverlaySubtitle.text = "No speech detected.\n\nTip: If on Emulator, ensure Microphone uses host input (Emulator Settings -> ... -> Microphone -> toggle 'Virtual microphone uses host audio input' ON)."
                    logMessage("Tip: Wait for the short vibration feedback before speaking!")
                } else {
                    binding.tvOverlaySubtitle.text = "Error code: $error. Please try again."
                }
                
                cleanupSpeechRecognizer()
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                binding.btnMic.setImageResource(android.R.drawable.ic_btn_speak_now)
                binding.tvMicStatus.text = "Tap microphone to speak"
                binding.layoutListeningOverlay.visibility = View.GONE

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val spokenText = matches[0]
                    logMessage("Transcribed: \"$spokenText\"")
                    parseVoiceIntent(spokenText)
                } else {
                    logMessage("No speech captured. Please try again.")
                }
                cleanupSpeechRecognizer()
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun stopSpeechRecognition() {
        isListening = false
        binding.btnMic.setImageResource(android.R.drawable.ic_btn_speak_now)
        binding.tvMicStatus.text = "Tap microphone to speak"
        binding.layoutListeningOverlay.visibility = View.GONE
        cleanupSpeechRecognizer()
    }

    private fun cleanupSpeechRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // Ignore errors
        } finally {
            speechRecognizer = null
        }
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
        if (lowercaseText.contains("whatsapp") || lowercaseText.contains("வாட்ஸ்அப்") || lowercaseText.contains("வாட்ஸ்அப்பில்")) {
            parsedApp = "WhatsApp"
            parsedPackage = "com.whatsapp"
        } else if (lowercaseText.contains("gmail") || lowercaseText.contains("email") || lowercaseText.contains("correo") || lowercaseText.contains("ஜிமெயில்") || lowercaseText.contains("மின்னஞ்சல்")) {
            parsedApp = "Gmail"
            parsedPackage = "com.google.android.gm"
        } else if (lowercaseText.contains("sms") || lowercaseText.contains("message") || lowercaseText.contains("messages") || lowercaseText.contains("மெசேஜ்") || lowercaseText.contains("குறுஞ்செய்தி")) {
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
        } else if (binding.rbTamil.isChecked) {
            // Tamil parse: "[Recipient]க்கு [App]ல் [Message] என்று மெசேஜ் அனுப்பு"
            // E.g., "ரோஹித்திற்கு வாட்ஸ்அப்பில் நான் 10 நிமிடத்தில் வருகிறேன் என்று மெசேஜ் அனுப்பு"
            
            // Find recipient markers: "க்கு", "வுக்கு", "ற்கு"
            val kkuIndex = lowercaseText.indexOf("க்கு")
            val irkkuIndex = lowercaseText.indexOf("ற்கு")
            val recipientEndIndex = if (kkuIndex != -1) kkuIndex else irkkuIndex
            
            val endruIndex = lowercaseText.indexOf(" என்று")
            
            if (recipientEndIndex != -1) {
                // Extract and clean recipient
                var rawRecipient = text.substring(0, recipientEndIndex).trim()
                // Strip common leading action words if any
                val sendVerbs = listOf("அனுப்பு", "அனுப்புங்கள்", "மெசேஜ் செய்")
                for (verb in sendVerbs) {
                    if (rawRecipient.lowercase().startsWith(verb)) {
                        rawRecipient = rawRecipient.substring(verb.length).trim()
                    }
                }
                parsedRecipient = rawRecipient
                
                if (endruIndex != -1 && endruIndex > recipientEndIndex) {
                    // Extract message between recipient suffix and "என்று"
                    var rawMessage = text.substring(recipientEndIndex + if (kkuIndex != -1) 3 else 3, endruIndex).trim()
                    // Strip app names from message body
                    val appKeywords = listOf("வாட்ஸ்அப்பில்", "வாட்ஸ்அப்", "whatsapp", "ஜிமெயில்", "ஜிமெயிலில்", "மின்னஞ்சல்", "மெசேஜ்", "எஸ்எம்எஸ்")
                    for (keyword in appKeywords) {
                        rawMessage = rawMessage.replace(keyword, "", ignoreCase = true)
                    }
                    parsedMessage = rawMessage.trim()
                }
            }
        } else {
            // English parse
            // Pattern 1: "... saying [message]" or "... telling [message]"
            val sayingIndex = lowercaseText.indexOf(" saying ")
            val tellingIndex = lowercaseText.indexOf(" telling ")
            val splitIndexForSaying = if (sayingIndex != -1) sayingIndex else tellingIndex

            if (splitIndexForSaying != -1) {
                parsedMessage = text.substring(splitIndexForSaying + if (sayingIndex != -1) 8 else 9).trim()
                // Recipient is before saying/telling, after " to "
                val toIndex = lowercaseText.lastIndexOf(" to ", splitIndexForSaying)
                if (toIndex != -1) {
                    parsedRecipient = text.substring(toIndex + 4, splitIndexForSaying).trim()
                } else {
                    // Try to guess recipient between starting words and saying/telling
                    var rawRecipientPart = text.substring(0, splitIndexForSaying).trim()
                    val stripWords = listOf("send whatsapp to", "send message to", "send mail to", "send email to", "send", "whatsapp", "email", "gmail", "text")
                    for (word in stripWords) {
                        if (rawRecipientPart.lowercase().startsWith(word)) {
                            rawRecipientPart = rawRecipientPart.substring(word.length).trim()
                        }
                    }
                    parsedRecipient = rawRecipientPart
                }
            } else {
                // Pattern 2: "send [message] to [recipient] on/via/using [app]"
                val toIndex = lowercaseText.indexOf(" to ")
                val onIndex = lowercaseText.lastIndexOf(" on ")
                val viaIndex = lowercaseText.lastIndexOf(" via ")
                val appIndex = if (onIndex != -1) onIndex else viaIndex

                if (toIndex != -1 && appIndex != -1 && appIndex > toIndex) {
                    // Recipient is between " to " and " on/via "
                    parsedRecipient = text.substring(toIndex + 4, appIndex).trim()
                    
                    // Message is before " to ", minus starting "send "
                    var rawMsg = text.substring(0, toIndex).trim()
                    if (rawMsg.lowercase().startsWith("send ")) {
                        rawMsg = rawMsg.substring(5).trim()
                    }
                    parsedMessage = rawMsg
                } else if (toIndex != -1) {
                    // Pattern 3: "send [message] to [recipient]" (possibly ending with app keywords)
                    parsedRecipient = text.substring(toIndex + 4).trim()
                    
                    val appKeywords = listOf("on whatsapp", "on gmail", "on email", "on messages", "on sms", "via whatsapp")
                    for (keyword in appKeywords) {
                        if (parsedRecipient.lowercase().endsWith(" " + keyword)) {
                            parsedRecipient = parsedRecipient.substring(0, parsedRecipient.length - keyword.length - 1).trim()
                        }
                    }
                    
                    var rawMsg = text.substring(0, toIndex).trim()
                    if (rawMsg.lowercase().startsWith("send ")) {
                        rawMsg = rawMsg.substring(5).trim()
                    }
                    parsedMessage = rawMsg
                } else {
                    // Pattern 4: Fallback
                    parsedRecipient = "Unknown"
                    parsedMessage = text
                }
            }

            // Cleanup recipient from trailing app helper words
            val stripAppFromRecipient = listOf("on whatsapp", "via whatsapp", "on gmail", "on mail", "on email", "on sms", "on messages")
            for (word in stripAppFromRecipient) {
                if (parsedRecipient.lowercase().endsWith(" " + word)) {
                    parsedRecipient = parsedRecipient.substring(0, parsedRecipient.length - word.length - 1).trim()
                }
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
        VoiceActionAccessibilityService.pendingAction = PendingAction(
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
    }
}
