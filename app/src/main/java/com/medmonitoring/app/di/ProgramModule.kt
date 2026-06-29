package com.medmonitoring.app.di

import com.medmonitoring.core.analytics.ProgramRecordMapper
import com.medmonitoring.core.ai.AiEngine
import com.medmonitoring.core.ai.LlamaCppAiEngine
import com.medmonitoring.core.domain.model.PlatformIntegrationConfig
import com.medmonitoring.core.domain.model.ProgramAnalyticsSchema
import com.medmonitoring.core.domain.model.ProgramUiDefinition
import com.medmonitoring.core.domain.model.UniversalProgramDefinition
import com.medmonitoring.core.domain.model.UserRecord
import com.medmonitoring.core.program.ProgramModuleDefinition
import com.medmonitoring.app.ProgramCatalog
import com.medmonitoring.core.util.StringProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProgramModule {
    @Binds
    @Singleton
    abstract fun bindStringProvider(provider: AndroidStringProvider): StringProvider

    companion object {
        @Provides
        @Singleton
        fun provideProgramModuleDefinition(): ProgramModuleDefinition =
            ProgramCatalog.active

        @Provides
        @Singleton
        fun provideProgramDefinition(module: ProgramModuleDefinition): UniversalProgramDefinition =
            module.program

        @Provides
        @Singleton
        fun provideProgramUiDefinition(module: ProgramModuleDefinition): ProgramUiDefinition =
            module.ui

        @Provides
        @Singleton
        fun providePlatformIntegrationConfig(module: ProgramModuleDefinition): PlatformIntegrationConfig =
            module.program.integrations

        @Provides
        @Singleton
        fun provideProgramAnalyticsSchema(module: ProgramModuleDefinition): ProgramAnalyticsSchema =
            module.analytics

        @Provides
        @Singleton
        fun provideProgramRecordMapper(module: ProgramModuleDefinition): ProgramRecordMapper<UserRecord> =
            module.recordMapper

        @Provides
        @Singleton
        fun provideAiEngine(engine: LlamaCppAiEngine): AiEngine = engine
    }
}
