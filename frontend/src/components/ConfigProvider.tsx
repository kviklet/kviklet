import React, { useState, useEffect, useContext } from "react";
import {
  ConfigPayload,
  ConfigResponse,
  getConfig,
  putConfig,
} from "../api/ConfigApi";
import { isApiErrorResponse } from "../api/Errors";
import useNotification from "../hooks/useNotification";

type ConfigContextType = {
  config: ConfigResponse | null;
  loading: boolean;
  refreshConfig: () => Promise<void>;
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

  const refreshConfig = async () => {
    setLoading(true);
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
    setLoading(false);
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

  useEffect(() => {
    void refreshConfig();
  }, []);

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
