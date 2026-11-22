package com.rishitgoklani.hermes_front_mobile.domain.bridge.shell

/** Blocks destructive shell patterns. Kept deliberately small: the phone is dedicated, bricking is the real risk. */
class DenyList(private val patterns: List<Pair<String, Regex>> = DEFAULTS) {
    /** Returns the name of the first rule the command violates, or null if allowed. */
    fun violation(command: String): String? =
        patterns.firstOrNull { it.second.containsMatchIn(command) }?.first

    companion object {
        val DEFAULTS: List<Pair<String, Regex>> = listOf(
            "dd" to Regex("""(^|[\s;&|])dd\s"""),
            "mkfs" to Regex("""\bmkfs(\.\w+)?\b"""),
            "fastboot" to Regex("""\bfastboot\b"""),
            "rm -rf /" to Regex("""\brm\s+(-\w*[rf]\w*\s+)+/(\s|\*|$)"""),
            "block device write" to Regex(""">\s*/dev/(block|sd|mmcblk|nvme)"""),
            "partition tool" to Regex("""\b(parted|fdisk|sgdisk|blkdiscard)\b"""),
            "bootloader" to Regex("""\b(flash_image|bootctl|reboot\s+(bootloader|fastboot|recovery))\b"""),
            "factory reset" to Regex("""(wipe\s+data|--wipe_data|recovery\s+--wipe|cmd\s+recovery\s+wipe)"""),
        )
    }
}
