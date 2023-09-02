import { ReactNode, useState } from "react";
import { Link, Outlet, Route, Routes } from "react-router-dom";
import DatabaseSettings from "./DatabaseSettings";
import UserSettings from "./UserSettings";
import RoleSettings from "./RolesSettings";

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
          "pr-2 rounded hover:bg-slate-100 dark:hover:bg-slate-900 " +
            (props.active && "bg-slate-200 dark:bg-slate-900 rounded") || ""
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
    <div className="flex flex-col bg-slate-50 dark:bg-slate-950 mx-2">
      <div className="flex flex-col divide-y-8 divide-slate-50 dark:divide-slate-950">
        {props.children}
      </div>
    </div>
  );
}

const BaseSettingsLayout = (props: LayoutProps) => {
  const [activeTab, setActiveTab] = useState<String>("databases");

  const tabs = props.tabs.map((tab) => {
    return (
      <Tab
        active={activeTab === tab.name}
        onClick={() => setActiveTab(tab.name)}
        link={tab.link}
      >
        {tab.tabContent}
      </Tab>
    );
  });

  return (
    <div className="w-screen h-screen">
      <div className="border-b border-slate-300 mb-3 dark:border-slate-700">
        <h1 className="text-xl pl-1.5 w-3/4 m-5 mx-auto">Settings</h1>
      </div>
      <div className="mx-auto w-3/4">
        <div className="flex w-full">
          <SettingsSidebar>{tabs}</SettingsSidebar>
          <div className="w-full ml-2 h-full">
            <Routes>
              <Route path="/*" element={<DatabaseSettings />} />
              <Route path="databases" element={<DatabaseSettings />} />
              <Route path="users" element={<UserSettings />} />
              <Route path="roles" element={<RoleSettings />} />
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
