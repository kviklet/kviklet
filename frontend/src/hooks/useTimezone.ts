import { useContext } from "react";
import { TimezoneStatusContext } from "../components/TimezoneProvider";
import { formatAbsoluteTime } from "../utils/timeFormat";

function useTimezone() {
  const { timezone, setTimezone } = useContext(TimezoneStatusContext);

  const formatTime = (date: Date): string => {
    return formatAbsoluteTime(date, timezone);
  };

  return { timezone, setTimezone, formatTime };
}

export default useTimezone;
