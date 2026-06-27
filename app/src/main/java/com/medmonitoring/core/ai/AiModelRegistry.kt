package com.medmonitoring.core.ai

object AiModelRegistry {
    val recommendedModels = listOf(
        AiModelSpec(
            id = "qwen2_5_0_5b_instruct_q4_k_m",
            displayName = "Qwen2.5 0.5B Instruct Q4_K_M",
            repo = "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
            filename = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            quantization = "GGUF Q4_K_M",
            sizeMb = 420,
            minRamGb = 1,
            recommendedRamGb = 2,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            expectedBytes = 491400032L,
            sha256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
            notes = "Debug/default model for the first local GGUF integration check."
        ),
        AiModelSpec(
            id = "qwen2_5_1_5b_instruct_q4_k_m",
            displayName = "Qwen2.5 1.5B Instruct Q4_K_M",
            repo = "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
            filename = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            quantization = "GGUF Q4_K_M",
            sizeMb = 1100,
            minRamGb = 2,
            recommendedRamGb = 3,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            expectedBytes = 1117320736L,
            sha256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
            notes = "Lower-RAM fallback with weaker reasoning."
        ),
        AiModelSpec(
            id = "qwen2_5_3b_instruct_q4_k_m",
            displayName = "Qwen2.5 3B Instruct Q4_K_M",
            repo = "Qwen/Qwen2.5-3B-Instruct-GGUF",
            filename = "qwen2.5-3b-instruct-q4_k_m.gguf",
            quantization = "GGUF Q4_K_M",
            sizeMb = 2100,
            minRamGb = 2,
            recommendedRamGb = 4,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
            expectedBytes = 2104932768L,
            sha256 = "626b4a6678b86442240e33df819e00132d3ba7dddfe1cdc4fbb18e0a9615c62d",
            notes = "Recommended compact local model for health-habit analytics."
        ),
    )

    fun specFor(id: String): AiModelSpec? = recommendedModels.firstOrNull { it.id == id }
}
