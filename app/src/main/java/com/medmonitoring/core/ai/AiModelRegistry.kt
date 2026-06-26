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
            notes = "Recommended compact local model for health-habit analytics."
        ),
        AiModelSpec(
            id = "qwen2_5_7b_instruct_q4_k_m",
            displayName = "Qwen2.5 7B Instruct Q4_K_M",
            repo = "Qwen/Qwen2.5-7B-Instruct-GGUF",
            filename = "qwen2.5-7b-instruct-q4_k_m.gguf",
            quantization = "GGUF Q4_K_M",
            sizeMb = 4700,
            minRamGb = 6,
            recommendedRamGb = 8,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/main/qwen2.5-7b-instruct-q4_k_m.gguf",
            notes = "Higher quality option for stronger devices."
        )
    )

    fun specFor(id: String): AiModelSpec? = recommendedModels.firstOrNull { it.id == id }
}
