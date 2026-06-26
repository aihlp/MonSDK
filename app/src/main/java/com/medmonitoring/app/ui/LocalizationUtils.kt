package com.medmonitoring.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.medmonitoring.app.R
import com.medmonitoring.core.domain.model.EmptyStateDefinition
import com.medmonitoring.core.domain.model.FindingCardModel
import com.medmonitoring.core.domain.model.GraphSeriesDefinition
import com.medmonitoring.core.domain.model.MetricComponent
import com.medmonitoring.core.domain.model.StatisticMetric
import com.medmonitoring.core.domain.model.TagGroupDefinition
import com.medmonitoring.core.domain.model.UniversalProgramDefinition
import androidx.compose.ui.platform.LocalConfiguration
import java.time.DayOfWeek
import java.time.Month
import java.time.format.TextStyle

@Composable
fun localizedResource(key: String?, fallback: String): String {
    if (key.isNullOrBlank()) return fallback.localize()
    val context = LocalContext.current
    val resourceId = rememberStringResourceId(key)
    return if (resourceId == 0) fallback.localize() else stringResource(resourceId)
}

@Composable
private fun rememberStringResourceId(key: String): Int {
    val context = LocalContext.current
    return androidx.compose.runtime.remember(key, context.packageName) {
        context.resources.getIdentifier(key, "string", context.packageName)
    }
}

@Composable
fun UniversalProgramDefinition.localizedDisplayName(): String =
    localizedResource(displayNameKey ?: localization.programNameStringKey, displayName)

@Composable
fun MetricComponent.localizedLabel(): String = localizedResource(labelKey, label)

@Composable
fun GraphSeriesDefinition.localizedLabel(): String = localizedResource(labelKey, label)

@Composable
fun TagGroupDefinition.localizedTitle(): String = localizedResource(titleKey, title)

@Composable
fun TagGroupDefinition.localizedTag(tag: String): String = localizedResource(tagKeys[tag], tag)

@Composable
fun EmptyStateDefinition.localizedTitle(): String = localizedResource(titleKey, title)

@Composable
fun EmptyStateDefinition.localizedMessage(): String = localizedResource(messageKey, message)

@Composable
fun EmptyStateDefinition.localizedActionLabel(): String = localizedResource(actionLabelKey, actionLabel)

@Composable
fun EmptyStateDefinition.localizedImageContentDescription(): String =
    localizedResource(imageContentDescriptionKey, imageContentDescription)

@Composable
fun EmptyStateDefinition.localizedInstructions(): List<String> {
    return if (instructionKeys.isNotEmpty() && instructionKeys.size == instructions.size) {
        instructionKeys.mapIndexed { index, key -> localizedResource(key, instructions[index]) }
    } else {
        instructions.map { it.localize() }
    }
}

