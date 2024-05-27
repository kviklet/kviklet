import { useEffect, useState } from "react";
import { ConfigResponse, getConfig } from "../api/ConfigApi";
import { isApiErrorResponse } from "../api/Errors";
import useNotification from "./useNotification";

const useConfig = () => {
  const [config, setConfig] = useState<ConfigResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const { addNotification } = useNotification();

  const refreshConfig = async () => {
    setLoading(true);
    const response = await getConfig();
    console.log(response);
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

  useEffect(() => {
    void refreshConfig();
  }, []);

  return { config, loading, refreshConfig };
};

export default useConfig;
