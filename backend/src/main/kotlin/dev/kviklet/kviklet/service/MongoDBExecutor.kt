package dev.kviklet.kviklet.service

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.MongoException
import com.mongodb.client.MongoClients
import dev.kviklet.kviklet.service.dto.ErrorQueryResult
import dev.kviklet.kviklet.service.dto.MongoRecordsQueryResult
import dev.kviklet.kviklet.service.dto.QueryResult
import dev.kviklet.kviklet.service.dto.UpdateQueryResult
import org.bson.Document
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class MongoDBExecutor {

    fun execute(connectionString: String, databaseName: String, query: String): List<QueryResult> {
        try {
            val settings = MongoClientSettings.builder()
                .applyConnectionString(ConnectionString(connectionString))
                .applyToClusterSettings { builder ->
                    builder.serverSelectionTimeout(3, TimeUnit.SECONDS)
                }
                .applyToSocketSettings { builder ->
                    builder.connectTimeout(3, TimeUnit.SECONDS)
                }
                .build()
            MongoClients.create(settings).use { client ->
                val database = client.getDatabase(databaseName)
                val command = Document.parse(query)
                val result = database.runCommand(command)
                return listOf(processResult(result))
            }
        } catch (e: MongoException) {
            return listOf(mongoExceptionToResult(e))
        }
    }

    private fun processResult(result: Document): QueryResult = when {
        result.containsKey("cursor") -> {
            val cursor = result.get("cursor", Document::class.java)
            val documents = cursor.getList("firstBatch", Document::class.java)
            MongoRecordsQueryResult(documents)
        }
        result.containsKey("n") -> {
            UpdateQueryResult(result.getInteger("n"))
        }
        else -> {
            MongoRecordsQueryResult(listOf(result))
        }
    }

    private fun mongoExceptionToResult(e: MongoException): ErrorQueryResult =
        ErrorQueryResult(e.code, e.message ?: "Unknown MongoDB error")

    fun testCredentials(connectionString: String): TestCredentialsResult = try {
        MongoClients.create(connectionString).use { client ->
            client.listDatabaseNames().first() // Just to test the connection
            TestCredentialsResult(success = true, message = "Connection successful")
        }
    } catch (e: MongoException) {
        TestCredentialsResult(success = false, message = e.message ?: "Unknown MongoDB error")
    }

    fun getAccessibleDatabases(connectionString: String): List<String> = try {
        MongoClients.create(connectionString).use { client ->
            client.listDatabaseNames().toList()
        }
    } catch (e: MongoException) {
        emptyList()
    }
}
