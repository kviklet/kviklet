import { VFC, useRef, useState, useEffect } from "react";
import * as monaco from "monaco-editor/esm/vs/editor/editor.api";

export const Editor: VFC = () => {
  const [editor, setEditor] =
    useState<monaco.editor.IStandaloneCodeEditor | null>(null);
  const monacoEl = useRef(null);

  useEffect(() => {
    if (monacoEl) {
      setEditor((editor) => {
        if (editor) return editor;

        return monaco.editor.create(monacoEl.current!, {
          value: "SELECT * FROM TEST;",
          language: "sql",
          suggest: {
            showKeywords: true,
          },
        });
      });
    }

    return () => editor?.dispose();
  }, [monacoEl.current]);

  return <div className="w-full h-full" ref={monacoEl}></div>;
};
