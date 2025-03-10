package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.proxy.postgres.isSessionExpired
import dev.kviklet.kviklet.service.dto.utcTimeNow
import org.junit.jupiter.api.Assertions.assertFalse
import org.springframework.test.context.ActiveProfiles
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test


@ActiveProfiles("test")
class IsSessionExpiredTest {
    @Test
    fun `Must return true if maximumDuration is less 0`() {
        val result = isSessionExpired(utcTimeNow(), -1)
        assertTrue(result)
    }
    @Test
    fun `Must return true if maximumDuration is 0`() {
        val result = isSessionExpired(utcTimeNow(), 0)
        assertTrue(result)
    }
    @Test
    fun `Must return true if the session was started X+1 minutes ago and maximum duration is X`() {
        for(i: Long in 10..100000.toLong()) {
            val result = isSessionExpired(utcTimeNow().minusMinutes(i+1), i)
            assertTrue(result)
        }
    }
    @Test
    fun `Must return true if the session was started X-1 minutes ago and maximum duration is X`() {
        for(i: Long in 10..100000.toLong()) {
            val result = isSessionExpired(utcTimeNow().minusMinutes(i-1), i)
            assertFalse(result)
        }
    }
    @Test
    fun `Must return true if the session was started now and maximum duration is X`() {
        for(i: Long in 10..100000.toLong()) {
            val result = isSessionExpired(utcTimeNow(), i)
            assertFalse(result)
        }
    }
    @Test
    fun `Must return true if the session was started now-Y, with Y less X-1 and maximum duration is X`() {
        for(i: Long in 10..100000.toLong()) {
            for(j: Int in 1..100) { // Run 100 iterations of generating random y between 10 and current I
                val y : Long = (1..<i).random()
                val result = isSessionExpired(utcTimeNow().minusMinutes(y), i)
                try { assertFalse(result) }
                catch (e : AssertionError) { println("Failed with i: ${i}, y: ${y}"); throw e}
            }
        }
    }
}