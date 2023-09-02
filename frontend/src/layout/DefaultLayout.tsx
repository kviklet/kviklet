import TopBanner from "./TopBanner";
import { Outlet } from "react-router-dom";

function DefaultLayout() {
  return (
    <div>
      <TopBanner></TopBanner>
      <div className="min-h-screen pb-10">
        <Outlet />
      </div>
    </div>
  );
}

export default DefaultLayout;
