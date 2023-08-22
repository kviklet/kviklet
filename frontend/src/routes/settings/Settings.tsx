import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { solid } from "@fortawesome/fontawesome-svg-core/import.macro";
import BaseSettingsLayout, { Tab } from "./BaseSettingsLayout";
import DatabaseSettings from "./DatabaseSettings";
import UserSettings from "./UserSettings";
import RoleSettings from "./RolesSettings";
import { Link, Route, Routes, useLocation } from "react-router-dom";

export const Settings = () => {
  const location = useLocation();
  console.log(location.pathname);
  const tabs = [
    {
      name: "databases",
      tabContent: (
        <Link to="/settings/databases">
          <div className="flex flex-col border-b border-gray-200">
            <div className="flex flex-row items-center justify-center text-slate-700 text-lg font-bold p-5">
              <FontAwesomeIcon icon={solid("database")} className="mr-2" />
              Databases
            </div>
          </div>
        </Link>
      ),
    },
    {
      name: "users",
      tabContent: (
        <Link to="/settings/users">
          <div className="flex flex-col">
            <div className="flex flex-row items-center justify-center text-slate-700 text-lg font-bold p-5">
              <FontAwesomeIcon icon={solid("user")} className="mr-2" />
              Users
            </div>
          </div>
        </Link>
      ),
    },
    {
      name: "roles",
      tabContent: (
        <Link to="/settings/roles">
          <div className="flex flex-col">
            <div className="flex flex-row items-center justify-center text-slate-700 text-lg font-bold p-5">
              <FontAwesomeIcon icon={solid("users")} className="mr-2" />
              Roles
            </div>
          </div>
        </Link>
      ),
    },
  ];
  return <BaseSettingsLayout tabs={tabs}></BaseSettingsLayout>;
};

export default Settings;
