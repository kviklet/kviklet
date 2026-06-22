import React, { useState, useEffect, useContext, useRef } from "react";
import {
  ConfigPayload,
  ConfigResponse,
  getConfig,
  putConfig,
} from "../api/ConfigApi";
import { isApiErrorResponse } from "../api/Errors";
import useNotification from "../hooks/useNotification";
import { UserStatusContext } from "./UserStatusProvider";

type ConfigContextType = {
  config: ConfigResponse | null;
  loading: boolean;
  refreshConfig: (background?: boolean) => Promise<void>;
  updateConfig: (config: ConfigPayload) => Promise<void>;
};

const ConfigContext = React.createContext<ConfigContextType>({
  config: null,
  loading: true,
  refreshConfig: async () => {},
  updateConfig: async () => {},
});

type Props = {
  children: React.ReactNode;
};

export const ConfigProvider: React.FC<Props> = ({ children }) => {
  const [config, setConfig] = useState<ConfigResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const { addNotification } = useNotification();

  const refreshConfig = async (background = false) => {
    // Background refreshes (e.g. after login, or when opening settings) keep the
    // existing UI visible instead of flashing the global loading spinner.
    if (!background) setLoading(true);
    const response = await getConfig();
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Error reaching backend",
        text: response.message,
        type: "error",
      });
    } else {
      setConfig(response);
    }
    if (!background) setLoading(false);
  };

  const updateConfig = async (newConfig: ConfigPayload) => {
    setLoading(true);
    const response = await putConfig(newConfig);
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Error updating config",
        text: response.message,
        type: "error",
      });
    } else {
      setConfig(response);
    }
    setLoading(false);
  };

  // The config endpoint returns the webhook URLs only to authenticated users; a
  // logged-out fetch comes back without them. Refetch whenever the logged-in user
  // changes (login, logout, user switch) so the config never stays stale after an
  // initial unauthenticated load. The first fetch shows the spinner; later ones
  // (auth transitions) refresh silently in the background.
  const { userStatus } = useContext(UserStatusContext);
  const userId = userStatus ? userStatus.id : null;
  const hasLoadedOnce = useRef(false);

  useEffect(() => {
    void refreshConfig(hasLoadedOnce.current);
    hasLoadedOnce.current = true;
  }, [userId]);

  return (
    <ConfigContext.Provider
      value={{ config, loading, refreshConfig, updateConfig }}
    >
      {children}
    </ConfigContext.Provider>
  );
};

const useConfig = () => useContext(ConfigContext);
export default useConfig;