@Composable
fun String.localize(): String {
    return when (this) {
        "Healthy", "healthy" -> stringResource(R.string.group_healthy)
        "Unhealthy", "unhealthy" -> stringResource(R.string.group_unhealthy)
        "Symptoms / Side effects", "symptoms" -> stringResource(R.string.group_symptoms)
        "Other Medications", "other_medications" -> stringResource(R.string.group_other_medications)
        "Custom", "custom" -> stringResource(R.string.group_custom)
        
        "Walking" -> stringResource(R.string.tag_walking)
        "Cardio" -> stringResource(R.string.tag_cardio)
        "Stretching" -> stringResource(R.string.tag_stretching)
        "Good Sleep" -> stringResource(R.string.tag_good_sleep)
        "Low Salt Diet" -> stringResource(R.string.tag_low_salt_diet)
        "Breathing Exercises" -> stringResource(R.string.tag_breathing_exercises)
        "Vegetables" -> stringResource(R.string.tag_vegetables)
        
        "Alcohol" -> stringResource(R.string.tag_alcohol)
        "Smoking" -> stringResource(R.string.tag_smoking)
        "Fast Food" -> stringResource(R.string.tag_fast_food)
        "Salt" -> stringResource(R.string.tag_salt)
        "Irregular Eating" -> stringResource(R.string.tag_irregular_eating)
        "Energy Drinks" -> stringResource(R.string.tag_energy_drinks)
        "Poor Sleep" -> stringResource(R.string.tag_poor_sleep)
        "Long Sitting" -> stringResource(R.string.tag_long_sitting)
        
        "Headache" -> stringResource(R.string.tag_headache)
        "Dizziness" -> stringResource(R.string.tag_dizziness)
        "Fatigue" -> stringResource(R.string.tag_fatigue)
        "Fast Heartbeat" -> stringResource(R.string.tag_fast_heartbeat)
        "Coordination Problems" -> stringResource(R.string.tag_coordination_problems)
        "Weakness" -> stringResource(R.string.tag_weakness)
        "Nausea" -> stringResource(R.string.tag_nausea)
        "Itchy Skin" -> stringResource(R.string.tag_itchy_skin)
        "Dry Cough" -> stringResource(R.string.tag_dry_cough)
        "Swelling" -> stringResource(R.string.tag_swelling)
        
        "Aspirin" -> stringResource(R.string.tag_aspirin)
        "Statin" -> stringResource(R.string.tag_statin)
        "Beta Blocker" -> stringResource(R.string.tag_beta_blocker)
        "Diuretic" -> stringResource(R.string.tag_diuretic)
        "Calcium Channel Blocker" -> stringResource(R.string.tag_calcium_channel_blocker)
        "Other Medication" -> stringResource(R.string.tag_other_medication)
        
        "Pulse" -> stringResource(R.string.pulse)
        "Dose" -> stringResource(R.string.dose)
        "Medication" -> stringResource(R.string.medication)
        "Measurement" -> stringResource(R.string.measurement)
        "Diary" -> stringResource(R.string.diary)
        "taken" -> stringResource(R.string.taken)
        "missed" -> stringResource(R.string.missed)
        "SYS", "systolic" -> "SYS"
        "DIA", "diastolic" -> "DIA"
        
        "Export CSV" -> stringResource(R.string.export_csv)
        "Import CSV" -> stringResource(R.string.import_csv)
        
        "Medication adherence" -> stringResource(R.string.finding_adherence)
        "Average blood pressure and pulse" -> stringResource(R.string.rule_averages)
        "Most frequent symptoms and factors" -> stringResource(R.string.rule_frequencies)
        "Missed dose blood pressure comparison" -> stringResource(R.string.rule_missed_sys)

        "Adherence" -> stringResource(R.string.dashboard_adherence)
        "Missed" -> stringResource(R.string.dashboard_missed_medication)
        "Records" -> stringResource(R.string.dashboard_records)
        "Occurrences" -> stringResource(R.string.occurrences)
        "Together" -> stringResource(R.string.together)
        "Lowest" -> stringResource(R.string.lowest)
        "Highest" -> stringResource(R.string.highest)
        "Tagged" -> stringResource(R.string.tagged)
        "Other" -> stringResource(R.string.other)

        else -> {
            if (this.startsWith("Avg ")) {
                stringResource(R.string.avg) + " " + this.substring(4).localize()
            } else if (this.startsWith("Frequent ")) {
                stringResource(R.string.finding_frequent_prefix, this.substring(9).localize())
            } else {
                this
            }
        }
    }
}

