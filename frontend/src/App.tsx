import { Route, Navigate, Routes } from "react-router-dom";
import Settings from "./routes/settings/Settings";
import DefaultLayout from "./layout/DefaultLayout";
import { Requests } from "./routes/Requests";
import Login from "./routes/Login";
import { useContext } from "react";
import {
  UserStatusContext,
  UserStatusProvider,
} from "./components/UserStatusProvider";
import { ThemeStatusProvider } from "./components/ThemeStatusProvider";
import ConnectionChooser from "./routes/NewRequest";
import Auditlog from "./routes/Auditlog";
import { NotificationContextProvider } from "./components/NotifcationStatusProvider";
import RequestReview from "./routes/Review";
import LiveSessionWebsockets from "./routes/LiveSessionWebsockets";
import LiveSession from "./routes/LiveSession";

export interface ProtectedRouteProps {
  children: JSX.Element;
}

export const ProtectedRoute = ({
  children,
}: ProtectedRouteProps): JSX.Element => {
  const userContext = useContext(UserStatusContext);

  if (userContext.userStatus === undefined) {
    return <div>Loading...</div>;
  }
  if (userContext.userStatus === false) {
    return <Navigate to="/login" />;
  }
  return children;
};
function App() {
  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 transition-colors dark:bg-slate-950 dark:text-slate-50">
      <UserStatusProvider>
        <ThemeStatusProvider>
          <NotificationContextProvider>
            <Routes>
              <Route path="/" element={<DefaultLayout />}>
                <Route
                  index
                  element={
                    <ProtectedRoute>
                      <Requests />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="settings/*"
                  element={
                    <ProtectedRoute>
                      <Settings />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="new"
                  element={
                    <ProtectedRoute>
                      <ConnectionChooser></ConnectionChooser>
                    </ProtectedRoute>
                  }
                ></Route>
                <Route
                  path="requests"
                  element={
                    <ProtectedRoute>
                      <Requests />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="auditlog"
                  element={
                    <ProtectedRoute>
                      <Auditlog />
                    </ProtectedRoute>
                  }
                ></Route>
                <Route
                  path="requests/:requestId"
                  element={
                    <ProtectedRoute>
                      <RequestReview />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="requests/:requestId/session-live"
                  element={
                    <ProtectedRoute>
                      <LiveSessionWebsockets />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="requests/:requestId/session"
                  element={
                    <ProtectedRoute>
                      <LiveSession />
                    </ProtectedRoute>
                  }
                />
                <Route path="login" element={<Login />} />
              </Route>
            </Routes>
          </NotificationContextProvider>
        </ThemeStatusProvider>
      </UserStatusProvider>
    </div>
  );
}

export default App;
