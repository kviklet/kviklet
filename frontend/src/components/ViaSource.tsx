import { RequestSource } from "../api/ExecutionRequestApi";

const labels: Record<RequestSource, string | null> = {
  WEB: null,
  API_KEY: "API key",
  MCP: "MCP",
};

// Marks what was done through an API key or an agent rather than in the UI.
function ViaSource({ source }: { source?: RequestSource }) {
  const label = source && labels[source];
  if (!label) return null;
  return <span title={`Made via ${label}`}> via {label}</span>;
}

export default ViaSource;
