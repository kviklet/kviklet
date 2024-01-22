import { useEffect, useState } from "react";
import { ConfigResponse, getConfig } from "../api/ConfigApi";

const useConfig = () => {
  const [config, setConfig] = useState<ConfigResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const refreshConfig = async () => {
    setLoading(true);
    try {
      const config = await getConfig();
      setConfig(config);
      setLoading(false);
    } catch (err) {
      console.error(err);
      setLoading(false);
    }
  };

  useEffect(() => {
    void refreshConfig();
  }, []);

  return { config, loading, refreshConfig };
};

export default useConfig;
