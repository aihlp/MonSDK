package com.medmonitoring.core.ingestion

import com.medmonitoring.core.domain.model.AggregationStrategy

object AggregationProcessor {
    fun aggregate(values: List<Double>, strategy: AggregationStrategy): Double? {
        if (values.isEmpty()) return null
        return when (strategy) {
            AggregationStrategy.None -> values.last()
            AggregationStrategy.Average -> values.average()
            AggregationStrategy.Median -> {
                val sorted = values.sorted()
                val middle = sorted.size / 2
                if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
            }
            AggregationStrategy.Max -> values.max()
            AggregationStrategy.Min -> values.min()
        }
    }
}
