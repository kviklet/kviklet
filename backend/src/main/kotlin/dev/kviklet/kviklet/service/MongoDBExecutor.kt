package dev.kviklet.kviklet.service

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.MongoException
import com.mongodb.client.MongoClients
import dev.kviklet.kviklet.service.dto.ErrorQueryResult
import dev.kviklet.kviklet.service.dto.MongoRecordsQueryResult
import dev.kviklet.kviklet.service.dto.QueryResult
import dev.kviklet.kviklet.service.dto.UpdateQueryResult
import org.bson.BSONException
import org.bson.BsonType
import org.bson.Document
import org.bson.codecs.DecoderContext
import org.bson.codecs.DocumentCodec
import org.bson.json.JsonParseException
import org.bson.json.JsonReader
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class MongoDBExecutor {

    fun execute(
        connectionString: String,
        databaseName: String,
        query: String,
        maxRowsToStore: Int? = null,
    ): List<QueryResult> {
        val command = try {
            parseCommand(query)
        } catch (e: InvalidMongoCommandException) {
            return listOf(ErrorQueryResult(-1, e.message))
        }
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
                val result = database.runCommand(command)
                return listOf(processResult(result, maxRowsToStore))
            }
        } catch (e: MongoException) {
            return listOf(mongoExceptionToResult(e))
        } catch (e: IllegalArgumentException) {
            // Thrown by the driver for an unusable connection string.
            return listOf(ErrorQueryResult(-1, e.message ?: "Invalid MongoDB connection settings"))
        }
    }

    /**
     * Parses the query as exactly one command document. Document.parse alone would silently ignore anything after
     * the first document, so a second command would never run without the user noticing.
     */
    private fun parseCommand(query: String): Document {
        val trimmed = query.trim().trimEnd(';').trimEnd()
        if (trimmed.isEmpty()) {
            throw InvalidMongoCommandException("The query is empty.")
        }
        if (trimmed.startsWith("[")) {
            throw InvalidMongoCommandException(
                "The query is an array, but it must be a single command document. To run an aggregation " +
                    "pipeline, wrap it in an aggregate command, e.g. " +
                    "{ aggregate: 'collection', pipeline: [...], cursor: {} }.",
            )
        }
        try {
            val reader = JsonReader(trimmed)
            val command = DocumentCodec().decode(reader, DecoderContext.builder().build())
            if (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
                throw InvalidMongoCommandException(
                    "The query contains more than one document. Only a single command document can be run at a time.",
                )
            }
            return command
        } catch (e: JsonParseException) {
            throw InvalidMongoCommandException("${e.message} $COMMAND_FORMAT_HINT")
        } catch (e: BSONException) {
            throw InvalidMongoCommandException("The query is not a JSON document. $COMMAND_FORMAT_HINT")
        }
    }

    private fun processResult(result: Document, maxRowsToStore: Int? = null): QueryResult = when {
        result["cursor"] is Document -> {
            val cursor = result.get("cursor", Document::class.java)
            val documents = cursor.getList("firstBatch", Document::class.java, emptyList())

            val storedDocuments = if (maxRowsToStore != null) {
                documents.take(maxRowsToStore).map { doc ->
                    doc.entries.associate { (key, value) -> key to (value?.toString() ?: "") }
                }
            } else {
                null
            }

            val storedRowCount = storedDocuments?.size

            MongoRecordsQueryResult(
                documents = documents,
                storedDocuments = storedDocuments,
                storedRowCount = storedRowCount,
            )
        }

        result["n"] is Number -> {
            UpdateQueryResult((result["n"] as Number).toInt())
        }

        else -> {
            val storedDocuments = if (maxRowsToStore != null) {
                listOf(result).map { doc ->
                    doc.entries.associate { (key, value) -> key to (value?.toString() ?: "") }
                }
            } else {
                null
            }

            val storedRowCount = storedDocuments?.size

            MongoRecordsQueryResult(
                documents = listOf(result),
                storedDocuments = storedDocuments,
                storedRowCount = storedRowCount,
            )
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
    } catch (e: IllegalArgumentException) {
        TestCredentialsResult(success = false, message = e.message ?: "Invalid MongoDB connection string")
    }

    fun getAccessibleDatabases(connectionString: String): List<String> = try {
        MongoClients.create(connectionString).use { client ->
            client.listDatabaseNames().toList()
        }
    } catch (e: MongoException) {
        emptyList()
    }
}

private const val COMMAND_FORMAT_HINT =
    "Kviklet runs a single database command document, e.g. { find: 'collection', filter: {} }. " +
        "Shell syntax like db.collection.find() is not supported."

private class InvalidMongoCommandException(override val message: String) : RuntimeException(message)
