import { RouterProvider, createBrowserRouter } from "react-router-dom";
import Settings from "./routes/Settings";
import DefaultLayout from "./layout/DefaultLayout";
import { AddRequestForm } from "./routes/AddRequestForm";

class Database {
  id: string;
  name: string;
  uri: string;
  username: string;

  constructor(id: string, name: string, uri: string, username: string) {
    this.id = id;
    this.name = name;
    this.uri = uri;
    this.username = username;
  }
}
const router = createBrowserRouter([
  {
    path: "/",
    element: <DefaultLayout />,
    children: [
      {
        path: "/settings",
        element: <Settings />,
      },
      {
        path: "/requests",
        element: <AddRequestForm />,
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
