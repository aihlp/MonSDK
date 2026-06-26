package com.medmonitoring.core.program

import com.medmonitoring.core.analytics.ProgramRecordMapper
import com.medmonitoring.core.domain.model.AnalyticsConfig
import com.medmonitoring.core.domain.model.ProgramUiDefinition
import com.medmonitoring.core.domain.model.UniversalProgramDefinition
import com.medmonitoring.core.domain.model.UserRecord

interface ProgramModuleDefinition {
    val program: UniversalProgramDefinition
    val ui: ProgramUiDefinition
    val analytics: AnalyticsConfig
    val recordMapper: ProgramRecordMapper<UserRecord>
}
