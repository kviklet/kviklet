import React, { useState, useEffect, useMemo, useRef } from "react";
import { StatusResponse, checklogin } from "../api/StatusApi";
import { useLocation } from "react-router-dom";
import { Permission } from "../api/Permissions";
import { logout } from "../api/LoginApi";

type UserContext = {
  userStatus: StatusResponse | false | undefined;
  refreshState: () => Promise<void>;
  /**
   * Whether the user holds this permission on at least one resource. False while the status is
   * still loading, so permission-gated controls never flash before we know.
   */
  hasPermission: (permission: Permission) => boolean;
  /** Ends the session and commits the logged-out status. */
  logout: () => Promise<void>;
  /**
   * True after a deliberate logout, until the next login. ProtectedRoute uses this to send the
   * user to a plain /login rather than one that would bring them back to the page they just
   * left.
   */
  loggedOut: boolean;
};

const UserStatusContext = React.createContext<UserContext>({
  userStatus: undefined,
  refreshState: async () => {},
  hasPermission: () => false,
  logout: async () => {},
  loggedOut: false,
});

type Props = {
  children: React.ReactNode;
};

export const UserStatusProvider: React.FC<Props> = ({ children }) => {
  const [userStatus, setUserStatus] = useState<{
    userStatus: StatusResponse | false | undefined;
    refreshState: () => Promise<void>;
  }>({
    userStatus: undefined,
    refreshState: async () => {},
  });

  const [loggedOut, setLoggedOut] = useState(false);

  const location = useLocation();
  // Status fetches fire on every navigation and on login, and responses can come back out of
  // order. Only the latest fetch may write, otherwise a stale pre-login response (a `false`)
  // overwrites the fresh logged-in status and bounces the user back to the login page.
  const fetchSeq = useRef(0);
  const fetchStatus = async () => {
    const seq = ++fetchSeq.current;
    try {
      const status = await checklogin();
      if (seq !== fetchSeq.current) {
        return;
      }
      const statusObject = {
        userStatus: status,
        refreshState: fetchStatus,
      };
      setUserStatus(statusObject);
      if (status) {
        setLoggedOut(false);
      }
    } catch (error) {
      console.error("Failed to fetch user status:", error);
    }
  };

  const logoutUser = async () => {
    await logout();
    // Flag first: the status refresh below commits the logged-out state, and ProtectedRoute
    // must already know it was a logout when it redirects. Don't navigate to /login
    // imperatively here — with the stale logged-in status still in context the login page
    // would bounce straight back to "/" (and fire unauthenticated fetches there).
    setLoggedOut(true);
    await fetchStatus();
  };

  const handleVisibilityChange = () => {
    if (document.visibilityState === "visible") {
      void fetchStatus();
    }
  };

  useEffect(() => {
    document.addEventListener("visibilitychange", handleVisibilityChange);
    return () => {
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, []);

  useEffect(() => {
    void fetchStatus();
  }, [location.pathname]);

  const contextValue = useMemo(() => {
    const permissions = new Set(
      userStatus.userStatus ? userStatus.userStatus.permissions : [],
    );
    return {
      ...userStatus,
      hasPermission: (permission: Permission) => permissions.has(permission),
      logout: logoutUser,
      loggedOut,
    };
  }, [userStatus, loggedOut]);

  return (
    <UserStatusContext.Provider value={contextValue}>
      {children}
    </UserStatusContext.Provider>
  );
};

export { UserStatusContext };
