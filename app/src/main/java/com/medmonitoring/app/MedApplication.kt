package com.medmonitoring.app

import android.app.Application
import com.medmonitoring.core.ai.AiAnalysisWorker
import com.medmonitoring.core.program.ActiveProgramModuleProvider
import com.medmonitoring.program.bloodpressure.BloodPressureProgramModule
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MedApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ActiveProgramModuleProvider.configure(BloodPressureProgramModule)
        AiAnalysisWorker.schedule(this)
    }
}
