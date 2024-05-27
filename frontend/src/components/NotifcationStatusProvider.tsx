import React, { useState } from "react";

type Notification = {
  title: string;
  text: string;
  type: "info" | "error";
};

type NotificationContext = {
  notifications: Notification[];
  addNotification: (notification: Notification) => void;
  removeNotification: (notification: Notification) => void;
};

const NotificationContext = React.createContext<NotificationContext>({
  notifications: [],
  addNotification: () => {},
  removeNotification: () => {},
});

type Props = {
  children: React.ReactNode;
};

export const NotificationContextProvider: React.FC<Props> = ({ children }) => {
  const [notifications, setNotifications] = useState<Notification[]>([]);

  const addNotification = (notification: Notification) => {
    setNotifications((notifications) => [...notifications, notification]);
  };

  const removeNotification = (notification: Notification) => {
    setNotifications((notifications) => {
      return notifications.filter((n) => n !== notification);
    });
  };

  return (
    <NotificationContext.Provider
      value={{
        notifications,
        addNotification,
        removeNotification,
      }}
    >
      {children}
    </NotificationContext.Provider>
  );
};

export { NotificationContext };
