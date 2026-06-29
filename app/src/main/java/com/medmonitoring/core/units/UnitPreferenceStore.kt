package com.medmonitoring.core.units

import android.content.Context
import com.medmonitoring.core.analytics.MetricUnitPreferenceProvider
import com.medmonitoring.core.domain.model.MetricUnitFormatter.inputConfigForMetric
import com.medmonitoring.core.domain.model.ProgramUiDefinition
import com.medmonitoring.core.domain.model.UniversalProgramDefinition
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the user's selected display unit per metric.
 *
 * The selected unit is global state: the input form is the only place that changes it, and every
 * other block (graph, statistics, history, AI prompts) observes [state] and re-renders in the new
 * unit. Stored values themselves are always canonical, so switching units never mutates data.
 */
@Singleton
class UnitPreferenceStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val program: UniversalProgramDefinition,
    private val ui: ProgramUiDefinition
) : MetricUnitPreferenceProvider {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(loadAll())

    /** metricId -> selected unit mode id (empty string means the metric's canonical unit). */
    val state: StateFlow<Map<String, String>> = _state.asStateFlow()

    override fun current(): Map<String, String> = _state.value

    fun selectedUnitId(metricId: String): String = _state.value[metricId].orEmpty()

    /** Switches the selected unit for [metricId]; persists and notifies all observers. */
    fun selectUnit(metricId: String, unitModeId: String) {
        if (_state.value[metricId] == unitModeId) return
        prefs.edit().putString(key(metricId), unitModeId).apply()
        _state.value = _state.value.toMutableMap().apply { put(metricId, unitModeId) }
    }

    private fun loadAll(): Map<String, String> =
        program.metricComponents.associate { metric ->
            val modes = ui.inputConfigForMetric(metric.id).unitModes
            metric.id to (prefs.getString(key(metric.id), null) ?: modes.firstOrNull()?.id ?: "")
        }

    private fun key(metricId: String) = "${program.programId}.$metricId.unit"

    private companion object {
        const val PREFS = "program_profile_metrics"
    }
}
