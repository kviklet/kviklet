import { Route, Navigate, Routes } from "react-router-dom";
import Settings from "./routes/settings/Settings";
import RootLayout from "./layout/RootLayout";
import { Requests } from "./routes/Requests";
import Login from "./routes/Login";
import { ReactElement, useContext } from "react";
import {
  UserStatusContext,
  UserStatusProvider,
} from "./components/UserStatusProvider";
import { ThemeStatusProvider } from "./components/ThemeStatusProvider";
import ConnectionChooser from "./routes/NewRequest";
import Auditlog from "./routes/Auditlog";
import { NotificationContextProvider } from "./components/NotifcationStatusProvider";
import { ConfigProvider } from "./components/ConfigProvider";
import RequestReview from "./routes/Review";
import LiveSessionWebsockets from "./routes/LiveSessionWebsockets";
import { useHasPermission, useUserStatusLoading } from "./hooks/permissions";

export interface ProtectedRouteProps {
  children: ReactElement;
}

// The index page is the requests list, which a role without execution_request:get can
// never load — send those users to their profile instead of a 403 toast.
const IndexLanding = (): ReactElement => {
  const loading = useUserStatusLoading();
  const canSeeRequests = useHasPermission("execution_request:get");
  if (loading) {
    return <div>Loading...</div>;
  }
  return canSeeRequests ? (
    <Requests />
  ) : (
    <Navigate to="/settings/profile" replace />
  );
};

export const ProtectedRoute = ({
  children,
}: ProtectedRouteProps): ReactElement => {
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
            <ConfigProvider>
              <Routes>
                <Route path="/" element={<RootLayout />}>
                  <Route
                    index
                    element={
                      <ProtectedRoute>
                        <IndexLanding />
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
                    path="requests/:requestId/session"
                    element={
                      <ProtectedRoute>
                        <LiveSessionWebsockets />
                      </ProtectedRoute>
                    }
                  />
                  <Route path="login" element={<Login />} />
                </Route>
              </Routes>
            </ConfigProvider>
          </NotificationContextProvider>
        </ThemeStatusProvider>
      </UserStatusProvider>
    </div>
  );
}

export default App;
