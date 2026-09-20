package dev.kviklet.kviklet.telemetry

import dev.kviklet.kviklet.service.dto.ConnectionType
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.RequestType
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

/**
 * The guarantee that telemetry can never carry a query, a result, or an error message: every event
 * property is an enum, a number, a boolean, or one of the few named strings listed here. Adding a
 * string field means adding its name to this allowlist, in the same diff a reviewer sees.
 */
class TelemetryEventTest {

    private val allowedStringProperties = setOf(
        "version",
        "gitCommit",
        "exceptionClass",
        "rootCauseClass",
        "httpMethod",
        "route",
        "oidcProvider",
    )

    private val events: List<KClass<out TelemetryEvent>> = TelemetryEvent::class.sealedSubclasses

    @Test
    fun `every event property is an enum, number, boolean, or an allowlisted string`() {
        events.shouldNotBeEmpty()
        for (event in events) {
            val parameters = event.primaryConstructor?.parameters ?: emptyList()
            for (parameter in parameters) {
                val type = parameter.type
                val allowed = type.isPrimitiveLike() ||
                    type.jvmErasure.isSubclassOf(Enum::class) ||
                    (type.jvmErasure == String::class && parameter.name in allowedStringProperties)
                allowed shouldBe true
            }
        }
    }

    @Test
    fun `event names are unique snake_case`() {
        val names = events.map { it.objectInstance?.name ?: it.eventName() }
        names.toSet().size shouldBe names.size
        names.forEach { it.matches(Regex("[a-z0-9_]+")) shouldBe true }
    }

    @Test
    fun `properties are the constructor parameters in snake_case with enums by name`() {
        val event = RequestCreated(
            requestType = RequestType.SingleExecution,
            connectionType = ConnectionType.DATASOURCE,
            datasourceType = DatasourceType.POSTGRESQL,
            requiredReviews = 2,
        )
        Telemetry.eventProperties(event) shouldBe mapOf(
            "request_type" to "SingleExecution",
            "connection_type" to "DATASOURCE",
            "datasource_type" to "POSTGRESQL",
            "required_reviews" to 2,
        )
    }

    @Test
    fun `an event without parameters has no properties of its own`() {
        Telemetry.eventProperties(RequestClosed) shouldBe emptyMap()
        RequestClosed.name shouldBe "request_closed"
    }

    @Test
    fun `the server error event carries class names and the route pattern only`() {
        val event = ServerError(
            exceptionClass = "java.lang.IllegalStateException",
            rootCauseClass = "java.sql.SQLException",
            httpMethod = "POST",
            route = "/execution-requests/{executionRequestId}/execute",
        )
        Telemetry.eventProperties(event).keys shouldContainExactlyInAnyOrder listOf(
            "exception_class",
            "root_cause_class",
            "http_method",
            "route",
        )
    }

    private fun KType.isPrimitiveLike(): Boolean =
        jvmErasure in setOf(Boolean::class, Int::class, Long::class, Double::class)

    private fun KClass<out TelemetryEvent>.eventName(): String {
        // Data classes: instantiate with placeholder values to read the name the constructor sets.
        val constructor = primaryConstructor!!
        val arguments = constructor.parameters.associateWith { parameter ->
            val erasure = parameter.type.jvmErasure
            when {
                parameter.type.isMarkedNullable -> null
                erasure == Boolean::class -> false
                erasure == Int::class -> 0
                erasure == Long::class -> 0L
                erasure == Double::class -> 0.0
                erasure == String::class -> ""
                erasure.isSubclassOf(Enum::class) -> erasure.java.enumConstants.first()
                else -> error("Unexpected parameter type ${parameter.type} on ${this.simpleName}")
            }
        }
        return constructor.callBy(arguments).name
    }

    private fun List<*>.shouldNotBeEmpty() {
        isEmpty() shouldBe false
    }
}
