package com.brahma.connect.voice

/**
 * A recognized spoken command, mapped to a DeviceCommandHandler action.
 * `sensitive` actions must be confirmed by the user before they run.
 */
data class VoiceIntent(
    val action: String,
    val parameters: Map<String, Any?> = emptyMap(),
    val sensitive: Boolean = false,
)

/**
 * Pure text parser: no Android dependencies, so it can be unit tested on the JVM.
 * Supports English, Banglish and Bengali phrases for a small, fixed set of commands.
 * Anything not matched returns null, and the caller must say it did not understand.
 */
object VoiceCommandParser {

    private const val NUMBER = """\+?\d{6,15}"""

    private val smsEn = Regex("""^(?:send|text)\s+(?:an?\s+)?(?:sms|message)\s+to\s+($NUMBER)\s*(?:saying|that|:)?\s*(.+)$""")
    private val smsBn = Regex("""^($NUMBER)\s*(?:নম্বরে|নাম্বারে)?\s*(?:মেসেজ|এসএমএস|message|sms)\s*(?:পাঠাও|পাঠান|পাঠা)\s*[:।-]?\s*(.+)$""")

    private val callEn = Regex("""^call\s+($NUMBER)$""")
    private val callBn = Regex("""^($NUMBER)\s*(?:-তে|তে|কে)?\s*কল\s*(?:করো|কর|দাও)?$""")

    private val openEn = Regex("""^open\s+(.+)$""")
    private val openBn = Regex("""^(.+?)\s*(?:খোলো|খোল|চালু করো)$""")

    private val volumeEn = Regex("""^(?:set\s+)?volume\s+(?:to\s+)?(\d{1,3})\s*%?$""")
    private val volumeBn = Regex("""^ভলিউম\s*(\d{1,3})\s*%?$""")

    fun parse(utterance: String): VoiceIntent? {
        val text = utterance.trim().lowercase().replace(Regex("\\s+"), " ")
        if (text.isEmpty()) return null

        smsEn.matchEntire(text)?.let { m ->
            return VoiceIntent("send_sms", mapOf("number" to m.groupValues[1], "message" to m.groupValues[2]), sensitive = true)
        }
        smsBn.matchEntire(text)?.let { m ->
            return VoiceIntent("send_sms", mapOf("number" to m.groupValues[1], "message" to m.groupValues[2]), sensitive = true)
        }

        callEn.matchEntire(text)?.let { m ->
            return VoiceIntent("make_call", mapOf("number" to m.groupValues[1]), sensitive = true)
        }
        callBn.matchEntire(text)?.let { m ->
            return VoiceIntent("make_call", mapOf("number" to m.groupValues[1]), sensitive = true)
        }

        volumeEn.matchEntire(text)?.let { m ->
            return VoiceIntent("volume_set", mapOf("value" to m.groupValues[1].toInt()))
        }
        volumeBn.matchEntire(text)?.let { m ->
            return VoiceIntent("volume_set", mapOf("value" to m.groupValues[1].toInt()))
        }

        if (text == "flashlight on" || text == "torch on" || text.contains("ফ্ল্যাশলাইট চালু") || text.contains("টর্চ চালু")) {
            return VoiceIntent("flashlight_on")
        }
        if (text == "flashlight off" || text == "torch off" || text.contains("ফ্ল্যাশলাইট বন্ধ") || text.contains("টর্চ বন্ধ")) {
            return VoiceIntent("flashlight_off")
        }

        if (text == "battery" || text == "battery status" || text.contains("ব্যাটারি")) {
            return VoiceIntent("get_battery")
        }
        if (text == "notifications" || text == "show notifications" || text.contains("নোটিফিকেশন")) {
            return VoiceIntent("notification_list")
        }

        openEn.matchEntire(text)?.let { m ->
            return VoiceIntent("launch_app", mapOf("app_name" to m.groupValues[1]))
        }
        openBn.matchEntire(text)?.let { m ->
            val app = m.groupValues[1].trim()
            if (app.isNotEmpty()) return VoiceIntent("launch_app", mapOf("app_name" to app))
        }

        return null
    }
}
