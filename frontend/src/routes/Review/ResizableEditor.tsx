import { ReactNode, useRef, useState } from "react";

export default function ResizableEditor({ children }: { children: ReactNode }) {
  const [editorHeight, setEditorHeight] = useState(280);
  const drag = useRef<{ y: number; height: number } | null>(null);
  return (
    <>
      <div style={{ height: editorHeight }} data-testid="monaco-editor-wrapper">
        {children}
      </div>
      <div
        role="separator"
        aria-label="Resize query editor"
        aria-orientation="horizontal"
        aria-valuemin={160}
        aria-valuemax={640}
        aria-valuenow={editorHeight}
        tabIndex={0}
        onKeyDown={(event) => {
          if (event.key === "ArrowUp" || event.key === "ArrowDown") {
            event.preventDefault();
            setEditorHeight((height) =>
              Math.max(
                160,
                Math.min(640, height + (event.key === "ArrowUp" ? -24 : 24)),
              ),
            );
          }
        }}
        onPointerDown={(event) => {
          event.preventDefault();
          drag.current = { y: event.clientY, height: editorHeight };
          event.currentTarget.setPointerCapture(event.pointerId);
        }}
        onPointerMove={(event) => {
          if (drag.current)
            setEditorHeight(
              Math.max(
                160,
                Math.min(
                  640,
                  drag.current.height + event.clientY - drag.current.y,
                ),
              ),
            );
        }}
        onPointerUp={() => {
          drag.current = null;
        }}
        onPointerCancel={() => {
          drag.current = null;
        }}
        className="flex h-4 cursor-row-resize touch-none items-center justify-center border-y border-slate-200 bg-slate-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-indigo-500 dark:border-slate-700 dark:bg-slate-800"
      >
        <span className="h-1 w-8 rounded-full bg-slate-300 dark:bg-slate-600" />
      </div>
    </>
  );
}
