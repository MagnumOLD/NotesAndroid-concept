package com.magnumold.notes

internal object ModuleVisibilityOperations {
    fun visibleRecords(records: List<Note>, enabledModules: Set<AppModule>): List<Note> {
        val enabledTypes = enabledModules.mapNotNull { it.recordType }.toSet()
        return records.filter { it.type in enabledTypes }
    }
}
