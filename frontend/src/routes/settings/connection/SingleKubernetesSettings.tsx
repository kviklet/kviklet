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

  return (
    <div className="mx-2 my-4 rounded-md border border-slate-200 bg-slate-50 px-4 py-4 shadow-md transition-colors dark:border dark:border-slate-700 dark:bg-slate-900">
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
            className="focus:shadow-outline mx-1 w-32 appearance-none rounded border border-slate-200 px-3 py-2 leading-tight 
            text-slate-600 transition-colors focus:border-slate-500 focus:outline-none hover:border-slate-300
            focus:hover:border-slate-500 dark:border-slate-700 dark:bg-slate-900
            dark:text-slate-50 dark:focus:border-slate-500 dark:hover:border-slate-600 dark:focus:hover:border-slate-500 sm:w-36 lg:w-auto"
          ></input>
        </div>
        <div className="flex justify-end">
          <button
            onClick={() => void submit()}
            className={`text-white-600 mr-1 mt-3 rounded-md border border-slate-300 px-5 shadow-sm
              transition-colors hover:border-slate-300 hover:text-sky-500 dark:border-slate-700 dark:bg-slate-800 dark:hover:border-slate-600 dark:hover:text-sky-400 ${
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
