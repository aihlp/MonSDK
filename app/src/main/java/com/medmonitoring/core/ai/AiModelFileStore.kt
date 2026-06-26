package com.medmonitoring.core.ai

import java.io.File

class AiModelFileStore(
    private val modelsDir: File
) {
    fun targetFor(spec: AiModelSpec): File = File(modelsDir, spec.filename)

    fun tempFor(spec: AiModelSpec): File = File(modelsDir, "${spec.filename}.part")

    fun prepare(spec: AiModelSpec): AiModelFileState {
        modelsDir.mkdirs()
        val target = targetFor(spec)
        val temp = tempFor(spec)
        if (temp.exists()) temp.delete()
        return if (target.exists() && target.length() > 0L) {
            AiModelFileState.Ready(target)
        } else {
            AiModelFileState.NeedsDownload(target, temp)
        }
    }

    fun commit(temp: File, target: File): File {
        if (!temp.exists() || temp.length() == 0L) {
            if (temp.exists()) temp.delete()
            error("Downloaded model file is empty")
        }
        if (target.exists()) target.delete()
        check(temp.renameTo(target)) { "Cannot move downloaded model into place" }
        return target
    }
}

sealed interface AiModelFileState {
    data class Ready(val file: File) : AiModelFileState
    data class NeedsDownload(val target: File, val temp: File) : AiModelFileState
}
