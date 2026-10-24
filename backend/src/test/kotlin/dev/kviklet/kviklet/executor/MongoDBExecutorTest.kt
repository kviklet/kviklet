package dev.kviklet.kviklet.executor

import com.mongodb.client.MongoClients
import dev.kviklet.kviklet.service.MongoDBExecutor
import dev.kviklet.kviklet.service.dto.ErrorQueryResult
import dev.kviklet.kviklet.service.dto.MongoRecordsQueryResult
import dev.kviklet.kviklet.service.dto.UpdateResultLog
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import org.bson.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@ActiveProfiles("test")
class MongoDBExecutorTest(@Autowired val mongoDBExecutor: MongoDBExecutor) {

    companion object {
        val mongoDBContainer: MongoDBContainer = MongoDBContainer(DockerImageName.parse("mongo:jammy"))
            .withReuse(true)

        init {
            mongoDBContainer.start()
        }
    }

    private lateinit var connectionString: String
    private lateinit var databaseName: String

    @BeforeEach
    fun setup() {
        connectionString = mongoDBContainer.replicaSetUrl
        databaseName = "testdb"

        // Initialize test data
        MongoClients.create(connectionString).use { client ->
            val database = client.getDatabase(databaseName)
            val collection = database.getCollection("test_collection")
            collection.insertOne(
                Document(
                    mapOf(
                        "int_field" to 1,
                        "string_field" to "test",
                        "boolean_field" to true,
                        "double_field" to 1.23,
                        "array_field" to listOf(1, 2, 3),
                        "nested_field" to mapOf("key" to "value"),
                    ),
                ),
            )
        }
    }

    @AfterEach
    fun cleanup() {
        MongoClients.create(connectionString).use { client ->
            client.getDatabase(databaseName).drop()
        }
    }

    @Test
    fun testFindSimple() {
        val result = mongoDBExecutor.execute(
            connectionString,
            databaseName,
            "{ find: 'test_collection', filter: {} }",
        )
        result.first() shouldBe MongoRecordsQueryResult(
            documents = listOf(
                Document(
                    mapOf(
                        "_id" to result.first().let { (it as MongoRecordsQueryResult).documents.first()["_id"] },
                        "int_field" to 1,
                        "string_field" to "test",
                        "boolean_field" to true,
                        "double_field" to 1.23,
                        "array_field" to listOf(1, 2, 3),
                        "nested_field" to Document(mapOf("key" to "value")),
                    ),
                ),
            ),
        )
    }

    @Test
    fun testDatabaseError() {
        val result = mongoDBExecutor.execute(
            connectionString,
            databaseName,
            "{ invalidCommand: 'test_collection' }",
        )
        (result.first() as ErrorQueryResult).errorCode shouldBe 59
        (result.first() as ErrorQueryResult).message shouldStartWith
            "Command failed with error 59 (CommandNotFound):"
    }

    @Test
    fun testConnectionError() {
        val invalidConnectionString = "mongodb://invalidhost:27017"
        val result = mongoDBExecutor.execute(
            invalidConnectionString,
            databaseName,
            "{ find: 'test_collection', filter: {} }",
        )
        (result.first() as ErrorQueryResult).message shouldContain "invalidhost"
    }

    @Test
    fun testComplexQuery() {
        val result = mongoDBExecutor.execute(
            connectionString,
            databaseName,
            """
            {
                aggregate: 'test_collection',
                pipeline: [
                    { ${'$'}match: { int_field: { ${'$'}gt: 0 } } },
                    { ${'$'}project: { 
                        _id: 0,
                        int_field: 1,
                        doubled_field: { ${'$'}multiply: ['${'$'}int_field', 2] }
                    }}
                ],
                cursor: {}
            }
            """.trimIndent(),
        )

        result.first() shouldBe MongoRecordsQueryResult(
            documents = listOf(
                Document(
                    mapOf(
                        "int_field" to 1,
                        "doubled_field" to 2,
                    ),
                ),
            ),
        )
    }

    @Test
    fun testUpdate() {
        val updateResult = mongoDBExecutor.execute(
            connectionString,
            databaseName,
            """
            {
                update: 'test_collection',
                updates: [
                    { q: { int_field: 1 }, u: { ${'$'}set: { string_field: 'updated' } } }
                ]
            }
            """.trimIndent(),
        )

        (updateResult.first().toResultLog() as UpdateResultLog).rowsUpdated shouldBe 1

        val findResult = mongoDBExecutor.execute(
            connectionString,
            databaseName,
            "{ find: 'test_collection', filter: { int_field: 1 } }",
        )
        (findResult.first() as MongoRecordsQueryResult).documents.first()["string_field"] shouldBe "updated"
    }

    @Test
    fun `aggregation pipeline without an aggregate command returns an error result`() {
        val result = mongoDBExecutor.execute(
            connectionString,
            databaseName,
            "[{ ${'$'}match: { int_field: 1 } }]",
        )
        val error = result.single().shouldBeInstanceOf<ErrorQueryResult>()
        error.errorCode shouldBe -1
        error.message shouldContain "aggregate"
    }

    @Test
    fun `shell syntax returns an error result`() {
        val result = mongoDBExecutor.execute(connectionString, databaseName, "db.test_collection.find({})")
        val error = result.single().shouldBeInstanceOf<ErrorQueryResult>()
        error.message shouldContain "found 'db'"
        error.message shouldContain "Shell syntax"
    }

    @ParameterizedTest
    @ValueSource(strings = ["{ find: 'test_collection', filter: {}", "'test_collection'", "42", "", "   "])
    fun `queries that are not a command document return an error result`(query: String) {
        val result = mongoDBExecutor.execute(connectionString, databaseName, query)
        result.single().shouldBeInstanceOf<ErrorQueryResult>().errorCode shouldBe -1
    }

    @Test
    fun `a second document is rejected instead of silently ignored`() {
        val result = mongoDBExecutor.execute(
            connectionString,
            databaseName,
            "{ find: 'test_collection', filter: {} } { delete: 'test_collection', deletes: [] }",
        )
        result.single().shouldBeInstanceOf<ErrorQueryResult>().message shouldContain "more than one document"
    }

    @Test
    fun `trailing semicolon is still accepted`() {
        val result = mongoDBExecutor.execute(
            connectionString,
            databaseName,
            "{ find: 'test_collection', filter: {} };\n",
        )
        result.single().shouldBeInstanceOf<MongoRecordsQueryResult>().documents shouldHaveSize 1
    }

    @Test
    fun `count returns the number of matching documents`() {
        val result = mongoDBExecutor.execute(connectionString, databaseName, "{ count: 'test_collection' }")
        (result.single().toResultLog() as UpdateResultLog).rowsUpdated shouldBe 1
    }

    @Test
    fun `invalid connection string returns an error result`() {
        val result = mongoDBExecutor.execute(
            "mongodb://localhost:notaport",
            databaseName,
            "{ find: 'test_collection', filter: {} }",
        )
        result.single().shouldBeInstanceOf<ErrorQueryResult>()
    }

    @Test
    fun `invalid connection string fails the credentials test`() {
        val result = mongoDBExecutor.testCredentials("not-a-mongodb-url")
        result.success shouldBe false
        result.message shouldContain "mongodb://"
    }

    @Test
    fun testAccessibleDatabases() {
        val databases = mongoDBExecutor.getAccessibleDatabases(connectionString)
        databases.contains(databaseName) shouldBe true
    }

    @Test
    fun testTestCredentials() {
        val result = mongoDBExecutor.testCredentials(connectionString)
        result.success shouldBe true
        result.message shouldBe "Connection successful"
    }
}
