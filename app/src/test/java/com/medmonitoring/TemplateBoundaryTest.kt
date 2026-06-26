package com.medmonitoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TemplateBoundaryTest {
    private val root = File(".")

    @Test
    fun templateDoesNotShipPrototypeArtifacts() {
        val forbiddenNames = listOf(
            "ai-logcat-prompts.txt",
            "screen-check.png",
            "ui-top.xml",
            "ui-tags.xml",
            "ui-final.xml"
        )
        forbiddenNames.forEach {
            assertFalse("Prototype artifact must not be copied into template: $it", File(root, it).exists())
        }
        assertFalse("Pixel debug screenshots must not be copied into template", root.walkTopDown().any { it.name.startsWith("pixel8-") })
        assertFalse("Vendored native runtime must be product-specific", File(root, "src/main/cpp").exists())
    }

    @Test
    fun defaultBuildDoesNotRequireNativeAiRuntime() {
        val buildFile = File(root, "build.gradle").readText()
        assertFalse(buildFile.contains("externalNativeBuild"))
        assertFalse(buildFile.contains("ndkVersion"))
        assertFalse(buildFile.contains("abiFilters"))
    }

    @Test
    fun appShellDoesNotImportReferenceProgramInViewModel() {
        val viewModel = File(root, "src/main/java/com/medmonitoring/app/ui/MedViewModel.kt").readText()
        assertFalse(viewModel.contains("BloodPressureDefinitions"))
        assertTrue(viewModel.contains("UniversalProgramDefinition"))
        assertTrue(viewModel.contains("ProgramUiDefinition"))
        assertTrue(viewModel.contains("AnalyticsConfig"))
    }

    @Test
    fun coreDoesNotImportConcreteProgramModules() {
        val coreFiles = File(root, "src/main/java/com/medmonitoring/core").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        coreFiles.forEach { file ->
            val text = file.readText()
            assertFalse(
                "Core must depend on ProgramModuleDefinition, not a concrete program module: ${file.path}",
                text.contains("com.medmonitoring.program.")
            )
        }
    }

    @Test
    fun appUiDoesNotImportConcreteProgramModules() {
        val uiFiles = File(root, "src/main/java/com/medmonitoring/app").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.invariantSeparatorsPath.endsWith("app/MedApplication.kt") }
            .filterNot { it.invariantSeparatorsPath.contains("app/di/") }
            .toList()

        uiFiles.forEach { file ->
            val text = file.readText()
            assertFalse(
                "App UI must receive program data through DI, not import a concrete program module: ${file.path}",
                text.contains("com.medmonitoring.program.")
            )
        }
    }

    @Test
    fun userRecordIsTheCanonicalRuntimeRecordName() {
        val sourceFiles = File(root, "src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.invariantSeparatorsPath.endsWith("core/domain/model/DomainModels.kt") }
            .toList()

        sourceFiles.forEach { file ->
            val text = file.readText()
            assertFalse(
                "Runtime code should use UserRecord as the canonical record name: ${file.path}",
                Regex("""\bHealthRecord\b""").containsMatchIn(text)
            )
        }

        val domainModels = File(root, "src/main/java/com/medmonitoring/core/domain/model/DomainModels.kt").readText()
        assertTrue(domainModels.contains("typealias HealthRecord = UserRecord"))
    }
}
