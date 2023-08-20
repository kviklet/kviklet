package com.example.executiongate.db.util

import com.example.executiongate.db.Payload
import com.example.executiongate.db.ReviewConfig
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.apache.commons.text.StringEscapeUtils


@Component
abstract class PayloadConverter<T> : AttributeConverter<T, String> {
    @Autowired
    lateinit var objectMapper: ObjectMapper

    override fun convertToDatabaseColumn(payload: T): String {
        return objectMapper.writeValueAsString(payload)
    }

    override fun convertToEntityAttribute(payloadJson: String): T {
        val unquoteJson = if (payloadJson.startsWith("\""))
            StringEscapeUtils.unescapeJson(payloadJson).removeSurrounding("\"")
        else
            payloadJson
        return objectMapper.readValue(unquoteJson, clazz())
    }

    abstract fun clazz(): Class<T>
}

@Converter(autoApply = true)
class EventPayloadConverter : PayloadConverter<Payload>() {
    override fun clazz() = Payload::class.java
}

@Converter(autoApply = true)
class ReviewConfigConverter : PayloadConverter<ReviewConfig>() {
    override fun clazz() = ReviewConfig::class.java
}
