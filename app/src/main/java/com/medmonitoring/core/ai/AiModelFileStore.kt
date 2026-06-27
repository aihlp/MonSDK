package com.medmonitoring.core.ai

import java.io.File
import java.security.MessageDigest

class AiModelFileStore(
    private val modelsDir: File
) {
    fun targetFor(spec: AiModelSpec): File = File(modelsDir, spec.filename)

    fun tempFor(spec: AiModelSpec): File = File(modelsDir, "${spec.filename}.part")

    fun prepare(spec: AiModelSpec): AiModelFileState {
        modelsDir.mkdirs()
        val target = targetFor(spec)
        val temp = tempFor(spec)
        return if (target.isVerified(spec)) {
            if (temp.exists()) temp.delete()
            AiModelFileState.Ready(target)
        } else {
            if (target.exists()) target.delete()
            if (temp.isVerified(spec)) {
                check(temp.renameTo(target)) { "Cannot recover completed model download" }
                AiModelFileState.Ready(target)
            } else {
                // A full-size invalid part file cannot be resumed: a Range request would
                // return 416 and leave the model permanently un-installable.
                if (temp.exists() && temp.length() >= spec.expectedBytes) temp.delete()
                AiModelFileState.NeedsDownload(target, temp)
            }
        }
    }

    fun commit(spec: AiModelSpec, temp: File, target: File): File {
        if (!temp.isVerified(spec)) {
            if (temp.exists()) temp.delete()
            error("Downloaded model failed integrity verification")
        }
        if (target.exists()) target.delete()
        check(temp.renameTo(target)) { "Cannot move downloaded model into place" }
        return target
    }

    private fun File.isVerified(spec: AiModelSpec): Boolean {
        if (!exists() || length() != spec.expectedBytes) return false
        val digest = inputStream().use { input ->
            val sha256 = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                sha256.update(buffer, 0, read)
            }
            sha256.digest().joinToString("") { "%02x".format(it) }
        }
        return digest.equals(spec.sha256, ignoreCase = true)
    }
}

sealed interface AiModelFileState {
    data class Ready(val file: File) : AiModelFileState
    data class NeedsDownload(val target: File, val temp: File) : AiModelFileState
}
