package com.rishitgoklani.hermes_front_mobile.domain.notifications

import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.notificationCapabilities
import com.rishitgoklani.hermes_front_mobile.testing.FakeNotificationPoster
import com.rishitgoklani.hermes_front_mobile.testing.FakeNotificationSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTests {
    private fun rec(key: String, app: String = "WhatsApp", title: String = "Mom", text: String = "hi", time: Long = 1000, ongoing: Boolean = false) =
        NotificationRecord(key, "pkg.$app", app, title, text, time, listOf("Reply"), ongoing, true)
    private fun a(vararg p: Pair<String, Any>) = JsonObject(p.associate { (k, v) -> k to (if (v is Boolean) JsonPrimitive(v) else if (v is Int) JsonPrimitive(v) else JsonPrimitive(v.toString())) })
    private fun caps(src: FakeNotificationSource, hist: NotificationHistory = NotificationHistory(), now: Long = 100_000, poster: FakeNotificationPoster = FakeNotificationPoster()) =
        notificationCapabilities(src, hist, poster) { now }.associateBy { it.name }

    @Test fun historyIgnoresOngoingAndCapsSize() {
        val h = NotificationHistory(2); h.add(rec("1")); h.add(rec("2", time = 2000)); h.add(rec("3", time = 3000)); h.add(rec("o", ongoing = true))
        assertEquals(2, h.size()); assertEquals(listOf("3", "2"), h.search().map { it.key })
    }
    @Test fun historySearchFilters() {
        val h = NotificationHistory(); h.add(rec("1", app = "Gmail", text = "invoice")); h.add(rec("2", app = "Slack", text = "lunch"))
        assertEquals(listOf("1"), h.search(query = "invoice").map { it.key }); assertEquals(listOf("2"), h.search(app = "slack").map { it.key })
    }
    @Test fun digestCountsPerApp() {
        val h = NotificationHistory(); h.add(rec("1")); h.add(rec("2", time = 2000)); h.add(rec("3", app = "Gmail", time = 3000))
        assertEquals(mapOf("WhatsApp" to 2, "Gmail" to 1), h.digest(0))
    }
    @Test fun otpWithKeyword() { assertEquals("482913", OtpExtractor.extract("Your verification code is 482913")); assertEquals("123456", OtpExtractor.extract("OTP 123 456 for login")) }
    @Test fun otpLoneSixDigits() = assertEquals("654321", OtpExtractor.extract("654321 is yours"))
    @Test fun otpAbsent() { assertNull(OtpExtractor.extract("Meet at 5pm")); assertNull(OtpExtractor.extract("Order 12345678901")) }
    @Test fun listFiltersOngoingAndByApp() = runBlocking {
        val src = FakeNotificationSource(listOf(rec("1"), rec("2", app = "Gmail"), rec("3", ongoing = true)))
        assertEquals(2, caps(src)["notifications_list"]!!.executor.execute(a()).message.lines().size)
        assertTrue(caps(src)["notifications_list"]!!.executor.execute(a("app" to "gmail")).message.startsWith("Gmail"))
    }
    @Test fun dismissByKeywordAndAll() = runBlocking {
        val src = FakeNotificationSource(listOf(rec("1", text = "promo"), rec("2", text = "hello")))
        caps(src)["notification_dismiss"]!!.executor.execute(a("keyword" to "promo")); assertEquals(listOf("1"), src.dismissed)
        caps(src)["notification_dismiss"]!!.executor.execute(a("all" to true)); assertTrue(src.dismissedAll)
    }
    @Test fun dismissRequiresSelector() = runBlocking { assertEquals("failed", caps(FakeNotificationSource())["notification_dismiss"]!!.executor.execute(a()).status) }
    @Test fun actPassesReply() = runBlocking {
        val src = FakeNotificationSource(listOf(rec("1"))); caps(src)["notification_act"]!!.executor.execute(a("key" to "1", "action" to "Reply", "reply" to "ok"))
        assertEquals(listOf(Triple("1", "Reply", "ok" as String?)), src.acted)
    }
    @Test fun postDelegatesToPoster() = runBlocking {
        val p = FakeNotificationPoster(); caps(FakeNotificationSource(), poster = p)["notification_post"]!!.executor.execute(a("title" to "Water", "text" to "Drink"))
        assertEquals("Water", p.posted.single().first)
    }
    @Test fun otpLatestFindsRecentCode() = runBlocking {
        val h = NotificationHistory(); h.add(rec("1", app = "Bank", text = "Your code is 998877", time = 99_000))
        val r = caps(FakeNotificationSource(), h)["otp_latest"]!!.executor.execute(a()); assertEquals("998877", r.data!!["code"]!!.jsonPrimitive.content)
    }
    @Test fun otpLatestIgnoresOld() = runBlocking {
        val h = NotificationHistory(); h.add(rec("1", text = "code 111222", time = 1)); assertEquals("failed", caps(FakeNotificationSource(), h, now = 10_000_000)["otp_latest"]!!.executor.execute(a()).status)
    }
    @Test fun historyDigestCapability() = runBlocking {
        val h = NotificationHistory(); h.add(rec("1", time = 99_000)); assertTrue(caps(FakeNotificationSource(), h)["notification_history"]!!.executor.execute(a("digest" to true)).message.contains("WhatsApp: 1"))
    }
}
