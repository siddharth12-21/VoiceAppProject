# VoiceActionAssistant - AI-Native Multilingual Voice Router 🎙️🤖

An accessibility-driven Android application designed to receive spoken voice commands in multiple languages (English, Spanish, Tamil), transcribe them locally, parse the communication routing intent (target app, contact recipient, message content), and automate the typing and sending process using Android's **Accessibility Service API**.

---

> [!NOTE]
> **AI-NATIVE METADATA**
> This repository is structured to be instantly readable and editable by AI coding assistants. If you are an LLM/agent pair-programming on this project, refer to [AGENTS.md](./AGENTS.md) for style rules, system prompt extensions, and integration playbooks.

---

## 🛠️ Architecture & Core Components

This application utilizes a simple two-phase pipeline to perform cross-app messaging without requiring deep APIs or visual coordinates:

```
[Voice Input] ──> [SpeechRecognizer] ──> [Multilingual Parser] ──> [Confirmation UI]
                                                                        │
                                                                        ▼ (Intent Trigger)
[MainActivity] <── (Go Back) ── [Accessibility Event] <── [Target App (WhatsApp/SMS)]
```

### Key Modules:
- **Speech Capture & Config:** [MainActivity.kt](./app/src/main/java/com/voiceaction/app/MainActivity.kt) initializes the system microphone, handles the runtime `RECORD_AUDIO` permission, sets up the speech intent with localized codes (`en-US`, `es-ES`, `ta-IN`), and processes transcribing callbacks.
- **Intent Parsing Engine:** Located in `MainActivity.parseVoiceIntent()`. It runs a rule-based parser that identifies verbs, target apps, and recipients in English, Spanish, or Tamil, extracting the core message text in its original spoken language.
- **UI Automation Service:** [VoiceActionAccessibilityService.kt](./app/src/main/java/com/voiceaction/app/VoiceActionAccessibilityService.kt) is an Android `AccessibilityService` that monitors foreground state changes. When triggered, it walks the target app's window node tree, pastes the message text, and clicks the send button.
- **Service Configuration:** [accessibility_service_config.xml](./app/src/main/res/xml/accessibility_service_config.xml) restricts service events to WhatsApp, Gmail, and Google Messages to maximize performance and protect user privacy.

---

## 🚀 Setting Up & Deploying

### Prerequisites:
- Android Studio Iguana (or newer)
- Gradle 8.2+
- A physical Android device or emulator with Google Play Services (required for Android SpeechRecognizer).

### Installation Steps:
1. Clone this repository to your system.
2. Open the project in Android Studio.
3. Sync Gradle and run the `:app` configuration on your device.
4. **Grant Permissions:**
   - On launch, approve the **Microphone** permission.
   - Go to `Settings -> Accessibility -> Installed Apps -> Voice Action Assistant` and toggle the switch to **On**.

---

## 🗣️ Supported Commands Playbook

You can speak to the assistant using natural phrases. Here are examples of successfully parsed triggers:

### 🇺🇸 English
- *"Send a WhatsApp to Mom saying I will be home in 10 minutes"*
- *"Send an email to John saying please review the updated plan"*
- *"Send a message to Sarah saying hello"*

### 🇪🇸 Spanish (Español)
- *"Enviar un WhatsApp a Mamá diciendo que llegaré en 10 minutos"*
- *"Enviar un correo a Juan diciendo por favor revisa el plan"*
- *"Enviar mensaje a Pedro diciendo hola"*

### 🇮🇳 Tamil (தமிழ்)
- *"ரோஹித்திற்கு வாட்ஸ்அப்பில் நான் 10 நிமிடத்தில் வருகிறேன் என்று மெசேஜ் அனுப்பு"*
- *"அப்பாவுக்கு எல்லாம் நலம் என்று மெசேஜ் அனுப்பு"*

---

## 🤝 Roadmap & AI Tasks
If you are adding features, please prioritize:
1. **Contact Directory Sync:** Integrate Android's `ContactsContract` in `MainActivity` to map phonetic contact names to actual phone numbers or email addresses, allowing direct deep-linking.
2. **Additional App Integrations:** Expand the Accessibility Service to target Telegram (`org.telegram.messenger`) and Slack (`com.Slack`).
3. **Dynamic Language Detection:** Integrate an on-device machine learning language ID model (such as ML Kit Language ID) to detect the language automatically without radio buttons.
