import { ReactNode } from "react";
import { Permission } from "../api/Permissions";
import { useHasPermission, useUserStatusLoading } from "../hooks/permissions";

type Props = {
  permission: Permission;
  children: ReactNode;
  /**
   * What to render instead when the permission is missing. Defaults to nothing, which is the
   * "hide it" case for nav entries and settings tabs; pass `<NotAuthorized />` to guard a route.
   */
  fallback?: ReactNode;
};

/**
 * Renders `children` only if the current user holds `permission` on at least one resource.
 *
 * This is the tier-1 gate: it hides what the user's roles can never use. Do not use it for things
 * the user could do to a different object — a request they authored, a connection they administer.
 * Those stay visible and get disabled with an explanation, driven by the object's own
 * `permissions`.
 *
 * Nothing renders until the user status has loaded, so a route guard never flashes its forbidden
 * state before we know the answer.
 */
export default function RequirePermission({
  permission,
  children,
  fallback = null,
}: Props) {
  const loading = useUserStatusLoading();
  const allowed = useHasPermission(permission);
  if (loading) {
    return null;
  }
  return <>{allowed ? children : fallback}</>;
}
