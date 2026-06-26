package com.medmonitoring

import com.medmonitoring.core.ai.AiModelRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelRegistryTest {
    @Test
    fun debugQwenHalfBIsFirstModelAndHasDirectGgufDownload() {
        val model = AiModelRegistry.recommendedModels.first()

        assertEquals("qwen2_5_0_5b_instruct_q4_k_m", model.id)
        assertEquals("Qwen/Qwen2.5-0.5B-Instruct-GGUF", model.repo)
        assertEquals("qwen2.5-0.5b-instruct-q4_k_m.gguf", model.filename)
        assertTrue(model.downloadUrl.endsWith("/qwen2.5-0.5b-instruct-q4_k_m.gguf"))
    }
}
