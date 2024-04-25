import { useState } from "react";
import { ConnectionPayload, DatabaseType } from "../../../api/DatasourceApi";
import { useIdNamePair } from "./DatabaseSettings";
import InputField from "../../../components/InputField";
import Button from "../../../components/Button";

export default function CreateDatasourceConnectionForm(props: {
  handleCreateConnection: (connection: ConnectionPayload) => Promise<void>;
}) {
  const [username, setUsername] = useState<string>("");
  const [password, setPassword] = useState<string>("");
  const { displayName, id, changeId, changeDisplayName } = useIdNamePair();
  const [description, setDescription] = useState<string>("");
  const [databaseName, setDatabaseName] = useState<string>("");
  const [type, setType] = useState<DatabaseType>(DatabaseType.POSTGRES);
  const [hostname, setHostname] = useState<string>("");
  const [port, setPort] = useState<number>(5432);
  const [additionalJDBCOptions, setAdditionalJDBCOptions] =
    useState<string>("");

  const submit = async (e: React.SyntheticEvent) => {
    e.preventDefault();
    await props.handleCreateConnection({
      displayName,
      id,
      username,
      password,
      description,
      databaseName,
      reviewConfig: {
        numTotalRequired: 1,
      },
      type,
      hostname,
      port,
      connectionType: "DATASOURCE",
      additionalJDBCOptions,
    });
  };

  return (
    <form method="post" onSubmit={(e) => void submit(e)}>
      <div className="flex flex-col w-2xl shadow px-10 py-5 bg-slate-50 border border-slate-300 dark:border-none dark:bg-slate-950 rounded-lg">
        <h1 className="text-lg font-semibold p-2">Add a new connection</h1>
        <InputField
          id="displayName"
          label="Name"
          placeholder="Connection name"
          value={displayName}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            changeDisplayName(e.target.value)
          }
        />
        <InputField
          id="description"
          label="Description"
          placeholder="Provides prod read access with no required reviews"
          value={description}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            setDescription(e.target.value)
          }
        />
        <InputField
          id="id"
          label="Id"
          placeholder="datasource-id"
          value={id}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
            changeId(e.target.value);
          }}
        ></InputField>
        <InputField
          id="databaseName"
          label="Database"
          placeholder="postgres"
          value={databaseName}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            setDatabaseName(e.target.value)
          }
        />
        <InputField
          id="username"
          label="Username"
          placeholder="readonly"
          value={username}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            setUsername(e.target.value)
          }
        />
        <InputField
          id="password"
          label="Password"
          type="passwordlike"
          placeholder="password"
          value={password}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            setPassword(e.target.value)
          }
        />
        <InputField
          id="additionalJDBCOptions"
          label="Additional JDBC Options"
          placeholder="?ssl=true"
          value={additionalJDBCOptions}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            setAdditionalJDBCOptions(e.target.value)
          }
        />
        <div className="flex flex-row justify-between">
          <div className="w-full">
            <InputField
              id="hostname"
              label="Hostname"
              placeholder="localhost"
              value={hostname}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                setHostname(e.target.value)
              }
            />
          </div>
          <div className="w-3/5">
            <InputField
              id="port"
              label="Port"
              type="number"
              placeholder="5432"
              value={port}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                setPort(parseInt(e.target.value))
              }
            />
          </div>
        </div>
        <div className="flex items-center justify-between pl-5 pr-2">
          <label
            htmlFor="type"
            className="block text-sm font-medium leading-6 text-slate-700 dark:text-slate-200"
          >
            Database type
          </label>
          <select
            id="type"
            name="type"
            className="basis-2/5 mt-2 block appearance-none rounded-md text-sm transition-colors border border-slate-300
              dark:bg-slate-900 hover:border-slate-400 ring-none focus-visible:outline-none
              focus-outline-none dark:focus:border-gray-500 dark:focus:hover:border-slate-700 dark:border-slate-700 focus:border-indigo-600 focus:hover:border-indigo-600 dark:hover:border-slate-600 dark:hover:focus:border-gray-500
              py-2 pl-2 pr-10 dark:text-slate-200"
            defaultValue="POSTGRES"
            onChange={(e) => setType(e.target.value as DatabaseType)}
          >
            {Object.values(DatabaseType).map((type) => (
              <option>{type}</option>
            ))}
          </select>
        </div>
        <div className="flex justify-end mx-2 mt-4 mb-1">
          <Button type="submit" className="px-8 text-sm">
            Add
          </Button>
        </div>
      </div>
    </form>
  );
}
