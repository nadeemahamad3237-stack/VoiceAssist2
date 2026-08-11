package com.example.voiceassist

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * Ye service phone ki screen par actions perform karti hai —
 * jaise ek insaan touch kar raha ho. User ko Settings > Accessibility
 * mein jaakar isse manually ON karna padta hai (Android ki security policy).
 */
class VoiceAccessibilityService : AccessibilityService() {

    companion object {
        // MainActivity is service ko command bhejne ke liye instance use karta hai
        var instance: VoiceAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Hume events sunne ki zaroorat nahi, hum sirf actions perform karte hain
    }

    override fun onInterrupt() {}

    /**
     * MainActivity yahan se bola hua text bhejta hai.
     * Ye function text ko samajh kar sahi action chalata hai.
     */
    fun executeCommand(rawText: String): String {
        val text = rawText.lowercase().trim()

        return when {
            "home" in text || "ghar" in text -> {
                performGlobalAction(GLOBAL_ACTION_HOME)
                "Home screen par ja raha hoon"
            }
            "wapas" in text || "back" in text || "peeche" in text -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
                "Wapas ja raha hoon"
            }
            "recent" in text -> {
                performGlobalAction(GLOBAL_ACTION_RECENTS)
                "Recent apps dikha raha hoon"
            }
            "notification" in text -> {
                performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                "Notifications khol raha hoon"
            }
            "scroll neeche" in text || "neeche scroll" in text || "scroll down" in text -> {
                performScroll(down = true)
                "Neeche scroll kar raha hoon"
            }
            "scroll upar" in text || "upar scroll" in text || "scroll up" in text -> {
                performScroll(down = false)
                "Upar scroll kar raha hoon"
            }
            "screenshot" in text -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                    "Screenshot le liya"
                } else {
                    "Ye Android version screenshot command support nahi karta"
                }
            }
            text.startsWith("dabao ") || text.startsWith("click ") || text.contains("par click karo") || text.contains("par tap karo") -> {
                val label = text
                    .replace("par click karo", "")
                    .replace("par tap karo", "")
                    .replace("dabao", "")
                    .replace("click", "")
                    .trim()
                clickOnLabel(label)
            }
            text.startsWith("likho ") || text.startsWith("type ") -> {
                val toType = text.replace("likho", "").replace("type", "").trim()
                typeInFocusedField(toType)
            }
            "kholo" in text || "khol" in text || "open" in text -> {
                val appName = text
                    .replace("kholo", "")
                    .replace("khol", "")
                    .replace("open", "")
                    .trim()
                openApp(appName)
            }
            else -> "Samajh nahi aaya: \"$rawText\". Phir se koshish karein."
        }
    }

    private fun performScroll(down: Boolean) {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val midX = width / 2f

        val startY = if (down) height * 0.7f else height * 0.3f
        val endY = if (down) height * 0.3f else height * 0.7f

        val path = Path().apply {
            moveTo(midX, startY)
            lineTo(midX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()

        dispatchGesture(gesture, null, null)
    }

    /**
     * Current screen (kisi bhi app) ke andar diye gaye naam se milta-julta
     * button/text dhundh kar usse click karta hai — jaise "dabao Send" ya
     * "dabao Like" kisi bhi app ke andar.
     */
    private fun clickOnLabel(label: String): String {
        if (label.isBlank()) return "Kya dabana hai, wo naam nahi suna"

        val root = rootInActiveWindow ?: return "Screen padh nahi paaya"
        val matchNode = findNodeByText(root, label)
            ?: return "\"$label\" naam ki cheez screen par nahi mili"

        val clickableNode = findClickableSelfOrAncestor(matchNode)
        return if (clickableNode != null) {
            clickableNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            "\"$label\" dabaa diya"
        } else {
            "\"$label\" mila lekin usko dabaya nahi ja sakta"
        }
    }

    /**
     * Currently focused text field mein type karta hai — jaise search box
     * ya message box mein bola gaya text daal deta hai.
     */
    private fun typeInFocusedField(toType: String): String {
        if (toType.isBlank()) return "Kya likhna hai, wo suna nahi"

        val root = rootInActiveWindow ?: return "Screen padh nahi paaya"
        val focused = findFocusedEditableNode(root)
            ?: return "Koi text box selected/focused nahi mila"

        val arguments = android.os.Bundle().apply {
            putCharSequence(
                android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                toType
            )
        }
        focused.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        return "\"$toType\" likh diya"
    }

    /** Node tree mein text ya content-description se match dhundhta hai (case-insensitive, partial match) */
    private fun findNodeByText(
        node: android.view.accessibility.AccessibilityNodeInfo,
        query: String
    ): android.view.accessibility.AccessibilityNodeInfo? {
        val nodeText = node.text?.toString()?.lowercase() ?: ""
        val nodeDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        val q = query.lowercase()

        if (nodeText.contains(q) || nodeDesc.contains(q)) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByText(child, query)
            if (result != null) return result
        }
        return null
    }

    /** Agar node khud clickable nahi hai, to uske parents mein clickable dhundhta hai */
    private fun findClickableSelfOrAncestor(
        node: android.view.accessibility.AccessibilityNodeInfo
    ): android.view.accessibility.AccessibilityNodeInfo? {
        var current: android.view.accessibility.AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    /** Screen mein jo text field abhi focused/editable hai use dhundhta hai */
    private fun findFocusedEditableNode(
        node: android.view.accessibility.AccessibilityNodeInfo
    ): android.view.accessibility.AccessibilityNodeInfo? {
        if (node.isFocused && node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFocusedEditableNode(child)
            if (result != null) return result
        }
        return null
    }

    private fun openApp(spokenName: String): String {
        val pm = packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        // Spoken naam ko installed apps ke labels se match karta hai
        val match = installedApps.firstOrNull { appInfo ->
            val label = pm.getApplicationLabel(appInfo).toString().lowercase()
            label.contains(spokenName) || spokenName.contains(label)
        }

        return if (match != null) {
            val launchIntent = pm.getLaunchIntentForPackage(match.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                "${pm.getApplicationLabel(match)} khol raha hoon"
            } else {
                "Ye app khola nahi ja sakta"
            }
        } else {
            "\"$spokenName\" naam ki app nahi mili"
        }
    }
}
