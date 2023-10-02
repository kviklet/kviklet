import React, { useState, useEffect } from "react";
import { StatusResponse, checklogin } from "../api/StatusApi";
import { useLocation } from "react-router-dom";

type UserContext = {
  userStatus: StatusResponse | false | undefined;
  refreshState: () => Promise<void>;
};

const UserStatusContext = React.createContext<UserContext>({
  userStatus: undefined,
  refreshState: async () => {},
});

type Props = {
  children: React.ReactNode;
};

export const UserStatusProvider: React.FC<Props> = ({ children }) => {
  const [userStatus, setUserStatus] = useState<UserContext>({
    userStatus: undefined,
    refreshState: async () => {},
  });

  const location = useLocation();
  const fetchStatus = async () => {
    try {
      const status = await checklogin();
      const statusObject = {
        userStatus: status,
        refreshState: fetchStatus,
      };
      setUserStatus(statusObject);
    } catch (error) {
      console.error("Failed to fetch user status:", error);
    }
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

  return (
    <UserStatusContext.Provider value={userStatus}>
      {children}
    </UserStatusContext.Provider>
  );
};

export { UserStatusContext };
