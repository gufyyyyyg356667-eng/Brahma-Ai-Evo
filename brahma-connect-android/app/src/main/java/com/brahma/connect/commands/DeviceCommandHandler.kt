package com.brahma.connect.commands

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.brahma.connect.core.BrahmaConnectCapabilities
import com.brahma.connect.core.CommandResult
import com.brahma.connect.device.AndroidDeviceInfoProvider
import com.brahma.connect.notification.BrahmaNotificationListenerService

class DeviceCommandHandler(private val context: Context) {
    companion object {
        private const val SMS_STATUS_TIMEOUT_SECONDS = 20L
    }

    private val infoProvider = AndroidDeviceInfoProvider(context)

    fun handle(action: String, parameters: Map<String, Any?>): CommandResult {
        return when (action.lowercase()) {
            "get_device_info" -> getDeviceInfo()
            "get_battery" -> getBattery()
            "flashlight_on" -> flashlight(true)
            "flashlight_off" -> flashlight(false)
            "launch_app" -> launchApp(parameters)
            "open_url" -> openUrl(parameters)
            "volume_get" -> volumeGet()
            "volume_set" -> volumeSet(parameters)
            "unlock_phone" -> unlockPhone(parameters)
            "file_list" -> fileList(parameters)
            "file_read" -> fileRead(parameters)
            "file_write" -> fileWrite(parameters)
            "file_delete" -> fileDelete(parameters)
            "ui_dump" -> uiDump()
            "ui_tap" -> uiTap(parameters)
            "ui_swipe" -> uiSwipe(parameters)
            "ui_type" -> uiType(parameters)
            "send_sms" -> sendSms(parameters)
            "make_call" -> makeCall(parameters)
            "notification_list" -> notificationList(parameters)
            else -> CommandResult(false, errorCode = "UNKNOWN_COMMAND", error = "Unsupported command: $action")
        }
    }

    private fun getDeviceInfo(): CommandResult {
        val battery = infoProvider.batterySnapshot()
        return CommandResult(
            success = true,
            data = mapOf(
                "device_id" to null,
                "device_name" to (Build.MODEL ?: "Android"),
                "platform" to "android",
                "android_version" to (Build.VERSION.RELEASE ?: "Unknown"),
                "agent_version" to "1.0.0",
                "model" to (Build.MODEL ?: "Android"),
                "battery" to battery.first,
                "charging" to battery.second,
                "wifi_state" to if (infoProvider.wifiEnabled()) "connected" else "disconnected",
                "capabilities" to BrahmaConnectCapabilities.INITIAL,
            ),
        )
    }

    private fun getBattery(): CommandResult {
        val battery = infoProvider.batterySnapshot()
        return CommandResult(true, data = mapOf("percentage" to battery.first, "charging" to battery.second))
    }

