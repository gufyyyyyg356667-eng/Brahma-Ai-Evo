package com.brahma.connect.voice

/**
 * A recognized spoken command, mapped to a DeviceCommandHandler action.
 * `sensitive` actions must be confirmed by the user before they run.
 * A `contact` parameter means the name still has to be resolved to a number.
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

    // Speech engines often put spaces inside spoken digits ("017 1234 5678"). Spaces are removed later.
    private const val NUMBER = """\+?\d(?:\s?\d){5,14}"""
    private const val BN_DIGITS = "০১২৩৪৫৬৭৮৯"

    // Polite or filler words that carry no meaning for a command. Removed as whole words.
    private val FILLER_WORDS = setOf("please", "pls", "plz", "bhai", "ektu", "একটু", "প্লিজ", "ভাই", "দয়া")

    private val smsEn = Regex("""^(?:send|text)\s+(?:an?\s+)?(?:sms|message|msg)\s+to\s+($NUMBER)\s*(?:saying|that|:)?\s*(.+)$""")
    private val smsEnName = Regex("""^(?:send|text)\s+(?:an?\s+)?(?:sms|message|msg)\s+to\s+(.+?)\s+(?:saying|that|:)\s*(.+)$""")
    private val smsBn = Regex("""^($NUMBER)\s*(?:নম্বরে|নাম্বারে|কে|ke)?\s*(?:মেসেজ|এসএমএস|message|sms|msg)\s*(?:পাঠাও|পাঠান|পাঠা|pathao|patao|pathan|patha)\s*[:-]?\s*(.+)$""")
    private val smsBnName = Regex("""^(.+?)\s*(?:কে|ke)\s*(?:মেসেজ|এসএমএস|message|sms|msg)\s*(?:পাঠাও|পাঠান|পাঠা|pathao|patao|pathan|patha)\s*[:-]?\s*(.+)$""")

    private val callEn = Regex("""^(?:call|dial)\s+($NUMBER)$""")
    private val callBn = Regex("""^($NUMBER)\s*(?:-তে|তে|কে|te|ke)?\s*(?:কল|call|ফোন|phone)\s*(?:করো|কর|দাও|koro|kor|dao)?$""")
    private val callBnNameFirst = Regex("""^(?:কল|ফোন|call|phone)\s*(?:করো|কর|দাও|koro|kor|dao)\s+(.+)$""")
    private val callBnName = Regex("""^(.+?)(?:\s+ke|\s*কে)\s*(?:কল|call|ফোন|phone)\s*(?:করো|কর|দাও|koro|kor|dao)?$""")
    private val callEnName = Regex("""^(?:call|dial)\s+(.+?)(?:\s+(?:koro|kor|dao))?$""")

    private val openEn = Regex("""^(?:open|launch|start)\s+(.+)$""")
    private val openBn = Regex("""^(.+?)\s*(?:খোলো|খোল|খুলো|খুল|খুলে দাও|চালু করো|চালাও|kholo|khulo|khul|open koro|open kor|koro open)$""")

    private val volumeEn = Regex("""^(?:set\s+)?(?:volume|sound)\s+(?:to\s+)?(\d{1,3})\s*%?\s*(?:koro|kor)?$""")
    private val volumeBn = Regex("""^(?:ভলিউম|volume|bhalium|valium)\s*(\d{1,3})\s*%?\s*(?:koro|kor|করো|কর)?$""")

    fun parse(utterance: String): VoiceIntent? {
        val text = normalize(utterance)
        if (text.isEmpty()) return null

        smsEn.matchEntire(text)?.let { m ->
            return VoiceIntent("send_sms", mapOf("number" to digits(m.groupValues[1]), "message" to m.groupValues[2].trim()), sensitive = true)
        }
        smsEnName.matchEntire(text)?.let { m ->
            return VoiceIntent("send_sms", mapOf("contact" to m.groupValues[1].trim(), "message" to m.groupValues[2].trim()), sensitive = true)
        }
        smsBn.matchEntire(text)?.let { m ->
            return VoiceIntent("send_sms", mapOf("number" to digits(m.groupValues[1]), "message" to m.groupValues[2].trim()), sensitive = true)
        }
        smsBnName.matchEntire(text)?.let { m ->
            return VoiceIntent("send_sms", mapOf("contact" to m.groupValues[1].trim(), "message" to m.groupValues[2].trim()), sensitive = true)
        }

        callEn.matchEntire(text)?.let { m ->
            return VoiceIntent("make_call", mapOf("number" to digits(m.groupValues[1])), sensitive = true)
        }
        callBn.matchEntire(text)?.let { m ->
            return VoiceIntent("make_call", mapOf("number" to digits(m.groupValues[1])), sensitive = true)
        }
        callBnNameFirst.matchEntire(text)?.let { m ->
            val name = stripContactSuffix(m.groupValues[1])
            if (name.isNotEmpty()) return VoiceIntent("make_call", mapOf("contact" to name), sensitive = true)
        }
        callBnName.matchEntire(text)?.let { m ->
            val name = stripContactSuffix(m.groupValues[1])
            if (name.isNotEmpty()) return VoiceIntent("make_call", mapOf("contact" to name), sensitive = true)
        }
        callEnName.matchEntire(text)?.let { m ->
            return VoiceIntent("make_call", mapOf("contact" to m.groupValues[1].trim()), sensitive = true)
        }

        volumeEn.matchEntire(text)?.let { m ->
            return VoiceIntent("volume_set", mapOf("value" to m.groupValues[1].toInt()))
        }
        volumeBn.matchEntire(text)?.let { m ->
            return VoiceIntent("volume_set", mapOf("value" to m.groupValues[1].toInt()))
        }

        val words = text.split(' ')
        val mentionsFlash = text.contains("ফ্ল্যাশ") || text.contains("টর্চ") || text.contains("flash") || text.contains("torch")
        if (mentionsFlash && ("off" in words || text.contains("বন্ধ") || text.contains("নিভাও") || text.contains("নেভাও"))) {
            return VoiceIntent("flashlight_off")
        }
        if (mentionsFlash && ("on" in words || text.contains("চালু") || text.contains("জ্বালাও") || text.contains("জ্বালো"))) {
            return VoiceIntent("flashlight_on")
        }

        if (text in setOf("battery", "battery status", "battery level", "battery koto", "battery kot") ||
            (text.contains("ব্যাটারি") && text.length < 30)
        ) {
            return VoiceIntent("get_battery")
        }

        if (text in setOf("notifications", "notification", "show notifications", "notifications dekhao", "notification koto") ||
            (text.contains("নোটিফিকেশন") && text.length < 40)
        ) {
            return VoiceIntent("notification_list")
        }

        openEn.matchEntire(text)?.let { m ->
            val app = cleanAppName(m.groupValues[1])
            if (app.isNotEmpty()) return VoiceIntent("launch_app", mapOf("app_name" to app))
        }
        openBn.matchEntire(text)?.let { m ->
            val app = cleanAppName(m.groupValues[1])
            if (app.isNotEmpty()) return VoiceIntent("launch_app", mapOf("app_name" to app))
        }

        return null
    }

    /** Lowercases, converts Bengali digits, drops punctuation and filler words. */
    private fun normalize(raw: String): String {
        val digitsFixed = raw.map { c ->
            val index = BN_DIGITS.indexOf(c)
            if (index >= 0) '0' + index else c
        }.joinToString("")
        return digitsFixed.lowercase()
            .replace(Regex("[?!,।]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() && it !in FILLER_WORDS }
            .joinToString(" ")
    }

    /** Keeps only the digits and a leading plus, removing the spaces speech put between them. */
    private fun digits(value: String): String = value.filter { it.isDigit() || it == '+' }

    private fun stripContactSuffix(name: String): String =
        name.trim()
            .removeSuffix("কে")
            .removeSuffix("ke")
            .trim()

    private fun cleanAppName(name: String): String =
        name.trim()
            .removeSuffix(" app")
            .removeSuffix(" অ্যাপ")
            .removeSuffix(" অ্যাপটা")
            .trim()
}
