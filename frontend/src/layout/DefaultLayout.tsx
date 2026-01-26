import TopBanner from "./TopBanner";
import Footer from "./Footer";
import { Outlet } from "react-router-dom";
import Notification, { ErrorNotification } from "../components/Notification";
import { NotificationContext } from "../components/NotifcationStatusProvider";
import { useContext } from "react";

function DefaultLayout() {
  const { notifications } =
    useContext<NotificationContext>(NotificationContext);

  return (
    <div className="flex min-h-screen flex-col">
      <TopBanner />
      <main className="flex-1 pb-10">
        <Outlet />
      </main>
      <Footer />
      <div
        aria-live="assertive"
        className="pointer-events-none fixed inset-0 mt-12 flex items-end px-4 py-6 sm:items-start sm:p-6"
      >
        <div className="flex w-full flex-col items-center space-y-4 sm:items-end">
          {notifications.map((notification) => {
            if (notification.type === "info") {
              return (
                <Notification
                  title={notification.title}
                  text={notification.text}
                />
              );
            } else {
              return (
                <ErrorNotification
                  title={notification.title}
                  text={notification.text}
                />
              );
            }
          })}
        </div>
      </div>
    </div>
  );
}

export default DefaultLayout;
