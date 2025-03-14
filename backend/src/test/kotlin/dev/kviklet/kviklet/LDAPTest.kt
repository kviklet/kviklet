package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.db.UserAdapter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.MountableFile
import java.nio.file.Files
import java.time.Duration
import java.util.*
import javax.naming.Context
import javax.naming.directory.InitialDirContext

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class LDAPTest {

    @Autowired
    private lateinit var userAdapter: UserAdapter

    companion object {
        @Container
        val ldapContainer = GenericContainer("osixia/openldap:1.5.0")
            .withExposedPorts(389, 636)
            .withEnv(
                mapOf(
                    "LDAP_ORGANISATION" to "Test Org",
                    "LDAP_DOMAIN" to "example.org",
                    "LDAP_ADMIN_PASSWORD" to "admin",
                    "LDAP_CONFIG_PASSWORD" to "config",
                    "LDAP_LOG_LEVEL" to "256", // Add this for more verbose logging
                ),
            )
            .waitingFor(Wait.forLogMessage(".*Start OpenLDAP.*", 1))
            .withStartupTimeout(Duration.ofMinutes(2))

        @BeforeAll
        @JvmStatic
        fun setupLdap() {
            // Wait for LDAP to be fully ready
            Thread.sleep(2000)

            // First create the people OU
            val ouLdifContent = """
        dn: ou=people,dc=example,dc=org
        objectClass: organizationalUnit
        ou: people
            """.trimIndent()

            val ouTempFile = Files.createTempFile("ou-data", ".ldif")
            Files.write(ouTempFile, ouLdifContent.toByteArray())

            // Copy the LDIF file to the container
            ldapContainer.copyFileToContainer(
                MountableFile.forHostPath(ouTempFile),
                "/tmp/ou-data.ldif",
            )

            val createOuResult = ldapContainer.execInContainer(
                "ldapadd",
                "-x",
                "-D",
                "cn=admin,dc=example,dc=org",
                "-w",
                "admin",
                "-f",
                "/tmp/ou-data.ldif",
            )

            // Then create the user with slappasswd to generate the password hash
            val hashResult = ldapContainer.execInContainer(
                "slappasswd",
                "-s",
                "password",
            )

            val passwordHash = hashResult.stdout.trim()

            // Create user LDIF with the hashed password
            val userLdifContent = """
        dn: uid=john.doe,ou=people,dc=example,dc=org
        objectClass: inetOrgPerson
        cn: John Doe
        sn: Doe
        uid: john.doe
        mail: john.doe@example.org
        userPassword: $passwordHash
            """.trimIndent()

            val userTempFile = Files.createTempFile("user-data", ".ldif")
            Files.write(userTempFile, userLdifContent.toByteArray())

            // Copy the LDIF file to the container
            ldapContainer.copyFileToContainer(
                MountableFile.forHostPath(userTempFile),
                "/tmp/user-data.ldif",
            )

            val createUserResult = ldapContainer.execInContainer(
                "ldapadd",
                "-x",
                "-D",
                "cn=admin,dc=example,dc=org",
                "-w",
                "admin",
                "-f",
                "/tmp/user-data.ldif",
            )

            // Cleanup
            Files.delete(ouTempFile)
            Files.delete(userTempFile)
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureLdapProperties(registry: DynamicPropertyRegistry) {
            registry.add("ldap.url") { "ldap://${ldapContainer.host}:${ldapContainer.getMappedPort(389)}" }
            registry.add("ldap.base") { "dc=example,dc=org" }
            registry.add("ldap.principal") { "cn=admin,dc=example,dc=org" }
            registry.add("ldap.password") { "admin" }
            registry.add("ldap.enabled") { "true" }
            registry.add("ldap.userOu") { "people" }
            registry.add("ldap.uniqueIdentifierAttribute") { "uid" }
            registry.add("ldap.emailAttribute") { "mail" }
            registry.add("ldap.fullNameAttribute") { "cn" }
        }
    }

    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var authenticationManager: AuthenticationManager

    @Test
    fun `authenticate with LDAP credentials`() {
        mockMvc.perform(
            post("/login")
                .content(
                    """
                {
                    "email": "john.doe",
                    "password": "password"
                }
                    """.trimIndent(),
                )
                .contentType("application/json"),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `verify direct LDAP authentication`() {
        val env = Hashtable<String, Any>()
        env[Context.INITIAL_CONTEXT_FACTORY] = "com.sun.jndi.ldap.LdapCtxFactory"
        env[Context.PROVIDER_URL] = "ldap://${ldapContainer.host}:${ldapContainer.getMappedPort(389)}"

        // Admin bind
        env[Context.SECURITY_AUTHENTICATION] = "simple"
        env[Context.SECURITY_PRINCIPAL] = "cn=admin,dc=example,dc=org"
        env[Context.SECURITY_CREDENTIALS] = "admin"
        val adminCtx = InitialDirContext(env)
        adminCtx.close()

        // User bind
        env[Context.SECURITY_AUTHENTICATION] = "simple"
        env[Context.SECURITY_PRINCIPAL] = "uid=john.doe,ou=people,dc=example,dc=org"
        env[Context.SECURITY_CREDENTIALS] = "password"
        // This will throw an exception if the bind fails
        val userCtx = InitialDirContext(env)
        userCtx.close()
    }

    @Test
    fun `list LDAP entries`() {
        val env = Hashtable<String, Any>()
        env[Context.INITIAL_CONTEXT_FACTORY] = "com.sun.jndi.ldap.LdapCtxFactory"
        env[Context.PROVIDER_URL] = "ldap://${ldapContainer.host}:${ldapContainer.getMappedPort(389)}"
        env[Context.SECURITY_AUTHENTICATION] = "simple"
        env[Context.SECURITY_PRINCIPAL] = "cn=admin,dc=example,dc=org"
        env[Context.SECURITY_CREDENTIALS] = "admin"

        val ctx = InitialDirContext(env)

        try {
            val results = ctx.search("dc=example,dc=org", "(objectClass=*)", null)
            while (results.hasMore()) {
                val result = results.next()
                println("Found: ${result.nameInNamespace}")
                result.attributes?.let { attrs ->
                    val all = attrs.all
                    while (all.hasMore()) {
                        val attr = all.next()
                        print("  ${attr.id}: ")
                        val values = attr.all
                        while (values.hasMore()) {
                            print("${values.next()} ")
                        }
                        println()
                    }
                }
            }

            val userResults = ctx.search(
                "ou=people,dc=example,dc=org",
                "(uid=john.doe)",
                null,
            )

            assert(userResults.hasMore())
        } finally {
            ctx.close()
        }
    }

    @Test
    fun `verify existing user can login via ldap and user gets updated with new auth method`() {
        // Create a user with the same email as the LDAP user but without LDAP identifier
        val existingUser = userAdapter.createUser(
            User(
                email = "john.doe@example.org",
                fullName = "Different Name", // Use a different name to verify update
                // Set a password to verify it's deleted after LDAP login
                password = "local-password",
            ),
        )

        val userCountBeforeLDAP = userAdapter.listUsers().size

        // Perform LDAP login
        mockMvc.perform(
            post("/login")
                .content(
                    """
                {
                    "email": "john.doe",
                    "password": "password"
                }
                    """.trimIndent(),
                )
                .contentType("application/json"),
        )
            .andExpect(status().isOk)

        // Assert that no new user was created
        assertThat(userAdapter.listUsers().size).isEqualTo(userCountBeforeLDAP)

        // Verify that the user's information was properly updated with LDAP details
        val updatedUser = userAdapter.findByEmail("john.doe@example.org")
        assertThat(updatedUser).isNotNull
        assertThat(updatedUser?.getId()).isEqualTo(existingUser.getId())

        // Assert LDAP identifier was set
        assertThat(updatedUser?.ldapIdentifier).isEqualTo("john.doe")

        // Assert the name was updated from LDAP
        assertThat(updatedUser?.fullName).isEqualTo("John Doe")

        // Check that the password was removed
        assertThat(updatedUser?.password).isNull()
    }
}
