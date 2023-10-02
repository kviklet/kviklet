import React, { useCallback, useState } from "react";

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
  const theme = (localStorage.getItem("theme") as "light" | "dark") || "light";
  const [currentTheme, setCurrentTheme] = useState<"light" | "dark">(theme);

  const setTheme = useCallback((theme: "light" | "dark") => {
    localStorage.setItem("theme", theme);
    setCurrentTheme(theme);
  }, []);

  return (
    <ThemeStatusContext.Provider
      value={{
        currentTheme: currentTheme,
        setTheme: setTheme,
      }}
    >
      {children}
    </ThemeStatusContext.Provider>
  );
};

export { ThemeStatusContext };
export type { ThemeContext };
