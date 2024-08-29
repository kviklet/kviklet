import { ChevronRightIcon } from "@heroicons/react/20/solid";
import { ConnectionResponse } from "../../../api/DatasourceApi";
import { Link } from "react-router-dom";

export default function ConnectionCard(props: {
  connection: ConnectionResponse;
}) {
  return (
    <Link
      to={`/settings/connections/${props.connection.id}`}
      data-testid={`connection-card-${props.connection.displayName}`}
    >
      <div className="group my-2 flex justify-between rounded-md border border-slate-200 bg-slate-50 px-2 py-1 align-middle shadow-md transition-colors dark:border dark:border-slate-700 dark:bg-slate-900">
        <div>
          <div className="text-md font-medium">
            {props.connection.displayName}
          </div>

          <div className="text-slate-500 dark:text-slate-400">
            {props.connection.description}
          </div>
        </div>

        <ChevronRightIcon className="w-8 text-slate-300 transition-colors group-hover:text-slate-500 dark:text-slate-500 dark:group-hover:text-slate-300"></ChevronRightIcon>
      </div>
    </Link>
  );
}
