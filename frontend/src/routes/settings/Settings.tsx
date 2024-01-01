import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { solid } from "@fortawesome/fontawesome-svg-core/import.macro";
import BaseSettingsLayout from "./BaseSettingsLayout";
import React from "react";
import {
  Bars4Icon,
  CircleStackIcon,
  UserCircleIcon,
  UserIcon,
  UsersIcon,
} from "@heroicons/react/20/solid";

export const Settings = () => {
  const tabStyles =
    "flex flex-row items-center justify-left text-slate-700 dark:text-slate-50 text-sm p-1";
  const tabs = [
    {
      name: "databases",
      tabContent: (
        <div className="flex flex-col">
          <div className={tabStyles}>
            <CircleStackIcon className="mr-2 h-6" />
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
            <UserIcon className="mr-2 h-6" />
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
            <UsersIcon className="mr-2 h-6" />
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
    {
      name: "auditlog",
      tabContent: (
        <div className="flex flex-col">
          <div className={tabStyles}>
            <Bars4Icon className="mr-2 h-6"></Bars4Icon>
            Auditlog
          </div>
        </div>
      ),
      link: "/settings/auditlog",
    },
  ];
  return <BaseSettingsLayout tabs={tabs}></BaseSettingsLayout>;
};

export default Settings;
