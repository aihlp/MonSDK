package com.medmonitoring.app.di

import com.medmonitoring.app.BuildConfig
import com.medmonitoring.core.program.ProgramModuleDefinition
import com.medmonitoring.program.bloodpressure.BloodPressureProgramModule
import com.medmonitoring.program.diabetes.DiabetesProgramModule
import com.medmonitoring.program.mood.MoodProgramModule

/**
 * Registry of all program modules shipped in this template. The active program for a build is
 * selected by [BuildConfig.ACTIVE_PROGRAM], which each product flavor sets to a program id.
 *
 * This lives in the DI layer on purpose: it is the one place (besides MedApplication) allowed to
 * reference concrete program modules. To add a new monitoring app: implement a
 * [ProgramModuleDefinition], register it here, and add a matching flavor entry in
 * app/program-flavors.json.
 */
object ProgramCatalog {
    val all: List<ProgramModuleDefinition> = listOf(
        BloodPressureProgramModule,
        DiabetesProgramModule,
        MoodProgramModule
    )

    fun byProgramId(programId: String): ProgramModuleDefinition =
        all.firstOrNull { it.program.programId == programId }
            ?: error("No program module registered for id='$programId'. Registered: ${all.map { it.program.programId }}")

    /** The program module selected for this build flavor. */
    val active: ProgramModuleDefinition by lazy { byProgramId(BuildConfig.ACTIVE_PROGRAM) }
}
