package com.medmonitoring.core.premium

enum class AppFeature(val isBasicAvailable: Boolean) {
    SENSOR_AUTO_RECORD(false),
    CUSTOM_TAGS(false),
    ANALYTICS(false),
    AI_ASSISTANT(false),
    DATA_EXPORT(true),
    MANUAL_RECORDING(true)
}
