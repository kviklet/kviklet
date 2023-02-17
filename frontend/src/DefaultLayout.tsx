import "./App.css";
import TopBanner from "./TopBanner";
import { Outlet } from "react-router-dom";

function DefaultLayout() {
  return (
    <div>
      <TopBanner></TopBanner>
      <Outlet />
    </div>
  );
}

export default DefaultLayout;
