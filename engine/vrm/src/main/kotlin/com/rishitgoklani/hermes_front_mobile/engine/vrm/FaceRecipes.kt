package com.rishitgoklani.hermes_front_mobile.engine.vrm

/** A repeating wobble: [amp] (radians for head motion, weight for mouth flutter) at [hz] cycles per second. */
class Osc(val amp: Float, val hz: Float)

/**
 * One facial expression written as a recipe instead of a single preset: VRM only defines happy, angry, sad, relaxed and surprised, plus
 * mouth shapes and blinks, so a richer feeling ("shy", "worried", "thinking") is a blend of those plus a small head and eye pose.
 *
 * Head angles are written the way you would describe them, and [FaceMixer] converts them to bone rotations:
 * [chinDown] radians (negative = chin up), [turnLeft] toward the character's left (negative = right), [tiltLeft] top of head toward the
 * character's left ear, [lean] spine forward (negative = back). [gazeX]/[gazeY] bias the eyes the same way the look-at input does.
 */
class FaceRecipe(
    /** VRM expression name -> weight 0..1. Names must be in [FaceChannels]; a channel the model lacks is skipped. */
    val expr: Map<String, Float> = emptyMap(),
    val chinDown: Float = 0f, val turnLeft: Float = 0f, val tiltLeft: Float = 0f, val lean: Float = 0f,
    val gazeX: Float = 0f, val gazeY: Float = 0f,
    val nod: Osc? = null, val shake: Osc? = null, val roll: Osc? = null, val flutter: Osc? = null,
)

/** The expressions a recipe may drive. Order is the mixer's channel order. */
object FaceChannels {
    val NAMES = arrayOf("happy", "sad", "angry", "surprised", "relaxed", "aa", "ih", "ou", "ee", "oh", "blink", "blinkLeft", "blinkRight")
    const val AA = 5
    private val byName = NAMES.withIndex().associate { it.value to it.index }
    fun index(name: String): Int = byName[name] ?: -1
}

object FaceRecipes {
    private fun r(vararg e: Pair<String, Float>, chinDown: Float = 0f, turnLeft: Float = 0f, tiltLeft: Float = 0f, lean: Float = 0f, gazeX: Float = 0f, gazeY: Float = 0f,
                  nod: Osc? = null, shake: Osc? = null, roll: Osc? = null, flutter: Osc? = null) =
        FaceRecipe(mapOf(*e), chinDown, turnLeft, tiltLeft, lean, gazeX, gazeY, nod, shake, roll, flutter)

    /** Keys are [DriverInput.mood] values (the lowercase `Mood` names). */
    val moods: Map<String, FaceRecipe> = mapOf(
        "happy" to r("happy" to 0.8f, chinDown = -0.04f),
        "sad" to r("sad" to 0.85f, chinDown = 0.12f, lean = 0.03f, gazeY = -0.25f),
        "angry" to r("angry" to 0.85f, "blink" to 0.1f, chinDown = 0.06f),
        "surprised" to r("surprised" to 0.9f, chinDown = -0.06f, lean = -0.03f),
        "worried" to r("sad" to 0.5f, "surprised" to 0.15f, chinDown = 0.05f, tiltLeft = 0.08f, gazeY = -0.1f),
        "scared" to r("surprised" to 0.6f, "sad" to 0.35f, chinDown = -0.04f, lean = -0.05f, shake = Osc(0.012f, 13f)),
        "confused" to r("ih" to 0.2f, "surprised" to 0.1f, "sad" to 0.15f, tiltLeft = 0.14f, turnLeft = 0.06f, gazeY = 0.1f),
        "bored" to r("relaxed" to 0.45f, "blink" to 0.45f, chinDown = 0.03f, tiltLeft = -0.07f, gazeX = 0.15f),
        "sleepy" to r("relaxed" to 0.5f, "blink" to 0.7f, chinDown = 0.13f, lean = 0.03f),
        "shy" to r("happy" to 0.3f, "relaxed" to 0.2f, "blink" to 0.12f, chinDown = 0.13f, turnLeft = 0.1f, gazeX = 0.25f, gazeY = -0.3f),
        "proud" to r("happy" to 0.45f, "relaxed" to 0.1f, chinDown = -0.09f, lean = -0.02f),
        "love" to r("happy" to 0.75f, "relaxed" to 0.25f, chinDown = 0.03f, tiltLeft = 0.1f),
        "focused" to r("blink" to 0.16f, "angry" to 0.1f, chinDown = 0.05f),
        "disgusted" to r("angry" to 0.35f, "sad" to 0.3f, "oh" to 0.12f, chinDown = -0.04f, turnLeft = -0.15f, tiltLeft = -0.05f),
    )

