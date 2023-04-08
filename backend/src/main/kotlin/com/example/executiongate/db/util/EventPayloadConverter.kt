package com.example.executiongate.db.util

import com.example.executiongate.db.Payload
import com.fasterxml.jackson.databind.ObjectMapper
import javax.persistence.AttributeConverter
import javax.persistence.Converter


@Converter(autoApply = true)
class EventPayloadConverter(
    private val objectMapper: ObjectMapper
) : AttributeConverter<Payload, String> {
    override fun convertToDatabaseColumn(payload: Payload): String {
        return objectMapper.writeValueAsString(payload)
    }

    override fun convertToEntityAttribute(payloadJson: String): Payload {
        return objectMapper.readValue(payloadJson, Payload::class.java)
    }
}

