package com.medmonitoring.core.ai

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface AiEngine {
    suspend fun generate(request: AiGenerationRequest): String
}

data class AiGenerationRequest(
    val prompt: String,
    val model: AiModelSpec,
    val grammar: String,
    val maxTokens: Int = 36,
    val temperature: Float = 0.1f
)

@Singleton
class LlamaCppAiEngine @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AiEngine {
    override suspend fun generate(request: AiGenerationRequest): String = withContext(Dispatchers.Default) {
        if (!AiLocalGenerationGate.tryEnter()) {
            throw IllegalStateException("Local AI generation is already running")
        }
        val spec = request.model
        try {
            val modelFile = File(context.filesDir, "ai_models/${spec.filename}")
            require(modelFile.exists()) { "GGUF file is missing: ${modelFile.absolutePath}" }
            AndroidLlamaRuntime.generate(
                modelPath = modelFile.absolutePath,
                prompt = qwenPrompt(request.prompt),
                grammar = request.grammar,
                maxTokens = request.maxTokens,
                temperature = request.temperature
            )
        } finally {
            AiLocalGenerationGate.leave()
        }
    }

    private fun qwenPrompt(prompt: String): String {
        return "<|im_start|>system\nReply only with valid JSON. No Markdown. Preserve UTF-8 Unicode text; do not transliterate or strip non-ASCII characters.<|im_end|>\n" +
            "<|im_start|>user\n$prompt<|im_end|>\n" +
            "<|im_start|>assistant\n"
    }
}

private object AiLocalGenerationGate {
    private val mutex = Mutex()

    fun tryEnter(): Boolean = mutex.tryLock()

    fun leave() {
        if (mutex.isLocked) {
            mutex.unlock()
        }
    }
}

object AndroidLlamaRuntime {
    val isAvailable: Boolean

    init {
        isAvailable = runCatching {
            System.loadLibrary("llama_jni")
            true
        }.getOrElse {
            Log.w("AiRuntime", "llama_jni is not bundled", it)
            false
        }
    }

    fun generate(modelPath: String, prompt: String, grammar: String, maxTokens: Int, temperature: Float): String {
        check(isAvailable) { "llama.cpp Android runtime is unavailable" }
        return runCatching {
            nativeGenerate(modelPath, prompt, grammar, maxTokens, temperature)
        }.getOrElse { error ->
            throw IllegalStateException(
                "llama.cpp Android runtime failed to generate a grammar-constrained response.",
                error
            )
        }
    }

    private external fun nativeGenerate(
        modelPath: String,
        prompt: String,
        grammar: String,
        maxTokens: Int,
        temperature: Float
    ): String
}
