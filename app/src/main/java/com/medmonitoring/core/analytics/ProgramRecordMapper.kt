package com.medmonitoring.core.analytics

import com.medmonitoring.core.domain.model.UserRecord

interface ProgramRecordMapper<T> {
    fun map(record: T): UserRecord

    fun mapAll(records: List<T>): List<UserRecord> = records.map(::map)
}
