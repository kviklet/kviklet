import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { solid } from "@fortawesome/fontawesome-svg-core/import.macro";
import BaseSettingsLayout, { Tab } from "./BaseSettingsLayout";
import { Link, Route, Routes, useLocation } from "react-router-dom";

export const Settings = () => {
  const location = useLocation();
  console.log(location.pathname);
  const tabs = [
    {
      name: "databases",
      tabContent: (
        <div className="flex flex-col border-b border-gray-200">
          <div className="flex flex-row items-center justify-center text-slate-700 text-lg font-bold p-5">
            <FontAwesomeIcon icon={solid("database")} className="mr-2" />
            Databases
          </div>
        </div>
      ),
      link: "/settings/databases",
    },
    {
      name: "users",
      tabContent: (
        <div className="flex flex-col border-b border-gray-200">
          <div className="flex flex-row items-center justify-center text-slate-700 text-lg font-bold p-5">
            <FontAwesomeIcon icon={solid("user")} className="mr-2" />
            Users
          </div>
        </div>
      ),
      link: "/settings/users",
    },
    {
      name: "roles",
      tabContent: (
        <div className="flex flex-col border-b border-gray-200">
          <div className="flex flex-row items-center justify-center text-slate-700 text-lg font-bold p-5">
            <FontAwesomeIcon icon={solid("users")} className="mr-2" />
            Roles
          </div>
        </div>
      ),
      link: "/settings/roles",
    },
  ];
  return <BaseSettingsLayout tabs={tabs}></BaseSettingsLayout>;
};

export default Settings;
