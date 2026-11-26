package com.rishitgoklani.hermes_front_mobile.engine.vrm

import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.Glb
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.ByteBuffer

class AssetCacheTest {
    @get:Rule val tmp = TemporaryFolder()
    private val stamp = SourceStamp(1234, 5678)
    private val names = ""","extras":{"targetNames":["Fcl_ALL_Joy","Fcl_Blush"]}"""

    private fun parse(vrm1: Boolean) = VrmParser.parse(Glb.parse(MiniVrm.build(vrm1 = vrm1, arms = true, targets = 2, secondPrimitive = true, meshExtras = names)))
    private fun roundTrip(a: VrmAsset): VrmAsset { val f = tmp.newFile(); AssetCache.write(a, f, stamp); return AssetCache.read(f, stamp) { byteArrayOf(it.toByte()) }!! }
    private fun bytes(b: ByteBuffer) = ByteArray(b.capacity()).also { b.duplicate().also { d -> d.clear() }.get(it) }

    @Test fun everyFieldSurvivesTheRoundTripForBothVrmVersions() {
        for (vrm1 in listOf(true, false)) {
            val a = parse(vrm1); val b = roundTrip(a)
            assertEquals(a.isVrm0, b.isVrm0); assertArrayEquals(a.roots, b.roots)
            assertEquals(a.nodes.map { "${it.index} ${it.name} ${it.parent} ${it.children.toList()} ${it.t.toList()} ${it.r.toList()} ${it.s.toList()} ${it.mesh} ${it.skin}" },
                b.nodes.map { "${it.index} ${it.name} ${it.parent} ${it.children.toList()} ${it.t.toList()} ${it.r.toList()} ${it.s.toList()} ${it.mesh} ${it.skin}" })
            assertEquals(a.primitives.size, b.primitives.size)
            for ((p, q) in a.primitives.zip(b.primitives)) {
                assertEquals(listOf(p.mesh, p.node, p.skin, p.material, p.vertexCount, p.attributeStride, p.indexCount), listOf(q.mesh, q.node, q.skin, q.material, q.vertexCount, q.attributeStride, q.indexCount))
                assertEquals(p.indicesAreInt, q.indicesAreInt)
                assertArrayEquals(bytes(p.positions), bytes(q.positions)); assertArrayEquals(bytes(p.attributes), bytes(q.attributes)); assertArrayEquals(bytes(p.indices), bytes(q.indices))
                assertArrayEquals(p.morphBase, q.morphBase, 0f)
                assertEquals(p.morphTargets.size, q.morphTargets.size)
                for ((s, t) in p.morphTargets.zip(q.morphTargets)) { assertArrayEquals(s.vertices, t.vertices); assertArrayEquals(s.deltas, t.deltas, 0f) }
            }
            assertEquals(a.materials.map { listOf(it.name, it.baseColor.toList(), it.shadeColor.toList(), it.baseImage, it.alphaMode, it.cutoff, it.doubleSided, it.shadeShift, it.shadeToony, it.outlineWidth, it.outlineColor.toList(), it.unlit) },
                b.materials.map { listOf(it.name, it.baseColor.toList(), it.shadeColor.toList(), it.baseImage, it.alphaMode, it.cutoff, it.doubleSided, it.shadeShift, it.shadeToony, it.outlineWidth, it.outlineColor.toList(), it.unlit) })
            assertEquals(a.skins.map { it.joints.toList() to it.inverseBind.toList() }, b.skins.map { it.joints.toList() to it.inverseBind.toList() })
            assertEquals(a.humanoid, b.humanoid)
            assertEquals("expression order matters to the rig", a.expressions.keys.toList(), b.expressions.keys.toList())
            assertEquals(a.expressions.mapValues { v -> v.value.map { listOf(it.mesh, it.index, it.weight) } }, b.expressions.mapValues { v -> v.value.map { listOf(it.mesh, it.index, it.weight) } })
            assertEquals(a.meshTargetCounts, b.meshTargetCounts); assertEquals(a.images.map { it.mime to it.view }, b.images.map { it.mime to it.view })
            assertEquals(a.springs.map { c -> c.joints.map { listOf(it.node, it.tailLocal.toList(), it.stiffness, it.gravityPower, it.gravityDir.toList(), it.dragForce, it.hitRadius) } to c.colliders.toList() },
                b.springs.map { c -> c.joints.map { listOf(it.node, it.tailLocal.toList(), it.stiffness, it.gravityPower, it.gravityDir.toList(), it.dragForce, it.hitRadius) } to c.colliders.toList() })
            assertEquals(a.colliders.map { listOf(it.node, it.offset.toList(), it.radius, it.tail?.toList()) }, b.colliders.map { listOf(it.node, it.offset.toList(), it.radius, it.tail?.toList()) })
            assertArrayEquals(a.boundsMin, b.boundsMin, 0f); assertArrayEquals(a.boundsMax, b.boundsMax, 0f)
            assertEquals(a.expressionMeta.mapValues { listOf(it.value.isBinary, it.value.overrideBlink, it.value.overrideLookAt, it.value.overrideMouth) },
                b.expressionMeta.mapValues { listOf(it.value.isBinary, it.value.overrideBlink, it.value.overrideLookAt, it.value.overrideMouth) })
            assertEquals(a.morphTargetNames, b.morphTargetNames)
            assertTrue("the synthetic blush expression is cached too", b.expressions.containsKey("blush"))
        }
    }

