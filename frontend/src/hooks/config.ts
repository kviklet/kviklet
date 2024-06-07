import { useEffect, useState } from "react";
import {
  ConfigPayload,
  ConfigResponse,
  getConfig,
  putConfig,
} from "../api/ConfigApi";
import { isApiErrorResponse } from "../api/Errors";
import useNotification from "./useNotification";

const useConfig = () => {
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

  const updateConfig = async (config: ConfigPayload) => {
    setLoading(true);
    const response = await putConfig(config);
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

  return { config, loading, refreshConfig, updateConfig };
};

export default useConfig;
