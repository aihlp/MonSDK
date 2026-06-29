package com.medmonitoring.program.mood

import com.medmonitoring.core.analytics.ProgramRecordMapper
import com.medmonitoring.core.domain.model.AnalyticsConfig
import com.medmonitoring.core.domain.model.ProgramUiDefinition
import com.medmonitoring.core.domain.model.UniversalProgramDefinition
import com.medmonitoring.core.domain.model.UserRecord
import com.medmonitoring.core.program.GenericProgramRecordMapper
import com.medmonitoring.core.program.ProgramModuleDefinition

object MoodProgramModule : ProgramModuleDefinition {
    override val program: UniversalProgramDefinition = MoodDefinitions.program
    override val ui: ProgramUiDefinition = MoodDefinitions.ui
    override val analytics: AnalyticsConfig = MoodDefinitions.analyticsConfig
    override val recordMapper: ProgramRecordMapper<UserRecord> = GenericProgramRecordMapper(program)
}
