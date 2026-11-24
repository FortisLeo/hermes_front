package com.rishitgoklani.hermes_front_mobile.domain.presence

/** Port: raw audit log lines, oldest first (`epochMs<TAB>tool<TAB>status<TAB>args`). */
fun interface AuditSource { fun lines(): List<String> }

/** A repeated action worth automating: the same tool and arguments at about the same time on several different days. */
data class RoutineProposal(val tool: String, val args: String, val minuteOfDay: Int, val days: Int) {
    val time: String get() = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
    val cron: String get() = "${minuteOfDay % 60} ${minuteOfDay / 60} * * *"
    val key: String get() = "$tool|$args|${minuteOfDay / 60}"
    fun spoken(): String = "You run $tool around $time most days. Want me to automate it?"
}

/**
 * #338 Routine learning: mines the audit log for the same tool+args repeating near the same time of day on at least
 * [minDays] distinct days. Only the user's own successful calls count; events, routines and voice turns are ignored.
 */
class RoutineMiner(
    private val minDays: Int = 3,
    private val windowMinutes: Int = 45,
    private val ignoredTools: Set<String> = setOf("voice_turn", "visual_change", "sound_spike", "presence", "kill_switch", "audit_log", "presence_check", "self_test", "battery", "time_now"),
) {
    private data class Hit(val day: Long, val minute: Int)

    fun mine(lines: List<String>, utcOffsetMs: Long = 0): List<RoutineProposal> {
        val groups = HashMap<Pair<String, String>, MutableList<Hit>>()
        for (line in lines) {
            val p = line.split('\t', limit = 4)
            if (p.size < 3) continue
            val ts = p[0].toLongOrNull() ?: continue
            val tool = p[1]; val status = p[2]; val args = p.getOrElse(3) { "" }
            if (status != "completed" && status != "accepted") continue
            if (tool in ignoredTools || tool.startsWith("routine:")) continue
            val local = ts + utcOffsetMs
            groups.getOrPut(tool to args) { mutableListOf() } += Hit(Math.floorDiv(local, 86_400_000L), (Math.floorMod(local, 86_400_000L) / 60_000L).toInt())
        }
        return groups.mapNotNull { (k, hits) -> cluster(k.first, k.second, hits) }
            .sortedWith(compareByDescending<RoutineProposal> { it.days }.thenBy { it.tool })
    }

    /** Best cluster: for each hit as an anchor, count distinct days within the window around it (wrapping midnight). */
    private fun cluster(tool: String, args: String, hits: List<Hit>): RoutineProposal? {
        if (hits.map { it.day }.distinct().size < minDays) return null
        var best: List<Hit> = emptyList()
        for (anchor in hits) {
            val near = hits.filter { circular(it.minute, anchor.minute) <= windowMinutes }
            if (near.map { it.day }.distinct().size > best.map { it.day }.distinct().size) best = near
        }
        val days = best.map { it.day }.distinct().size
        if (days < minDays) return null
        val sorted = best.map { it.minute }.sorted()
        return RoutineProposal(tool, args, sorted[sorted.size / 2], days)
    }

    private fun circular(a: Int, b: Int): Int { val d = Math.abs(a - b); return minOf(d, 1440 - d) }
}

/** #338 Surfaces one proposal at a time in the evening, never the same one twice within [repeatAfterMs]. */
class RoutineLearning(
    private val source: AuditSource,
    private val miner: RoutineMiner = RoutineMiner(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val hour: () -> Int,
    private val utcOffsetMs: () -> Long = { 0L },
    private val repeatAfterMs: Long = 7 * 86_400_000L,
) : Behavior {
    override val id = "routine-learning"
    private val offered = HashMap<String, Long>()

    override suspend fun react(input: Input): List<Cue> {
        if (input !is Input.Tick || hour() !in 18..21) return emptyList()
        val now = clock()
        val next = miner.mine(source.lines(), utcOffsetMs()).firstOrNull { offered[it.key]?.let { t -> now - t >= repeatAfterMs } ?: true } ?: return emptyList()
        offered[next.key] = now
        return listOf(Cue.Speak(next.spoken(), Urgency.Low))
    }
}
