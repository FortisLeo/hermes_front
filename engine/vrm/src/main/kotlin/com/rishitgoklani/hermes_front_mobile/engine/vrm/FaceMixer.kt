package com.rishitgoklani.hermes_front_mobile.engine.vrm

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Blends the mood, the reaction and the activity (thinking, listening, ...) into expression weights, a small head/eye pose and the fade of the
 * on-face effects, easing everything so changes do not snap. Recipes are looked up only when an input changes; [update] and [apply] allocate nothing.
 *
 * Where several layers want the same expression the largest weight wins; head and gaze offsets add up.
 * Blush: if the model has its own blush expression (or a morph target named like one, which the parser turns into an expression) it is driven
 * and the 3D blush decal stays off; otherwise [decalWeight] gives the decal's fade.
 */
class FaceMixer(private val rig: Rig) {
    companion object {
        const val FX_BLUSH = 0; const val FX_TEARS = 1; const val FX_SWEAT = 2; const val FX_ANGER = 3; const val FX_COUNT = 4
        /** Names in [DriverInput.effects] that the engine draws on the face itself. */
        val FX_NAMES = arrayOf("blush", "tears", "sweatdrop", "angermark")
        private const val EASE = 9f; private const val FX_EASE = 7f
        private const val CHIN = 0; private const val TURN = 1; private const val TILT = 2; private const val LEAN = 3; private const val GX = 4; private const val GY = 5
        private const val NOD = 0; private const val SHAKE = 1; private const val ROLL = 2; private const val FLUTTER = 3
        private const val TWO_PI = 6.2831855f
    }

    private val names = FaceChannels.NAMES
    private val chan = IntArray(names.size) { rig.expressionIndex(names[it]) }
    private val tgt = FloatArray(names.size); private val cur = FloatArray(names.size)
    private val ptgt = FloatArray(6); private val pcur = FloatArray(6)
    private val oscAmp = FloatArray(4); private val oscHz = FloatArray(4); private var oscEnv = 0f; private var oscWant = 0f
    private val fxCur = FloatArray(FX_COUNT)
    private var time = 0f

    /** Index of the model's own blush expression, or -1. */
    val blushIndex: Int = BlushNames.find(rig.expressionNames())?.let { rig.expressionIndex(it) } ?: -1
    val hasOwnBlush get() = blushIndex >= 0

    private var mood = ""; private var moodI = 1f; private var reaction = ""; private var reactionI = 1f; private var activity = ""

    /** Head pose offsets in the agent's bone frame (x: positive = chin up), gaze bias, ready for [PoseDriver] to add on top of its own. */
    var headX = 0f; private set
    var headY = 0f; private set
    var headZ = 0f; private set
    var spineX = 0f; private set
    var gazeX = 0f; private set
    var gazeY = 0f; private set

    /** True while anything is still easing, wobbling or showing an animated effect, so the renderer keeps drawing. */
    var busy = false; private set

    fun reset() { mood = ""; reaction = ""; activity = ""; tgt.fill(0f); cur.fill(0f); ptgt.fill(0f); pcur.fill(0f); oscAmp.fill(0f); oscEnv = 0f; oscWant = 0f; fxCur.fill(0f); busy = false }

    /** Fade 0..1 of the 3D decal for effect [kind]; the blush decal is off when the model blushes by itself. */
    fun decalWeight(kind: Int): Float = if (kind == FX_BLUSH && hasOwnBlush) 0f else fxCur[kind]

    fun update(dt: Float, input: DriverInput) {
        time += dt
        if (input.mood != mood || input.moodIntensity != moodI || input.reaction != reaction || input.reactionIntensity != reactionI || input.activity != activity) {
            mood = input.mood; moodI = input.moodIntensity; reaction = input.reaction; reactionI = input.reactionIntensity; activity = input.activity; rebuild()
        }
        val k = 1f - exp(-dt * EASE); var moving = false
        for (c in cur.indices) { cur[c] += (tgt[c] - cur[c]) * k; if (abs(tgt[c] - cur[c]) > 0.004f) moving = true }
        for (p in pcur.indices) { pcur[p] += (ptgt[p] - pcur[p]) * k; if (abs(ptgt[p] - pcur[p]) > 0.002f) moving = true }
        oscEnv += (oscWant - oscEnv) * k; if (oscEnv > 0.01f || oscWant > 0f) moving = true
        val kf = 1f - exp(-dt * FX_EASE)
        for (i in 0 until FX_COUNT) {
            val want = if (input.effects.contains(FX_NAMES[i])) 1f else 0f
            fxCur[i] += (want - fxCur[i]) * kf; if (fxCur[i] > 0.01f) moving = true   // tears and the anger mark animate on their own
        }
        val e = oscEnv
        val nod = osc(NOD, e); val shake = osc(SHAKE, e); val roll = osc(ROLL, e)
        headX = -(pcur[CHIN] + nod); headY = pcur[TURN] + shake; headZ = pcur[TILT] + roll; spineX = -pcur[LEAN]; gazeX = pcur[GX]; gazeY = pcur[GY]
        busy = moving
    }

    private fun osc(o: Int, env: Float): Float = if (oscAmp[o] == 0f) 0f else sin(time * TWO_PI * oscHz[o]) * oscAmp[o] * env

    /** Writes the blended expression weights into the rig. Call after the driver's own blink and lip sync, and before [Rig.applyExpressions]. */
    fun apply() {
        for (c in chan.indices) {
            var v = cur[c]
            if (c == FaceChannels.AA && oscAmp[FLUTTER] != 0f) v += oscAmp[FLUTTER] * oscEnv * (0.5f + 0.5f * sin(time * TWO_PI * oscHz[FLUTTER]))
            if (chan[c] >= 0 && v > 0.003f) rig.raiseExpression(chan[c], v)
        }
        if (blushIndex >= 0 && fxCur[FX_BLUSH] > 0.003f) rig.raiseExpression(blushIndex, 0.9f * fxCur[FX_BLUSH])
    }

    private fun rebuild() {
        tgt.fill(0f); ptgt.fill(0f); oscAmp.fill(0f); oscHz.fill(0f); oscWant = 0f
        add(FaceRecipes.moods[mood], moodI); add(FaceRecipes.reactions[reaction], reactionI); add(FaceRecipes.activities[activity], 1f)
        for (c in tgt.indices) if (tgt[c] > 1f) tgt[c] = 1f
    }

    private fun add(r: FaceRecipe?, w: Float) {
        if (r == null || w <= 0f) return
        for ((name, v) in r.expr) { val i = FaceChannels.index(name); if (i >= 0 && v * w > tgt[i]) tgt[i] = v * w }
        ptgt[CHIN] += r.chinDown * w; ptgt[TURN] += r.turnLeft * w; ptgt[TILT] += r.tiltLeft * w; ptgt[LEAN] += r.lean * w; ptgt[GX] += r.gazeX * w; ptgt[GY] += r.gazeY * w
        setOsc(NOD, r.nod, w); setOsc(SHAKE, r.shake, w); setOsc(ROLL, r.roll, w); setOsc(FLUTTER, r.flutter, w)
    }

    private fun setOsc(slot: Int, o: Osc?, w: Float) {
        if (o == null || o.amp * w <= oscAmp[slot]) return
        oscAmp[slot] = o.amp * w; oscHz[slot] = o.hz; oscWant = 1f
    }
}
