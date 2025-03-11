package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.proxy.postgres.TlsCertEnvConfig
import dev.kviklet.kviklet.proxy.postgres.preprocessPEMObject
import dev.kviklet.kviklet.proxy.postgres.tlsCertificateFactory
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files


class TLSCertificateTest {
    private var testCert : String = """
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
    private var testKey : String ="""
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
    fun `tlsCertificateFactory must return use env vars when the PROXY_TLS_CERTIFICATES_SOURCE is set to env`() {
        val env = TlsCertEnvConfig()
        env.PROXY_TLS_CERTIFICATES_SOURCE = "env"
        env.PROXY_TLS_CERTIFICATE_CERT = testCert
        env.PROXY_TLS_CERTIFICATE_KEY = testKey
        assert(tlsCertificateFactory(env) != null)
    }
    @Test
    fun `tlsCertificateFactory must return use file when the PROXY_TLS_CERTIFICATES_SOURCE is set to file`() {
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
        env.PROXY_TLS_CERTIFICATES_SOURCE = "file"
        env.PROXY_TLS_CERTIFICATE_FILE = fileCert.absolutePath
        env.PROXY_TLS_CERTIFICATE_KEY_FILE = fileKey.absolutePath
        assert(tlsCertificateFactory(env) != null)
    }
    @Test
    fun `preprocessPEMObject must remove lines with ----- and new lines`() {
        val expectedKey = """MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDOIyXVB4f0rBjysnbvXtVRXTO0GIojh52O7CnSRig8Ni7L46LFYGO+Ehj01Iwae0G9o5fw4tpgm3DPcQcx2I+vD2Z5YbCxCWkeZFW+M65D6diDEO/bH4rjWoP5KUU5r3VfA9qiMN3X6T7/nmIgTFi++TCE2earc3yltRTy8R9zPMnX0NyJrLdGhekEqwRmMigpAGVDOVSdXOAYeLWDMbuFBCQEZgdCW6vKhrcxjv2yuJ74AR/gf4dk50XgSmEKO/YmZHuCQifu683A93B3DuF9P9oEhYsblMAnfRmkWY7D9GY/2LPWhcvyTG49lFV1fZTdOwcQt2LYs3FeH4j7hdUxAgMBAAECggEAHEuu0cMq4mcNNaNRuCHoXjbQ9hO4QpBHDGtWgkqnEzzMx6gDm9xTVK/fRRw37xqkN4fRP3ukRkaQAameNzVm47zVcCv8uRB1oXpcWrN1ZFUhJzyX8BgwVG0EWJtVqUlwbw50YHccvJqDz0rKZWyVcgF6q4HNrBM6NPTaX07B5mteS5LYeoGmdn+bIPdIike2dgPLx2h8MiwH19r+if+qVESyo2cbuKjncCXFG7nslO3t0dqybcleeKknqO5jZrlYsd+7xcdf1t2u05cSd9qqPq1Y0OR9VerJMTZKGUBbrKVsDtfXLHy+cBo479xtGUMmSwRPRnRNfXdPi76DwxsyEQKBgQDOWwVLiTqK18upenFC/HUkokg7Zts9JU/1jq8fFn3GRB8R7Movg7zFf3LRrlGmL3MpVaC9XJpmGX571oEPAprozXZxYHGbqyzviyPimDuXqVZEBwWvIVf8WH69KTLGbRCLzunCMPKYYk0GDHLMFgRpVL4p88hTl/Ft3YKNzgXrvwKBgQD/uq9zBsKCQUBKrckFx4aVLWnpkge1C63tC7LeG7VQi7UuF1TWugy9vY3/po3PSvf6XUSaRwCrT4SN4KLWREdCEnU4h/Fl9F0ntQhP3VA2Uy8mQsilYp1REdNajx2JP/q2dr3TljXkiqEDgiNUn1Lvtiw1QFixor3F8rlzyig7DwKBgAiQfofECkn46tr92fWNxM7gbV8Jxc+j3M20PlBr/oxcB24XBc0zCoKn53wMYBcloQH2K9WwIjhaloVNQc39rbA71s6d0hlD4XmPrM2aw95niM0J/ZJnL9+pTJlNPG4/2I/05n7IyUjJy6iUm68cutIkUkArfgT6KWsF5oU8J8LBAoGBAPcRETMrg76+dfPwhLfNtkvoDVx5FnMm7omHdO87i+heodP+/JtcMrUaLtegvX9Zqc08UOxQzuezspg0QH6Mht/h31iXlnTvKxUSxQ4L/tQNeA8aFKocZWsOssjaXindI0cn32xNwpGkEb3G/IVkTIeF1J46JbaxSXG2eM/Srx2nAoGAC/rjYVJ+eDAKz867hTNu9fw+vdX1Yl/ph59oY851QeKxL7KMTb0FDLbrvfhBCiLAn4Xk0NUwhgRhCP80UDv/OirQUFE5QvnbBZ0BZeZGcz2lW62jzpvng5N6NwK2Nxm+qEdtLGJjjhfvy9iTX7p2CJC2+PPy2deEXgREkGk/jpo="""
        val expectedCert = """MIIC9DCCAdygAwIBAgIGAZViVxKQMA0GCSqGSIb3DQEBCwUAMDsxDTALBgNVBAMMBHRlc3QxCzAJBgNVBAYTAlVTMR0wGwYJKoZIhvcNAQkBFg50ZXN0QHRlc3QudGVzdDAeFw0yNTAzMDQxODA3MDhaFw0yNjAzMDQxODA3MDhaMDsxDTALBgNVBAMMBHRlc3QxCzAJBgNVBAYTAlVTMR0wGwYJKoZIhvcNAQkBFg50ZXN0QHRlc3QudGVzdDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAM4jJdUHh/SsGPKydu9e1VFdM7QYiiOHnY7sKdJGKDw2LsvjosVgY74SGPTUjBp7Qb2jl/Di2mCbcM9xBzHYj68PZnlhsLEJaR5kVb4zrkPp2IMQ79sfiuNag/kpRTmvdV8D2qIw3dfpPv+eYiBMWL75MITZ5qtzfKW1FPLxH3M8ydfQ3Imst0aF6QSrBGYyKCkAZUM5VJ1c4Bh4tYMxu4UEJARmB0Jbq8qGtzGO/bK4nvgBH+B/h2TnReBKYQo79iZke4JCJ+7rzcD3cHcO4X0/2gSFixuUwCd9GaRZjsP0Zj/Ys9aFy/JMbj2UVXV9lN07BxC3YtizcV4fiPuF1TECAwEAATANBgkqhkiG9w0BAQsFAAOCAQEAH0zmELPuP1nwSwSbY6rmQsR6gMHpwGYKHdo51194bkZgwVDwV1VcU74EaCyKGXHR9Jko24+raxuMJQHyxNv/JtvNWuz/jmNVDPSrtKOaQ3z9xfM+Lql8dIk1X/7xJgOES3IQQ32tSfXCCDaRs/nFL/HOsybqRftmxerci1fwiDdA/fgtc3GDY11vY2Yj9+oIWQLxwxc8UsGRibSzIFZTPD9Pn6SiriiZFX9ETYm90JaGuiJU5ZP/zmQys8GRq3jbskmo6aCNA1quJ9Gb7lO+vXcknA4Y+13/rMJ0Ao/kaOlhwXLPDla42taOY4qhcAm2CCzQqBd5lyaay33ZnWHPFw=="""
        assert(preprocessPEMObject(testCert) == expectedCert)
        assert(preprocessPEMObject(testKey) == expectedKey)
    }
}