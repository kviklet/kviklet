import React, { useState, useEffect } from "react";
import { StatusResponse, checklogin } from "../api/StatusApi";
import { useLocation } from "react-router-dom";
import { Theme } from "react-data-table-component";

type ThemeContext = {
  currentTheme: "dark" | "light";
  setTheme(theme: "dark" | "light"): void;
};

const ThemeStatusContext = React.createContext<ThemeContext>({
  currentTheme: "light",
  setTheme: () => {},
});

type Props = {
  children: React.ReactNode;
};

export const ThemeStatusProvider: React.FC<Props> = ({ children }) => {
  let theme = localStorage.getItem("theme");
  if (theme === "dark") {
    theme = "dark";
  } else {
    theme = "light";
  }
  const [currentTheme, setCurrentTheme] = useState<"light" | "dark">(
    theme as "light" | "dark"
  );

  return (
    <ThemeStatusContext.Provider
      value={{
        currentTheme: currentTheme,
        setTheme: (theme) => {
          localStorage.setItem("theme", theme);
          setCurrentTheme(theme);
        },
      }}
    >
      {children}
    </ThemeStatusContext.Provider>
  );
};

export { ThemeStatusContext };
export type { ThemeContext };
