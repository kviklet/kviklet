import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { solid } from "@fortawesome/fontawesome-svg-core/import.macro";
import BaseSettingsLayout from "./BaseSettingsLayout";
import React from "react";
import { UserCircleIcon } from "@heroicons/react/20/solid";

export const Settings = () => {
  const tabStyles =
    "flex flex-row items-center justify-left text-slate-700 dark:text-slate-50 text-sm p-1";
  const tabs = [
    {
      name: "databases",
      tabContent: (
        <div className="flex flex-col">
          <div className={tabStyles}>
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
        <div className="flex flex-col">
          <div className={tabStyles}>
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
        <div className="flex flex-col">
          <div className={tabStyles}>
            <FontAwesomeIcon icon={solid("users")} className="mr-2" />
            Roles
          </div>
        </div>
      ),
      link: "/settings/roles",
    },
    {
      name: "profile",
      tabContent: (
        <div className="flex flex-col">
          <div className={tabStyles}>
            <UserCircleIcon className="mr-2 h-6"></UserCircleIcon>
            Profile
          </div>
        </div>
      ),
      link: "/settings/profile",
    },
  ];
  return <BaseSettingsLayout tabs={tabs}></BaseSettingsLayout>;
};

export default Settings;