    @Test fun buffersSharedByPrimitivesStaySharedSoTheyUploadOnce() {
        val a = parse(true); assertSame(a.primitives[0].positions, a.primitives[1].positions)
        val b = roundTrip(a)
        assertSame(b.primitives[0].positions, b.primitives[1].positions); assertSame(b.primitives[0].attributes, b.primitives[1].attributes)
        assertSame(b.primitives[0].morphTargets, b.primitives[1].morphTargets); assertSame(b.primitives[0].morphBase, b.primitives[1].morphBase)
    }

    @Test fun aRigBuiltFromTheCachePosesExactlyLikeOneFromTheParse() {
        val a = parse(false); val b = roundTrip(a)
        val ra = Rig(a); val rb = Rig(b)
        for (r in listOf(ra, rb)) { r.setBoneEuler(r.nodeFor("head"), 0.2f, 0.1f, 0f); r.setExpression("aa", 0.7f); r.applyExpressions(); r.update() }
        assertArrayEquals(ra.world, rb.world, 0f); assertArrayEquals(ra.meshWeights.getValue(0), rb.meshWeights.getValue(0), 0f)
    }

    @Test fun aCacheForAnotherSourceVersionOrADamagedFileIsIgnored() {
        val f = tmp.newFile(); AssetCache.write(parse(true), f, stamp)
        assertNotNull(AssetCache.read(f, stamp) { ByteArray(0) })
        assertNull("model file changed", AssetCache.read(f, SourceStamp(1234, 9999)) { ByteArray(0) })
        val cut = tmp.newFile(); cut.writeBytes(f.readBytes().copyOf((f.length() * 2 / 3).toInt()))
        assertNull("truncated", AssetCache.read(cut, stamp) { ByteArray(0) })
        val junk = tmp.newFile(); junk.writeBytes(ByteArray(64) { 7 }); assertNull("not a cache", AssetCache.read(junk, stamp) { ByteArray(0) })
        assertNull("missing", AssetCache.read(java.io.File(tmp.root, "nope"), stamp) { ByteArray(0) })
    }

    @Test fun rewritingReplacesTheFileAtomically() {
        val f = tmp.newFile(); AssetCache.write(parse(true), f, stamp); AssetCache.write(parse(false), f, stamp)
        assertTrue(AssetCache.read(f, stamp) { ByteArray(0) }!!.isVrm0); assertEquals(listOf(f.name), tmp.root.list()!!.filter { it.startsWith(f.name) })
    }
}
