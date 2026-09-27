package dev.kviklet.kviklet

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.JacksonModule
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.module.SimpleModule
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * All `LocalDateTime` values in Kviklet represent UTC instants (see `utcTimeNow()`).
 * This module makes that explicit on the wire by appending `Z`, so the frontend
 * `new Date(...)` parses them as UTC instead of misinterpreting them as local time.
 */
@Configuration
class JacksonConfig {
    @Bean
    fun localDateTimeUtcModule(): JacksonModule = SimpleModule().apply {
        addSerializer(LocalDateTime::class.java, UtcLocalDateTimeSerializer())
    }
}

private class UtcLocalDateTimeSerializer : ValueSerializer<LocalDateTime>() {
    override fun serialize(value: LocalDateTime, gen: JsonGenerator, ctxt: SerializationContext) {
        gen.writeString(value.toInstant(ZoneOffset.UTC).toString())
    }
}
