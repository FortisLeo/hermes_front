package com.rishitgoklani.hermes_front_mobile.domain.ui

/** Pure snapshot of one accessibility node. The Android adapter maps AccessibilityNodeInfo into this. */
data class UiNode(
    val text: String = "",
    val desc: String = "",
    val id: String = "",
    val cls: String = "",
    val pkg: String = "",
    val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0,
    val clickable: Boolean = false,
    val scrollable: Boolean = false,
    val editable: Boolean = false,
    val enabled: Boolean = true,
    val focused: Boolean = false,
    val checked: Boolean? = null,
    val children: List<UiNode> = emptyList(),
) {
    val centerX get() = (left + right) / 2
    val centerY get() = (top + bottom) / 2
    val label get() = text.ifEmpty { desc }
}

/** How to locate an element: any non-null field must match (AND). Matching is case-insensitive. */
data class UiQuery(val text: String? = null, val exact: Boolean = false, val id: String? = null, val desc: String? = null, val index: Int = 0)

object UiTree {
    fun flatten(root: UiNode): List<UiNode> = buildList {
        fun walk(n: UiNode) { add(n); n.children.forEach(::walk) }
        walk(root)
    }

    fun find(root: UiNode, q: UiQuery): List<UiNode> = flatten(root).filter { matches(it, q) }

    fun matches(n: UiNode, q: UiQuery): Boolean {
        q.text?.let { t ->
            val hay = n.text.ifEmpty { n.desc }
            if (!(if (q.exact) hay.equals(t, true) else hay.contains(t, true))) return false
        }
        q.id?.let { if (!n.id.endsWith(it, true)) return false }
        q.desc?.let { if (!n.desc.contains(it, true)) return false }
        return q.text != null || q.id != null || q.desc != null
    }

    /** Nodes worth showing to the model: has a label, or is interactive. Keeps output small. */
    fun interesting(root: UiNode): List<UiNode> = flatten(root).filter {
        it.label.isNotEmpty() || it.clickable || it.editable || it.scrollable
    }

    /** One line per node: `[i] Class "label" id=… (cx,cy) flags`. Truncated to [maxNodes]. */
    fun compact(root: UiNode, maxNodes: Int = 120): String {
        val nodes = interesting(root)
        val lines = nodes.take(maxNodes).mapIndexed { i, n ->
            val flags = buildList {
                if (n.clickable) add("click"); if (n.editable) add("edit"); if (n.scrollable) add("scroll")
                if (!n.enabled) add("disabled"); if (n.focused) add("focus"); if (n.checked == true) add("checked")
            }.joinToString(",")
            "[$i] ${n.cls.substringAfterLast('.')} \"${n.label.take(60)}\"" +
                (if (n.id.isNotEmpty()) " id=${n.id.substringAfter(":id/")}" else "") +
                " (${n.centerX},${n.centerY})" + (if (flags.isNotEmpty()) " $flags" else "")
        }
        val more = if (nodes.size > maxNodes) "\n… ${nodes.size - maxNodes} more" else ""
        return lines.joinToString("\n") + more
    }

    fun keyboardLikely(root: UiNode): Boolean = flatten(root).any { it.pkg.contains("inputmethod", true) || it.pkg.contains("keyboard", true) }
}
