package com.rishitgoklani.hermes_front_mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the "everything in the Feature catalog has a skill" promise: every catalog feature number appears in exactly one
 * skill section (`### <n> ...`), and every skill file is well-formed. Runs from the :core:domain module directory.
 */
class FeatureCoverageTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "docs/Feature catalog.md").exists() }
    private val catalog = File(root, "docs/Feature catalog.md").readLines().takeWhile { !it.startsWith("## Removed") }
    private val skillFiles = File(root, "hermes-skills").walkTopDown().filter { it.name == "SKILL.md" }.toList()

    private val catalogNumbers: Map<Int, String> = catalog.mapNotNull { Regex("""^\| (\d{1,3}) \| (.+?) \|""").find(it) }
        .associate { it.groupValues[1].toInt() to it.groupValues[2].replace("**", "") }
        .filterKeys { it in 1..431 }

    private val skillSections: List<Pair<Int, File>> = skillFiles.flatMap { f ->
        f.readLines().mapNotNull { Regex("""^### (\d{1,3}) """).find(it)?.groupValues?.get(1)?.toInt()?.let { n -> n to f } }
    }

    @Test fun catalogParsesTo384Features() = assertEquals(384, catalogNumbers.size)

    @Test fun everyCatalogFeatureHasExactlyOneSkillSection() {
        val counts = skillSections.groupingBy { it.first }.eachCount()
        val missing = catalogNumbers.keys.filter { it !in counts }
        val dup = counts.filter { it.value > 1 }.keys
        val extra = counts.keys.filter { it !in catalogNumbers }
        assertTrue("Missing skill sections for: $missing", missing.isEmpty())
        assertTrue("Duplicated sections: $dup", dup.isEmpty())
        assertTrue("Sections for non-catalog numbers: $extra", extra.isEmpty())
    }

    @Test fun everySkillHasValidFrontmatter() {
        assertTrue("expected skill files", skillFiles.size >= 20)
        skillFiles.forEach { f ->
            val lines = f.readLines()
            assertEquals("${f.path}: must start with ---", "---", lines.first())
            val end = lines.drop(1).indexOf("---")
            assertTrue("${f.path}: unterminated frontmatter", end > 0)
            val fm = lines.subList(1, end + 1)
            val name = fm.firstOrNull { it.startsWith("name: ") }?.removePrefix("name: ")
            assertTrue("${f.path}: name must be deskbot-*", name?.startsWith("deskbot-") == true)
            assertTrue("${f.path}: description required", fm.any { it.startsWith("description: ") && it.length > 20 })
        }
        val names = skillFiles.map { f -> f.readLines().first { it.startsWith("name: ") } }
        assertEquals("skill names must be unique", names.size, names.toSet().size)
    }

    @Test fun everySectionHasSubstantiveBody() {
        skillFiles.forEach { f ->
            val sections = f.readText().split(Regex("(?m)^### ")).drop(1)
            sections.forEach { s -> assertTrue("${f.name}: section '${s.lineSequence().first()}' too short", s.length > 60) }
        }
    }

    @Test fun referencedBridgeToolsExist() {
        val known = File(root, "hermes-skills/tools.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val used = skillFiles.flatMap { f -> Regex("""deskbot_([a-z_]+)""").findAll(f.readText().replace(Regex("""deskbot_[a-z_]*\*"""), "")).map { m -> m.groupValues[1] }.toList() }.toSet()
        val unknown = used.filter { it !in known }
        assertTrue("Skills reference unknown tools: $unknown", unknown.isEmpty())
    }
}
