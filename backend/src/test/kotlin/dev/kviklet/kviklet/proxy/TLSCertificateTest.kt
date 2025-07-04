package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.proxy.postgres.TLSCertificate
import dev.kviklet.kviklet.proxy.postgres.TlsCertEnvConfig
import dev.kviklet.kviklet.proxy.postgres.preprocessPEMObject
import dev.kviklet.kviklet.proxy.postgres.tlsCertificateFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

class TLSCertificateTest {
    private var testCert: String = """
-----BEGIN CERTIFICATE-----
MIIC9DCCAdygAwIBAgIGAZViVxKQMA0GCSqGSIb3DQEBCwUAMDsxDTALBgNVBAMM
BHRlc3QxCzAJBgNVBAYTAlVTMR0wGwYJKoZIhvcNAQkBFg50ZXN0QHRlc3QudGVz
dDAeFw0yNTAzMDQxODA3MDhaFw0yNjAzMDQxODA3MDhaMDsxDTALBgNVBAMMBHRl
c3QxCzAJBgNVBAYTAlVTMR0wGwYJKoZIhvcNAQkBFg50ZXN0QHRlc3QudGVzdDCC
ASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAM4jJdUHh/SsGPKydu9e1VFd
M7QYiiOHnY7sKdJGKDw2LsvjosVgY74SGPTUjBp7Qb2jl/Di2mCbcM9xBzHYj68P
ZnlhsLEJaR5kVb4zrkPp2IMQ79sfiuNag/kpRTmvdV8D2qIw3dfpPv+eYiBMWL75
MITZ5qtzfKW1FPLxH3M8ydfQ3Imst0aF6QSrBGYyKCkAZUM5VJ1c4Bh4tYMxu4UE
JARmB0Jbq8qGtzGO/bK4nvgBH+B/h2TnReBKYQo79iZke4JCJ+7rzcD3cHcO4X0/
2gSFixuUwCd9GaRZjsP0Zj/Ys9aFy/JMbj2UVXV9lN07BxC3YtizcV4fiPuF1TEC
AwEAATANBgkqhkiG9w0BAQsFAAOCAQEAH0zmELPuP1nwSwSbY6rmQsR6gMHpwGYK
Hdo51194bkZgwVDwV1VcU74EaCyKGXHR9Jko24+raxuMJQHyxNv/JtvNWuz/jmNV
DPSrtKOaQ3z9xfM+Lql8dIk1X/7xJgOES3IQQ32tSfXCCDaRs/nFL/HOsybqRftm
xerci1fwiDdA/fgtc3GDY11vY2Yj9+oIWQLxwxc8UsGRibSzIFZTPD9Pn6SiriiZ
FX9ETYm90JaGuiJU5ZP/zmQys8GRq3jbskmo6aCNA1quJ9Gb7lO+vXcknA4Y+13/
rMJ0Ao/kaOlhwXLPDla42taOY4qhcAm2CCzQqBd5lyaay33ZnWHPFw==
-----END CERTIFICATE-----
"""
    private var testKey: String = """
-----BEGIN RSA PRIVATE KEY-----
MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDOIyXVB4f0rBjy
snbvXtVRXTO0GIojh52O7CnSRig8Ni7L46LFYGO+Ehj01Iwae0G9o5fw4tpgm3DP
cQcx2I+vD2Z5YbCxCWkeZFW+M65D6diDEO/bH4rjWoP5KUU5r3VfA9qiMN3X6T7/
nmIgTFi++TCE2earc3yltRTy8R9zPMnX0NyJrLdGhekEqwRmMigpAGVDOVSdXOAY
eLWDMbuFBCQEZgdCW6vKhrcxjv2yuJ74AR/gf4dk50XgSmEKO/YmZHuCQifu683A
93B3DuF9P9oEhYsblMAnfRmkWY7D9GY/2LPWhcvyTG49lFV1fZTdOwcQt2LYs3Fe
H4j7hdUxAgMBAAECggEAHEuu0cMq4mcNNaNRuCHoXjbQ9hO4QpBHDGtWgkqnEzzM
x6gDm9xTVK/fRRw37xqkN4fRP3ukRkaQAameNzVm47zVcCv8uRB1oXpcWrN1ZFUh
JzyX8BgwVG0EWJtVqUlwbw50YHccvJqDz0rKZWyVcgF6q4HNrBM6NPTaX07B5mte
S5LYeoGmdn+bIPdIike2dgPLx2h8MiwH19r+if+qVESyo2cbuKjncCXFG7nslO3t
0dqybcleeKknqO5jZrlYsd+7xcdf1t2u05cSd9qqPq1Y0OR9VerJMTZKGUBbrKVs
DtfXLHy+cBo479xtGUMmSwRPRnRNfXdPi76DwxsyEQKBgQDOWwVLiTqK18upenFC
/HUkokg7Zts9JU/1jq8fFn3GRB8R7Movg7zFf3LRrlGmL3MpVaC9XJpmGX571oEP
AprozXZxYHGbqyzviyPimDuXqVZEBwWvIVf8WH69KTLGbRCLzunCMPKYYk0GDHLM
FgRpVL4p88hTl/Ft3YKNzgXrvwKBgQD/uq9zBsKCQUBKrckFx4aVLWnpkge1C63t
C7LeG7VQi7UuF1TWugy9vY3/po3PSvf6XUSaRwCrT4SN4KLWREdCEnU4h/Fl9F0n
tQhP3VA2Uy8mQsilYp1REdNajx2JP/q2dr3TljXkiqEDgiNUn1Lvtiw1QFixor3F
8rlzyig7DwKBgAiQfofECkn46tr92fWNxM7gbV8Jxc+j3M20PlBr/oxcB24XBc0z
CoKn53wMYBcloQH2K9WwIjhaloVNQc39rbA71s6d0hlD4XmPrM2aw95niM0J/ZJn
L9+pTJlNPG4/2I/05n7IyUjJy6iUm68cutIkUkArfgT6KWsF5oU8J8LBAoGBAPcR
ETMrg76+dfPwhLfNtkvoDVx5FnMm7omHdO87i+heodP+/JtcMrUaLtegvX9Zqc08
UOxQzuezspg0QH6Mht/h31iXlnTvKxUSxQ4L/tQNeA8aFKocZWsOssjaXindI0cn
32xNwpGkEb3G/IVkTIeF1J46JbaxSXG2eM/Srx2nAoGAC/rjYVJ+eDAKz867hTNu
9fw+vdX1Yl/ph59oY851QeKxL7KMTb0FDLbrvfhBCiLAn4Xk0NUwhgRhCP80UDv/
OirQUFE5QvnbBZ0BZeZGcz2lW62jzpvng5N6NwK2Nxm+qEdtLGJjjhfvy9iTX7p2
CJC2+PPy2deEXgREkGk/jpo=
-----END RSA PRIVATE KEY-----
"""

