import { ReactNode } from "react";

function TimelineItem({
  connectTop = true,
  header,
  children,
}: {
  connectTop?: boolean;
  header: ReactNode;
  children?: ReactNode;
}) {
  return (
    <div>
      <div className="relative ml-4 flex py-4">
        <div
          className={`absolute bottom-0 left-0 ${
            connectTop ? "top-0" : "top-5"
          } block w-0.5 whitespace-pre bg-slate-700`}
        >
          {" "}
        </div>
        {header}
      </div>
      {children}
    </div>
  );
}

export default TimelineItem;
