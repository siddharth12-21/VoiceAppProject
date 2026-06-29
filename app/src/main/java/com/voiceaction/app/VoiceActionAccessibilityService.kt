package com.voiceaction.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class VoiceActionAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "VoiceAccessibility"
        
        // Structure to pass automation instruction from Activity to Service
        data class PendingAction(
            val appPackage: String,
            val recipient: String,
            val message: String
        )

        var pendingAction: PendingAction? = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val action = pendingAction ?: return
        
        val packageName = event.packageName?.toString() ?: ""
        if (!packageName.startsWith(action.appPackage)) {
            return
        }

        Log.d(TAG, "onAccessibilityEvent: Target app foregrounded: $packageName")

        val rootNode = rootInActiveWindow ?: return
        
        // Execute app-specific automation
        when (action.appPackage) {
            "com.whatsapp" -> handleWhatsAppAutomation(rootNode, action)
            "com.google.android.gm" -> handleGmailAutomation(rootNode, action)
            "com.google.android.apps.messaging" -> handleSMSAutomation(rootNode, action)
        }
    }

    private fun handleWhatsAppAutomation(rootNode: AccessibilityNodeInfo, action: PendingAction) {
        // Step 1: Find text input field (WhatsApp chat input)
        // Usually has ID 'com.whatsapp:id/entry' or class 'android.widget.EditText'
        val inputNode = findNodeByViewId(rootNode, "com.whatsapp:id/entry") 
            ?: findNodeByClassName(rootNode, "android.widget.EditText")

        if (inputNode != null) {
            Log.d(TAG, "WhatsApp: Found input node. Injecting message.")
            
            // Set message text
            val arguments = Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                action.message
            )
            inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            
            // Step 2: Find send button
            // Usually has ID 'com.whatsapp:id/send' or content-desc 'Send' / 'Enviar' / 'भेजें'
            // We sleep briefly to allow WhatsApp UI to update the send button visibility
            try { Thread.sleep(200) } catch (e: Exception) {}
            
            val freshRoot = rootInActiveWindow ?: rootNode
            val sendButton = findNodeByViewId(freshRoot, "com.whatsapp:id/send")
                ?: findNodeByContentDescription(freshRoot, listOf("Send", "Enviar", "Envoyer", "भेजें", "भेजो"))
                ?: findClickableImageButtonAfterEditText(freshRoot)

            if (sendButton != null) {
                Log.d(TAG, "WhatsApp: Found Send button. Triggering click.")
                sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                
                // Clear state
                pendingAction = null
                
                // Go back to assistant app after action
                goBackToAssistant()
            } else {
                Log.w(TAG, "WhatsApp: Send button not found. Message is pasted, waiting for user.")
            }
        } else {
            Log.w(TAG, "WhatsApp: Input text field not found.")
        }
    }

    private fun handleGmailAutomation(rootNode: AccessibilityNodeInfo, action: PendingAction) {
        // For Gmail, we typically open it via Intent with pre-filled To and Body.
        // Therefore, we only need to click the Send button.
        // Gmail Send button ID is 'com.google.android.gm:id/send'
        val sendButton = findNodeByViewId(rootNode, "com.google.android.gm:id/send")
            ?: findNodeByContentDescription(rootNode, listOf("Send", "envoyer", "भेजें"))

        if (sendButton != null) {
            Log.d(TAG, "Gmail: Found Send button. Clicking.")
            sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            pendingAction = null
            goBackToAssistant()
        } else {
            Log.w(TAG, "Gmail: Send button not found.")
        }
    }

    private fun handleSMSAutomation(rootNode: AccessibilityNodeInfo, action: PendingAction) {
        // SMS app text field and send buttons
        val inputNode = findNodeByViewId(rootNode, "com.google.android.apps.messaging:id/message_compose_text_input")
            ?: findNodeByClassName(rootNode, "android.widget.EditText")

        if (inputNode != null) {
            Log.d(TAG, "SMS: Found message input. Pasting text.")
            val arguments = Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                action.message
            )
            inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

            try { Thread.sleep(200) } catch (e: Exception) {}
            val freshRoot = rootInActiveWindow ?: rootNode
            
            val sendButton = findNodeByViewId(freshRoot, "com.google.android.apps.messaging:id/send_message_button")
                ?: findNodeByContentDescription(freshRoot, listOf("Send", "Send SMS", "Enviar", "भेजें"))

            if (sendButton != null) {
                Log.d(TAG, "SMS: Found Send button. Clicking.")
                sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                pendingAction = null
                goBackToAssistant()
            } else {
                Log.w(TAG, "SMS: Send button not found.")
            }
        }
    }

    // Helper: Find node by View Resource ID
    private fun findNodeByViewId(node: AccessibilityNodeInfo, viewId: String): AccessibilityNodeInfo? {
        val nodes = node.findAccessibilityNodeInfosByViewId(viewId)
        if (nodes != null && nodes.isNotEmpty()) {
            return nodes[0]
        }
        return null
    }

    // Helper: Find node by Class Name (recursive traversal)
    private fun findNodeByClassName(node: AccessibilityNodeInfo?, className: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.className?.toString() == className) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findNodeByClassName(child, className)
            if (result != null) return result
        }
        return null
    }

    // Helper: Find node by matching content descriptions
    private fun findNodeByContentDescription(node: AccessibilityNodeInfo?, keywords: List<String>): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        for (keyword in keywords) {
            if (desc.contains(keyword.lowercase())) {
                return node
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findNodeByContentDescription(child, keywords)
            if (result != null) return result
        }
        return null
    }

    // Helper: Fallback to scan for typical clickable icons next to input fields
    private fun findClickableImageButtonAfterEditText(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        // Traverses the tree and returns the first clickable ImageButton or ImageView that is likely the send button.
        if ((node.className?.toString()?.contains("ImageButton") == true || 
             node.className?.toString()?.contains("ImageView") == true) && node.isClickable) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findClickableImageButtonAfterEditText(child)
            if (result != null) return result
        }
        return null
    }

    private fun goBackToAssistant() {
        try {
            Thread.sleep(800) // Brief delay to let the target app send completes
        } catch (e: Exception) {}
        
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt: Service interrupted")
    }
}
