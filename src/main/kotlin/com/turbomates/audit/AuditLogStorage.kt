package com.turbomates.audit

/**
 * Interface for storing audit log entries
 */
interface AuditLogStorage {
    /**
     * Stores an audit log entry
     * @param entry The audit log entry to store
     */
    suspend fun store(entry: AuditLogEntry)
    
    /**
     * Retrieves audit log entries (optional method for implementations that support querying)
     * @param limit Maximum number of entries to retrieve
     * @param offset Number of entries to skip
     * @return List of audit log entries
     */
    suspend fun retrieve(limit: Int = 100, offset: Int = 0): List<AuditLogEntry> = emptyList()
}

/**
 * Simple in-memory implementation of AuditLogStorage for testing and development
 */
class InMemoryAuditLogStorage : AuditLogStorage {
    private val entries = mutableListOf<AuditLogEntry>()
    
    override suspend fun store(entry: AuditLogEntry) {
        entries.add(entry)
    }
    
    override suspend fun retrieve(limit: Int, offset: Int): List<AuditLogEntry> {
        return entries.drop(offset).take(limit)
    }
    
    /**
     * Gets all stored entries (useful for testing)
     */
    fun getAllEntries(): List<AuditLogEntry> = entries.toList()
    
    /**
     * Clears all stored entries (useful for testing)
     */
    fun clear() {
        entries.clear()
    }
}
