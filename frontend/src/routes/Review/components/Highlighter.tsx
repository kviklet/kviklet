import { ReactNode, useContext } from "react";
import SyntaxHighlighter from "react-syntax-highlighter";
import {
  a11yDark,
  a11yLight,
} from "react-syntax-highlighter/dist/esm/styles/hljs";
import {
  ThemeContext,
  ThemeStatusContext,
} from "../../../components/ThemeStatusProvider";
import { CodeProps } from "react-markdown/lib/ast-to-react";

const Highlighter = ({
  children,
  language = "sql",
}: {
  children: string;
  language?: string;
}) => {
  const { currentTheme } = useContext<ThemeContext>(ThemeStatusContext);
  const style = currentTheme === "dark" ? a11yDark : a11yLight;

  return (
    <SyntaxHighlighter
      style={style}
      language={language}
      className="scrollbar-thin scrollbar-track-transparent scrollbar-thumb-slate-300 scrollbar-thumb-rounded-full dark:scrollbar-thumb-slate-700"
      customStyle={{
        background: "transparent",
        fontSize: "0.8125rem",
      }}
      PreTag={"div"}
    >
      {children}
    </SyntaxHighlighter>
  );
};

const componentMap = {
  code: ({ children }: CodeProps) => {
    return <Highlighter>{children as string}</Highlighter>;
  },
  ul: ({ children }: { children: ReactNode }) => (
    <ul className="ml-4 mt-4 list-disc">{children}</ul>
  ),
};

export { componentMap, Highlighter };
