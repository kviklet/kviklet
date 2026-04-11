import { useContext, useCallback } from "react";
import { TimezoneStatusContext } from "../components/TimezoneProvider";
import type { TimezoneMode } from "../components/TimezoneProvider";
import { formatAbsoluteTime } from "../utils/timeFormat";

function useTimezone() {
  const ctx = useContext(TimezoneStatusContext);

  const formatTime = useCallback(
    (date: Date): string => formatAbsoluteTime(date, ctx.timezone),
    [ctx.timezone],
  );

  const setTimezone = useCallback(
    (tz: TimezoneMode) => ctx.setTimezone(tz),
    [ctx],
  );

  return { timezone: ctx.timezone, setTimezone, formatTime };
}

export default useTimezone;
