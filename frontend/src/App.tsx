import {
  Route,
  RouterProvider,
  createBrowserRouter,
  Navigate,
  BrowserRouter as Router,
  Routes,
} from "react-router-dom";
import Settings from "./routes/Settings";
import DefaultLayout from "./layout/DefaultLayout";
import { AddRequestForm } from "./routes/AddRequestForm";
import { Requests } from "./routes/Requests";
import RequestReview from "./routes/RequestReview";
import Login from "./routes/Login";
import { checklogin } from "./api/StatusApi";
import { useEffect, useState } from "react";

export interface ProtectedRouteProps {
  children: JSX.Element;
}

export const ProtectedRoute = ({
  children,
}: ProtectedRouteProps): JSX.Element => {
  const [userName, setUserName] = useState<string | false | undefined>(
    undefined
  );

  useEffect(() => {
    const fetchData = async () => {
      const userName = await checklogin();
      setUserName(userName);
    };
    fetchData();
  }, []);

  if (userName === undefined) {
    return <div>Loading...</div>;
  }
  if (userName === false) {
    return <Navigate to="/login" />;
  }
  return children;
};

const router = createBrowserRouter([
  {
    path: "/",
    element: <DefaultLayout />,
    children: [
      {
        element: <Requests />,
        path: "/",
      },
      {
        path: "/settings",
        element: <Settings />,
      },
      {
        path: "/requests",
        element: <Requests />,
      },
      {
        path: "/requests/new",
        element: <AddRequestForm />,
      },
      {
        path: "/requests/:requestId",
        element: <RequestReview />,
      },
      {
        path: "/login",
        element: <Login />,
      },
    ],
  },
]);

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
            <Route path="settings" element={<Settings />} />
            <Route path="requests" element={<Requests />} />
            <Route path="requests/new" element={<AddRequestForm />} />
            <Route path="requests/:requestId" element={<RequestReview />} />
            <Route path="login" element={<Login />} />
          </Route>
        </Routes>
      </Router>
    </div>
  );
}

export default App;