@Composable
fun String.localizeContextTag(): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (this) {
        "context.environment.pressure_low" -> stringResource(R.string.context_pressure_low)
        "context.environment.pressure_high" -> stringResource(R.string.context_pressure_high)
        "context.environment.dark" -> stringResource(R.string.context_dark)
        "context.environment.bright_light" -> stringResource(R.string.context_bright_light)
        "context.environment.cold" -> stringResource(R.string.context_cold)
        "context.environment.hot" -> stringResource(R.string.context_hot)
        "context.environment.air_dry" -> stringResource(R.string.context_air_dry)
        "context.environment.air_humid" -> stringResource(R.string.context_air_humid)
        "context.activity.motion_low" -> stringResource(R.string.context_motion_low)
        "context.activity.motion_high" -> stringResource(R.string.context_motion_high)
        "context.activity.stationary_possible" -> stringResource(R.string.context_stationary_possible)
        "context.activity.high_possible" -> stringResource(R.string.context_activity_high)
        "context.activity.steps_high" -> stringResource(R.string.context_steps_high)
        "context.activity.step_detected" -> stringResource(R.string.context_step_detected)
        "context.activity.stationary" -> stringResource(R.string.context_stationary)
        "context.activity.motion_detected" -> stringResource(R.string.context_motion_detected)
        "context.behavior.phone_interaction" -> stringResource(R.string.context_phone_interaction)
        "context.quality.device_off_body" -> stringResource(R.string.context_device_off_body)
        "context.calendar.weekday" -> stringResource(R.string.context_weekday)
        "context.calendar.weekend" -> stringResource(R.string.context_weekend)
        "context.calendar.season.winter" -> stringResource(R.string.context_winter)
        "context.calendar.season.spring" -> stringResource(R.string.context_spring)
        "context.calendar.season.summer" -> stringResource(R.string.context_summer)
        "context.calendar.season.autumn" -> stringResource(R.string.context_autumn)
        "context.calendar.time.morning" -> stringResource(R.string.context_morning)
        "context.calendar.time.day" -> stringResource(R.string.context_day)
        "context.calendar.time.evening" -> stringResource(R.string.context_evening)
        "context.calendar.time.night" -> stringResource(R.string.context_night)
        else -> when {
            startsWith("context.calendar.day.") -> {
                val day = substringAfterLast('.').uppercase()
                runCatching {
                    DayOfWeek.valueOf(day).getDisplayName(TextStyle.FULL, locale)
                }.getOrElse { humanizeContextId() }
            }
            startsWith("context.calendar.month.") -> {
                val month = substringAfterLast('.').toIntOrNull()
                month?.let { Month.of(it).getDisplayName(TextStyle.FULL, locale) }
                    ?: humanizeContextId()
            }
            else -> humanizeContextId()
        }
    }
}

private fun String.humanizeContextId(): String =
    substringAfterLast('.').replace('_', ' ').replaceFirstChar(Char::uppercase)

@Composable
fun String.localizeDashboardLabel(): String {
    return when (this) {
        "dashboard_adherence" -> stringResource(R.string.dashboard_adherence)
        "dashboard_missed_medication" -> stringResource(R.string.dashboard_missed_medication)
        "dashboard_avg_sys" -> stringResource(R.string.dashboard_avg_sys)
        "dashboard_avg_dia" -> stringResource(R.string.dashboard_avg_dia)
        "dashboard_avg_pulse" -> stringResource(R.string.dashboard_avg_pulse)
        "dashboard_records" -> stringResource(R.string.dashboard_records)
        else -> this.localize()
    }
}

@Composable
fun StatisticMetric.localizedLabel(): String = localizedResource(labelKey ?: id, label)

@Composable
fun FindingCardModel.localizedTitle(): String {
    titleKey?.let { key ->
        return when (key) {
            "finding_adherence", "finding_combination" -> localizedResource(key, title)
            "finding_frequent_prefix" -> {
                val groupLabel = sourceRuleId.removePrefix("frequent_tag_")
                    .replace("_", " ")
                    .replaceFirstChar { it.uppercase() }
                    .localize()
                stringResource(R.string.finding_frequent_prefix, groupLabel)
            }
            "finding_comparison" -> {
                val metric = metrics.firstOrNull()?.label?.localize() ?: id.substringBefore("_by_").localize()
                val group = id.substringAfter("_by_").substringBefore("_")
                    .replace("_", " ")
                    .replaceFirstChar { it.uppercase() }
                    .localize()
                stringResource(R.string.finding_comparison, metric, group)
            }
            "finding_extreme" -> {
                val mode = if (id.startsWith("highest_")) stringResource(R.string.highest) else stringResource(R.string.lowest)
                val metric = metrics.firstOrNull()?.localizedLabel() ?: id.substringAfter("_").localize()
                stringResource(R.string.finding_extreme, mode, metric)
            }
            else -> localizedResource(key, title)
        }
    }
    return when {
        id.startsWith("adherence_") -> stringResource(R.string.finding_adherence)
        id.startsWith("frequent_tag_") -> {
            val groupKey = id.removePrefix("frequent_tag_").substringBefore("_")
            val groupLabel = groupKey.replace("_", " ").replaceFirstChar { it.uppercase() }.localize()
            stringResource(R.string.finding_frequent_prefix, groupLabel)
        }
        id.startsWith("frequent_combination_") -> stringResource(R.string.finding_combination)
        id.contains("_by_") -> {
            val metricPart = id.substringBefore("_by_").localize()
            val groupPart = id.substringAfter("_by_").substringBefore("_").replace("_", " ").replaceFirstChar { it.uppercase() }.localize()
            stringResource(R.string.finding_comparison, metricPart, groupPart)
        }
        id.startsWith("highest_") || id.startsWith("lowest_") -> {
            val mode = if (id.startsWith("highest_")) stringResource(R.string.highest) else stringResource(R.string.lowest)
            val metric = id.substringAfter("_").localize()
            stringResource(R.string.finding_extreme, mode, metric)
        }
        else -> title.localize()
    }
}

