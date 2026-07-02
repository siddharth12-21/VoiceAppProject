package com.voiceaction.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

// Structure to pass automation instruction from Activity to Service
data class PendingAction(
    val appPackage: String,
    val recipient: String,
    val message: String
)

class VoiceActionAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "VoiceAccessibility"
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
        // Usually has ID 'com.whatsapp:id/entry'
        val inputNode = findNodeByViewId(rootNode, "com.whatsapp:id/entry")

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
            try { Thread.sleep(300) } catch (e: Exception) {}
            
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
            // We are not in the chat screen (inputNode is null).
            // Try to find the search button or search bar to open the chat automatically!
            Log.d(TAG, "WhatsApp: Chat input not found. Checking if search can be opened...")
            
            // 1. Check if search input field is already active (by ID, or any EditText on this screen since there's no chat input)
            val searchInputNode = findNodeByViewId(rootNode, "com.whatsapp:id/search_src_text")
                ?: findNodeByViewId(rootNode, "com.whatsapp:id/search_input")
                ?: findNodeByViewId(rootNode, "com.whatsapp:id/search_text")
                ?: findNodeByClassName(rootNode, "android.widget.EditText")

            if (searchInputNode != null) {
                val currentText = searchInputNode.text?.toString() ?: ""
                if (!currentText.equals(action.recipient, ignoreCase = true)) {
                    Log.d(TAG, "WhatsApp: Active search bar found. Typing: ${action.recipient}")
                    val arguments = Bundle()
                    arguments.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        action.recipient
                    )
                    searchInputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                }
                
                // 2. Click the matching contact row from search results (ignoring the search bar itself!)
                val freshRoot = rootInActiveWindow ?: rootNode
                val contactNode = findContactResultNode(freshRoot, action.recipient)
                if (contactNode != null) {
                    Log.d(TAG, "WhatsApp: Found contact row text. Clicking to open chat.")
                    clickNodeOrParent(contactNode)
                } else {
                    Log.w(TAG, "WhatsApp: Contact row text matching '${action.recipient}' not found in search results yet.")
                }
            } else {
                // 3. Search input is not active. Find and click the Search icon/button.
                val searchBtn = findNodeByViewId(rootNode, "com.whatsapp:id/menuitem_search")
                    ?: findNodeByContentDescription(rootNode, listOf("Search", "Buscar", "தேடு", "Search contacts"))
                    ?: findNodeByText(rootNode, "Search")
                    ?: findNodeByText(rootNode, "Buscar")
                    ?: findNodeByText(rootNode, "தேடு")
                
                if (searchBtn != null) {
                    Log.d(TAG, "WhatsApp: Found search button/bar. Clicking to activate.")
                    clickNodeOrParent(searchBtn)
                } else {
                    Log.w(TAG, "WhatsApp: Main chat input and search icon/bar not found on screen.")
                }
            }
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

    private fun findNodeByText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val nodeText = node.text?.toString()?.lowercase() ?: ""
        if (nodeText.contains(text.lowercase())) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findNodeByText(child, text)
            if (result != null) return result
        }
        return null
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        val parent = node.parent
        if (parent != null) {
            val clicked = clickNodeOrParent(parent)
            if (clicked) return true
        }
        return false
    }
    private fun findContactResultNode(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        
        // Ignore EditText nodes so we don't accidentally match the search query input box itself!
        val className = node.className?.toString() ?: ""
        if (!className.contains("EditText")) {
            val nodeText = node.text?.toString()?.lowercase() ?: ""
            if (nodeText.contains(text.lowercase())) {
                return node
            }
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findContactResultNode(child, text)
            if (result != null) return result
        }
        return null
    }
    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt: Service interrupted")
    }
}
