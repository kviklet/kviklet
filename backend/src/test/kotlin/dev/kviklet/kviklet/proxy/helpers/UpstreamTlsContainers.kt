// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.helpers

import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.io.File

// Database testcontainers that serve TLS with the certificates in src/test/resources/upstream-tls (a CA,
// and a server cert whose SANs cover localhost/127.0.0.1 so verify-full passes against the mapped port).
//
// The key file is copied in root-owned, but the servers run as their own user and refuse (Postgres) or
// cannot read (MySQL/MariaDB) a key they don't own -- so the entrypoint is wrapped to chown/chmod the key
// as root before handing over to the image's real entrypoint, which then drops privileges as usual.

// Host filesystem path of one of the upstream-tls resources, for client-side options that take a file path
// (pgjdbc's sslrootcert, MariaDB Connector/J's serverSslCert).
fun upstreamTlsResourcePath(name: String): String =
    File(object {}.javaClass.getResource("/upstream-tls/$name")!!.toURI()).absolutePath

private fun <T : org.testcontainers.containers.GenericContainer<*>> T.withUpstreamTlsFiles(): T {
    withCopyFileToContainer(MountableFile.forClasspathResource("upstream-tls/server.crt"), "/tls/server.crt")
    withCopyFileToContainer(MountableFile.forClasspathResource("upstream-tls/server.key"), "/tls/server.key")
    return this
}

private fun fixKeyOwnershipAnd(user: String, realEntrypoint: String): Array<String> = arrayOf(
    "sh",
    "-c",
    "chown $user:$user /tls/server.key && chmod 600 /tls/server.key && exec $realEntrypoint",
)

fun tlsPostgresContainer(): PostgreSQLContainer<Nothing> = PostgreSQLContainer<Nothing>("postgres:13").apply {
    withDatabaseName("testdb")
    withUsername("test")
    withPassword("test")
    withUpstreamTlsFiles()
    withCreateContainerCmdModifier { cmd ->
        cmd.withEntrypoint(
            *fixKeyOwnershipAnd(
                "postgres",
                "docker-entrypoint.sh postgres -c ssl=on" +
                    " -c ssl_cert_file=/tls/server.crt -c ssl_key_file=/tls/server.key",
            ),
        )
    }
}

fun tlsMariaDbContainer(): MariaDBContainer<*> = MariaDBContainer(DockerImageName.parse("mariadb:11.4")).apply {
    withDatabaseName("testdb")
    withUsername("test")
    withPassword("test")
    withUpstreamTlsFiles()
    withCreateContainerCmdModifier { cmd ->
        cmd.withEntrypoint(
            *fixKeyOwnershipAnd(
                "mysql",
                "docker-entrypoint.sh mariadbd --ssl-cert=/tls/server.crt --ssl-key=/tls/server.key",
            ),
        )
    }
}

fun tlsMySqlContainer(): MySQLContainer<*> = MySQLContainer(DockerImageName.parse("mysql:8.2")).apply {
    withDatabaseName("testdb")
    withUsername("test")
    withPassword("test")
    withUpstreamTlsFiles()
    withCreateContainerCmdModifier { cmd ->
        cmd.withEntrypoint(
            *fixKeyOwnershipAnd(
                "mysql",
                "docker-entrypoint.sh mysqld --ssl-cert=/tls/server.crt --ssl-key=/tls/server.key",
            ),
        )
    }
}

// MariaDB 11.4 auto-generates a certificate and offers TLS out of the box, so a server that genuinely
// cannot do TLS needs it disabled explicitly. Used to assert the fail-closed path: a connection whose
// options request TLS must be refused, not silently downgraded.
fun noTlsMariaDbContainer(): MariaDBContainer<*> = MariaDBContainer(DockerImageName.parse("mariadb:11.4")).apply {
    withDatabaseName("testdb")
    withUsername("test")
    withPassword("test")
    withCommand("--skip-ssl")
}
