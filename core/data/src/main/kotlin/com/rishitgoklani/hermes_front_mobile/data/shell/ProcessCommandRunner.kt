package com.rishitgoklani.hermes_front_mobile.data.shell

import com.rishitgoklani.hermes_front_mobile.domain.bridge.shell.CommandResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.shell.CommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/** Runs commands through `sh -c` (or `su -c` for root) with a hard timeout. [shell]/[su] are injectable for tests. */
class ProcessCommandRunner(
    private val timeoutSeconds: Long = 30,
    private val shell: List<String> = listOf("sh", "-c"),
    private val su: List<String> = listOf("su", "--mount-master", "-c"),
) : CommandRunner {
    override suspend fun run(command: String, root: Boolean): CommandResult = withContext(Dispatchers.IO) {
        val process = try {
            ProcessBuilder((if (root) su else shell) + command).start()
        } catch (e: java.io.IOException) {
            return@withContext CommandResult(127, "", e.message ?: "cannot start process")
        }
        coroutineScope {
            val out = async { process.inputStream.bufferedReader().readText() }
            val err = async { process.errorStream.bufferedReader().readText() }
            val finished = withTimeoutOrNull(TimeUnit.SECONDS.toMillis(timeoutSeconds)) {
                withContext(Dispatchers.IO) { process.waitFor() }
            }
            if (finished == null) {
                process.destroyForcibly()
                CommandResult(124, out.await(), "timed out after ${timeoutSeconds}s")
            } else CommandResult(finished, out.await(), err.await())
        }
    }
}
