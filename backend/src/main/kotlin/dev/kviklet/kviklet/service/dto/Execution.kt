package dev.kviklet.kviklet.service.dto

import dev.kviklet.kviklet.security.Resource
import dev.kviklet.kviklet.security.SecuredDomainObject
import dev.kviklet.kviklet.service.ColumnInfo
import org.bson.Document
import java.io.OutputStream

sealed class QueryResult {

    abstract fun toResultLog(): ResultLog
}

data class RecordsQueryResult(val columns: List<ColumnInfo>, val data: List<Map<String, String>>) : QueryResult() {
    override fun toResultLog(): QueryResultLog = QueryResultLog(
        columnCount = columns.size,
        rowCount = data.size,
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

data class MongoRecordsQueryResult(val documents: List<Document>) : QueryResult() {
    override fun toResultLog(): QueryResultLog = QueryResultLog(
        columnCount = documents.firstOrNull()?.keys?.size ?: 0,
        rowCount = documents.size,
    )
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