    @Test
    fun `tlsCertificateFactory must return null when the PROXY_TLS_CERTIFICATES_SOURCE is not set`() {
        assert(tlsCertificateFactory() == null)
    }

    @Test
    fun `tlsCertificateFactory must use env vars when the PROXY_TLS_CERTIFICATES_SOURCE is set to env`() {
        val env = TlsCertEnvConfig("env", null, null, testKey, testCert)
        assert(tlsCertificateFactory(env) != null)
    }

    @Test
    fun `tlsCertificateFactory must use file when the PROXY_TLS_CERTIFICATES_SOURCE is set to file`() {
        val tmpdir: String = Files.createTempDirectory("tmpDirPrefix").toFile().absolutePath
        val fileCert = File(tmpdir + "crt.pem")
        val fileKey = File(tmpdir + "key.pem")
        FileOutputStream(fileCert).use { fos ->
            fos.write(testCert.toByteArray())
        }
        FileOutputStream(fileKey).use { fos ->
            fos.write(testKey.toByteArray())
        }

        val env = TlsCertEnvConfig()
        env.PROXY_TLS_CERTIFICATE_SOURCE = "file"
        env.PROXY_TLS_CERTIFICATE_FILE = fileCert.absolutePath
        env.PROXY_TLS_CERTIFICATE_KEY_FILE = fileKey.absolutePath
        assert(tlsCertificateFactory(env) != null)
    }

