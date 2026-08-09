import { ReactNode } from "react";

function TimelineItem({
  connectTop = true,
  connectBottom = true,
  header,
  children,
}: {
  connectTop?: boolean;
  /** Whether another timeline item follows below this one. */
  connectBottom?: boolean;
  header: ReactNode;
  children?: ReactNode;
}) {
  // The line always runs down to a content card, but on the timeline's last
  // card-less item it would dangle into nothing, so it stops at the icon
  // (which masks the line end with its own background).
  const connectorExtent = `${connectTop ? "top-0" : "top-5"} ${
    connectBottom || children ? "bottom-0" : "bottom-1/2"
  }`;
  return (
    <div>
      <div className="relative ml-4 flex py-4">
        <div
          className={`absolute left-0 ${connectorExtent} block w-0.5 whitespace-pre bg-slate-700`}
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
