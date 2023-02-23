import React, { useEffect, useState } from "react";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { solid } from "@fortawesome/fontawesome-svg-core/import.macro";
import {
  fetchDatabases,
  Database,
  Connection,
  ConnectionPayload,
  DatabasePayload,
} from "../api/DatasourceApi";

function ConnectionSettings(props: {
  selectedIndex: number | undefined;
  connections: Connection[];
  addConnectionHandler: () => void;
}) {
  return (
    <div className="w-full ml-10">
      <h2 className="text-lg font-bold m-5 pl-1.5">Connection Settings</h2>
      <ul className="list-disc">
        {props.connections.map((connection) => (
          <li className="text-slate-700 text-lg">{connection.displayName}</li>
        ))}
      </ul>
      <button
        className="rounded-md ml-auto mr-5 text-slate-700 p-2.5 border border-slate-500"
        onClick={props.addConnectionHandler}
      >
        Add Connection
      </button>
    </div>
  );
}

function InputField(props: {
  id: string;
  name: string;
  type?: string;
  placeholder?: string;
}) {
  return (
    <div className="flex m-2">
      <label htmlFor={props.id} className="my-auto ml-5 pl-1.5 mr-auto">
        {props.name}
      </label>
      <input
        type={props.type || "text"}
        className="basis-2/3 focus:border-blue-600 my-auto appearance-none border rounded w-full mx-1 py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
        placeholder={props.placeholder || ""}
        name={props.id}
        id={props.id}
      />
    </div>
  );
}
function SubmitButton(props: { text: string }) {
  return (
    <div className="flex mx-2 my-2">
      <button
        className="text-white bg-sky-500 border rounded w-full basis-1/5 py-2 ml-auto active:text-sky-500 active:bg-white"
        type="submit"
      >
        {props.text}
      </button>
    </div>
  );
}

function CreateDatabaseForm(props: {
  handleCreateDatabase: (e: React.SyntheticEvent) => Promise<void>;
}) {
  return (
    <form method="post" onSubmit={props.handleCreateDatabase}>
      <div className="w-2xl shadow p-3 bg-white rounded">
        <InputField id="displayName" name="Name" placeholder="Database Name" />
        <InputField
          id="datasourceType"
          name="Database Engine"
          placeholder="POSTGRESQL"
        />
        <InputField id="hostname" name="Hostname" placeholder="localhost" />
        <InputField id="port" name="Port" type="number" placeholder="5432" />
        <SubmitButton text="Add" />
      </div>
    </form>
  );
}

function CreateConnectionForm(props: {
  handleCreateConnection: (e: React.SyntheticEvent) => Promise<void>;
}) {
  return (
    <form method="post" onSubmit={props.handleCreateConnection}>
      <div className="w-2xl shadow p-3 bg-white rounded">
        <InputField id="displayName" name="Name" placeholder="Database Name" />
        <InputField id="username" name="username" placeholder="readonly" />
        <InputField id="password" name="Password" placeholder="password" />
        <SubmitButton text="Add" />
      </div>
    </form>
  );
}

function Settings() {
  const datasourceUrl = "http://localhost:8080/datasource/";
  const [databases, setDatabases] = useState<Database[]>([]);

  const [selectedIndex, setSelectedIndex] = useState<number | undefined>(
    undefined
  );

  const [showAddDatasourceModal, setShowAddDatasourceModal] =
    useState<boolean>(false);

  const [showAddConnectionModal, setShowAddConnectionModal] =
    useState<boolean>(false);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const databases = await fetchDatabases();
        setDatabases(databases);
        if (databases.length > 0) {
          setSelectedIndex(0);
        }
      } catch (error) {
        console.log("error", error);
      }
    };

    fetchData();
  }, [showAddDatasourceModal]);

  const handleCreateDatabase = async (e: React.SyntheticEvent) => {
    e.preventDefault();
    const form = e.target as HTMLFormElement;
    const formData = new FormData(form);
    const json = Object.fromEntries(formData.entries());
    const payload = DatabasePayload.parse(json);
    console.log(payload);
    await fetch(datasourceUrl, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    });
    setShowAddDatasourceModal(false);
  };

  const handleCreateConnection = async (e: React.SyntheticEvent) => {
    e.preventDefault();
    const form = e.target as HTMLFormElement;
    const formData = new FormData(form);
    const json = Object.fromEntries(formData.entries());
    const payload = ConnectionPayload.parse(json);
    console.log(payload);
    const id = databases[selectedIndex || 0].id;
    await fetch(`${datasourceUrl}${id}/connection`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    });
    setShowAddConnectionModal(false);
  };

  const disableModalOutsideClick = (event: React.SyntheticEvent) => {
    if (event.target === event.currentTarget) {
      setShowAddDatasourceModal(false);
      setShowAddConnectionModal(false);
    }
  };

  return (
    <div>
      <div className="flex max-w-6xl mx-auto">
        <div className="basis-1/5 mr-auto">
          <h2 className="text-2xl font-bold m-5 pl-1.5">Databases</h2>
          <div className="text-center max-h-96 overflow-y-scroll scrollbar-thin scrollbar-track-slate-300 scrollbar-thumb-slate-600 scrollbar-thumb-rounded border-r-slate-300 border-r-2">
            <div className="inline">
              {databases.map((database, index) => (
                //flex items-center rounded-md p-1.5 bg-indigo-600 text-white
                <div
                  className={`flex items-center rounded-md m-5 p-1.5 cursor-pointer hover:bg-sky-500 hover:text-white ${
                    index === selectedIndex ? "bg-sky-500 text-white" : ""
                  }`}
                  onClick={() => setSelectedIndex(index)}
                >
                  <div className="basis-1/2 my-auto text-left">
                    {database.displayName}
                  </div>
                  <div className="basis-1/4 self-end my-2 px-5 text-right">
                    {database.hostname}
                  </div>
                  <div className="hover:text-sky-500 hover:bg-white p-1.5 rounded-md active:bg-red-600 ">
                    <FontAwesomeIcon icon={solid("trash")}></FontAwesomeIcon>
                  </div>
                </div>
              ))}
              <div className="flex items-start">
                <button
                  className="rounded-md ml-auto mr-5 text-slate-700 p-2.5 border border-slate-500"
                  onClick={() => setShowAddDatasourceModal(true)}
                >
                  Add Datasource
                </button>
              </div>
            </div>
          </div>
        </div>
        <ConnectionSettings
          selectedIndex={selectedIndex}
          connections={
            selectedIndex === undefined
              ? []
              : databases[selectedIndex].datasourceConnections
          }
          addConnectionHandler={() => {
            setShowAddConnectionModal(true);
          }}
        />
        {showAddDatasourceModal ? (
          <div
            className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full"
            onClick={disableModalOutsideClick}
          >
            <div className="relative top-20 mx-auto max-w-lg">
              <CreateDatabaseForm handleCreateDatabase={handleCreateDatabase} />
            </div>
          </div>
        ) : (
          ""
        )}
        {showAddConnectionModal ? (
          <div
            className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full"
            onClick={disableModalOutsideClick}
          >
            <div className="relative top-20 mx-auto max-w-lg">
              <CreateConnectionForm
                handleCreateConnection={handleCreateConnection}
              />
            </div>
          </div>
        ) : (
          ""
        )}
      </div>
    </div>
  );
}

export default Settings;
