import { useEffect } from "react";
import { useLocation } from "react-router-dom";
import Spinner from "../../components/Spinner";
import { mcpAuthorizationUrl } from "../../api/McpOAuthApi";

/**
 * Where the backend sends an MCP client's authorization request when the browser has no Kviklet
 * session. Wrapped in ProtectedRoute, so the user logs in first (with any login method); then the
 * request continues at the backend's authorization endpoint, which asks for consent next.
 */
const McpAuthorize = () => {
  const location = useLocation();

  useEffect(() => {
    window.location.replace(mcpAuthorizationUrl(location.search));
  }, [location.search]);

  return <Spinner size="lg" page />;
};

export default McpAuthorize;
