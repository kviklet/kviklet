package dev.kviklet.kviklet.mcp

import dev.kviklet.kviklet.service.dto.Policy
import dev.kviklet.kviklet.service.dto.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The flags must map to the same policies as the role page in the UI (`frontend/src/hooks/roles.test.ts`). */
class RolePermissionsTest {

    // The payload the UI sends for a role that reads roles and has every flag on the connection "asdf".
    private val uiPayload = setOf(
        policy("role:get", "*"),
        policy("datasource_connection:get", "asdf"),
        policy("execution_request:get", "asdf"),
        policy("execution_request:review", "asdf"),
        policy("execution_request:edit", "asdf"),
        policy("execution_request:execute", "asdf"),
    )

    private val uiFlags = RolePermissions(
        roles = RoleReadPermission(read = true),
        connections = listOf(ConnectionPermissions("asdf", read = true, write = true, review = true)),
    )

    @Test
    fun `flags map to the policies the UI sends`() {
        assertThat(uiFlags.toPolicies()).isEqualTo(uiPayload)
    }

    @Test
    fun `the UI's policies map back to the same flags`() {
        assertThat(RolePermissions.fromPolicies(uiPayload.withIds())).isEqualTo(uiFlags)
    }

    @Test
    fun `admins have a single policy`() {
        assertThat(RolePermissions(isAdmin = true, roles = RoleReadPermission(true)).toPolicies())
            .containsExactly(policy("*:*", "*"))
        assertThat(RolePermissions.fromPolicies(setOf(policy("*:*", "*")))?.isAdmin).isTrue()
    }

    @Test
    fun `a connection entry always lets the role see the connection`() {
        assertThat(RolePermissions(connections = listOf(ConnectionPermissions("prod-*"))).toPolicies())
            .containsExactly(policy("datasource_connection:get", "prod-*"))
    }

    @Test
    fun `the default role is expressed as flags`() {
        val permissions = RolePermissions.fromPolicies(Role.DEFAULT_ROLE_POLICIES)

        assertThat(permissions).isEqualTo(
            RolePermissions(
                users = UserPermissions(read = true),
                connections = listOf(ConnectionPermissions("*", read = true)),
            ),
        )
    }

    @Test
    fun `policies the UI wouldn't create can't be expressed as flags`() {
        listOf(
            setOf(policy("execution_request:get", "orders")),
            setOf(policy("user:get", "some-user")),
            setOf(policy("*:*", "*"), policy("role:get", "*")),
            setOf(policy("datasource_connection:edit", "*")),
            setOf(policy("datasource_connection:get", "x"), policy("execution_request:edit", "x")),
        ).forEach { assertThat(RolePermissions.fromPolicies(it)).describedAs(it.toString()).isNull() }
    }

    private fun policy(action: String, resource: String) = Policy(action = action, resource = resource)

    private fun Set<Policy>.withIds() = mapIndexed { index, it -> it.copy(id = "policy-$index") }.toSet()
}
