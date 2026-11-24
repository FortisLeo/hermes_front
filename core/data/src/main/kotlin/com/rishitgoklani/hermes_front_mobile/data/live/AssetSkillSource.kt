package com.rishitgoklani.hermes_front_mobile.data.live

import android.content.res.AssetManager
import com.rishitgoklani.hermes_front_mobile.domain.live.SkillInfo
import com.rishitgoklani.hermes_front_mobile.domain.live.SkillParser
import com.rishitgoklani.hermes_front_mobile.domain.live.SkillSource

/** The Hermes skill guides (`hermes-skills/deskbot/<id>/SKILL.md`) packaged as app assets, so the live model reads the same recipes without needing Termux. */
class AssetSkillSource(private val assets: AssetManager, private val root: String = "deskbot") : SkillSource {
    private val index: List<SkillInfo> by lazy {
        (runCatching { assets.list(root)?.toList() }.getOrNull().orEmpty()).sorted().mapNotNull { id ->
            read(id)?.let { SkillParser.parse(id, it) }
        }
    }
    override fun list(): List<SkillInfo> = index
    override fun read(id: String): String? = runCatching { assets.open("$root/$id/SKILL.md").bufferedReader().use { it.readText() } }.getOrNull()
}
