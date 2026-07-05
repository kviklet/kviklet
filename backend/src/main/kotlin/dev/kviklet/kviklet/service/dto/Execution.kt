package dev.kviklet.kviklet.service.dto

import dev.kviklet.kviklet.security.Resource
import dev.kviklet.kviklet.security.SecuredDomainObject
import dev.kviklet.kviklet.service.ColumnInfo
import org.bson.Document
import java.io.OutputStream

sealed class QueryResult {

    abstract fun toResultLog(): ResultLog
}

data class RecordsQueryResult(
    val columns: List<ColumnInfo>,
    val data: List<Map<String, String>>,
    val storedRows: List<Map<String, String>>? = null,
    val storedRowCount: Int? = null,
) : QueryResult() {
    override fun toResultLog(): QueryResultLog = QueryResultLog(
        columnCount = columns.size,
        rowCount = data.size,
        columns = if (storedRows != null) columns else null,
        storedRows = storedRows,
        storedRowCount = storedRowCount,
    )
}

data class UpdateQueryResult(val rowsUpdated: Int) : QueryResult() {

    override fun toResultLog(): UpdateResultLog = UpdateResultLog(
        rowsUpdated = rowsUpdated,
    )
}

data class ErrorQueryResult(val errorCode: Int, val message: String) : QueryResult() {
    override fun toResultLog(): ResultLog = ErrorResultLog(
        errorCode = errorCode,
        message = message,
    )
}

data class MongoRecordsQueryResult(
    val documents: List<Document>,
    val storedDocuments: List<Map<String, String>>? = null,
    val storedRowCount: Int? = null,
) : QueryResult() {
    override fun toResultLog(): QueryResultLog = QueryResultLog(
        columnCount = documents.firstOrNull()?.keys?.size ?: 0,
        rowCount = documents.size,
        columns = null,
        storedRows = storedDocuments,
        storedRowCount = storedRowCount,
    )
}

/**
 * The fully built file a download produces. The service assembles the bytes (single file or a ZIP)
 * so the controller only needs to set the matching headers and write them out. Carries the
 * execution request so the @Policy check runs object-scoped, like ExecutionResult.
 */
data class DownloadResult(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
    val executionRequest: ExecutionRequestDetails,
) : SecuredDomainObject {
    override fun getSecuredObjectId() = executionRequest.getSecuredObjectId()
    override fun getDomainObjectType() = executionRequest.getDomainObjectType()
    override fun getRelated(resource: Resource) = executionRequest.getRelated(resource)
}

sealed class ExecutionResponse : SecuredDomainObject {
    data class Stream(val connectionId: ConnectionId, val stream: (OutputStream) -> Unit) : ExecutionResponse() {
        override fun getSecuredObjectId(): String = connectionId.toString()
        override fun getDomainObjectType(): Resource = Resource.EXECUTION_REQUEST
        override fun getRelated(resource: Resource): SecuredDomainObject? = null
    }
    data class Executed(val connectionId: ConnectionId, val result: ExecutionResult) : ExecutionResponse() {
        override fun getSecuredObjectId(): String = connectionId.toString()
        override fun getDomainObjectType(): Resource = Resource.EXECUTION_REQUEST
        override fun getRelated(resource: Resource): SecuredDomainObject? = null
    }
}
