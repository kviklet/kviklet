import TopBanner from "./TopBanner";
import Footer from "./Footer";
import { Outlet } from "react-router-dom";
import Notification, { ErrorNotification } from "../components/Notification";
import { NotificationContext } from "../components/NotifcationStatusProvider";
import { useContext } from "react";
import { useAutoAnimate } from "@formkit/auto-animate/react";
import type { AutoAnimationPlugin } from "@formkit/auto-animate";

const ANIMATION_MS = 200;

// Custom plugin: animate toasts with opacity + translate only. The default
// auto-animate plugin grows added elements from zero width/height, which makes
// the overflow-hidden cards visibly squish/shrink for a frame.
// NOTE: at runtime auto-animate calls the plugin as
// (el, "remain", oldCoords, newCoords) — the reverse of its published types,
// so we name the positional args by what they actually receive.
const notificationAnimation: AutoAnimationPlugin = (
  el,
  action,
  oldCoords,
  newCoords,
) => {
  let keyframes: Keyframe[] = [];
  if (action === "add") {
    keyframes = [
      { opacity: 0, transform: "translateX(1rem)" },
      { opacity: 1, transform: "translateX(0)" },
    ];
  } else if (action === "remove") {
    keyframes = [
      { opacity: 1, transform: "translateX(0)" },
      { opacity: 0, transform: "translateX(1rem)" },
    ];
  } else if (oldCoords && newCoords) {
    // remain: slide existing toasts from their old position to the new one.
    const deltaY = oldCoords.top - newCoords.top;
    keyframes = [
      { transform: `translateY(${deltaY}px)` },
      { transform: "translateY(0)" },
    ];
  }
  return new KeyframeEffect(el, keyframes, {
    duration: ANIMATION_MS,
    easing: "ease-out",
  });
};

function DefaultLayout() {
  const { notifications, removeNotification } =
    useContext<NotificationContext>(NotificationContext);
  const [notificationListRef] = useAutoAnimate(notificationAnimation);

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
        <div
          ref={notificationListRef}
          className="flex w-full flex-col items-center gap-4 sm:items-end"
        >
          {notifications.map((notification) => {
            if (notification.type === "info") {
              return (
                <Notification
                  key={notification.id}
                  title={notification.title}
                  text={notification.text}
                  onClose={() => removeNotification(notification.id)}
                />
              );
            } else {
              return (
                <ErrorNotification
                  key={notification.id}
                  title={notification.title}
                  text={notification.text}
                  onClose={() => removeNotification(notification.id)}
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
