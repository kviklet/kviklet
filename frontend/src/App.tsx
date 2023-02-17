import React, { useEffect, useState } from "react";
import "./App.css";
import TopBanner from "./TopBanner";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  solid,
  regular,
  brands,
  icon,
} from "@fortawesome/fontawesome-svg-core/import.macro";
import { RouterProvider, createBrowserRouter } from "react-router-dom";
import Settings from "./Settings";
import DefaultLayout from "./DefaultLayout";
import Requests from "./Requests";

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
        element: <Requests />,
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
