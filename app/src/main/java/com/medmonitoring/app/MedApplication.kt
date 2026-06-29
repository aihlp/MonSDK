package com.medmonitoring.app

import android.app.Application
import com.medmonitoring.core.ai.AiAnalysisWorker
import com.medmonitoring.core.program.ActiveProgramModuleProvider
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MedApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ActiveProgramModuleProvider.configure(ProgramCatalog.active)
        AiAnalysisWorker.schedule(this)
    }
}
