import { RouterProvider, createBrowserRouter } from "react-router-dom";
import Settings from "./routes/Settings";
import DefaultLayout from "./layout/DefaultLayout";
import { AddRequestForm } from "./routes/AddRequestForm";
import { Requests } from "./routes/Requests";
import RequestReview from "./routes/RequestReview";

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
    ],
  },
]);

function App() {
  return (
    <div>
      <RouterProvider router={router} />
    </div>
  );
}

export default App;
