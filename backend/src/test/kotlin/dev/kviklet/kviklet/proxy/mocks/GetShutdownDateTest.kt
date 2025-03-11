package dev.kviklet.kviklet.proxy.mocks

import dev.kviklet.kviklet.proxy.postgres.getShutdownDate
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

class GetShutdownDateTest {
    val startTimes : Array<LocalDateTime> = arrayOf(
        LocalDateTime.ofInstant(Instant.parse("1970-01-01T00:00:00Z"), ZoneId.systemDefault()),
        LocalDateTime.ofInstant(Instant.parse("1999-04-28T08:24:00Z"), ZoneId.systemDefault()),
        LocalDateTime.ofInstant(Instant.parse("2000-01-01T00:00:00Z"), ZoneId.systemDefault()),
        LocalDateTime.ofInstant(Instant.parse("2010-10-10T01:00:00Z"), ZoneId.systemDefault()),
        LocalDateTime.ofInstant(Instant.parse("2010-10-10T15:00:00Z"), ZoneId.systemDefault()),
        LocalDateTime.ofInstant(Instant.parse("2020-09-24T18:00:00Z"), ZoneId.systemDefault()),
    )
    val addedMinutes : Array<Long> = arrayOf(
        1,
        30,
        60,
        60,
        720,
        1440
    )
    val expected : Array<Date> = arrayOf(
        Date.from(Instant.parse("1970-01-01T00:01:00Z")),
        Date.from(Instant.parse("1999-04-28T08:54:00Z")),
        Date.from(Instant.parse("2000-01-01T01:00:00Z")),
        Date.from(Instant.parse("2010-10-10T02:00:00Z")),
        Date.from(Instant.parse("2010-10-11T03:00:00Z")),
        Date.from(Instant.parse("2020-09-25T18:00:00Z")),
    )
    @Test
    fun `getShutdownDate() should return startTime+X minutes`() {
        for(i in 0..4) {
            assert(getShutdownDate(startTimes[i], addedMinutes[i]) == expected[i])
        }
    }
}