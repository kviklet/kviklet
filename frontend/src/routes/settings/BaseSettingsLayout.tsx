import { ReactNode, useState } from "react";
import { Link, Outlet, Route, Routes } from "react-router-dom";
import ConnectionSettings from "./connection/ConnectionSettings";
import UserSettings from "./UserSettings";
import RoleSettings from "./RolesSettings";
import React from "react";
import ProfileSettings from "./ProfileSettings";
import ConnectionDetails from "./connection/ConnectionDetails";
import RoleDetailsView from "./RoleDetailsView";

const Tab = (props: {
  children: React.ReactNode;
  active: boolean;
  onClick: () => void;
  link: string;
}) => {
  return (
    <Link to={props.link}>
      <div
        onClick={props.onClick}
        className={
          "rounded pr-2 hover:bg-slate-100 dark:hover:bg-slate-900 " +
            (props.active &&
              "rounded bg-slate-200 hover:bg-slate-200 dark:bg-slate-900") || ""
        }
      >
        {props.children}
      </div>
    </Link>
  );
};

interface TabObject {
  name: string;
  tabContent: ReactNode;
  link: string;
}

interface LayoutProps {
  tabs: TabObject[];
  children?: ReactNode;
}

function SettingsSidebar(props: { children: React.ReactNode }) {
  return (
    <div className="mx-2 flex flex-col">
      <div className="flex flex-col divide-y-8 divide-slate-50 dark:divide-slate-950">
        {props.children}
      </div>
    </div>
  );
}

const BaseSettingsLayout = (props: LayoutProps) => {
  const [activeTab, setActiveTab] = useState<string>("databases");

  const tabs = props.tabs.map((tab) => {
    return (
      <Tab
        active={activeTab === tab.name}
        onClick={() => setActiveTab(tab.name)}
        link={tab.link}
        key={tab.name}
      >
        {tab.tabContent}
      </Tab>
    );
  });

  return (
    <div className="h-full w-screen dark:bg-slate-950">
      <div className="mb-3 border-b border-slate-300 dark:border-slate-700">
        <h1 className="m-5 mx-auto w-3/4 pl-1.5 text-xl">Settings</h1>
      </div>
      <div className="mx-auto h-full w-3/4">
        <div className="flex h-full w-full pt-4">
          <SettingsSidebar>{tabs}</SettingsSidebar>
          <div className="ml-2 h-full w-full">
            <Routes>
              <Route path="/*" element={<ConnectionSettings />} />
              <Route path="databases" element={<ConnectionSettings />} />
              <Route
                path="connections/:connectionId"
                element={<ConnectionDetails />}
              />
              <Route path="users" element={<UserSettings />} />
              <Route path="roles" element={<RoleSettings />} />
              <Route path="/roles/:roleId" element={<RoleDetailsView />} />
              <Route path="profile" element={<ProfileSettings />} />
            </Routes>
            <Outlet></Outlet>
          </div>
        </div>
      </div>
    </div>
  );
};

export default BaseSettingsLayout;
export { Tab };
