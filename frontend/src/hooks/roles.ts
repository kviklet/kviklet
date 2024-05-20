import { useEffect, useState } from "react";
import { RoleResponse, getRole } from "../api/RoleApi";

const useRole = (id: string) => {
  const [role, setRole] = useState<RoleResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  async function request() {
    setLoading(true);
    const role = await getRole(id);
    setRole(role);
    setLoading(false);
  }

  useEffect(() => {
    void request();
  }, [id]);

  return {
    loading,
    role,
  };
};

export { useRole };
