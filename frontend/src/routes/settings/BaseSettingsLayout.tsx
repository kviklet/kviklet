import { ReactNode, useState } from "react";
import { Outlet, Route, Routes } from "react-router-dom";
import DatabaseSettings from "./DatabaseSettings";
import UserSettings from "./UserSettings";
import RoleSettings from "./RolesSettings";

const Tab = (props: {
  children: React.ReactNode;
  active: boolean;
  onClick: () => void;
}) => {
  return (
    <button
      onClick={props.onClick}
      className={`flex flex-col border-b border-gray-200 pointer-events-auto ${
        props.active ? "border-b-2 border-blue-500" : ""
      }`}
    >
      {props.children}
    </button>
  );
};

interface TabObject {
  name: string;
  tabContent: ReactNode;
}

interface LayoutProps {
  tabs: TabObject[];
  children?: ReactNode;
}

function SettingsSidebar(props: { children: React.ReactNode }) {
  return (
    <div className="flex flex-col bg-white border-r border-gray-200">
      <div className="flex flex-col">{props.children}</div>
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
      >
        {tab.tabContent}
      </Tab>
    );
  });
  console.log("hellow1");

  return (
    <div className="flex w-screen">
      <div className="mx-auto w-3/4">
        <h1 className="text-2xl font-bold m-5 pl-1.5">Settings</h1>
        <div className="flex w-full">
          <SettingsSidebar>{tabs}</SettingsSidebar>
          <div className="w-full ml-2">
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