@Composable
fun FindingCardModel.localizedMessage(): String {
    messageKey?.let { key ->
        return when (key) {
            "msg_adherence" -> {
                val percent = metrics.firstOrNull { it.id.endsWith("_percent") }?.value?.toIntOrNull() ?: 0
                val status = if (message.contains("taken")) stringResource(R.string.taken) else stringResource(R.string.missed)
                stringResource(R.string.msg_adherence, percent, status)
            }
            "msg_frequent" -> {
                val tag = message.substringBefore(" appeared").localizedCompoundTag()
                val count = metrics.firstOrNull { it.id.endsWith("_count") }?.value?.toIntOrNull() ?: 0
                val total = basis.recordCountFromBasis()
                stringResource(R.string.msg_frequent, tag, count, total)
            }
            "msg_combination" -> {
                val combo = message.substringBefore(" appeared").localizedCompoundTag()
                val count = metrics.firstOrNull { it.id.endsWith("_count") }?.value?.toIntOrNull() ?: 0
                stringResource(R.string.msg_combination, combo, count)
            }
            "msg_comparison" -> {
                val right = metrics.firstOrNull { it.id.endsWith("_right") }
                val left = metrics.firstOrNull { it.id.endsWith("_left") }
                if (right != null && left != null) {
                    stringResource(
                        R.string.msg_comparison,
                        right.localizedLabel(),
                        right.value.toIntOrNull() ?: 0,
                        right.unit.orEmpty(),
                        left.value.toIntOrNull() ?: 0,
                        left.localizedLabel()
                    )
                } else {
                    message
                }
            }
            "msg_extreme" -> {
                val mode = if (id.startsWith("highest_")) stringResource(R.string.highest) else stringResource(R.string.lowest)
                val metric = metrics.firstOrNull()
                stringResource(
                    R.string.msg_extreme,
                    mode,
                    metric?.localizedLabel() ?: id.substringAfter("_").localize(),
                    metric?.value?.toIntOrNull() ?: 0,
                    metric?.unit.orEmpty()
                )
            }
            else -> localizedResource(key, message)
        }
    }
    return when {
        id.startsWith("adherence_") -> {
            val percent = metrics.firstOrNull { it.id.endsWith("_percent") }?.value?.toIntOrNull() ?: 0
            val status = if (message.contains("taken")) stringResource(R.string.taken) else stringResource(R.string.missed)
            stringResource(R.string.msg_adherence, percent, status)
        }
        id.startsWith("frequent_tag_") -> {
            val tag = message.substringBefore(" appeared")
            val count = metrics.firstOrNull { it.id.endsWith("_count") }?.value?.toIntOrNull() ?: 0
            val totalMatch = Regex("of (\\d+) records").find(message)
            val total = totalMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            stringResource(R.string.msg_frequent, tag.localize(), count, total)
        }
        id.startsWith("frequent_combination_") -> {
            val combo = message.substringBefore(" appeared")
            val count = metrics.firstOrNull { it.id.endsWith("_count") }?.value?.toIntOrNull() ?: 0
            stringResource(R.string.msg_combination, combo, count)
        }
        id.contains("_by_") -> {
            val labelMatch = Regex("(\\w+) average was (\\d+) (\\w+) vs (\\d+) \\3 for (\\w+)").find(message)
            if (labelMatch != null) {
                val rightLabel = labelMatch.groupValues[1].localize()
                val rightVal = labelMatch.groupValues[2].toIntOrNull() ?: 0
                val unit = labelMatch.groupValues[3]
                val leftVal = labelMatch.groupValues[4].toIntOrNull() ?: 0
                val leftLabel = labelMatch.groupValues[5].localize()
                stringResource(R.string.msg_comparison, rightLabel, rightVal, unit, leftVal, leftLabel)
            } else message
        }
        id.startsWith("highest_") || id.startsWith("lowest_") -> {
            val mode = if (id.startsWith("highest_")) stringResource(R.string.highest) else stringResource(R.string.lowest)
            val metric = id.substringAfter("_").localize()
            val value = metrics.firstOrNull()?.value?.toIntOrNull() ?: 0
            val unit = metrics.firstOrNull()?.unit ?: ""
            stringResource(R.string.msg_extreme, mode, metric, value, unit)
        }
        else -> message
    }
}

