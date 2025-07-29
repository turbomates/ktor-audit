package com.turbomates.audit

/**
 * Base interface for all search criteria
 */
interface SearchCriteria

/**
 * Empty search criteria that matches all entries
 */
object EmptySearchCriteria : SearchCriteria

/**
 * Search criteria for InMemoryAuditLogStorage
 */
data class InMemorySearchCriteria(
    val method: String? = null,
    val path: String? = null,
    val principal: String? = null,
    val remoteHost: String? = null,
    val userAgent: String? = null,
    val timestampFrom: kotlinx.datetime.Instant? = null,
    val timestampTo: kotlinx.datetime.Instant? = null
) : SearchCriteria

/**
 * Interface for storing audit log entries
 */
interface AuditLogStorage<T : SearchCriteria> {
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

    /**
     * Searches audit log entries (optional method for implementations that support querying)
     * @param parameters Search parameters
     * @param limit Maximum number of entries to retrieve
     * @param offset Number of entries to skip
     * @return List of audit log entries
     */
    suspend fun search(
        criteria: T,
        limit: Int = 100,
        offset: Int = 0
    ): List<AuditLogEntry>
}

/**
 * Simple in-memory implementation of AuditLogStorage for testing and development
 */
class InMemoryAuditLogStorage : AuditLogStorage<InMemorySearchCriteria> {
    private val entries = mutableListOf<AuditLogEntry>()

    override suspend fun store(entry: AuditLogEntry) {
        entries.add(entry)
    }

    override suspend fun retrieve(limit: Int, offset: Int): List<AuditLogEntry> {
        return entries.drop(offset).take(limit)
    }

    override suspend fun search(
        criteria: InMemorySearchCriteria,
        limit: Int,
        offset: Int
    ): List<AuditLogEntry> {
        return entries.filter { entry ->
            (criteria.method == null || entry.method == criteria.method) &&
            (criteria.path == null || entry.path == criteria.path) &&
            (criteria.principal == null || entry.principal?.name == criteria.principal) &&
            (criteria.remoteHost == null || entry.remoteHost == criteria.remoteHost) &&
            (criteria.userAgent == null || entry.userAgent == criteria.userAgent) &&
            (criteria.timestampFrom == null || entry.timestamp >= criteria.timestampFrom) &&
            (criteria.timestampTo == null || entry.timestamp <= criteria.timestampTo)
        }.drop(offset).take(limit)
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