    private fun flashlight(enabled: Boolean): CommandResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return CommandResult(
                false,
                errorCode = "CAMERA_PERMISSION_REQUIRED",
                error = "Camera permission is required to control the flashlight.",
            )
        }
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return CommandResult(false, errorCode = "FLASHLIGHT_UNAVAILABLE", error = "Camera service unavailable.")
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            runCatching {
                val chars = manager.getCameraCharacteristics(id)
                val flash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                flash && (facing == CameraCharacteristics.LENS_FACING_BACK || facing == CameraCharacteristics.LENS_FACING_EXTERNAL)
            }.getOrDefault(false)
        } ?: return CommandResult(false, errorCode = "FLASHLIGHT_UNAVAILABLE", error = "No flashlight on this device.")
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val maxStrength = runCatching {
                    manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                }.getOrDefault(1)
                if (enabled && maxStrength > 1) {
                    manager.turnOnTorchWithStrengthLevel(cameraId, maxStrength)
                } else {
                    manager.setTorchMode(cameraId, enabled)
                }
            } else {
                manager.setTorchMode(cameraId, enabled)
            }
            CommandResult(true, data = mapOf("flashlight" to if (enabled) "on" else "off"))
        } catch (security: SecurityException) {
            CommandResult(false, errorCode = "FLASHLIGHT_UNAVAILABLE", error = security.message ?: "Flashlight permission denied.")
        } catch (exc: Exception) {
            CommandResult(false, errorCode = "FLASHLIGHT_UNAVAILABLE", error = exc.message ?: "Flashlight unavailable.")
        }
    }

    private fun launchApp(parameters: Map<String, Any?>): CommandResult {
        val requested = listOf("package", "package_name", "app_name", "app", "name")
            .firstNotNullOfOrNull { key -> parameters[key]?.toString()?.trim() }
            .orEmpty()
        if (requested.isBlank()) {
            return CommandResult(false, errorCode = "INVALID_ARGUMENT", error = "App name or package name is required.")
        }

        val packageName = resolveLaunchablePackage(aliasFor(requested))
            ?: requested.takeIf { context.packageManager.getLaunchIntentForPackage(it) != null }
            ?: return CommandResult(false, errorCode = "APP_NOT_FOUND", error = "Package not found: $requested")

        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return CommandResult(false, errorCode = "APP_NOT_FOUND", error = "Package not found: $packageName")
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            if (waitForForeground(packageName)) {
                CommandResult(true, data = mapOf("package" to packageName, "app_name" to requested, "verified" to true))
            } else {
                CommandResult(
                    false,
                    data = mapOf("package" to packageName, "app_name" to requested, "verified" to false),
                    errorCode = "UNVERIFIED",
                    error = "Opening $requested was requested, but it could not be confirmed. " +
                        "Enable Brahma Connect Accessibility to verify app launches.",
                )
            }
        } catch (exc: Exception) {
            CommandResult(false, errorCode = "APP_NOT_FOUND", error = exc.message ?: "Unable to launch app.")
        }
    }

    /**
     * Polls the active window through the accessibility service for up to ~2 seconds.
     * Returns false if the service is off or the app never came to the foreground.
     * Runs on the command thread, never on the UI thread.
     */
    private fun waitForForeground(packageName: String): Boolean {
        val service = com.brahma.connect.accessibility.BrahmaAccessibilityService.instance ?: return false
        repeat(10) {
            Thread.sleep(200)
            if (service.rootInActiveWindow?.packageName?.toString() == packageName) return true
        }
        return false
    }

    private fun resolveLaunchablePackage(requested: String): String? {
        val normalizedRequested = normalizeAppQuery(requested)
        if (normalizedRequested.isBlank()) return null

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchableApps = runCatching {
            context.packageManager.queryIntentActivities(launcherIntent, 0)
        }.getOrDefault(emptyList())

        fun labelOf(info: ResolveInfo): String {
            return info.loadLabel(context.packageManager)?.toString().orEmpty()
        }

        val exactMatch = launchableApps.firstOrNull { info ->
            val label = normalizeAppQuery(labelOf(info))
            val packageId = normalizeAppQuery(info.activityInfo.packageName)
            normalizedRequested == label || normalizedRequested == packageId
        }
        if (exactMatch != null) return exactMatch.activityInfo.packageName

        val containsMatch = launchableApps.firstOrNull { info ->
            val label = normalizeAppQuery(labelOf(info))
            val packageId = normalizeAppQuery(info.activityInfo.packageName)
            label.contains(normalizedRequested) ||
                normalizedRequested.contains(label) ||
                packageId.contains(normalizedRequested) ||
                normalizedRequested.contains(packageId)
        }
        return containsMatch?.activityInfo?.packageName
    }

    /**
     * Spoken Bengali or Banglish names mapped to the English name an installed app is usually labelled with.
     * Anything not listed here is still matched against the installed apps by its own name.
     */
    private val appAliases = mapOf(
        "ইউটিউব" to "youtube",
        "ফেসবুক" to "facebook",
        "হোয়াটসঅ্যাপ" to "whatsapp",
        "ইনস্টাগ্রাম" to "instagram",
        "টেলিগ্রাম" to "telegram",
        "স্পটিফাই" to "spotify",
        "ক্রোম" to "chrome",
        "ক্যামেরা" to "camera",
        "ক্যালকুলেটর" to "calculator",
        "ক্যালেন্ডার" to "calendar",
        "গ্যালারি" to "gallery",
        "ফটো" to "photos",
        "মেসেজ" to "messages",
        "সেটিংস" to "settings",
        "ম্যাপ" to "maps",
        "গুগল ম্যাপ" to "maps",
        "ক্লক" to "clock",
        "ফাইল" to "files",
        "ফাইলস" to "files",
        "প্লে স্টোর" to "play store",
    )

    private fun aliasFor(requested: String): String {
        val key = requested.trim().lowercase()
        return appAliases[key] ?: appAliases.entries.firstOrNull { key.contains(it.key) }?.value ?: requested
    }

    private fun normalizeAppQuery(value: String): String {
        // Keep letters, combining marks (Bengali vowel signs) and digits from any script.
        return value.lowercase().replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), "")
    }

    private fun openUrl(parameters: Map<String, Any?>): CommandResult {
        val url = (parameters["url"] ?: parameters["link"])?.toString()?.trim().orEmpty()
        if (url.isBlank()) {
            return CommandResult(false, errorCode = "INVALID_ARGUMENT", error = "URL is required.")
        }
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            CommandResult(true, data = mapOf("url" to url))
        } catch (exc: Exception) {
            CommandResult(false, errorCode = "OPEN_URL_FAILED", error = exc.message ?: "Unable to open URL.")
        }
    }

    private fun volumeGet(): CommandResult {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return CommandResult(false, errorCode = "AUDIO_UNAVAILABLE", error = "Audio service unavailable.")
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val percentage = ((current * 100f) / max).toInt().coerceIn(0, 100)
        return CommandResult(true, data = mapOf("stream" to "music", "percentage" to percentage, "current" to current, "max" to max))
    }

    private fun volumeSet(parameters: Map<String, Any?>): CommandResult {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return CommandResult(false, errorCode = "AUDIO_UNAVAILABLE", error = "Audio service unavailable.")
        val targetRaw = parameters["value"] ?: parameters["percentage"]
            ?: return CommandResult(false, errorCode = "INVALID_ARGUMENT", error = "Volume value is required.")
        val target = when (targetRaw) {
            is Number -> targetRaw.toInt()
            else -> targetRaw.toString().toIntOrNull()
        } ?: return CommandResult(false, errorCode = "INVALID_ARGUMENT", error = "Volume value must be numeric.")
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val clamped = target.coerceIn(0, 100)
        val level = ((clamped / 100f) * max).toInt().coerceIn(0, max)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
        val actual = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val data = mapOf("stream" to "music", "percentage" to clamped, "current" to actual, "max" to max)
        return if (actual == level) {
            CommandResult(true, data = data)
        } else {
            CommandResult(false, data = data, errorCode = "UNVERIFIED", error = "Volume did not change as requested.")
        }
    }

    private fun unlockPhone(parameters: Map<String, Any?>): CommandResult {
        val pin = parameters["pin"]?.toString()
        if (pin.isNullOrBlank()) {
            return CommandResult(false, errorCode = "MISSING_PIN", error = "PIN is required to unlock phone.")
        }
        // Phone unlock is not supported; report it honestly instead of a fake success.
        return CommandResult(
            false,
            errorCode = "UNSUPPORTED",
            error = "Phone unlock is not supported. Use a trusted device such as a smartwatch instead.",
        )
    }

    private fun sendSms(parameters: Map<String, Any?>): CommandResult {
        val number = cleanPhoneNumber(parameters["number"] ?: parameters["to"])
        val message = (parameters["message"] ?: parameters["text"])?.toString()?.trim().orEmpty()
        if (number.isBlank() || message.isBlank()) {
            return CommandResult(false, errorCode = "INVALID_ARGUMENT", error = "number and message are required.")
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return CommandResult(false, errorCode = "SMS_PERMISSION_REQUIRED", error = "SMS permission is not granted on the phone.")
        }
        return try {
            val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(android.telephony.SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                android.telephony.SmsManager.getDefault()
            }
            val requestId = java.util.UUID.randomUUID().toString()
            val future = com.brahma.connect.sms.SmsStatusTracker.expect(requestId)
            val sentIntent = android.app.PendingIntent.getBroadcast(
                context,
                requestId.hashCode(),
                Intent(com.brahma.connect.sms.SmsStatusReceiver.ACTION)
                    .setPackage(context.packageName)
                    .putExtra(com.brahma.connect.sms.SmsStatusReceiver.EXTRA_REQUEST_ID, requestId),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
            manager.sendTextMessage(number, null, message, sentIntent, null)
            val code = try {
                future.get(SMS_STATUS_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            } catch (timeout: java.util.concurrent.TimeoutException) {
                com.brahma.connect.sms.SmsStatusTracker.forget(requestId)
                return CommandResult(
                    false,
                    data = mapOf("number" to number, "status" to "unknown"),
                    errorCode = "UNVERIFIED",
                    error = "No confirmation from the phone within $SMS_STATUS_TIMEOUT_SECONDS seconds. The SMS may not have been sent.",
                )
            }
            if (code == android.app.Activity.RESULT_OK) {
                // Sent to the carrier. Delivery to the recipient is not reported by Android here.
                CommandResult(true, data = mapOf("number" to number, "status" to "sent_to_carrier"))
            } else {
                CommandResult(
                    false,
                    data = mapOf("number" to number, "status" to "failed"),
                    errorCode = "SMS_FAILED",
                    error = describeSmsFailure(code),
                )
            }
        } catch (security: SecurityException) {
            CommandResult(false, errorCode = "SMS_FAILED", error = security.message ?: "SMS permission denied.")
        } catch (exc: Exception) {
            CommandResult(false, errorCode = "SMS_FAILED", error = exc.message ?: "Unable to send SMS.")
        }
    }

    private fun describeSmsFailure(code: Int): String = when (code) {
        android.telephony.SmsManager.RESULT_ERROR_RADIO_OFF -> "Mobile radio is off."
        android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE -> "No mobile network service."
        android.telephony.SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "SMS sending limit reached."
        android.telephony.SmsManager.RESULT_ERROR_NULL_PDU -> "The message could not be encoded."
        else -> "The SMS could not be sent (code $code)."
    }

    private fun makeCall(parameters: Map<String, Any?>): CommandResult {
        val number = cleanPhoneNumber(parameters["number"] ?: parameters["to"])
        if (number.isBlank()) {
            return CommandResult(false, errorCode = "INVALID_ARGUMENT", error = "number is required.")
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return CommandResult(false, errorCode = "CALL_PERMISSION_REQUIRED", error = "Phone call permission is not granted on the phone.")
        }
        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            CommandResult(true, data = mapOf("number" to number, "status" to "dialing"))
        } catch (security: SecurityException) {
            CommandResult(false, errorCode = "CALL_FAILED", error = security.message ?: "Call permission denied.")
        } catch (exc: Exception) {
            CommandResult(false, errorCode = "CALL_FAILED", error = exc.message ?: "Unable to place call.")
        }
    }

    private fun notificationList(parameters: Map<String, Any?>): CommandResult {
        if (!BrahmaNotificationListenerService.isConnected()) {
            return CommandResult(
                false,
                errorCode = "NOTIFICATION_ACCESS_REQUIRED",
                error = "Enable notification access for Brahma Connect in Android settings.",
            )
        }
        val limit = (parameters["limit"]?.toString()?.toIntOrNull() ?: 20).coerceIn(1, 50)
        val items = BrahmaNotificationListenerService.recent(limit).map { entry ->
            mapOf(
                "package" to entry.packageName,
                "title" to entry.title,
                "text" to entry.text,
                "time" to entry.postTime,
            )
        }
        return CommandResult(true, data = mapOf("notifications" to items, "count" to items.size))
    }

    private fun cleanPhoneNumber(raw: Any?): String {
        return raw?.toString().orEmpty().filter { it.isDigit() || it == '+' }
    }

    private fun resolveFileTarget(path: String?): java.io.File {
        val storage = android.os.Environment.getExternalStorageDirectory()
        if (path.isNullOrBlank() || path.equals("home", ignoreCase = true)) {
            return storage
        }
        val lower = path.lowercase().trim()
        val baseDir = when (lower) {
            "downloads" -> android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            "documents" -> android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            "pictures" -> android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            "music" -> android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
            "movies", "videos" -> android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
            else -> java.io.File(storage, path)
        }
        return baseDir
    }

    private fun fileList(parameters: Map<String, Any?>): CommandResult {
        val path = parameters["path"]?.toString()
        val dir = resolveFileTarget(path)
        if (!dir.exists() || !dir.isDirectory) {
            return CommandResult(false, errorCode = "NOT_FOUND", error = "Directory not found: ${dir.absolutePath}")
        }
        val items = dir.listFiles()?.map { file ->
            mapOf(
                "name" to file.name,
                "is_dir" to file.isDirectory,
                "size" to file.length(),
                "path" to file.absolutePath
            )
        } ?: emptyList()
        return CommandResult(true, data = mapOf("items" to items, "path" to dir.absolutePath))
    }

    private fun fileRead(parameters: Map<String, Any?>): CommandResult {
        val path = parameters["path"]?.toString()
        val file = resolveFileTarget(path)
        if (!file.exists() || !file.isFile) {
            return CommandResult(false, errorCode = "NOT_FOUND", error = "File not found: ${file.absolutePath}")
        }
        return try {
            val content = file.readText()
            CommandResult(true, data = mapOf("content" to content, "path" to file.absolutePath))
        } catch (e: Exception) {
            CommandResult(false, errorCode = "READ_ERROR", error = e.message ?: "Failed to read file.")
        }
    }

    private fun fileWrite(parameters: Map<String, Any?>): CommandResult {
        val path = parameters["path"]?.toString()
        val content = parameters["content"]?.toString() ?: ""
        val append = parameters["append"] as? Boolean ?: false
        val file = resolveFileTarget(path)
        return try {
            file.parentFile?.mkdirs()
            if (append) {
                file.appendText(content)
            } else {
                file.writeText(content)
            }
            CommandResult(true, data = mapOf("message" to "File written successfully.", "path" to file.absolutePath))
        } catch (e: Exception) {
            CommandResult(false, errorCode = "WRITE_ERROR", error = e.message ?: "Failed to write file.")
        }
    }

    private fun fileDelete(parameters: Map<String, Any?>): CommandResult {
        val path = parameters["path"]?.toString()
        val file = resolveFileTarget(path)
        if (!file.exists()) {
            return CommandResult(false, errorCode = "NOT_FOUND", error = "File or directory not found: ${file.absolutePath}")
        }
        return try {
            val success = if (file.isDirectory) file.deleteRecursively() else file.delete()
            if (success) {
                CommandResult(true, data = mapOf("message" to "Deleted successfully.", "path" to file.absolutePath))
            } else {
                CommandResult(false, errorCode = "DELETE_FAILED", error = "Failed to delete.")
            }
        } catch (e: Exception) {
            CommandResult(false, errorCode = "DELETE_ERROR", error = e.message ?: "Failed to delete file.")
        }
    }

    private fun uiDump(): CommandResult {
        val service = com.brahma.connect.accessibility.BrahmaAccessibilityService.instance
        if (service == null) {
            return CommandResult(false, errorCode = "ACCESSIBILITY_DISABLED", error = "Brahma Accessibility Service is not enabled.")
        }
        val result = service.dumpUiTree()
        return if (result.containsKey("error")) {
            CommandResult(false, errorCode = "UI_DUMP_FAILED", error = result["error"] as String)
        } else {
            CommandResult(true, data = result)
        }
    }

    private fun uiTap(parameters: Map<String, Any?>): CommandResult {
        val service = com.brahma.connect.accessibility.BrahmaAccessibilityService.instance
        if (service == null) {
            return CommandResult(false, errorCode = "ACCESSIBILITY_DISABLED", error = "Brahma Accessibility Service is not enabled.")
        }
        val x = (parameters["x"] as? Number)?.toInt()
        val y = (parameters["y"] as? Number)?.toInt()
        if (x == null || y == null) {
            return CommandResult(false, errorCode = "INVALID_ARGUMENT", error = "x and y coordinates are required.")
        }
        val success = service.tap(x, y)
        return CommandResult(success, data = mapOf("action" to "tap", "success" to success))
    }

    private fun uiSwipe(parameters: Map<String, Any?>): CommandResult {
        val service = com.brahma.connect.accessibility.BrahmaAccessibilityService.instance
        if (service == null) {
            return CommandResult(false, errorCode = "ACCESSIBILITY_DISABLED", error = "Brahma Accessibility Service is not enabled.")
        }
        val x1 = (parameters["x1"] as? Number)?.toInt()
        val y1 = (parameters["y1"] as? Number)?.toInt()
        val x2 = (parameters["x2"] as? Number)?.toInt()
        val y2 = (parameters["y2"] as? Number)?.toInt()
        val duration = (parameters["duration"] as? Number)?.toLong() ?: 300L
        if (x1 == null || y1 == null || x2 == null || y2 == null) {
            return CommandResult(false, errorCode = "INVALID_ARGUMENT", error = "x1, y1, x2, y2 coordinates are required.")
        }
        val success = service.swipe(x1, y1, x2, y2, duration)
        return CommandResult(success, data = mapOf("action" to "swipe", "success" to success))
    }

    private fun uiType(parameters: Map<String, Any?>): CommandResult {
        val service = com.brahma.connect.accessibility.BrahmaAccessibilityService.instance
        if (service == null) {
            return CommandResult(false, errorCode = "ACCESSIBILITY_DISABLED", error = "Brahma Accessibility Service is not enabled.")
        }
        val text = parameters["text"]?.toString()
        if (text.isNullOrEmpty()) {
            return CommandResult(false, errorCode = "INVALID_ARGUMENT", error = "text is required.")
        }
        val success = service.typeText(text)
        return CommandResult(success, data = mapOf("action" to "type", "success" to success))
    }
}
