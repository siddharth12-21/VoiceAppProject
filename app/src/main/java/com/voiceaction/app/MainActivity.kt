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


    // ActivityResultLauncher for native system speech recognition overlay dialog
    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        binding.btnMic.setImageResource(android.R.drawable.ic_btn_speak_now)
        binding.tvMicStatus.text = "Tap microphone to speak"
        
        if (result.resultCode == RESULT_OK && result.data != null) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val spokenText = matches[0]
                logMessage("Transcribed: \"$spokenText\"")
                parseVoiceIntent(spokenText)
            } else {
                logMessage("No speech captured from system dialog.")
            }
        } else {
            logMessage("System speech dialog cancelled or failed.")
        }
    }

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

        binding.suggestCard1.setOnClickListener {
            logMessage("Suggestion clicked: \"Send hi to Mummy\"")
            parseVoiceIntent("Send hi to Mummy")
        }
        binding.suggestCard2.setOnClickListener {
            logMessage("Suggestion clicked: \"Enviar hola a Mamá\"")
            binding.rbSpanish.isChecked = true
            parseVoiceIntent("Enviar hola a Mamá")
        }
        binding.suggestCard3.setOnClickListener {
            logMessage("Suggestion clicked: \"Send ready to boss\"")
            parseVoiceIntent("Send ready to boss")
        }
        binding.suggestCard4.setOnClickListener {
            logMessage("Suggestion clicked: \"Telegram hello to Boss\"")
            parseVoiceIntent("Telegram hello to Boss")
        }
        binding.suggestCard5.setOnClickListener {
            logMessage("Suggestion clicked: \"Email update to Team\"")
            parseVoiceIntent("Email update to Team")
        }
        binding.suggestCard6.setOnClickListener {
            logMessage("Suggestion clicked: \"SMS I am driving\"")
            parseVoiceIntent("SMS I am driving")
        }
        
        binding.favContactMummy.setOnClickListener {
            logMessage("Favorite clicked: Mummy")
            parsedRecipient = "Mummy"
            parsedApp = "WhatsApp"
            parsedPackage = "com.whatsapp"
            parsedMessage = ""
            
            binding.tvConfirmRecipient.text = parsedRecipient
            binding.tvConfirmApp.text = parsedApp
            binding.etConfirmMessage.setText("")
            binding.cardConfirmation.visibility = View.VISIBLE
        }

        binding.favContactDad.setOnClickListener {
            logMessage("Favorite clicked: Dad")
            parsedRecipient = "Dad"
            parsedApp = "SMS"
            parsedPackage = "com.google.android.apps.messaging"
            parsedMessage = ""
            
            binding.tvConfirmRecipient.text = parsedRecipient
            binding.tvConfirmApp.text = parsedApp
            binding.etConfirmMessage.setText("")
            binding.cardConfirmation.visibility = View.VISIBLE
        }

        binding.favContactBoss.setOnClickListener {
            logMessage("Favorite clicked: Boss")
            parsedRecipient = "Boss"
            parsedApp = "Telegram"
            parsedPackage = "org.telegram.messenger"
            parsedMessage = ""
            
            binding.tvConfirmRecipient.text = parsedRecipient
            binding.tvConfirmApp.text = parsedApp
            binding.etConfirmMessage.setText("")
            binding.cardConfirmation.visibility = View.VISIBLE
        }
        
        loadHistoryFromPrefs()
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
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            val languageCode = when {
                binding.rbSpanish.isChecked -> "es-ES"
                binding.rbTamil.isChecked -> "ta-IN"
                else -> "en-US"
            }
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your command clearly...")
        }
        try {
            isListening = true
            binding.tvMicStatus.text = "Listening... Speak now"
            binding.btnMic.setImageResource(android.R.drawable.presence_audio_online)
            logMessage("Microphone active. Opening system speech overlay...")
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            isListening = false
            binding.btnMic.setImageResource(android.R.drawable.ic_btn_speak_now)
            binding.tvMicStatus.text = "Error starting speech dialog"
            logMessage("Speech recognition error: ${e.localizedMessage}")
            Toast.makeText(this, "Speech recognition is not supported on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopSpeechRecognition() {
        isListening = false
        binding.btnMic.setImageResource(android.R.drawable.ic_btn_speak_now)
        binding.tvMicStatus.text = "Tap microphone to speak"
    }

    // Multilingual Intent Parser Engine
    private fun parseVoiceIntent(text: String) {
        val lowercaseText = text.lowercase(Locale.getDefault())

        // Default settings
        var appResult = "WhatsApp"
        var packageResult = "com.whatsapp"
        var recipientResult = "Unknown"
        var messageResult = text

        // 1. Detect target application
        if (lowercaseText.contains("whatsapp") || lowercaseText.contains("வாட்ஸ்அப்") || lowercaseText.contains("வாட்ஸ்அப்பில்")) {
            appResult = "WhatsApp"
            packageResult = "com.whatsapp"
        } else if (lowercaseText.contains("telegram") || lowercaseText.contains("டெலிகிராம்")) {
            appResult = "Telegram"
            packageResult = "org.telegram.messenger"
        } else if (lowercaseText.contains("gmail") || lowercaseText.contains("email") || lowercaseText.contains("correo") || lowercaseText.contains("ஜிமெயில்") || lowercaseText.contains("மின்னஞ்சல்")) {
            appResult = "Gmail"
            packageResult = "com.google.android.gm"
        } else if (lowercaseText.contains("sms") || lowercaseText.contains("message") || lowercaseText.contains("messages") || lowercaseText.contains("text") || lowercaseText.contains("மெசேஜ்") || lowercaseText.contains("குறுஞ்செய்தி")) {
            appResult = "SMS"
            packageResult = "com.google.android.apps.messaging"
        }

        // 2. Multilingual recipient and message extraction
        if (binding.rbSpanish.isChecked) {
            // Spanish parse: "Enviar WhatsApp a [Juan] diciendo [mensaje]"
            val aIndex = lowercaseText.indexOf(" a ")
            val diciendoIndex = lowercaseText.indexOf(" diciendo ")
            val queDiceIndex = lowercaseText.indexOf(" que dice ")
            val splitIndex = if (diciendoIndex != -1) diciendoIndex else queDiceIndex

            if (aIndex != -1 && splitIndex != -1 && splitIndex > aIndex) {
                recipientResult = text.substring(aIndex + 3, splitIndex).trim()
                messageResult = text.substring(splitIndex + 10).trim()
            } else if (aIndex != -1) {
                recipientResult = text.substring(aIndex + 3).trim()
                var rawMsg = text.substring(0, aIndex).trim()
                val stripVerbs = listOf("enviar whatsapp", "enviar mensaje", "enviar correo", "enviar", "escribir")
                for (verb in stripVerbs) {
                    if (rawMsg.lowercase().startsWith(verb)) {
                        rawMsg = rawMsg.substring(verb.length).trim()
                    }
                }
                messageResult = rawMsg
            }
        } else if (binding.rbTamil.isChecked) {
            // Tamil parse: "[Recipient]க்கு [App]ல் [Message] என்று மெசேஜ் அனுப்பு"
            val kkuIndex = lowercaseText.indexOf("க்கு")
            val irkkuIndex = lowercaseText.indexOf("ற்கு")
            val recipientEndIndex = if (kkuIndex != -1) kkuIndex else irkkuIndex
            val endruIndex = lowercaseText.indexOf(" என்று")
            
            if (recipientEndIndex != -1) {
                var rawRecipient = text.substring(0, recipientEndIndex).trim()
                val sendVerbs = listOf("அனுப்பு", "அனுப்புங்கள்", "மெசேஜ் செய்")
                for (verb in sendVerbs) {
                    if (rawRecipient.lowercase().startsWith(verb)) {
                        rawRecipient = rawRecipient.substring(verb.length).trim()
                    }
                }
                recipientResult = rawRecipient
                
                if (endruIndex != -1 && endruIndex > recipientEndIndex) {
                    var rawMessage = text.substring(recipientEndIndex + 3, endruIndex).trim()
                    val appKeywords = listOf("வாட்ஸ்அப்பில்", "வாட்ஸ்அப்", "whatsapp", "ஜிமெயில்", "ஜிமெயிலில்", "மின்னஞ்சல்", "மெசேஜ்", "எஸ்எம்எஸ்")
                    for (keyword in appKeywords) {
                        rawMessage = rawMessage.replace(keyword, "", ignoreCase = true)
                    }
                    messageResult = rawMessage.trim()
                }
            }
        } else {
            // English parse
            // Pattern 1: "... saying [message]" or "... telling [message]" or "... that [message]"
            val sayingIndex = lowercaseText.indexOf(" saying ")
            val tellingIndex = lowercaseText.indexOf(" telling ")
            val thatIndex = lowercaseText.indexOf(" that ")
            
            var splitIndexForSaying = -1
            var splitterLen = 0
            
            if (sayingIndex != -1) {
                splitIndexForSaying = sayingIndex
                splitterLen = 8
            } else if (tellingIndex != -1) {
                splitIndexForSaying = tellingIndex
                splitterLen = 9
            } else if (thatIndex != -1) {
                splitIndexForSaying = thatIndex
                splitterLen = 6
            }

            if (splitIndexForSaying != -1) {
                messageResult = text.substring(splitIndexForSaying + splitterLen).trim()
                
                val beforePart = text.substring(0, splitIndexForSaying).trim()
                val toIndex = beforePart.lowercase().lastIndexOf(" to ")
                
                if (toIndex != -1) {
                    recipientResult = beforePart.substring(toIndex + 4).trim()
                } else {
                    var rawRecipientPart = beforePart
                    val stripWords = listOf("send whatsapp to", "send telegram to", "send message to", "send mail to", "send email to", "send to", "tell", "whatsapp", "telegram", "email", "gmail", "text", "message", "sms", "ping", "buzz")
                    for (word in stripWords) {
                        if (rawRecipientPart.lowercase().startsWith(word)) {
                            rawRecipientPart = rawRecipientPart.substring(word.length).trim()
                        }
                    }
                    recipientResult = rawRecipientPart
                }
            } else {
                // Pattern 2: "send [message] to [recipient] on/via/using [app]"
                val toIndex = lowercaseText.indexOf(" to ")
                val onIndex = lowercaseText.lastIndexOf(" on ")
                val viaIndex = lowercaseText.lastIndexOf(" via ")
                val appIndex = if (onIndex != -1) onIndex else viaIndex

                if (toIndex != -1 && appIndex != -1 && appIndex > toIndex) {
                    recipientResult = text.substring(toIndex + 4, appIndex).trim()
                    
                    var rawMsg = text.substring(0, toIndex).trim()
                    val stripVerbs = listOf("send whatsapp", "send telegram", "send message", "send sms", "send text", "send", "tell", "whatsapp", "telegram", "sms", "text", "message")
                    for (verb in stripVerbs) {
                        if (rawMsg.lowercase().startsWith(verb)) {
                            rawMsg = rawMsg.substring(verb.length).trim()
                        }
                    }
                    messageResult = rawMsg
                } else if (toIndex != -1) {
                    // Pattern 3: "send [message] to [recipient]" (possibly ending with app keywords)
                    recipientResult = text.substring(toIndex + 4).trim()
                    
                    val appKeywords = listOf("on whatsapp", "via whatsapp", "on telegram", "via telegram", "on gmail", "on email", "on messages", "on sms")
                    for (keyword in appKeywords) {
                        if (recipientResult.lowercase().endsWith(" " + keyword)) {
                            recipientResult = recipientResult.substring(0, recipientResult.length - keyword.length - 1).trim()
                        }
                    }
                    
                    var rawMsg = text.substring(0, toIndex).trim()
                    val stripVerbs = listOf("send whatsapp", "send telegram", "send message", "send sms", "send text", "send", "tell", "whatsapp", "telegram", "sms", "text", "message")
                    for (verb in stripVerbs) {
                        if (rawMsg.lowercase().startsWith(verb)) {
                            rawMsg = rawMsg.substring(verb.length).trim()
                        }
                    }
                    messageResult = rawMsg
                } else {
                    // Pattern 4: "[Verb] [Recipient] [Message]"
                    // e.g. "WhatsApp Mummy hi" or "Text John call me"
                    val words = text.split(Regex("\\s+"))
                    if (words.size >= 3) {
                        val firstWord = words[0].lowercase()
                        val verbs = listOf("whatsapp", "telegram", "text", "sms", "mail", "email", "message", "ping", "tell")
                        if (verbs.contains(firstWord)) {
                            recipientResult = words[1]
                            messageResult = words.drop(2).joinToString(" ")
                        }
                    }
                }
            }

            // Cleanup recipient from trailing app helper words
            val stripAppFromRecipient = listOf("on whatsapp", "via whatsapp", "on telegram", "via telegram", "on gmail", "on mail", "on email", "on sms", "on messages")
            for (word in stripAppFromRecipient) {
                if (recipientResult.lowercase().endsWith(" " + word)) {
                    recipientResult = recipientResult.substring(0, recipientResult.length - word.length - 1).trim()
                }
            }
        }

        // Post-parsing cleanups
        recipientResult = recipientResult.replace(Regex("[.,?!;:]$"), "").trim()
        val lowercaseRecipient = recipientResult.lowercase()
        if (lowercaseRecipient.startsWith("a ")) recipientResult = recipientResult.substring(2).trim()
        if (lowercaseRecipient.startsWith("the ")) recipientResult = recipientResult.substring(4).trim()
        if (recipientResult.lowercase().startsWith("to ")) {
            recipientResult = recipientResult.substring(3).trim()
        }

        if (messageResult.startsWith("\"") && messageResult.endsWith("\"")) {
            messageResult = messageResult.substring(1, messageResult.length - 1)
        }

        parsedRecipient = recipientResult
        parsedApp = appResult
        parsedPackage = packageResult
        parsedMessage = messageResult

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
                addHistoryItem(parsedRecipient, finalMessage, parsedApp)
            } else {
                Toast.makeText(this, "$parsedApp is not installed on this device.", Toast.LENGTH_SHORT).show()
                logMessage("Error: Launcher intent for $parsedPackage not found.")
            }
        } catch (e: Exception) {
            logMessage("Error opening app: ${e.localizedMessage}")
        }
    }

    private fun logMessage(msg: String) {
        android.util.Log.d("VoiceAction", msg)
    }

    private fun addHistoryItem(recipient: String, message: String, appName: String) {
        saveHistoryToPrefs(recipient, message, appName)
        createHistoryItemView(recipient, message, appName, index = 0)
    }

    private fun createHistoryItemView(recipient: String, message: String, appName: String, index: Int) {
        binding.tvHistoryPlaceholder.visibility = View.GONE
        
        val cardView = androidx.cardview.widget.CardView(this).apply {
            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 24)
            layoutParams = params
            radius = 36f  // Sleek rounded corners
            setCardBackgroundColor(android.graphics.Color.parseColor("#171324"))
            cardElevation = 0f
        }
        
        val itemLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(32, 24, 32, 24)
        }
        
        val iconText = android.widget.TextView(this).apply {
            text = "💬"
            textSize = 20f
            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = 24
            layoutParams = params
        }
        
        val textLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            )
        }
        
        val titleText = android.widget.TextView(this).apply {
            text = "Sent to $recipient via $appName"
            setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
            textSize = 14f
            val font = androidx.core.content.res.ResourcesCompat.getFont(this@MainActivity, R.font.fredoka)
            setTypeface(font, android.graphics.Typeface.BOLD)
        }
        
        val bodyText = android.widget.TextView(this).apply {
            text = "\"$message\""
            setTextColor(android.graphics.Color.parseColor("#D1D5DB"))
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            val font = androidx.core.content.res.ResourcesCompat.getFont(this@MainActivity, R.font.fredoka)
            setTypeface(font, android.graphics.Typeface.BOLD)
        }
        
        textLayout.addView(titleText)
        textLayout.addView(bodyText)
        
        val badgeCard = androidx.cardview.widget.CardView(this).apply {
            radius = 16f
            setCardBackgroundColor(android.graphics.Color.parseColor("#14B8A6"))
            cardElevation = 0f
        }
        
        val badgeText = android.widget.TextView(this).apply {
            text = "SENT"
            setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
            textSize = 9f
            val font = androidx.core.content.res.ResourcesCompat.getFont(this@MainActivity, R.font.fredoka)
            setTypeface(font, android.graphics.Typeface.BOLD)
            setPadding(16, 6, 16, 6)
        }
        badgeCard.addView(badgeText)
        
        itemLayout.addView(iconText)
        itemLayout.addView(textLayout)
        itemLayout.addView(badgeCard)
        
        cardView.addView(itemLayout)
        
        if (index >= 0 && index <= binding.layoutRecentHistory.childCount) {
            binding.layoutRecentHistory.addView(cardView, index)
        } else {
            binding.layoutRecentHistory.addView(cardView)
        }
    }

    private fun saveHistoryToPrefs(recipient: String, message: String, appName: String) {
        val sharedPrefs = getSharedPreferences("voice_history", Context.MODE_PRIVATE)
        val currentStr = sharedPrefs.getString("items_list", "") ?: ""
        val newItem = "$recipient|$message|$appName"
        val newStr = if (currentStr.isEmpty()) newItem else "$newItem###$currentStr"
        sharedPrefs.edit().putString("items_list", newStr).apply()
    }

    private fun loadHistoryFromPrefs() {
        val sharedPrefs = getSharedPreferences("voice_history", Context.MODE_PRIVATE)
        val currentStr = sharedPrefs.getString("items_list", "") ?: ""
        if (currentStr.isNotEmpty()) {
            binding.tvHistoryPlaceholder.visibility = View.GONE
            val items = currentStr.split("###")
            // Populate in reverse order to keep latest at the top since we are appending
            for (item in items) {
                val parts = item.split("|")
                if (parts.size >= 3) {
                    createHistoryItemView(parts[0], parts[1], parts[2], index = -1)
                }
            }
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
