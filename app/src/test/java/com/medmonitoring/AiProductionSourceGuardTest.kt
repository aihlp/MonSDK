package com.medmonitoring

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class AiProductionSourceGuardTest {
    private val sourceRoot = File("src/main/java")

    @Test
    fun productionSourcesDoNotContainMockAiEngine() {
        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("MockAiEngine") }
            .map { it.invariantSeparatorsPath }
            .toList()

        assertFalse("Mock AI engine must not be present in production sources: $offenders", offenders.isNotEmpty())
    }

    @Test
    fun productionKotlinSourcesDoNotContainCyrillicText() {
        val cyrillic = Regex("[А-Яа-яЁё]")
        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { cyrillic.containsMatchIn(it.readText()) }
            .map { it.invariantSeparatorsPath }
            .toList()

        assertFalse("Move localized text to resources; Cyrillic found in: $offenders", offenders.isNotEmpty())
    }

    @Test
    fun onboardingDoesNotContainLanguageSpecificValidation() {
        val forbidden = listOf("validateOnboardingAnswer", "genderTokens", "yesNoTokens")
        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> forbidden.any { token -> file.readText().contains(token) } }
            .map { it.invariantSeparatorsPath }
            .toList()

        assertFalse("Onboarding must accept answers in any supported language: $offenders", offenders.isNotEmpty())
    }

    @Test
    fun graphUiDoesNotHardcodeProgramEventMarkers() {
        val graphUi = File(sourceRoot, "com/medmonitoring/core/ui/components/PlatformWidgets.kt").readText()

        assertFalse(
            "Graph UI must render event markers from GraphDefinition, not hard-coded medication symbols.",
            graphUi.contains("\\uD83D\\uDC8A") || graphUi.contains("MedicationStatus")
        )
    }

    @Test
    fun llamaRuntimeContractRequiresGrammar() {
        val engineSource = File(sourceRoot, "com/medmonitoring/core/ai/AiEngine.kt").readText()

        assertFalse(
            "Runtime contract must pass a GBNF grammar to native generation.",
            !engineSource.contains("grammar: String") || !engineSource.contains("nativeGenerate(modelPath, prompt, grammar")
        )
    }
}