    /** Keys are [DriverInput.reaction] values (the lowercase `Reaction` names). Blink, glance and lookAround are handled elsewhere. */
    val reactions: Map<String, FaceRecipe> = mapOf(
        "cry" to r("sad" to 1f, "blink" to 0.3f, chinDown = 0.14f, lean = 0.04f, nod = Osc(0.02f, 6f)),
        "laugh" to r("happy" to 1f, "aa" to 0.5f, chinDown = -0.1f, nod = Osc(0.03f, 7f), flutter = Osc(0.22f, 8f)),
        "giggle" to r("happy" to 0.9f, "aa" to 0.15f, chinDown = 0.04f, tiltLeft = 0.1f, nod = Osc(0.02f, 9f), flutter = Osc(0.12f, 10f)),
        "wink" to r("blinkLeft" to 1f, "happy" to 0.5f, tiltLeft = 0.06f),
        "nod" to r(nod = Osc(0.18f, 2.2f)),
        "shakeno" to r(shake = Osc(0.22f, 2.6f)),
        "eyeroll" to r("relaxed" to 0.3f, "blink" to 0.2f, chinDown = -0.1f, gazeY = 0.6f),
        "startle" to r("surprised" to 1f, chinDown = -0.08f, lean = -0.06f),
        "sigh" to r("sad" to 0.5f, "relaxed" to 0.3f, chinDown = 0.15f, lean = 0.05f),
        "yawn" to r("aa" to 0.9f, "blink" to 0.8f, chinDown = -0.08f),
        "celebrate" to r("happy" to 1f, "aa" to 0.35f, chinDown = -0.1f, roll = Osc(0.1f, 3f), nod = Osc(0.05f, 3f)),
        "dizzy" to r("blink" to 0.45f, "ih" to 0.2f, roll = Osc(0.15f, 1.8f), shake = Osc(0.1f, 1.2f)),
        "hearteyes" to r("happy" to 0.8f, "relaxed" to 0.1f),
        "stareyes" to r("happy" to 0.8f, "surprised" to 0.2f),
        "mindblown" to r("surprised" to 1f, "aa" to 0.4f, chinDown = -0.12f, lean = -0.04f),
    )

    /** Keys are [DriverInput.activity] values (the lowercase `BotState` names). Speaking is lip sync, idle is nothing. */
    val activities: Map<String, FaceRecipe> = mapOf(
        "thinking" to r("ih" to 0.12f, "blink" to 0.1f, tiltLeft = 0.1f, turnLeft = 0.1f, gazeX = 0.35f, gazeY = 0.45f),
        "listening" to r("surprised" to 0.1f, tiltLeft = 0.06f),
        "working" to r("blink" to 0.12f, chinDown = 0.1f, gazeY = -0.3f),
        "error" to r("sad" to 0.4f, "surprised" to 0.2f),
    )
}

/** Which morph target or expression names mean "blushing cheeks" across the naming habits of VRoid, VRChat-style and Japanese models. */
object BlushNames {
    private val WORDS = listOf("blush", "flush", "cheek", "照れ", "頬", "赤面")
    /** ARKit-style names that contain "cheek" but move the cheek shape, not its colour. */
    private val NOT = listOf("puff", "squint", "raise", "dimple", "suck")

    fun matches(name: String): Boolean {
        val n = name.lowercase()
        return NOT.none { it in n } && WORDS.any { it in n }
    }

    fun find(names: Collection<String>): String? = names.firstOrNull { matches(it) }
}
