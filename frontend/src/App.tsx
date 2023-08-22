import {
  Route,
  Navigate,
  BrowserRouter as Router,
  Routes,
  useLocation,
} from "react-router-dom";
import Settings from "./routes/settings/Settings";
import DefaultLayout from "./layout/DefaultLayout";
import { AddRequestForm } from "./routes/AddRequestForm";
import { Requests } from "./routes/Requests";
import RequestReview from "./routes/RequestReview";
import Login from "./routes/Login";
import { checklogin } from "./api/StatusApi";
import { useEffect, useState } from "react";
import { UserStatusProvider } from "./components/UserStatusProvider";

export interface ProtectedRouteProps {
  children: JSX.Element;
}

export const ProtectedRoute = ({
  children,
}: ProtectedRouteProps): JSX.Element => {
  const [userName, setUserName] = useState<string | false | undefined>(
    undefined
  );
  const location = useLocation();

  useEffect(() => {
    const fetchData = async () => {
      const response = await checklogin();
      if (response) {
        setUserName(response.email);
      }
      if (response === false) {
        setUserName(false);
      }
    };
    fetchData();
  }, [location.pathname]);

  if (userName === undefined) {
    return <div>Loading...</div>;
  }
  if (userName === false) {
    return <Navigate to="/login" />;
  }
  return children;
};

function App() {
  return (
    <div>
      <Router>
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
      </Router>
    </div>
  );
}

export default App;
