import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { solid } from "@fortawesome/fontawesome-svg-core/import.macro";
import BaseSettingsLayout, { Tab } from "./BaseSettingsLayout";
import DatabaseSettings from "./DatabaseSettings";
import UserSettings from "./UserSettings";

function Settings() {
  return (
    <BaseSettingsLayout
      tabs={[
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
          renderContent: () => <DatabaseSettings />,
        },
        {
          name: "users",
          tabContent: (
            <div className="flex flex-col">
              <div className="flex flex-row items-center justify-center text-slate-700 text-lg font-bold p-5">
                <FontAwesomeIcon icon={solid("user")} className="mr-2" />
                Users
              </div>
            </div>
          ),
          renderContent: () => <UserSettings></UserSettings>,
        },
      ]}
    ></BaseSettingsLayout>
  );
}

export default Settings;
