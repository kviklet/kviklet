import { useContext } from "react";
import { UserStatusContext } from "../components/UserStatusProvider";
import { Permission } from "../api/Permissions";

/**
 * Whether the current user holds `permission` on at least one resource.
 *
 * Use this to hide surfaces a role can never reach (settings tabs, nav entries, admin buttons).
 * It cannot answer whether the user may act on a particular connection or request — a policy
 * scoped to one resource already counts here — so for that, check the `permissions` the
 * backend puts on the object itself with `hasPermission` from `api/Permissions`.
 *
 * Returns false while the user status is still loading.
 */
function useHasPermission(permission: Permission): boolean {
  const { hasPermission } = useContext(UserStatusContext);
  return hasPermission(permission);
}

/** True until the user status has been fetched for the first time. */
function useUserStatusLoading(): boolean {
  const { userStatus } = useContext(UserStatusContext);
  return userStatus === undefined;
}

export { useHasPermission, useUserStatusLoading };
