import { ReactNode, useState } from "react";
import { TableDataCellComponent } from "react-markdown/lib/ast-to-react";

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
  renderContent: () => ReactNode;
}

interface LayoutProps {
  tabs: TabObject[];
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

  const getActiveTab = () => {
    const activeTabObject = props.tabs.find((tab) => tab.name === activeTab);
    if (activeTabObject) {
      return activeTabObject.renderContent();
    }
    return null;
  };

  return (
    <div className="flex w-screen">
      <div className="mx-auto w-3/4">
        <h1 className="text-2xl font-bold m-5 pl-1.5">Settings</h1>
        <div className="flex w-full">
          <SettingsSidebar>{tabs}</SettingsSidebar>
          <div className="w-full ml-2">{getActiveTab()}</div>
        </div>
      </div>
    </div>
  );
};

export default BaseSettingsLayout;
export { Tab };
