import { useContext } from "react";
import { NotificationContext } from "../components/NotifcationStatusProvider";

const useNotification = () => {
  const { addNotification } =
    useContext<NotificationContext>(NotificationContext);

  return { addNotification };
};

export default useNotification;
