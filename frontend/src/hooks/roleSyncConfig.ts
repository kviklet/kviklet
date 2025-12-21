import { useEffect, useState } from "react";
import {
  RoleSyncConfigResponse,
  RoleSyncConfigUpdate,
  AddRoleSyncMappingRequest,
  getRoleSyncConfig,
  updateRoleSyncConfig,
  addRoleSyncMapping,
  deleteRoleSyncMapping,
} from "../api/RoleSyncConfigApi";
import { isApiErrorResponse } from "../api/Errors";
import useNotification from "./useNotification";

const useRoleSyncConfig = () => {
  const [config, setConfig] = useState<RoleSyncConfigResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const { addNotification } = useNotification();

  const refreshConfig = async () => {
    setLoading(true);
    const response = await getRoleSyncConfig();
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Error loading role sync configuration",
        text: response.message,
        type: "error",
      });
    } else {
      setConfig(response);
    }
    setLoading(false);
  };

  const updateConfig = async (configUpdate: RoleSyncConfigUpdate) => {
    setLoading(true);
    const response = await updateRoleSyncConfig(configUpdate);
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Error updating role sync configuration",
        text: response.message,
        type: "error",
      });
    } else {
      setConfig(response);
      addNotification({
        title: "Configuration updated",
        text: "Role sync configuration has been updated successfully.",
        type: "info",
      });
    }
    setLoading(false);
  };

  const addMapping = async (request: AddRoleSyncMappingRequest) => {
    setLoading(true);
    const response = await addRoleSyncMapping(request);
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Error adding role mapping",
        text: response.message,
        type: "error",
      });
    } else {
      addNotification({
        title: "Mapping added",
        text: "Role mapping has been added successfully.",
        type: "info",
      });
      await refreshConfig();
    }
    setLoading(false);
  };

  const deleteMapping = async (id: string) => {
    setLoading(true);
    const response = await deleteRoleSyncMapping(id);
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Error deleting role mapping",
        text: response.message,
        type: "error",
      });
    } else {
      addNotification({
        title: "Mapping deleted",
        text: "Role mapping has been deleted successfully.",
        type: "info",
      });
      await refreshConfig();
    }
    setLoading(false);
  };

  useEffect(() => {
    void refreshConfig();
  }, []);

  return {
    config,
    loading,
    refreshConfig,
    updateConfig,
    addMapping,
    deleteMapping,
  };
};

export default useRoleSyncConfig;
