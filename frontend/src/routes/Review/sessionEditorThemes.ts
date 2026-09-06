import * as monaco from "monaco-editor/esm/vs/editor/editor.api";

// Slate surfaces and indigo accents match the surrounding Kviklet workspace.
export function defineSessionEditorThemes() {
  monaco.editor.defineTheme("kviklet-light", {
    base: "vs",
    inherit: true,
    rules: [
      { token: "keyword", foreground: "4338CA" },
      { token: "string", foreground: "047857" },
      { token: "comment", foreground: "64748B" },
    ],
    colors: {
      "editor.background": "#FFFFFF",
      "editor.foreground": "#1E293B",
      "editorLineNumber.foreground": "#94A3B8",
      "editorLineNumber.activeForeground": "#475569",
      "editor.lineHighlightBackground": "#F8FAFC",
      "editor.selectionBackground": "#E0E7FF",
      "editor.inactiveSelectionBackground": "#EEF2FF",
      "editorCursor.foreground": "#4F46E5",
      "editorIndentGuide.background1": "#E2E8F0",
    },
  });
  monaco.editor.defineTheme("kviklet-dark", {
    base: "vs-dark",
    inherit: true,
    rules: [
      { token: "keyword", foreground: "A5B4FC" },
      { token: "string", foreground: "6EE7B7" },
      { token: "comment", foreground: "94A3B8" },
    ],
    colors: {
      "editor.background": "#0F172A",
      "editor.foreground": "#E2E8F0",
      "editorLineNumber.foreground": "#64748B",
      "editorLineNumber.activeForeground": "#CBD5E1",
      "editor.lineHighlightBackground": "#1E293B",
      "editor.selectionBackground": "#37306B",
      "editor.inactiveSelectionBackground": "#292545",
      "editorCursor.foreground": "#A5B4FC",
      "editorIndentGuide.background1": "#334155",
    },
  });
}
