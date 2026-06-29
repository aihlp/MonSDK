package com.medmonitoring.core.analytics

import android.content.Context
import com.medmonitoring.core.domain.model.UniversalProgramDefinition
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

fun interface MetricUnitPreferenceProvider {
    fun current(): Map<String, String>
}

@Singleton
class AndroidMetricUnitPreferenceProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val program: UniversalProgramDefinition
) : MetricUnitPreferenceProvider {
    override fun current(): Map<String, String> {
        val prefs = context.getSharedPreferences("program_profile_metrics", Context.MODE_PRIVATE)
        return program.metricComponents.associate { metric ->
            metric.id to prefs.getString("${program.programId}.${metric.id}.unit", null).orEmpty()
        }
    }
}
