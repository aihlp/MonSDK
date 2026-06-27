package com.medmonitoring

import com.medmonitoring.core.ai.AiModelFileState
import com.medmonitoring.core.ai.AiModelFileStore
import com.medmonitoring.core.ai.AiModelRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AiModelFileStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val spec = AiModelRegistry.recommendedModels.first().copy(
        expectedBytes = 3,
        sha256 = "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81"
    )

    @Test
    fun existingModelFileIsReusedWithoutNewDownload() {
        val dir = temp.newFolder("models")
        val target = dir.resolve(spec.filename)
        target.writeBytes(byteArrayOf(1, 2, 3))
        dir.resolve("${spec.filename}.part").writeText("stale")

        val state = AiModelFileStore(dir).prepare(spec)

        assertTrue(state is AiModelFileState.Ready)
        assertEquals(target.absolutePath, (state as AiModelFileState.Ready).file.absolutePath)
        assertFalse(dir.resolve("${spec.filename}.part").exists())
    }

    @Test
    fun commitMovesTempFileIntoStableTarget() {
        val dir = temp.newFolder("models")
        val store = AiModelFileStore(dir)
        val state = store.prepare(spec) as AiModelFileState.NeedsDownload
        state.temp.writeBytes(byteArrayOf(1, 2, 3))

        val target = store.commit(spec, state.temp, state.target)

        assertTrue(target.exists())
        assertEquals(3L, target.length())
        assertFalse(state.temp.exists())
    }

    @Test(expected = IllegalStateException::class)
    fun emptyTempFileIsRejected() {
        val dir = temp.newFolder("models")
        val store = AiModelFileStore(dir)
        val state = store.prepare(spec) as AiModelFileState.NeedsDownload
        state.temp.writeBytes(byteArrayOf())

        store.commit(spec, state.temp, state.target)
    }
}
