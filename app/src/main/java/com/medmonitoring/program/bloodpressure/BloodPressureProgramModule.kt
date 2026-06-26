package com.medmonitoring.program.bloodpressure

import com.medmonitoring.core.analytics.ProgramRecordMapper
import com.medmonitoring.core.domain.model.AnalyticsConfig
import com.medmonitoring.core.domain.model.ProgramUiDefinition
import com.medmonitoring.core.domain.model.UniversalProgramDefinition
import com.medmonitoring.core.domain.model.UserRecord
import com.medmonitoring.core.program.GenericProgramRecordMapper
import com.medmonitoring.core.program.ProgramModuleDefinition

object BloodPressureProgramModule : ProgramModuleDefinition {
    override val program: UniversalProgramDefinition = BloodPressureDefinitions.program
    override val ui: ProgramUiDefinition = BloodPressureDefinitions.ui
    override val analytics: AnalyticsConfig = BloodPressureDefinitions.analyticsConfig
    override val recordMapper: ProgramRecordMapper<UserRecord> = GenericProgramRecordMapper(program)
}
