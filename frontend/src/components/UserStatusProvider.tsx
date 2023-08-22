import React, { useState, useEffect } from "react";
import { StatusResponse, checklogin } from "../api/StatusApi";

const UserStatusContext = React.createContext<StatusResponse | false>(false);

type Props = {
  children: React.ReactNode;
};

export const UserStatusProvider: React.FC<Props> = ({ children }) => {
  const [userStatus, setUserStatus] = useState<StatusResponse | false>(false);

  useEffect(() => {
    const fetchStatus = async () => {
      try {
        const status = await checklogin();
        setUserStatus(status);
      } catch (error) {
        console.error("Failed to fetch user status:", error);
      }
    };
    fetchStatus();
  }, []);

  return (
    <UserStatusContext.Provider value={userStatus}>
      {children}
    </UserStatusContext.Provider>
  );
};

export { UserStatusContext };
