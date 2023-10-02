import React from "react";
import Button from "./Button";

const DeleteConfirm = (props: {
  title: string;
  message: string;
  onConfirm: () => Promise<void>;
  onCancel: () => void;
}) => {
  return (
    <div className="w-2xl shadow p-3 bg-slate-50 border border-slate-200 dark:border-none dark:bg-slate-950 rounded">
      <div className="flex flex-col mb-3">
        <h1 className="text-l">{props.title}</h1>
        <p className="text-slate-700 dark:text-slate-200 mt-2">
          {props.message}
        </p>

        <div className="flex flex-row mt-3">
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

export default DeleteConfirm;
