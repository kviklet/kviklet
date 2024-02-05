package dev.kviklet.kviklet

import dev.kviklet.kviklet.service.LicenseService
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.security.Security
import java.util.*

@SpringBootTest
@ActiveProfiles("test")
class LicenseServiceTest {
    @Autowired private lateinit var licenseService: LicenseService

    @Test
    fun `test license service`() {
        val algorithms = TreeSet<String>()
        for (provider in Security.getProviders()) for (service in provider.getServices()) if (service.getType().equals(
                "Signature",
            )
        ) {
            algorithms.add(service.getAlgorithm())
        }
        val pubkey = licenseService.loadPublicKey()
        val valid = licenseService.verifyLicense(
            """{"max_users":20,"expiry_date":"2024-01-01"}""",
            "VWT9Bo5HuN4Y+bkT6FK7EtrCXavJhyI+Tk62FnfbzkJaqyOOAh3qnyFMGz12enLQjmMLIwaqyoogjkOeJXezyfEcJRFDT" +
                "adk3IdXYMZWz8MPMHVHbGmvA18+5vJRZ1cXnNClEkHg3aanbNgtRNlFzp2fen5UVUCAwn/oI0MlQqxwR6qyi2ZST5v0s1P" +
                "hhI20Byo7gjCDMjOtpWSsn0rTLeDsADS7WR5/26+VhHvt3s1AQlJ6n0gOGOWBruluZkO/NOqIqF2y9NSufJjuF8WkbJt9J" +
                "xhmPApScMa+nCuqDgDRXIAQa0I65xCwMWixDMz9S/vLb5XlqlJZhGJnC1KKMUzOB87wFtVFmrkhvxSaOH/ejX8rrTDMKyG" +
                "9ztlKZf+qiff0nZ7GWkHltmc+YLQPoBJt39aDrpW75/2GOQKWLHjwcaB45EPzEv7Tsz4gG5eqWG4kMG3UigJKrEQwoUp10" +
                "LEMH0YIOFO9WAcjNQB7RpND91VvDESsBKUbwtS/J3rG",
            pubkey,
        )
        valid shouldBe true
    }
}
