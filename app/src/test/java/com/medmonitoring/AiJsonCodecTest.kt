package com.medmonitoring

import com.medmonitoring.core.ai.AiGoalChecklistItem
import com.medmonitoring.core.ai.AiJsonCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AiJsonCodecTest {
    @Test
    fun validModelJsonParsesAndInvalidJsonFallsBackToNull() {
        val valid = """
            {
              "slider": [{"type":"progress","title":"Progress","text":"1 of 2 complete"}],
              "messages": [{"type":"recommendation","text":"Take an evening measurement."}],
              "checklist": [{"id":"evening","title":"Evening measurement","done":false}],
              "notification": {"title":"Ready","body":"Analysis is ready"}
            }
        """.trimIndent()

        assertNotNull(AiJsonCodec.parseResponse(valid))
        assertNull(AiJsonCodec.parseResponse("not json"))
    }

    @Test
    fun dailyDecisionAcceptsFreeTextRecommendationAndGoal() {
        val output = """{"summary":"elevated","findingIndex":1,"recommendation":"Добавьте контекст симптомов к следующей записи и обсудите повторение с врачом.","checklist":["Записать симптомы"],"alert":true}"""

        val parsed = AiJsonCodec.parseDailyDecision(output)

        assertNotNull(parsed)
        assertEquals(1, parsed?.findingIndex)
        assertEquals("Добавьте контекст симптомов к следующей записи и обсудите повторение с врачом.", parsed?.recommendation)
        assertEquals(listOf("Записать симптомы"), parsed?.checklist)
    }

    @Test
    fun checklistRoundTripKeepsDoneState() {
        val items = listOf(
            AiGoalChecklistItem("one", "First item", true, 10L, 20L),
            AiGoalChecklistItem("two", "Second item", false, 11L, null)
        )

        val restored = AiJsonCodec.checklistFromJson(AiJsonCodec.checklistToJson(items))

        assertEquals(2, restored.size)
        assertEquals(1, restored.count { it.done })
    }
}
