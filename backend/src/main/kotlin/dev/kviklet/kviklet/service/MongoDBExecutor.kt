package dev.kviklet.kviklet.service

import com.mongodb.MongoException
import com.mongodb.client.MongoClients
import dev.kviklet.kviklet.service.dto.ErrorResultLog
import dev.kviklet.kviklet.service.dto.QueryResultLog
import dev.kviklet.kviklet.service.dto.ResultLog
import dev.kviklet.kviklet.service.dto.UpdateResultLog
import org.bson.Document
import org.springframework.stereotype.Service

sealed class MongoQueryResult {
    abstract fun toResultLog(): ResultLog
}

data class MongoRecordsQueryResult(val documents: List<Document>) : MongoQueryResult() {
    override fun toResultLog(): QueryResultLog = QueryResultLog(
        columnCount = documents.firstOrNull()?.keys?.size ?: 0,
        rowCount = documents.size,
    )
}

data class MongoUpdateQueryResult(val modifiedCount: Int) : MongoQueryResult() {
    override fun toResultLog(): UpdateResultLog = UpdateResultLog(
        rowsUpdated = modifiedCount,
    )
}

data class MongoErrorQueryResult(val errorCode: Int, val message: String) : MongoQueryResult() {
    override fun toResultLog(): ResultLog = ErrorResultLog(
        errorCode = errorCode,
        message = message,
    )
}

data class MongoTestCredentialsResult(val success: Boolean, val message: String)

@Service
class MongoDBExecutor {

    fun execute(connectionString: String, databaseName: String, query: String): List<MongoQueryResult> {
        try {
            MongoClients.create(connectionString).use { client ->
                val database = client.getDatabase(databaseName)
                val command = Document.parse(query)

                val result = database.runCommand(command)
                return listOf(processResult(result))
            }
        } catch (e: MongoException) {
            return listOf(mongoExceptionToResult(e))
        }
    }

    private fun processResult(result: Document): MongoQueryResult = when {
        result.containsKey("cursor") -> {
            val cursor = result.get("cursor", Document::class.java)
            val documents = cursor.getList("firstBatch", Document::class.java)
            MongoRecordsQueryResult(documents)
        }
        result.containsKey("n") -> {
            MongoUpdateQueryResult(result.getInteger("n"))
        }
        else -> {
            MongoRecordsQueryResult(listOf(result))
        }
    }

    private fun mongoExceptionToResult(e: MongoException): MongoErrorQueryResult =
        MongoErrorQueryResult(e.code, e.message ?: "Unknown MongoDB error")

    fun testCredentials(connectionString: String): MongoTestCredentialsResult = try {
        MongoClients.create(connectionString).use { client ->
            client.listDatabaseNames().first() // Just to test the connection
            MongoTestCredentialsResult(success = true, message = "Connection successful")
        }
    } catch (e: MongoException) {
        MongoTestCredentialsResult(success = false, message = e.message ?: "Unknown MongoDB error")
    }

    fun getAccessibleDatabases(connectionString: String): List<String> = try {
        MongoClients.create(connectionString).use { client ->
            client.listDatabaseNames().toList()
        }
    } catch (e: MongoException) {
        emptyList()
    }

    fun executeAndStreamDbResponse(
        connectionString: String,
        databaseName: String,
        query: String,
        callback: (List<String>) -> Unit,
    ) {
        try {
            MongoClients.create(connectionString).use { client ->
                val database = client.getDatabase(databaseName)
                val command = Document.parse(query)

                val result = database.runCommand(command)
                if (result.containsKey("cursor")) {
                    val cursor = result.get("cursor", Document::class.java)
                    val documents = cursor.getList("firstBatch", Document::class.java)

                    if (documents.isNotEmpty()) {
                        // Send column names
                        callback(documents.first().keys.toList())

                        // Send data
                        documents.forEach { doc ->
                            callback(doc.values.map { it.toString() })
                        }
                    }
                } else {
                    throw IllegalStateException("Can't stream a non-query result")
                }
            }
        } catch (e: MongoException) {
            throw IllegalStateException("Error executing query", e)
        }
    }
}