    @Test
    fun `preprocessPEMObject must remove lines with ----- and new lines`() {
        val expectedKey = """MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDOIyXVB4f0rBjysnbvXtVRXTO0GIojh52O7CnSR
            |ig8Ni7L46LFYGO+Ehj01Iwae0G9o5fw4tpgm3DPcQcx2I+vD2Z5YbCxCWkeZFW+M65D6diDEO/bH4rjWoP5KUU5r3VfA9qiMN3X6T7/nm
            |IgTFi++TCE2earc3yltRTy8R9zPMnX0NyJrLdGhekEqwRmMigpAGVDOVSdXOAYeLWDMbuFBCQEZgdCW6vKhrcxjv2yuJ74AR/gf4dk50X
            |gSmEKO/YmZHuCQifu683A93B3DuF9P9oEhYsblMAnfRmkWY7D9GY/2LPWhcvyTG49lFV1fZTdOwcQt2LYs3FeH4j7hdUxAgMBAAECggEA
            |HEuu0cMq4mcNNaNRuCHoXjbQ9hO4QpBHDGtWgkqnEzzMx6gDm9xTVK/fRRw37xqkN4fRP3ukRkaQAameNzVm47zVcCv8uRB1oXpcWrN1Z
            |FUhJzyX8BgwVG0EWJtVqUlwbw50YHccvJqDz0rKZWyVcgF6q4HNrBM6NPTaX07B5mteS5LYeoGmdn+bIPdIike2dgPLx2h8MiwH19r+if
            |+qVESyo2cbuKjncCXFG7nslO3t0dqybcleeKknqO5jZrlYsd+7xcdf1t2u05cSd9qqPq1Y0OR9VerJMTZKGUBbrKVsDtfXLHy+cBo479x
            |tGUMmSwRPRnRNfXdPi76DwxsyEQKBgQDOWwVLiTqK18upenFC/HUkokg7Zts9JU/1jq8fFn3GRB8R7Movg7zFf3LRrlGmL3MpVaC9XJpm
            |GX571oEPAprozXZxYHGbqyzviyPimDuXqVZEBwWvIVf8WH69KTLGbRCLzunCMPKYYk0GDHLMFgRpVL4p88hTl/Ft3YKNzgXrvwKBgQD/u
            |q9zBsKCQUBKrckFx4aVLWnpkge1C63tC7LeG7VQi7UuF1TWugy9vY3/po3PSvf6XUSaRwCrT4SN4KLWREdCEnU4h/Fl9F0ntQhP3VA2Uy
            |8mQsilYp1REdNajx2JP/q2dr3TljXkiqEDgiNUn1Lvtiw1QFixor3F8rlzyig7DwKBgAiQfofECkn46tr92fWNxM7gbV8Jxc+j3M20PlB
            |r/oxcB24XBc0zCoKn53wMYBcloQH2K9WwIjhaloVNQc39rbA71s6d0hlD4XmPrM2aw95niM0J/ZJnL9+pTJlNPG4/2I/05n7IyUjJy6iU
            |m68cutIkUkArfgT6KWsF5oU8J8LBAoGBAPcRETMrg76+dfPwhLfNtkvoDVx5FnMm7omHdO87i+heodP+/JtcMrUaLtegvX9Zqc08UOxQz
            |uezspg0QH6Mht/h31iXlnTvKxUSxQ4L/tQNeA8aFKocZWsOssjaXindI0cn32xNwpGkEb3G/IVkTIeF1J46JbaxSXG2eM/Srx2nAoGAC/
            |rjYVJ+eDAKz867hTNu9fw+vdX1Yl/ph59oY851QeKxL7KMTb0FDLbrvfhBCiLAn4Xk0NUwhgRhCP80UDv/OirQUFE5QvnbBZ0BZeZGcz2
            |lW62jzpvng5N6NwK2Nxm+qEdtLGJjjhfvy9iTX7p2CJC2+PPy2deEXgREkGk/jpo=
        """.trimMargin()
        val expectedCert = """MIIC9DCCAdygAwIBAgIGAZViVxKQMA0GCSqGSIb3DQEBCwUAMDsxDTALBgNVBAMMBHRlc3QxCzAJBgNVBAYTAlVT
            |MR0wGwYJKoZIhvcNAQkBFg50ZXN0QHRlc3QudGVzdDAeFw0yNTAzMDQxODA3MDhaFw0yNjAzMDQxODA3MDhaMDsxDTALBgNVBAMMBHRlc
            |3QxCzAJBgNVBAYTAlVTMR0wGwYJKoZIhvcNAQkBFg50ZXN0QHRlc3QudGVzdDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAM
            |4jJdUHh/SsGPKydu9e1VFdM7QYiiOHnY7sKdJGKDw2LsvjosVgY74SGPTUjBp7Qb2jl/Di2mCbcM9xBzHYj68PZnlhsLEJaR5kVb4zrkP
            |p2IMQ79sfiuNag/kpRTmvdV8D2qIw3dfpPv+eYiBMWL75MITZ5qtzfKW1FPLxH3M8ydfQ3Imst0aF6QSrBGYyKCkAZUM5VJ1c4Bh4tYMx
            |u4UEJARmB0Jbq8qGtzGO/bK4nvgBH+B/h2TnReBKYQo79iZke4JCJ+7rzcD3cHcO4X0/2gSFixuUwCd9GaRZjsP0Zj/Ys9aFy/JMbj2UV
            |XV9lN07BxC3YtizcV4fiPuF1TECAwEAATANBgkqhkiG9w0BAQsFAAOCAQEAH0zmELPuP1nwSwSbY6rmQsR6gMHpwGYKHdo51194bkZgwV
            |DwV1VcU74EaCyKGXHR9Jko24+raxuMJQHyxNv/JtvNWuz/jmNVDPSrtKOaQ3z9xfM+Lql8dIk1X/7xJgOES3IQQ32tSfXCCDaRs/nFL/H
            |OsybqRftmxerci1fwiDdA/fgtc3GDY11vY2Yj9+oIWQLxwxc8UsGRibSzIFZTPD9Pn6SiriiZFX9ETYm90JaGuiJU5ZP/zmQys8GRq3jb
            |skmo6aCNA1quJ9Gb7lO+vXcknA4Y+13/rMJ0Ao/kaOlhwXLPDla42taOY4qhcAm2CCzQqBd5lyaay33ZnWHPFw==
        """.trimMargin()
        assert(preprocessPEMObject(testCert) == expectedCert)
        assert(preprocessPEMObject(testKey) == expectedKey)
    }

