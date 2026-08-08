import { Link } from "react-router-dom";
import { useHasPermission } from "../../../hooks/permissions";

// Links a connection's display name to its settings page so reviewers can
// inspect the connection (user, host, review config). Falls back to plain
// text for users whose roles don't include the connection settings pages.
const ConnectionLink = ({
  connectionId,
  displayName,
}: {
  connectionId: string;
  displayName: string;
}) => {
  const canViewConnections = useHasPermission("datasource_connection:get");

  if (!canViewConnections) {
    return <span className="italic">{displayName}</span>;
  }
  return (
    <Link
      to={`/settings/connections/${encodeURIComponent(connectionId)}`}
      className="italic hover:underline"
      title="View connection settings"
    >
      {displayName}
    </Link>
  );
};

export default ConnectionLink;
