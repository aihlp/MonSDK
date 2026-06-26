package com.medmonitoring.core.program

object ActiveProgramModuleProvider {
    @Volatile
    private var activeModule: ProgramModuleDefinition? = null

    fun configure(module: ProgramModuleDefinition) {
        activeModule = module
    }

    fun current(): ProgramModuleDefinition =
        requireNotNull(activeModule) { "Active program module is not configured" }
}
