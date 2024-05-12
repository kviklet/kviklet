import { VFC, useRef, useState, useEffect, useContext } from "react";
import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
import { ThemeContext, ThemeStatusContext } from "./ThemeStatusProvider";

export const Editor: VFC = () => {
  const [editor, setEditor] =
    useState<monaco.editor.IStandaloneCodeEditor | null>(null);
  const monacoEl = useRef(null);

  const { currentTheme } = useContext<ThemeContext>(ThemeStatusContext);
  const theme = currentTheme === "dark" ? "vs-dark" : "vs";

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
          minimap: { enabled: false },
          automaticLayout: true,
        });
      });
    }

    return () => editor?.dispose();
  }, [monacoEl.current]);

  monaco.editor.setTheme(theme);

  return (
    <div className="resize">
      <div className="h-full w-full" ref={monacoEl}></div>;
    </div>
  );
};
