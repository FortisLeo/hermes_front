package com.rishitgoklani.hermes_front_mobile.engine.vrm

import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.Glb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VrmInspectorTest {
    private fun inspect(json: String) = VrmInspector.inspect(Glb.parse(MiniVrm.glb(json, ByteArray(4))))

    private val nodes = """"nodes":[{"name":"Root","children":[1]},{"name":"hips","translation":[0,1,0],"children":[2]},{"name":"head","translation":[0,0.5,0],"children":[3]},
        {"name":"Eye_L","translation":[0.03,0.05,0.08]},{"name":"Face","mesh":0}]"""
    private val meshes = """"accessors":[{"count":120,"componentType":5126,"type":"VEC3"}],
        "meshes":[{"name":"Face","primitives":[{"attributes":{"POSITION":0},"material":1,"targets":[{},{},{}]}],"extras":{"targetNames":["Fcl_ALL_Joy","Blush","Fcl_MTH_A"]}}]"""

    private val vrm1 = """{"asset":{"version":"2.0"},$nodes,$meshes,
        "materials":[{"name":"Skin","extensions":{"KHR_materials_mtoon":{}}},{"name":"CheekBlush","alphaMode":"BLEND"}],
        "extensions":{"VRMC_vrm":{"specVersion":"1.0","meta":{"name":"Test","authors":["Ann","Bo"],"licenseUrl":"https://example/l"},
          "humanoid":{"humanBones":{"hips":{"node":1},"head":{"node":2},"leftEye":{"node":3}}},
          "lookAt":{"type":"bone","offsetFromHeadBone":[0,0.06,0]},
          "expressions":{"preset":{"happy":{"morphTargetBinds":[{"node":4,"index":0,"weight":1}]}},
            "custom":{"blush":{"morphTargetBinds":[{"node":4,"index":1,"weight":1}]},
              "tint":{"materialColorBinds":[{"material":1,"type":"color","targetValue":[1,0.4,0.5,1]}],"textureTransformBinds":[{"material":1,"scale":[1,1],"offset":[0,0.25]}]}}}}}}"""

    private val vrm0 = """{"asset":{"version":"2.0"},$nodes,$meshes,
        "materials":[{"name":"Skin"},{"name":"CheekBlush"}],
        "extensions":{"VRM":{"meta":{"title":"Old","author":"Cy","licenseName":"CC0"},
          "humanoid":{"humanBones":[{"bone":"hips","node":1},{"bone":"head","node":2}]},
          "firstPerson":{"lookAtTypeName":"BlendShape","firstPersonBoneOffset":{"x":0,"y":0.06,"z":0}},
          "blendShapeMaster":{"blendShapeGroups":[
            {"name":"Joy","presetName":"joy","binds":[{"mesh":0,"index":0,"weight":100}]},
            {"name":"Blush","presetName":"unknown","isBinary":true,"binds":[{"mesh":0,"index":1,"weight":50}],"materialValues":[{"materialName":"CheekBlush","propertyName":"_Color","targetValue":[1,0,0,1]}]}]},
          "materialProperties":[{"name":"CheekBlush","shader":"VRM/MToon"}]}}}"""

    @Test fun keepsMorphTargetAndMeshNamesTheRuntimeAssetDrops() {
        val m = inspect(vrm1).meshes.single()
        assertEquals("Face", m.name); assertEquals(listOf(4), m.nodes); assertEquals(120, m.vertexCount); assertEquals(listOf(1), m.materials)
        assertEquals(listOf("Fcl_ALL_Joy", "Blush", "Fcl_MTH_A"), m.morphTargetNames)
    }

    @Test fun unnamedMorphTargetsGetPlaceholderNames() {
        val m = inspect(vrm1.replace(""","extras":{"targetNames":["Fcl_ALL_Joy","Blush","Fcl_MTH_A"]}""", "")).meshes.single()
        assertEquals(listOf("target0", "target1", "target2"), m.morphTargetNames)
    }

    @Test fun vrm1ExpressionsResolveBindsToTargetNamesAndSplitPresetFromCustom() {
        val i = inspect(vrm1)
        assertFalse(i.isVrm0); assertTrue(i.expression("happy")!!.preset); assertFalse(i.expression("blush")!!.preset)
        val b = i.expression("blush")!!.morphBinds.single(); assertEquals("Blush", b.targetName); assertEquals("Face", b.meshName); assertEquals(1f, b.weight, 0f)
    }

    @Test fun vrm1MaterialOnlyExpressionsAreKept() {
        val e = inspect(vrm1).expression("tint")!!
        assertTrue(e.morphBinds.isEmpty()); assertEquals(listOf("color", "textureTransform"), e.materialBinds.map { it.kind })
        assertEquals("CheekBlush", e.materialBinds[0].materialName); assertEquals(listOf(1f, 0.4f, 0.5f, 1f), e.materialBinds[0].value)
    }

    @Test fun vrm0ReadsGroupsWithMaterialValuesAndBinaryFlag() {
        val i = inspect(vrm0)
        assertTrue(i.isVrm0); assertTrue(i.expression("Joy")!!.preset)
        val b = i.expression("Blush")!!; assertFalse(b.preset); assertTrue(b.isBinary)
        assertEquals(0.5f, b.morphBinds.single().weight, 0f); assertEquals("Blush", b.morphBinds.single().targetName)
        assertEquals("CheekBlush", b.materialBinds.single().materialName); assertEquals(1, b.materialBinds.single().material)
    }

    @Test fun materialShaderComesFromVrm0PropertiesOrMtoonExtension() {
        assertEquals("VRM/MToon", inspect(vrm0).materials[1].shader); assertEquals("MToon", inspect(vrm1).materials[0].shader)
        assertEquals("BLEND", inspect(vrm1).materials[1].alphaMode)
    }

    @Test fun bonesCarryNodeNameAndRestPosition() {
        val eye = inspect(vrm1).bone("leftEye")!!
        assertEquals("Eye_L", eye.nodeName); assertEquals(0.03f, eye.restPosition[0], 1e-6f); assertEquals(1.55f, eye.restPosition[1], 1e-5f)
        assertNull(inspect(vrm1).bone("rightEye"))
    }

    @Test fun readsMetaAndLookAtForBothVersions() {
        val a = inspect(vrm1); assertEquals(listOf("Ann", "Bo"), a.meta.authors); assertEquals("https://example/l", a.meta.license)
        assertEquals("bone", a.lookAt!!.type); assertEquals(0.06f, a.lookAt!!.offsetFromHead!![1], 1e-6f)
        val b = inspect(vrm0); assertEquals("Old", b.meta.name); assertEquals("CC0", b.meta.license); assertEquals("blendshape", b.lookAt!!.type)
    }

    @Test fun searchFindsBlushAcrossExpressionsMorphsAndMaterials() {
        val hits = inspect(vrm1).search("blush", "cheek")
        assertEquals(setOf("expression", "morph", "material"), hits.map { it.kind }.toSet())
        assertTrue(hits.any { it.kind == "morph" && it.name == "Blush" })
        assertTrue(inspect(vrm1).search("nothing-like-this").isEmpty())
    }

    @Test fun summaryListsExpressionsWithCountsAndMarksCustomOnes() {
        val s = inspect(vrm1).summary()
        assertTrue(s, "blush*[m1/x0]" in s); assertTrue("happy[m1/x0]" in s); assertNotNull(Regex("mesh 0 'Face'").find(s))
    }

    @Test fun runsOnTheRealMiniFixtureWithoutOptionalBlocks() {
        val i = VrmInspector.inspect(Glb.parse(MiniVrm.build(vrm1 = true, arms = true)))
        assertEquals(7 + 6, i.nodeCount); assertNotNull(i.bone("leftEye")); assertTrue(i.expressions.any { it.name == "wink" })
    }
}
