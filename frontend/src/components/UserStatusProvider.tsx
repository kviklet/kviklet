import React, { useState, useEffect } from "react";
import { StatusResponse, checklogin } from "../api/StatusApi";
import { useLocation } from "react-router-dom";

const UserStatusContext = React.createContext<
  StatusResponse | false | undefined
>(undefined);

type Props = {
  children: React.ReactNode;
};

export const UserStatusProvider: React.FC<Props> = ({ children }) => {
  const [userStatus, setUserStatus] = useState<StatusResponse | false>(false);

  const location = useLocation();
  const fetchStatus = async () => {
    try {
      const status = await checklogin();
      console.log(status);
      setUserStatus(status);
    } catch (error) {
      console.error("Failed to fetch user status:", error);
    }
  };

  const handleVisibilityChange = () => {
    if (!document.hidden) {
      fetchStatus();
    }
  };
  document.addEventListener("visibilitychange", handleVisibilityChange);

  useEffect(() => {
    fetchStatus();
  }, [location.pathname]);

  return (
    <UserStatusContext.Provider value={userStatus}>
      {children}
    </UserStatusContext.Provider>
  );
};

export { UserStatusContext };
