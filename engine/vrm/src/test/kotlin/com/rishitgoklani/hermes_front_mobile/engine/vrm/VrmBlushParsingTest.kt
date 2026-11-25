package com.rishitgoklani.hermes_front_mobile.engine.vrm

import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.Glb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VrmBlushParsingTest {
    private fun parse(extras: String, expressions: String? = null) = VrmParser.parse(Glb.parse(MiniVrm.build(vrm1 = true, targets = 2, meshExtras = extras, expressions1 = expressions)))
    private val names = ""","extras":{"targetNames":["Fcl_ALL_Joy","Fcl_Blush"]}"""

    @Test fun keepsMorphTargetNames() {
        assertEquals(listOf("Fcl_ALL_Joy", "Fcl_Blush"), parse(names).morphTargetNames[0])
        assertTrue(parse("").morphTargetNames.isEmpty())
    }

    @Test fun aBlushShapeKeyWithoutAnExpressionBecomesABlushExpression() {
        val e = parse(names).expressions["blush"]!!.single()
        assertEquals(0, e.mesh); assertEquals(1, e.index); assertEquals(1f, e.weight, 0f)
        assertTrue(Rig(parse(names)).let { FaceMixer(it).hasOwnBlush })
    }

    @Test fun anExistingBlushExpressionIsNotDuplicated() {
        val a = parse(names, """"expressions":{"custom":{"Blush":{"morphTargetBinds":[{"node":6,"index":0,"weight":1}]}}}""")
        assertFalse(a.expressions.containsKey("blush")); assertTrue(a.expressions.containsKey("Blush"))
    }

    @Test fun cheekShapeKeysThatAreNotBlushAreIgnored() {
        val a = parse(""","extras":{"targetNames":["cheekPuff","cheekSquintLeft"]}""")
        assertFalse(a.expressions.containsKey("blush")); assertFalse(Rig(a).let { FaceMixer(it).hasOwnBlush })
    }
}
