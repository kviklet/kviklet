package com.example.executiongate.db.util

import com.example.executiongate.db.Payload
import com.fasterxml.jackson.databind.ObjectMapper
import liquibase.repackaged.org.apache.commons.text.StringEscapeUtils
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
        val unquoteJson = StringEscapeUtils.unescapeJson(payloadJson).removeSurrounding("\"")
        val res = objectMapper.readValue(unquoteJson, Payload::class.java)
        println(res)
        return res

    }
}

