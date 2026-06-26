package com.medmonitoring

import com.medmonitoring.core.domain.model.AggregationStrategy
import com.medmonitoring.core.ingestion.AggregationProcessor
import org.junit.Assert.assertEquals
import org.junit.Test

class AggregationProcessorTest {
    @Test
    fun averagesHourlyHeartRateSamples() {
        val samples = listOf(60, 62, 64, 66, 68, 70, 72, 74, 76, 78)

        assertEquals(69.0, AggregationProcessor.aggregate(samples.map(Int::toDouble), AggregationStrategy.Average)!!, 0.0)
    }

    @Test
    fun calculatesMedianForEvenSampleCount() {
        assertEquals(75.0, AggregationProcessor.aggregate(listOf(90.0, 60.0, 80.0, 70.0), AggregationStrategy.Median)!!, 0.0)
    }
}
