package dev.kviklet.kviklet.migration

import com.fasterxml.jackson.databind.ObjectMapper
import liquibase.change.custom.CustomTaskChange
import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.CustomChangeException
import liquibase.exception.SetupException
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor
import java.time.LocalDateTime

/**
 * Migration to backfill the review_status and execution_status columns.
 * This is a snapshot of the calculation logic as of January 2025.
 *
 * This migration runs once to populate the materialized status columns with
 * calculated values based on existing execution request and event data.
 */
class RecalculateRequestStatuses : CustomTaskChange {
    private lateinit var resourceAccessor: ResourceAccessor
    private val objectMapper = ObjectMapper()

    @Throws(CustomChangeException::class)
    override fun execute(database: Database) {
        val connection = database.connection as JdbcConnection

        try {
            // Fetch all execution request IDs
            val requestIds = mutableListOf<String>()
            connection.prepareStatement("SELECT id FROM execution_request").use { stmt ->
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    requestIds.add(rs.getString("id"))
                }
            }

            println("RecalculateRequestStatuses: Processing ${requestIds.size} execution requests")

            // Process each request
            requestIds.forEach { requestId ->
                try {
                    processExecutionRequest(connection, requestId)
                } catch (e: Exception) {
                    // Log error but continue processing other requests
                    println("RecalculateRequestStatuses: Error processing request $requestId: ${e.message}")
                    e.printStackTrace()
                }
            }

            println("RecalculateRequestStatuses: Completed successfully")
        } catch (e: Exception) {
            throw CustomChangeException("Error executing RecalculateRequestStatuses", e)
        }
    }

    private fun processExecutionRequest(connection: JdbcConnection, requestId: String) {
        // Fetch request metadata
        val requestSql = """
            SELECT er.execution_type, er.temporary_access_duration, er.created_at, er.datasource_id,
                   c.review_config, c.max_executions
            FROM execution_request er
            JOIN connection c ON er.datasource_id = c.id
            WHERE er.id = ?
        """

        var requestType: String? = null
        var temporaryAccessDuration: Long? = null
        var createdAt: LocalDateTime? = null
        var reviewConfigJson: String? = null
        var maxExecutions: Int? = null

        connection.prepareStatement(requestSql).use { stmt ->
            stmt.setString(1, requestId)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                requestType = rs.getString("execution_type")
                temporaryAccessDuration = rs.getObject("temporary_access_duration") as? Long
                createdAt = rs.getTimestamp("created_at")?.toLocalDateTime()
                reviewConfigJson = rs.getString("review_config")
                val maxExecObj = rs.getObject("max_executions")
                maxExecutions = when (maxExecObj) {
                    is Int -> maxExecObj
                    is Number -> maxExecObj.toInt()
                    is String -> maxExecObj.toIntOrNull()
                    else -> null
                }
            }
        }

        if (requestType == null || createdAt == null) {
            println("RecalculateRequestStatuses: Request $requestId missing required data, skipping")
            return
        }

        // Parse review config
        val numTotalRequired = try {
            reviewConfigJson?.let {
                objectMapper.readTree(it).get("numTotalRequired")?.asInt() ?: 0
            } ?: 0
        } catch (e: Exception) {
            println("RecalculateRequestStatuses: Error parsing review_config for request $requestId: ${e.message}")
            0
        }

        // Fetch all events for this request
        val events = fetchEvents(connection, requestId)

        // Calculate statuses
        val reviewStatus = calculateReviewStatus(events, numTotalRequired, requestType!!)
        val executionStatus = calculateExecutionStatus(
            events,
            requestType!!,
            maxExecutions,
            temporaryAccessDuration,
            createdAt!!,
        )

        // Update the request
        connection.prepareStatement(
            "UPDATE execution_request SET review_status = ?, execution_status = ? WHERE id = ?",
        ).use { stmt ->
            stmt.setString(1, reviewStatus)
            stmt.setString(2, executionStatus)
            stmt.setString(3, requestId)
            stmt.executeUpdate()
        }
    }

    private fun fetchEvents(connection: JdbcConnection, requestId: String): List<EventData> {
        val events = mutableListOf<EventData>()
        val sql = """
            SELECT id, type, payload, created_at, author_id
            FROM event
            WHERE execution_request_id = ?
            ORDER BY created_at ASC
        """

        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, requestId)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                events.add(
                    EventData(
                        id = rs.getString("id"),
                        type = rs.getString("type"),
                        payload = rs.getString("payload"),
                        createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                        authorId = rs.getString("author_id"),
                    ),
                )
            }
        }

        return events
    }

    private fun calculateReviewStatus(events: List<EventData>, numTotalRequired: Int, requestType: String): String {
        val reviewEvents = events.filter { it.type == "REVIEW" }

        // Check for rejection
        val hasRejection = reviewEvents.any { event ->
            try {
                val payload = objectMapper.readTree(event.payload)
                payload.get("action")?.asText() == "REJECT"
            } catch (e: Exception) {
                false
            }
        }

        if (hasRejection) {
            return "REJECTED"
        }

        // Check for active change requests
        if (hasActiveChangeRequests(events)) {
            return "CHANGE_REQUESTED"
        }

        // Count approvals
        val approvalCount = getApprovalCount(events, requestType)

        return if (approvalCount >= numTotalRequired) {
            "APPROVED"
        } else {
            "AWAITING_APPROVAL"
        }
    }

    private fun hasActiveChangeRequests(events: List<EventData>): Boolean {
        val reviewEvents = events.filter { it.type == "REVIEW" }

        val changesRequested = reviewEvents.filter { event ->
            try {
                val payload = objectMapper.readTree(event.payload)
                payload.get("action")?.asText() == "REQUEST_CHANGE"
            } catch (e: Exception) {
                false
            }
        }

        val approvals = reviewEvents.filter { event ->
            try {
                val payload = objectMapper.readTree(event.payload)
                payload.get("action")?.asText() == "APPROVE"
            } catch (e: Exception) {
                false
            }
        }

        // Check if any change request doesn't have a subsequent approval from the same author
        return changesRequested.any { changeRequest ->
            approvals.none { approval ->
                approval.authorId == changeRequest.authorId &&
                    approval.createdAt.isAfter(changeRequest.createdAt)
            }
        }
    }

    private fun getApprovalCount(events: List<EventData>, requestType: String): Int {
        val resetTimestamp = getLatestResetTimestamp(events, requestType)

        val approvals = events.filter { event ->
            event.type == "REVIEW" && event.createdAt.isAfter(resetTimestamp)
        }.filter { event ->
            try {
                val payload = objectMapper.readTree(event.payload)
                payload.get("action")?.asText() == "APPROVE"
            } catch (e: Exception) {
                false
            }
        }

        // Group by author and count unique authors
        return approvals.map { it.authorId }.toSet().size
    }

    private fun getLatestResetTimestamp(events: List<EventData>, requestType: String): LocalDateTime {
        val latestEdit = events
            .filter { it.type == "EDIT" }
            .maxByOrNull { it.createdAt }

        val latestEditTimestamp = latestEdit?.createdAt ?: LocalDateTime.MIN

        // For temporary access, only edits reset the approval count
        if (requestType == "TemporaryAccess") {
            return latestEditTimestamp
        }

        // For other types, execution errors also reset the approval count
        val latestExecutionError = events
            .filter { it.type == "EXECUTE" }
            .filter { event ->
                try {
                    val payload = objectMapper.readTree(event.payload)
                    val results = payload.get("results")
                    results?.any { result ->
                        result.get("type")?.asText() == "ERROR"
                    } ?: false
                } catch (e: Exception) {
                    false
                }
            }
            .maxByOrNull { it.createdAt }

        val latestErrorTimestamp = latestExecutionError?.createdAt ?: LocalDateTime.MIN

        return if (latestEditTimestamp.isAfter(latestErrorTimestamp)) {
            latestEditTimestamp
        } else {
            latestErrorTimestamp
        }
    }

    private fun calculateExecutionStatus(
        events: List<EventData>,
        requestType: String,
        maxExecutions: Int?,
        temporaryAccessDuration: Long?,
        requestCreatedAt: LocalDateTime,
    ): String {
        val executeEvents = events.filter { it.type == "EXECUTE" }

        return when (requestType) {
            "SingleExecution", "Dump" -> {
                // Count successful executions (no errors)
                val successfulExecutions = executeEvents.filter { event ->
                    try {
                        val payload = objectMapper.readTree(event.payload)
                        val results = payload.get("results")
                        val hasError = results?.any { result ->
                            result.get("type")?.asText() == "ERROR"
                        } ?: false
                        !hasError
                    } catch (e: Exception) {
                        false
                    }
                }

                maxExecutions?.let { max ->
                    if (max == 0) { // Magic number for unlimited executions
                        return "EXECUTABLE"
                    }
                    if (successfulExecutions.size >= max) {
                        return "EXECUTED"
                    }
                }

                "EXECUTABLE"
            }

            "TemporaryAccess" -> {
                if (executeEvents.isEmpty()) {
                    return "EXECUTABLE"
                }

                val firstExecution = executeEvents.minByOrNull { it.createdAt }

                // If duration is null, it means infinite access
                if (temporaryAccessDuration == null) {
                    return "ACTIVE"
                }

                val expirationTime = firstExecution?.createdAt?.plusMinutes(temporaryAccessDuration)
                val now = LocalDateTime.now()

                if (expirationTime != null && expirationTime.isBefore(now)) {
                    "EXECUTED"
                } else {
                    "ACTIVE"
                }
            }

            else -> {
                // Default for unknown types
                "EXECUTABLE"
            }
        }
    }

    @Throws(SetupException::class)
    override fun setUp() {
        // No setup needed
    }

    @Throws(SetupException::class)
    override fun setFileOpener(resourceAccessor: ResourceAccessor) {
        this.resourceAccessor = resourceAccessor
    }

    override fun validate(database: Database): ValidationErrors? = null

    override fun getConfirmationMessage(): String =
        "Recalculated review_status and execution_status for all execution requests"
}

/**
 * Simple data class to hold event information during migration
 */
data class EventData(
    val id: String,
    val type: String,
    val payload: String,
    val createdAt: LocalDateTime,
    val authorId: String,
)