    @Test
    fun `Must fail if certificate is invalid`() {
        assertThrows<Exception> {
            TLSCertificate(testKey, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        }
    }

    @Test
    fun `Must fail if private key is invalid`() {
        assertThrows<Exception> {
            TLSCertificate("aaaaaaaaaaaaaaaaaaaaaaaaaaaaa", testCert)
        }
    }

    @Test
    fun `Must fail if public key and certificate don't match`() {
        val fakeKey = """MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAM76pTcYomMXF020VsxDvLm473NhBagLpcDXkMKQSaeQw
            |K8KjB92KC5kN3CNOJOhiuxThZsVpP3+vuHE3nQUIhF4GJbA34W1GscikN3qm4GKleAPU3MUSGtvoqt1PukryVYP0XAQDlJzszMYkyKpO9
            |Z/dVnom6ZHiBBxhtwz6lErAgMBAAECgYAVcOeOhpHD3A+A8C6RqG6zepHrjOBuIQ7BpFMNpK7MmfUr7NbJJ49QBverRCXZPUHL63cKsrp
            |NyYbykldNBQzmPJPtEqHEMa71GMRe0NQEcdZiFefn3VRAETNyd9LIqRNnRx+OEzwXX302tt2Gw4j/kABd/P3VY+ysy635laez4QJBAPXw
            |5jJHDOi7yAFOXeBZA4AlHyk7Ta99GzTJ5ykiYCZIdLHHfInxCX/tF2VaISeqlaVdAD8YkQ3E1Io1+AeYItsCQQDXccyXPJ8hZaQ55zmEv
            |eaJCGsQen9Rjwp9m7rcLzheh53jRIrHYXnWc9v4chJ48PPjyer5uUruWBQIZQR3FdPxAkAeTZdffIenqXOETa6ddPpMcMZ9IxR4WfbfMz
            |1rQRQNw4G1YfoDWRKtk339e/R32bnkjSf5nkJJKwZxHSM5dFJfAkEAz1uLI4DIVBeE0eo3lQhFa1y711dfVTtMSIrrdWLJaUoz73qX68B
            |oyLwoWl5IYzjeND6yNvpdITuKxG2dt5Q9sQJAaRM7kdGpe8vEwPcDtO6fnhsOF+ogvnHj7Bq5LM1YmDLZpgRSZ3UwBsiC0ilhRHD4JMSW
            |uHRFfwD7ZidjC5QHFg==
        """.trimMargin()
        // The below var is  cert which matches the key, left for testing/exploring if needed.
        // val fakecert =  """MIICNjCCAZ+gAwIBAgIBADANBgkqhkiG9w0BAQ0FADA4MQswCQYDVQQGEwJ1czELMAkGA1UECAwCVVMxDTALBgNVBAoMBE5vbmUxDTALBgNVBAMMBHRlc3QwHhcNMjUwMzEyMDUxNDI4WhcNMjYwMzEyMDUxNDI4WjA4MQswCQYDVQQGEwJ1czELMAkGA1UECAwCVVMxDTALBgNVBAoMBE5vbmUxDTALBgNVBAMMBHRlc3QwgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBAM76pTcYomMXF020VsxDvLm473NhBagLpcDXkMKQSaeQwK8KjB92KC5kN3CNOJOhiuxThZsVpP3+vuHE3nQUIhF4GJbA34W1GscikN3qm4GKleAPU3MUSGtvoqt1PukryVYP0XAQDlJzszMYkyKpO9Z/dVnom6ZHiBBxhtwz6lErAgMBAAGjUDBOMB0GA1UdDgQWBBTE51xlc1MK1mdpCwcvaQjGAsn6vDAfBgNVHSMEGDAWgBTE51xlc1MK1mdpCwcvaQjGAsn6vDAMBgNVHRMEBTADAQH/MA0GCSqGSIb3DQEBDQUAA4GBAKa+jvipD4nn0qNTz2OBoL9lJxc61GUn6Wpe3s2mPz1nl9Vce8cQeehDE4xosN1TveaDdtQ8TXe37EHx7egjwMpW9sv+3/QxT00QPY0RXAIU5RFvaksFpAybRfewkODKa+Ph/9+ZzGFBl1PFnnOXPh7UErKf7AJ0XDbtMPIoaOuC"""
        assertThrows<Exception> {
            TLSCertificate(fakeKey, testCert)
        }
    }
}
