import { Route, Navigate, Routes } from "react-router-dom";
import Settings from "./routes/settings/Settings";
import DefaultLayout from "./layout/DefaultLayout";
import { AddRequestForm } from "./routes/AddRequestForm";
import { Requests } from "./routes/Requests";
import RequestReview from "./routes/RequestReview";
import Login from "./routes/Login";
import { useContext } from "react";
import {
  UserStatusContext,
  UserStatusProvider,
} from "./components/UserStatusProvider";
import { ThemeStatusProvider } from "./components/ThemeStatusProvider";

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
    <div className="dark:text-slate-50 dark:bg-slate-950 text-slate-900 bg-slate-50">
      <UserStatusProvider>
        <ThemeStatusProvider>
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
                path="requests"
                element={
                  <ProtectedRoute>
                    <Requests />
                  </ProtectedRoute>
                }
              />
              <Route
                path="requests/new"
                element={
                  <ProtectedRoute>
                    <AddRequestForm />
                  </ProtectedRoute>
                }
              />
              <Route
                path="requests/:requestId"
                element={
                  <ProtectedRoute>
                    <RequestReview />
                  </ProtectedRoute>
                }
              />
              <Route path="login" element={<Login />} />
            </Route>
          </Routes>
        </ThemeStatusProvider>
      </UserStatusProvider>
    </div>
  );
}

export default App;
