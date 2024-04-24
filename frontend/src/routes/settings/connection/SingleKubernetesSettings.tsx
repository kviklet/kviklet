import { useState } from "react";
import {
  KubernetesConnectionResponse,
  PatchKubernetesConnectionPayload,
} from "../../../api/DatasourceApi";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";

import { solid } from "@fortawesome/fontawesome-svg-core/import.macro";

export default function SingleKubernetesConnectionSettings(props: {
  connection: KubernetesConnectionResponse;
  editConnectionHandler: (
    connectionId: string,
    connection: PatchKubernetesConnectionPayload,
  ) => Promise<void>;
}) {
  const [numTotalRequired, setNumTotalRequired] = useState<number>(
    props.connection.reviewConfig.numTotalRequired,
  );
  const [showCheck, setShowCheck] = useState<boolean>(false);

  const submit = async () => {
    await props.editConnectionHandler(props.connection.id, {
      reviewConfig: {
        numTotalRequired,
      },
      connectionType: "KUBERNETES",
    });
    setShowCheck(false);
  };

  // Main screen showing connection
  return (
    <div className="my-4 mx-2 px-4 py-4 shadow-md border border-slate-200 bg-slate-50 dark:bg-slate-900 dark:border dark:border-slate-700 rounded-md transition-colors">
      <div className="flex justify-between">
        <div className="text-md font-medium">
          {props.connection.displayName}
        </div>
      </div>

      <div className="flex flex-col pl-2 pt-3">
        <div className="pb-3 text-slate-500 dark:text-slate-400">
          {props.connection.description}
        </div>
        <div className="flex justify-between">
          <label htmlFor="number" className="mr-auto dark:text-slate-400">
            Required reviews:
          </label>
          <input
            type="number"
            min="0"
            value={numTotalRequired}
            onChange={(e) => {
              setNumTotalRequired(parseInt(e.target.value));
              setShowCheck(true);
            }}
            className="w-32 sm:w-36 lg:w-auto rounded mx-1 py-2 px-3 appearance-none border border-slate-200 
            hover:border-slate-300 focus:border-slate-500 focus:hover:border-slate-500 focus:shadow-outline focus:outline-none
            text-slate-600 dark:text-slate-50 leading-tight
            dark:bg-slate-900 dark:border-slate-700 dark:hover:border-slate-600 dark:focus:border-slate-500 dark:focus:hover:border-slate-500 transition-colors"
          ></input>
        </div>

        {/* Accept button */}
        <div className="flex justify-end">
          <button
            onClick={() => void submit()}
            className={`dark:bg-slate-800 mt-3 mr-1 px-5 rounded-md text-white-600 hover:text-sky-500 dark:hover:text-sky-400
              shadow-sm border border-slate-300 dark:border-slate-700 hover:border-slate-300 dark:hover:border-slate-600 transition-colors ${
                showCheck ? "visible" : "invisible"
              }`}
          >
            <FontAwesomeIcon icon={solid("check")} />
          </button>
        </div>
      </div>
    </div>
  );
}
