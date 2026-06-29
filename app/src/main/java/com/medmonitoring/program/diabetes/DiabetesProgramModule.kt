package com.medmonitoring.program.diabetes

import com.medmonitoring.core.analytics.ProgramRecordMapper
import com.medmonitoring.core.domain.model.AnalyticsConfig
import com.medmonitoring.core.domain.model.ProgramUiDefinition
import com.medmonitoring.core.domain.model.UniversalProgramDefinition
import com.medmonitoring.core.domain.model.UserRecord
import com.medmonitoring.core.program.GenericProgramRecordMapper
import com.medmonitoring.core.program.ProgramModuleDefinition

object DiabetesProgramModule : ProgramModuleDefinition {
    override val program: UniversalProgramDefinition = DiabetesDefinitions.program
    override val ui: ProgramUiDefinition = DiabetesDefinitions.ui
    override val analytics: AnalyticsConfig = DiabetesDefinitions.analyticsConfig
    override val recordMapper: ProgramRecordMapper<UserRecord> = GenericProgramRecordMapper(program)
}
