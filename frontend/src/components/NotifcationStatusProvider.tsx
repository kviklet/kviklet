import React, { useState } from "react";

type NotificationInput = {
  title: string;
  text: string;
  type: "info" | "error";
};

type Notification = NotificationInput & { id: number };

type NotificationContext = {
  notifications: Notification[];
  addNotification: (notification: NotificationInput) => void;
  removeNotification: (id: number) => void;
};

const NotificationContext = React.createContext<NotificationContext>({
  notifications: [],
  addNotification: () => {},
  removeNotification: () => {},
});

type Props = {
  children: React.ReactNode;
};

let nextNotificationId = 0;

export const NotificationContextProvider: React.FC<Props> = ({ children }) => {
  const [notifications, setNotifications] = useState<Notification[]>([]);

  const addNotification = (notification: NotificationInput) => {
    const id = nextNotificationId++;
    setNotifications((notifications) => [
      ...notifications,
      { ...notification, id },
    ]);
  };

  const removeNotification = (id: number) => {
    setNotifications((notifications) => {
      return notifications.filter((n) => n.id !== id);
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
