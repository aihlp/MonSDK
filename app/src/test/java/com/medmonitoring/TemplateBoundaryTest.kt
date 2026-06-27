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
        assertTrue("The local AI runtime must be available to every generated app", File(root, "src/main/cpp/llama.cpp").isDirectory)
    }

    @Test
    fun localAiBuildIncludesArm64NativeRuntime() {
        val buildFile = File(root, "build.gradle").readText()
        assertTrue(buildFile.contains("externalNativeBuild"))
        assertTrue(buildFile.contains("ndkVersion"))
        assertTrue(buildFile.contains("arm64-v8a"))
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
    fun aiCoreDoesNotHardcodeReferenceProgramId() {
        val aiFiles = File(root, "src/main/java/com/medmonitoring/core/ai").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        aiFiles.forEach { file ->
            assertFalse(
                "AI core must use injected/current program id, not the reference app id: ${file.path}",
                file.readText().contains("blood-pressure-monitor")
            )
        }
    }

    @Test
    fun referenceManifestDoesNotRequestGlucosePermission() {
        val manifest = File(root, "src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android.permission.health.READ_BLOOD_PRESSURE"))
        assertFalse(
            "Glucose permission belongs to diabetes products, not the blood-pressure reference APK.",
            manifest.contains("android.permission.health.READ_BLOOD_GLUCOSE")
        )
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
