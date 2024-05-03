import { ChevronRightIcon } from "@heroicons/react/20/solid";
import { ConnectionResponse } from "../../../api/DatasourceApi";
import { Link } from "react-router-dom";

export default function ConnectionCard(props: {
  connection: ConnectionResponse;
}) {
  return (
    <Link to={`/settings/connections/${props.connection.id}`}>
      <div className="flex group justify-between align-middle my-2 py-1 px-2 shadow-md border border-slate-200 bg-slate-50 dark:bg-slate-900 dark:border dark:border-slate-700 rounded-md transition-colors">
        <div>
          <div className="text-md font-medium">
            {props.connection.displayName}
          </div>

          <div className="text-slate-500 dark:text-slate-400">
            {props.connection.description}
          </div>
        </div>

        <ChevronRightIcon className="w-8 text-slate-300 group-hover:text-slate-500 dark:text-slate-500 dark:group-hover:text-slate-300 transition-colors"></ChevronRightIcon>
      </div>
    </Link>
  );
}
