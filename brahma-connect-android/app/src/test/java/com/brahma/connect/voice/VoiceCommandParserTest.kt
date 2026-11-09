package com.brahma.connect.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandParserTest {

    @Test
    fun opensAppEnglish() {
        val intent = VoiceCommandParser.parse("open YouTube")
        assertEquals("launch_app", intent?.action)
        assertEquals("youtube", intent?.parameters?.get("app_name"))
        assertFalse(intent!!.sensitive)
    }

    @Test
    fun opensAppBengali() {
        val intent = VoiceCommandParser.parse("ইউটিউব খোলো")
        assertEquals("launch_app", intent?.action)
        assertEquals("ইউটিউব", intent?.parameters?.get("app_name"))
    }

    @Test
    fun setsVolume() {
        val intent = VoiceCommandParser.parse("volume 40%")
        assertEquals("volume_set", intent?.action)
        assertEquals(40, intent?.parameters?.get("value"))
    }

    @Test
    fun setsVolumeBengali() {
        val intent = VoiceCommandParser.parse("ভলিউম 25")
        assertEquals("volume_set", intent?.action)
        assertEquals(25, intent?.parameters?.get("value"))
    }

    @Test
    fun flashlightOnAndOff() {
        assertEquals("flashlight_on", VoiceCommandParser.parse("flashlight on")?.action)
        assertEquals("flashlight_off", VoiceCommandParser.parse("torch off")?.action)
    }

    @Test
    fun smsIsSensitiveAndNeedsConfirmation() {
        val intent = VoiceCommandParser.parse("send sms to +8801712345678 saying I am on my way")
        assertEquals("send_sms", intent?.action)
        assertEquals("+8801712345678", intent?.parameters?.get("number"))
        assertEquals("i am on my way", intent?.parameters?.get("message"))
        assertTrue(intent!!.sensitive)
    }

    @Test
    fun callIsSensitive() {
        val intent = VoiceCommandParser.parse("call +8801712345678")
        assertEquals("make_call", intent?.action)
        assertTrue(intent!!.sensitive)
    }

    @Test
    fun callBengali() {
        val intent = VoiceCommandParser.parse("01712345678 তে কল করো")
        assertEquals("make_call", intent?.action)
        assertEquals("01712345678", intent?.parameters?.get("number"))
    }

    @Test
    fun unknownTextReturnsNull() {
        assertNull(VoiceCommandParser.parse("tell me a joke"))
        assertNull(VoiceCommandParser.parse(""))
        assertNull(VoiceCommandParser.parse("   "))
    }

    @Test
    fun callByContactNameIsResolvedLater() {
        val intent = VoiceCommandParser.parse("call rahim")
        assertEquals("make_call", intent?.action)
        assertEquals("rahim", intent?.parameters?.get("contact"))
        assertTrue(intent!!.sensitive)
    }

    @Test
    fun callByContactNameBengali() {
        val intent = VoiceCommandParser.parse("রহিমকে কল করো")
        assertEquals("make_call", intent?.action)
        assertEquals("রহিম", intent?.parameters?.get("contact"))
    }

    @Test
    fun smsByContactNameEnglish() {
        val intent = VoiceCommandParser.parse("send sms to rahim saying see you soon")
        assertEquals("send_sms", intent?.action)
        assertEquals("rahim", intent?.parameters?.get("contact"))
        assertEquals("see you soon", intent?.parameters?.get("message"))
    }
}
