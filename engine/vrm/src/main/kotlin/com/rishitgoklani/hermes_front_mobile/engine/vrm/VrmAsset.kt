package com.rishitgoklani.hermes_front_mobile.engine.vrm

import java.nio.ByteBuffer

class VrmNode(val index: Int, val name: String, val parent: Int, val children: IntArray, val t: FloatArray, val r: FloatArray, val s: FloatArray, val mesh: Int, val skin: Int)

/** Morph targets stored sparsely: most face shapes touch a small part of the mesh, so only the moved vertices are kept. */
class MorphTarget(val vertices: IntArray, val deltas: FloatArray)

/**
 * One drawable chunk, already packed the way the GPU wants it (see [VrmParser]):
 * [positions] float3 per vertex; [attributes] interleaved snorm8x4 normal, half2 uv, and for skinned meshes u8x4 joints + unorm8x4 weights.
 */
class VrmPrimitive(
    val mesh: Int, val node: Int, val skin: Int, val material: Int, val vertexCount: Int,
    val positions: ByteBuffer, val attributes: ByteBuffer, val attributeStride: Int,
    val indices: ByteBuffer, val indexCount: Int, val indicesAreInt: Boolean,
    /** Float positions kept only when the primitive has morph targets (needed to blend on the CPU). */
    val morphBase: FloatArray?, val morphTargets: List<MorphTarget>,
) { val skinned get() = skin >= 0 }

enum class AlphaMode { OPAQUE, MASK, BLEND }

class VrmMaterial(
    val name: String, val baseColor: FloatArray, val shadeColor: FloatArray, val baseImage: Int, val shadeImage: Int,
    val emissive: FloatArray, val emissiveImage: Int, val alphaMode: AlphaMode, val cutoff: Float, val doubleSided: Boolean,
    val shadeShift: Float, val shadeToony: Float, val outlineWidth: Float, val outlineColor: FloatArray, val unlit: Boolean,
)

class VrmSkin(val joints: IntArray, val inverseBind: FloatArray)

/** An expression bind: set morph target [index] of glTF mesh [mesh] to weight * the expression's value. */
class ExpressionBind(val mesh: Int, val index: Int, val weight: Float)

class ImageRef(val mime: String, val view: Int)

/** [tailLocal] is where the bone's tip sits in its own local space (the next joint's rest position, or a short stub for the last one). */
class SpringJoint(val node: Int, val tailLocal: FloatArray, val stiffness: Float, val gravityPower: Float, val gravityDir: FloatArray, val dragForce: Float, val hitRadius: Float)
/** A sphere, or a capsule when [tail] is set, attached to [node] in that node's local space. */
class SphereCollider(val node: Int, val offset: FloatArray, val radius: Float, val tail: FloatArray? = null)
/** A chain of bones simulated together. [colliders] are indexes into [VrmAsset.colliders]. */
class SpringChain(val joints: List<SpringJoint>, val center: Int, val colliders: IntArray)

/** Everything the renderer and rig need from a VRM, in compact form. The source file is not kept on the Java heap. */
class VrmAsset(
    val isVrm0: Boolean, val nodes: List<VrmNode>, val roots: IntArray, val primitives: List<VrmPrimitive>, val materials: List<VrmMaterial>,
    val skins: List<VrmSkin>, val humanoid: Map<String, Int>, val expressions: Map<String, List<ExpressionBind>>,
    val meshTargetCounts: Map<Int, Int>, val images: List<ImageRef>, val springs: List<SpringChain>, val colliders: List<SphereCollider>,
    val boundsMin: FloatArray, val boundsMax: FloatArray, private val imageBytes: (Int) -> ByteArray,
) {
    fun imageBytes(image: Int): ByteArray = imageBytes.invoke(images[image].view)
    fun node(name: String): Int = nodes.firstOrNull { it.name == name }?.index ?: -1

    /** Rough GPU texture bytes at [cap] px if every image is decoded as RGBA8 with mipmaps (used by memory policies). */
    val imageCount get() = images.size
}