@Composable
fun FindingCardModel.localizedBasis(): String {
    basisKey?.let { key ->
        return when (key) {
            "basis_meds" -> stringResource(R.string.basis_meds, basis.recordCountFromBasis())
            "basis_records" -> stringResource(R.string.basis_records, basis.recordCountFromBasis())
            "basis_comparison", "basis_tagged_comparison" -> {
                val counts = Regex("(\\d+)").findAll(basis).mapNotNull { it.value.toIntOrNull() }.toList()
                val labels = comparisonBasisLabels()
                if (counts.size >= 2 && labels != null) {
                    stringResource(
                        R.string.basis_comparison,
                        counts[0],
                        labels.first,
                        counts[1],
                        labels.second
                    )
                } else {
                    basis
                }
            }
            else -> localizedResource(key, basis)
        }
    }
    return when {
        basis.startsWith("Based on ") && basis.endsWith(" medication records.") -> {
            val count = basis.removePrefix("Based on ").removeSuffix(" medication records.").toIntOrNull() ?: 0
            stringResource(R.string.basis_meds, count)
        }
        basis.startsWith("Based on ") && basis.endsWith(" records.") && !basis.contains(" and ") -> {
            val count = basis.removePrefix("Based on ").removeSuffix(" records.").toIntOrNull() ?: 0
            stringResource(R.string.basis_records, count)
        }
        basis.startsWith("Based on ") && basis.contains(" and ") && basis.endsWith(" records.") -> {
            val match = Regex("Based on (\\d+) (\\w+) and (\\d+) (\\w+) records").find(basis)
            if (match != null) {
                val count1 = match.groupValues[1].toIntOrNull() ?: 0
                val label1 = match.groupValues[2].localize()
                val count2 = match.groupValues[3].toIntOrNull() ?: 0
                val label2 = match.groupValues[4].localize()
                stringResource(R.string.basis_comparison, count1, label1, count2, label2)
            } else basis
        }
        else -> basis
    }
}

@Composable
private fun String.localizedCompoundTag(): String =
    split(" + ").map { it.localize() }.joinToString(" + ")

private fun String.recordCountFromBasis(): Int =
    Regex("(\\d+)").find(this)?.groupValues?.get(1)?.toIntOrNull() ?: 0

@Composable
private fun FindingCardModel.comparisonBasisLabels(): Pair<String, String>? {
    val taggedMatch = Regex("Based on \\d+ tagged and \\d+ other records").find(basis)
    if (taggedMatch != null) return stringResource(R.string.tagged) to stringResource(R.string.other)
    val labelMatch = Regex("Based on \\d+ (\\w+) and \\d+ (\\w+) records").find(basis) ?: return null
    return labelMatch.groupValues[1].localize() to labelMatch.groupValues[2].localize()
}
