package dev.kviklet.kviklet.db

import dev.kviklet.kviklet.db.util.EventPayloadConverter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import tools.jackson.databind.json.JsonMapper

class EventPayloadConverterTest {

    // Same mapper setup as the application (see application.yaml)
    private fun withConverter(block: (EventPayloadConverter) -> Unit) {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration::class.java))
            .withPropertyValues("spring.jackson.use-jackson2-defaults=true")
            .run { context ->
                block(EventPayloadConverter().apply { objectMapper = context.getBean(JsonMapper::class.java) })
            }
    }

    @Test
    fun `reads stored rows with NULL cells`() = withConverter { converter ->
        val payload = converter.convertToEntityAttribute(
            """
            {"foobar":"EXECUTE","query":"SELECT 1 AS id, NULL AS note","results":[{"type":"QUERY",
            "columnCount":2,"rowCount":1,"columns":[{"label":"id","typeName":"int4","typeClass":"java.lang.Integer"},
            {"label":"note","typeName":"text","typeClass":"java.lang.String"}],
            "storedRows":[{"id":"1","note":null}],"storedRowCount":1}]}
            """.trimIndent(),
        ) as ExecutePayload

        val result = payload.results.single() as QueryResultLogPayload
        assertEquals(listOf(mapOf("id" to "1", "note" to null)), result.storedRows)
    }

    @Test
    fun `round-trips stored rows with NULL cells`() = withConverter { converter ->
        val payload = ExecutePayload(
            query = "SELECT NULL AS note",
            results = listOf(
                QueryResultLogPayload(columnCount = 1, rowCount = 1, storedRows = listOf(mapOf("note" to null))),
            ),
        )

        assertEquals(payload, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(payload)))
    }
}
