/**
 * Mirrors the backend `Permission` enum
 * (backend/src/main/kotlin/dev/kviklet/kviklet/security/MethodSecurityConfiguration.kt).
 * Keep in sync when a permission is added there.
 */
const PERMISSIONS = [
  "configuration:get",
  "configuration:edit",
  "datasource_connection:get",
  "datasource_connection:edit",
  "datasource_connection:create",
  "execution_request:get",
  "execution_request:edit",
  "execution_request:review",
  "execution_request:execute",
  "role:get",
  "role:edit",
  "user:get",
  "user:edit",
  "user:create",
  "user:edit_roles",
  "api_key:get",
  "api_key:create",
  "api_key:edit",
] as const;

type Permission = (typeof PERMISSIONS)[number];

/**
 * Whether `permissions` grants `permission`.
 *
 * The backend resolves the whole policy vote, so this is only ever a membership check — never
 * re-implement the policy Ant-pattern matching from `PolicyGrantedAuthority.kt` here.
 *
 * Use it with the object-scoped `permissions` of a connection or execution request. For the
 * current user's global permissions use `useHasPermission`, and mind the difference: the global
 * set means "allowed on at least one resource" and cannot answer whether the user may act on a
 * particular connection or request. A nullish list means the backend did not resolve permissions
 * for that object, which is treated as "not allowed".
 */
/**
 * User-facing copy for actions blocked by a missing `execution_request:execute` permission.
 * The e2e suite asserts on this exact string (`EXECUTE_TOOLTIP` in
 * e2e/tests/permissions/requests.spec.ts) — keep them in sync when rewording.
 */
const NO_EXECUTE_PERMISSION_MESSAGE =
  "You lack permission to execute on this connection";

function hasPermission(
  permissions: string[] | null | undefined,
  permission: Permission,
): boolean {
  return permissions?.includes(permission) ?? false;
}

export { PERMISSIONS, hasPermission, NO_EXECUTE_PERMISSION_MESSAGE };
export type { Permission };
