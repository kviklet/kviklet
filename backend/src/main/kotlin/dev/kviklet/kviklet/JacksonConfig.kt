package dev.kviklet.kviklet

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
    fun localDateTimeUtcModule(): Module = SimpleModule().apply {
        addSerializer(LocalDateTime::class.java, UtcLocalDateTimeSerializer())
    }
}

private class UtcLocalDateTimeSerializer : JsonSerializer<LocalDateTime>() {
    override fun serialize(value: LocalDateTime, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.toInstant(ZoneOffset.UTC).toString())
    }
}
