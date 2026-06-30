# AI-Native Codebase Guidelines (AGENTS.md) 🤖💻

This file provides system prompt extensions, instructions, and context for AI coding agents working on the **VoiceActionAssistant** project. Read this before modifying any code.

---

## 🧭 Codebase Context Map

Use this directory and class mapping to navigate changes efficiently:

- **Build Systems:**
  - [settings.gradle.kts](./settings.gradle.kts) - Multi-module configuration.
  - [build.gradle.kts](./build.gradle.kts) - Root plugin definitions.
  - [gradle/libs.versions.toml](./gradle/libs.versions.toml) - Dependency versions catalog.
  - [app/build.gradle.kts](./app/build.gradle.kts) - App-level plugins, target SDK, and dependencies.
- **App Layout & Manifests:**
  - [AndroidManifest.xml](./app/src/main/AndroidManifest.xml) - Permissions, Queries, Activity/Service registry.
  - [accessibility_service_config.xml](./app/src/main/res/xml/accessibility_service_config.xml) - Monitored packages and flags.
  - [activity_main.xml](./app/src/main/res/layout/activity_main.xml) - Speech UI and Confirmation card layouts.
- **Kotlin Sources:**
  - [MainActivity.kt](./app/src/main/java/com/voiceaction/app/MainActivity.kt) - SpeechRecognizer, parsing NLP rules, and intent routing.
  - [VoiceActionAccessibilityService.kt](./app/src/main/java/com/voiceaction/app/VoiceActionAccessibilityService.kt) - Accessibility event loops and screen node interactions.

---

## 🛡️ Coding Rules for Agents

1. **Accessibility View Inspection Robustness:**
   - App layouts change over time. When writing code to search the node hierarchy in [VoiceActionAccessibilityService.kt](./app/src/main/java/com/voiceaction/app/VoiceActionAccessibilityService.kt), never rely on a single resource ID.
   - Always chain fallbacks: Look for the specific Resource ID -> Look for class name (`EditText` / `ImageButton`) -> Traverse descendants -> Look for content descriptions.
2. **Keep Business Logic in MainActivity:**
   - Do not perform speech recognition or parsing inside the `AccessibilityService`. Keep the service focused strictly on executing UI interactions (pasting and clicking). Communication between the two components must flow through the process companion object (`VoiceActionAccessibilityService.pendingAction`).
3. **Respect Speech Recognition Lifecycle:**
   - Always destroy and release the `SpeechRecognizer` in `onDestroy` of `MainActivity`.
   - Ensure you check and handle runtime permission requests (`RECORD_AUDIO`) before launching the recognizer to prevent runtime crashes.
4. **Multilingual Parsing Consistency:**
   - When modifying or adding parsing rules in `MainActivity.parseVoiceIntent()`, ensure that you maintain compatibility for all three primary languages (English, Spanish, Tamil). Test modifications using the mock phrases outlined in the [README.md](./README.md).

---

## 📝 Playbook: Adding a New Messaging App

To integrate support for a new third-party messaging app (e.g., **Telegram**):

1. **Query Visibility:** Add the package name (e.g., `org.telegram.messenger`) to the `<queries>` block in [AndroidManifest.xml](./app/src/main/AndroidManifest.xml).
2. **Accessibility Scope:** Add the package name to the `android:packageNames` comma-separated list in [accessibility_service_config.xml](./app/src/main/res/xml/accessibility_service_config.xml).
3. **Parser Matching:** Update `MainActivity.parseVoiceIntent()` to detect the app's name in speech transcripts and set `parsedPackage` to `org.telegram.messenger`.
4. **Service Automation Handler:**
   - In [VoiceActionAccessibilityService.kt](./app/src/main/java/com/voiceaction/app/VoiceActionAccessibilityService.kt), add a new branch to the event handler:
     ```kotlin
     "org.telegram.messenger" -> handleTelegramAutomation(rootNode, action)
     ```
   - Write `handleTelegramAutomation(rootNode, action)` to find Telegram's chat text field (typically `org.telegram.messenger:id/message_input` or class `android.widget.EditText`) and its send button (typically content description "Send" or a clickable button class next to the input), set text, and click.
