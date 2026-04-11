import React, { useCallback, useState } from "react";

type TimezoneMode = "local" | "utc";

type TimezoneContext = {
  timezone: TimezoneMode;
  setTimezone(tz: TimezoneMode): void;
};

const TimezoneStatusContext = React.createContext<TimezoneContext>({
  timezone: "local",
  setTimezone: () => {},
});

type Props = {
  children: React.ReactNode;
};

export const TimezoneProvider: React.FC<Props> = ({ children }) => {
  const stored = (localStorage.getItem("timezone") as TimezoneMode) || "local";
  const [timezone, setTimezoneState] = useState<TimezoneMode>(stored);

  const setTimezone = useCallback((tz: TimezoneMode) => {
    localStorage.setItem("timezone", tz);
    setTimezoneState(tz);
  }, []);

  return (
    <TimezoneStatusContext.Provider value={{ timezone, setTimezone }}>
      {children}
    </TimezoneStatusContext.Provider>
  );
};

export { TimezoneStatusContext };
export type { TimezoneMode, TimezoneContext };
