package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import dev.kviklet.kviklet.proxy.helpers.*
import dev.kviklet.kviklet.proxy.postgres.TLSCertificate
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.beans.factory.annotation.Autowired
import java.sql.Connection
import java.sql.DriverManager
import java.util.*

@SpringBootTest
@ActiveProfiles("test")
class PostgresProxyTLSTest {
    @Autowired
    lateinit var executionRequestAdapter: ExecutionRequestAdapter
    @Autowired
    lateinit var eventAdapter: EventAdapter
    private lateinit var directConnection : Connection
    private lateinit var proxy : ProxyInstance
    private lateinit var postgresContainer : PostgreSQLContainer<Nothing>
    @BeforeEach
    fun setup() {
        postgresContainer = PostgreSQLContainer<Nothing>("postgres:13").apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
        }
        postgresContainer.start()
        while(!postgresContainer.isRunning) { Thread.sleep(1000) }
        this.directConnection = directConnectionFactory(postgresContainer)
        val cert = TLSCertificate(
            """MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDOIyXVB4f0rBjysnbvXtVRXTO0GIojh52O7CnSRig8Ni7L46LFYGO+Ehj01Iwae0G9o5fw4tpgm3DPcQcx2I+vD2Z5YbCxCWkeZFW+M65D6diDEO/bH4rjWoP5KUU5r3VfA9qiMN3X6T7/nmIgTFi++TCE2earc3yltRTy8R9zPMnX0NyJrLdGhekEqwRmMigpAGVDOVSdXOAYeLWDMbuFBCQEZgdCW6vKhrcxjv2yuJ74AR/gf4dk50XgSmEKO/YmZHuCQifu683A93B3DuF9P9oEhYsblMAnfRmkWY7D9GY/2LPWhcvyTG49lFV1fZTdOwcQt2LYs3FeH4j7hdUxAgMBAAECggEAHEuu0cMq4mcNNaNRuCHoXjbQ9hO4QpBHDGtWgkqnEzzMx6gDm9xTVK/fRRw37xqkN4fRP3ukRkaQAameNzVm47zVcCv8uRB1oXpcWrN1ZFUhJzyX8BgwVG0EWJtVqUlwbw50YHccvJqDz0rKZWyVcgF6q4HNrBM6NPTaX07B5mteS5LYeoGmdn+bIPdIike2dgPLx2h8MiwH19r+if+qVESyo2cbuKjncCXFG7nslO3t0dqybcleeKknqO5jZrlYsd+7xcdf1t2u05cSd9qqPq1Y0OR9VerJMTZKGUBbrKVsDtfXLHy+cBo479xtGUMmSwRPRnRNfXdPi76DwxsyEQKBgQDOWwVLiTqK18upenFC/HUkokg7Zts9JU/1jq8fFn3GRB8R7Movg7zFf3LRrlGmL3MpVaC9XJpmGX571oEPAprozXZxYHGbqyzviyPimDuXqVZEBwWvIVf8WH69KTLGbRCLzunCMPKYYk0GDHLMFgRpVL4p88hTl/Ft3YKNzgXrvwKBgQD/uq9zBsKCQUBKrckFx4aVLWnpkge1C63tC7LeG7VQi7UuF1TWugy9vY3/po3PSvf6XUSaRwCrT4SN4KLWREdCEnU4h/Fl9F0ntQhP3VA2Uy8mQsilYp1REdNajx2JP/q2dr3TljXkiqEDgiNUn1Lvtiw1QFixor3F8rlzyig7DwKBgAiQfofECkn46tr92fWNxM7gbV8Jxc+j3M20PlBr/oxcB24XBc0zCoKn53wMYBcloQH2K9WwIjhaloVNQc39rbA71s6d0hlD4XmPrM2aw95niM0J/ZJnL9+pTJlNPG4/2I/05n7IyUjJy6iUm68cutIkUkArfgT6KWsF5oU8J8LBAoGBAPcRETMrg76+dfPwhLfNtkvoDVx5FnMm7omHdO87i+heodP+/JtcMrUaLtegvX9Zqc08UOxQzuezspg0QH6Mht/h31iXlnTvKxUSxQ4L/tQNeA8aFKocZWsOssjaXindI0cn32xNwpGkEb3G/IVkTIeF1J46JbaxSXG2eM/Srx2nAoGAC/rjYVJ+eDAKz867hTNu9fw+vdX1Yl/ph59oY851QeKxL7KMTb0FDLbrvfhBCiLAn4Xk0NUwhgRhCP80UDv/OirQUFE5QvnbBZ0BZeZGcz2lW62jzpvng5N6NwK2Nxm+qEdtLGJjjhfvy9iTX7p2CJC2+PPy2deEXgREkGk/jpo=""",
            """MIIC9DCCAdygAwIBAgIGAZViVxKQMA0GCSqGSIb3DQEBCwUAMDsxDTALBgNVBAMMBHRlc3QxCzAJBgNVBAYTAlVTMR0wGwYJKoZIhvcNAQkBFg50ZXN0QHRlc3QudGVzdDAeFw0yNTAzMDQxODA3MDhaFw0yNjAzMDQxODA3MDhaMDsxDTALBgNVBAMMBHRlc3QxCzAJBgNVBAYTAlVTMR0wGwYJKoZIhvcNAQkBFg50ZXN0QHRlc3QudGVzdDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAM4jJdUHh/SsGPKydu9e1VFdM7QYiiOHnY7sKdJGKDw2LsvjosVgY74SGPTUjBp7Qb2jl/Di2mCbcM9xBzHYj68PZnlhsLEJaR5kVb4zrkPp2IMQ79sfiuNag/kpRTmvdV8D2qIw3dfpPv+eYiBMWL75MITZ5qtzfKW1FPLxH3M8ydfQ3Imst0aF6QSrBGYyKCkAZUM5VJ1c4Bh4tYMxu4UEJARmB0Jbq8qGtzGO/bK4nvgBH+B/h2TnReBKYQo79iZke4JCJ+7rzcD3cHcO4X0/2gSFixuUwCd9GaRZjsP0Zj/Ys9aFy/JMbj2UVXV9lN07BxC3YtizcV4fiPuF1TECAwEAATANBgkqhkiG9w0BAQsFAAOCAQEAH0zmELPuP1nwSwSbY6rmQsR6gMHpwGYKHdo51194bkZgwVDwV1VcU74EaCyKGXHR9Jko24+raxuMJQHyxNv/JtvNWuz/jmNVDPSrtKOaQ3z9xfM+Lql8dIk1X/7xJgOES3IQQ32tSfXCCDaRs/nFL/HOsybqRftmxerci1fwiDdA/fgtc3GDY11vY2Yj9+oIWQLxwxc8UsGRibSzIFZTPD9Pn6SiriiZFX9ETYm90JaGuiJU5ZP/zmQys8GRq3jbskmo6aCNA1quJ9Gb7lO+vXcknA4Y+13/rMJ0Ao/kaOlhwXLPDla42taOY4qhcAm2CCzQqBd5lyaay33ZnWHPFw=="""
        )
        this.proxy = proxyServerFactory(postgresContainer, executionRequestAdapter, eventAdapter, cert)
    }
    @AfterEach
    fun tearDown() {
        this.proxy.proxy.shutdownServer()
        this.proxy.connection.close()
        this.postgresContainer.stop()
    }
    @Test
    fun `Postgres proxy must support TLS`() {
        assertDoesNotThrow {
            val proxyProps = Properties()
            proxyProps.setProperty("user", "proxyUser")
            proxyProps.setProperty("password", "proxyPassword")
            proxyProps.setProperty("ssl", "true")
            proxyProps.setProperty("sslmode", "require")
            // NonValidatingFactory make sure the test doesn't check if the certificate is valid.
            val ignoreTLSIssuer = "&sslfactory=org.postgresql.ssl.NonValidatingFactory"

            val conn = DriverManager.getConnection(this.proxy.connectionString + ignoreTLSIssuer, proxyProps)
            val stmt = conn.createStatement()
            val result = stmt.executeQuery("SELECT 1")
            while (result.next()) {
                val isOne = result.getInt("?column?")
                assertTrue(isOne == 1)
            }

            this.proxy.eventService.assertQueryIsAudited("SELECT 1")
        }
    }

}

