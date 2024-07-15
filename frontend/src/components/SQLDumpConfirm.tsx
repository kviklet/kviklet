import React from "react";
import Button from "./Button";

const SQLDumpConfirm = (props: {
  title: string;
  message: string;
  onConfirm: () => Promise<void>;
  onCancel: () => void;
}) => {
  return (
    <div className="w-2xl rounded border border-slate-200 bg-slate-50 p-3 shadow dark:border-none dark:bg-slate-950">
      <div className="mb-3 flex flex-col">
        <h1 className="text-l">{props.title}</h1>
        <p className="mt-2 text-slate-700 dark:text-slate-200">
          {props.message}
        </p>

        <div className="mt-3 flex flex-row">
          <Button className="ml-auto" onClick={props.onCancel}>
            Cancel
          </Button>
          <Button
            className="ml-2"
            onClick={() => void props.onConfirm()}
            type="submit"
          >
            Confirm
          </Button>
        </div>
      </div>
    </div>
  );
};

export default SQLDumpConfirm;
