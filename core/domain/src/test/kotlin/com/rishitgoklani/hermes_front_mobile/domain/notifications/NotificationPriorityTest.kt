package com.rishitgoklani.hermes_front_mobile.domain.notifications

import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.notificationPriorityCapabilities
import com.rishitgoklani.hermes_front_mobile.testing.FakeNotificationSource
import com.rishitgoklani.hermes_front_mobile.testing.FakeStateStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPriorityTest {
    private val now = 1_000_000_000L
    private fun n(key: String, pkg: String, title: String, text: String = "", age: Long = 60_000, reply: Boolean = false) =
        NotificationRecord(key, pkg, pkg.substringAfterLast('.'), title, text, now - age, canReply = reply)

    @Test fun urgentFromPersonOutranksPromo() {
        val ranked = NotificationRanker.rank(listOf(n("a", "com.shop.amazon", "50% off sale today"), n("b", "org.whatsapp", "Mum", "call me urgent", reply = true)), now)
        assertEquals("b", ranked.first().record.key)
        assertEquals(Importance.HIGH, ranked.first().importance)
        assertEquals(Importance.LOW, ranked.last().importance)
    }

    @Test fun rankingIsDeterministicWithTieBreakNewestThenKey() {
        val list = listOf(n("z", "x.app", "hello", age = 5 * 60_000), n("a", "x.app", "hello", age = 5 * 60_000), n("m", "x.app", "hello", age = 1_000))
        assertEquals(listOf("m", "a", "z"), NotificationRanker.rank(list, now).map { it.record.key })
        assertEquals(NotificationRanker.rank(list, now), NotificationRanker.rank(list.reversed(), now))
    }

    @Test fun oldNotificationsScoreLowerThanRecent() {
        val (recent, _) = NotificationRanker.score(n("a", "x.app", "hi", age = 60_000), now)
        val (old, _) = NotificationRanker.score(n("a", "x.app", "hi", age = 3 * 24 * 3_600_000L), now)
        assertTrue(recent > old)
    }

    @Test fun silenceRuleMatchesAppAndOptionalText() {
        val rules = SilenceRules(FakeStateStore()) { now }
        rules.add(SilenceRule("insta", contains = "liked"))
        assertTrue(rules.isSilenced(n("a", "com.instagram.android", "x liked your photo")))
        assertFalse(rules.isSilenced(n("a", "com.instagram.android", "DM from Sam")))
    }

    @Test fun timedRuleExpires() {
        var t = now; val rules = SilenceRules(FakeStateStore()) { t }
        rules.add(SilenceRule("news", untilMs = now + 60_000))
        assertEquals(1, rules.list().size)
        t = now + 61_000
        assertTrue(rules.list().isEmpty())
    }

    @Test fun removeAndReplaceRules() {
        val rules = SilenceRules(FakeStateStore()) { now }
        rules.add(SilenceRule("a1")); rules.add(SilenceRule("a1", untilMs = now + 10)); assertEquals(1, rules.list().size)
        assertTrue(rules.remove("A1")); assertFalse(rules.remove("a1"))
    }

    @Test fun corruptStoreYieldsEmptyRules() {
        val rules = SilenceRules(FakeStateStore(mutableMapOf("notification_silence" to "{bad")))
        assertTrue(rules.list().isEmpty())
    }

    @Test fun useCaseSkipsSilencedAndDismissesOnRequest() = runTest {
        val src = FakeNotificationSource(listOf(n("a", "com.instagram.android", "liked"), n("b", "org.whatsapp", "Hi"), NotificationRecord("c", "p", "p", "t", "", now, ongoing = true)))
        val rules = SilenceRules(FakeStateStore()) { now }.also { it.add(SilenceRule("instagram")) }
        val plain = RankNotifications(src, rules) { now }()
        assertEquals(listOf("b"), plain.ranked.map { it.record.key }); assertEquals(1, plain.silenced.size); assertTrue(src.dismissed.isEmpty())
        val cleared = RankNotifications(src, rules) { now }(dismissSilenced = true)
        assertEquals(1, cleared.dismissed); assertEquals(listOf("a"), src.dismissed)
    }

    @Test fun bridgeToolsRankAndManageRules() = runTest {
        val src = FakeNotificationSource(listOf(n("b", "org.whatsapp", "Hi")))
        val rules = SilenceRules(FakeStateStore()) { now }
        val caps = notificationPriorityCapabilities(RankNotifications(src, rules) { now }, rules) { now }.associateBy { it.name }
        fun args(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject
        assertEquals("completed", caps.getValue("notification_silence").executor.execute(args("""{"action":"add","app":"insta","minutes":30}""")).status)
        assertEquals(now + 30 * 60_000, rules.list().single().untilMs)
        assertEquals("failed", caps.getValue("notification_silence").executor.execute(args("""{"action":"add","app":"x"}""")).status)
        assertEquals("failed", caps.getValue("notification_silence").executor.execute(args("""{"action":"nope"}""")).status)
        assertTrue(caps.getValue("notification_silence").executor.execute(args("""{"action":"list"}""")).message.contains("insta"))
        val r = caps.getValue("notifications_rank").executor.execute(args("{}"))
        assertEquals("completed", r.status); assertTrue(r.message.contains("Hi"))
        assertEquals("completed", caps.getValue("notification_silence").executor.execute(args("""{"action":"remove","app":"insta"}""")).status)
    }
}
