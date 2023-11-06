import { useEffect, useState } from "react";
import {
  ConnectionPayload,
  ConnectionResponse,
  DatabasePayload,
  DatabaseResponse,
  PatchConnectionPayload,
  addConnection,
  addDatabase,
  fetchDatabases,
  patchConnection,
  removeDatabase,
} from "../../api/DatasourceApi";
import Button from "../../components/Button";
import InputField from "../../components/InputField";
import Modal from "../../components/Modal";
import Spinner from "../../components/Spinner";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { solid } from "@fortawesome/fontawesome-svg-core/import.macro";
import DeleteConfirm from "../../components/DeleteConfirm";
import React from "react";

const useIdNamePair = () => {
  const [displayName, setDisplayName] = useState<string>("");
  const [id, setId] = useState<string>("");
  const [idManuallyChanged, setIdManuallyChanged] = useState<boolean>(false);

  const changeId = (id: string) => {
    setId(id);
    setIdManuallyChanged(true);
  };

  function formatId(input: string): string {
    const lowerCasedString = input.toLowerCase();
    return lowerCasedString.replace(/\s+/g, "-");
  }

  const changeDisplayName = (name: string) => {
    setDisplayName(name);
    if (!idManuallyChanged) {
      setId(formatId(name));
    }
  };

  return { displayName, id, changeId, changeDisplayName };
};

function CreateDatabaseForm(props: {
  handleCreateDatabase: (database: DatabasePayload) => Promise<void>;
}) {
  const [datasourceType, setDatasourceType] = useState<string>("");
  const [hostname, setHostname] = useState<string>("");
  const [port, setPort] = useState<number>(0);
  const { displayName, id, changeId, changeDisplayName } = useIdNamePair();

  const handleCreateDatabase = async (e: React.SyntheticEvent) => {
    e.preventDefault();
    await props.handleCreateDatabase({
      displayName,
      id,
      datasourceType,
      hostname,
      port,
    });
  };

  return (
    <form method="post" onSubmit={(e) => void handleCreateDatabase(e)}>
      <div className="w-2xl shadow p-3 bg-slate-50 dark:bg-slate-950 rounded">
        <InputField
          id="displayName"
          name="Name"
          placeholder="Database Name"
          value={displayName}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            changeDisplayName(e.target.value)
          }
        />
        <InputField
          id="id"
          name="Id"
          placeholder="database-id"
          value={id}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            changeId(e.target.value)
          }
        />
        <div className="flex m-2">
          <label
            htmlFor="datasourceType"
            className="my-auto text-sm font-medium text-slate-700 dark:text-slate-200 ml-2 pl-1.5 mr-auto"
          >
            Database Engine
          </label>
          <select
            className="basis-2/3 px-2 py-2 rounded-md border 
              border-slate-300 dark:bg-slate-900 hover:border-slate-400 focus:border-indigo-600 focus:hover:border-indigo-600
        focus:outline-none dark:hover:border-slate-600 dark:hover:focus:border-gray-500 dark:border-slate-700
         dark:focus:border-gray-500 text-xs transition-colors indent-0"
            id="datasourceType"
            defaultValue="POSTGRESQL"
            value={datasourceType}
            onChange={(e: React.ChangeEvent<HTMLSelectElement>) =>
              setDatasourceType(e.target.value)
            }
          >
            <option className="bg-slate-200 dark:bg-slate-800 text-slate-900 dark:text-slate-100">
              POSTGRESQL
            </option>
            <option className="bg-slate-200 dark:bg-slate-800 text-slate-900 dark:text-slate-100">
              MYSQL
            </option>
          </select>
        </div>
        <InputField
          id="hostname"
          name="Hostname"
          placeholder="localhost"
          value={hostname}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            setHostname(e.target.value)
          }
        />
        <InputField
          id="port"
          name="Port"
          type="number"
          placeholder="5432"
          value={port}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            setPort(parseInt(e.target.value))
          }
        />
        <div className="flex justify-end mx-2 my-2">
          <Button type="submit">Add</Button>
        </div>
      </div>
    </form>
  );
}

const useDatasources = () => {
  const [datasources, setDatasources] = useState<DatabaseResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  useEffect(() => {
    async function request() {
      const apiDatasources = await fetchDatabases();
      setDatasources(apiDatasources);
      setLoading(false);
    }
    void request();
  }, []);

  const createDatabase = async (database: DatabasePayload) => {
    const newDatabase = await addDatabase(database);
    setDatasources([...datasources, newDatabase]);
  };

  const createConnection = async (
    datasourceId: string,
    connection: ConnectionPayload,
  ) => {
    const newConnection = await addConnection(connection, datasourceId);
    const newDatasources = datasources.map((datasource) => {
      if (datasource.id === datasourceId) {
        return {
          ...datasource,
          datasourceConnections: [
            ...datasource.datasourceConnections,
            newConnection,
          ],
        };
      }
      return datasource;
    });
    setDatasources(newDatasources);
  };

  const editConnection = async (
    datasourceId: string,
    connectionId: string,
    connection: PatchConnectionPayload,
  ) => {
    const updatedConnection = await patchConnection(
      connection,
      datasourceId,
      connectionId,
    );
    const newDatasources = datasources.map((datasource) => {
      if (datasource.id === datasourceId) {
        return {
          ...datasource,
          datasourceConnections: datasource.datasourceConnections.map(
            (connection) => {
              if (connection.id === connectionId) {
                return updatedConnection;
              }
              return connection;
            },
          ),
        };
      }
      return datasource;
    });
    setDatasources(newDatasources);
  };

  const deleteDatasource = async (id: string) => {
    await removeDatabase(id);
    const newDatasources = datasources.filter((datasource) => {
      return datasource.id !== id;
    });
    setDatasources(newDatasources);
  };

  return {
    datasources,
    createDatabase,
    loading,
    createConnection,
    deleteDatasource,
    editConnection,
  };
};

function CreateConnectionForm(props: {
  handleCreateConnection: (connection: ConnectionPayload) => Promise<void>;
}) {
  const [username, setUsername] = useState<string>("");
  const [password, setPassword] = useState<string>("");
  const { displayName, id, changeId, changeDisplayName } = useIdNamePair();
  const [description, setDescription] = useState<string>("");
  const [databaseName, setDatabaseName] = useState<string>("");

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
    });
  };

  return (
    <form method="post" onSubmit={(e) => void submit(e)}>
      <div className="w-2xl shadow p-3 bg-slate-50 border border-slate-300 dark:border-none dark:bg-slate-950 rounded">
        <InputField
          id="displayName"
          name="Name"
          placeholder="Connection Name"
          value={displayName}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            changeDisplayName(e.target.value)
          }
        />
        <InputField
          id="description"
          name="Description"
          placeholder="Provides prod read access with no required reviews"
          value={description}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            setDescription(e.target.value)
          }
        />
        <InputField
          id="databaseName"
          name="database"
          placeholder="postgres"
          value={databaseName}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            setDatabaseName(e.target.value)
          }
        />
        <InputField
          id="id"
          name="Id"
          placeholder="datasource-id"
          value={id}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
            changeId(e.target.value);
          }}
        ></InputField>
        <InputField
          id="username"
          name="username"
          placeholder="readonly"
          value={username}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            setUsername(e.target.value)
          }
        />
        <InputField
          id="password"
          name="Password"
          type="passwordlike"
          placeholder="password"
          value={password}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            setPassword(e.target.value)
          }
        />
        <div className="flex justify-end mx-2 my-2">
          <Button type="submit">Add</Button>
        </div>
      </div>
    </form>
  );
}

function SingleConnectionSettings(props: {
  connection: ConnectionResponse;
  editConnectionHandler: (
    connectionId: string,
    connection: PatchConnectionPayload,
  ) => Promise<void>;
}) {
  const [numTotalRequired, setNumTotalRequired] = useState<number>(
    props.connection.reviewConfig.numTotalRequired,
  );
  const [databaseName, setDatabaseName] = useState<string>(
    props.connection.databaseName || "",
  );
  //useEffect(() => {
  //  setNumTotalRequired(props.connection.reviewConfig.numTotalRequired);
  //}, [props.connection.reviewConfig.numTotalRequired]);
  const [showCheck, setShowCheck] = useState<boolean>(false);

  const submit = async () => {
    await props.editConnectionHandler(props.connection.id, {
      databaseName,
      reviewConfig: {
        numTotalRequired,
      },
    });
    setShowCheck(false);
  };

  return (
    <div className="shadow-md border border-slate-200 bg-slate-50 my-4 mx-2 px-4 py-4 dark:bg-slate-900 dark:border dark:border-slate-700 rounded-md transition-colors">
      <div className="flex justify-between">
        <div className="font-medium text-md">
          {props.connection.displayName}
        </div>
        <div className="font-mono text-slate-300 text-sm">
          {props.connection.shortUsername + "..."}
        </div>
      </div>
      <div className="m-2">
        <div className="text-slate-500 text-sm mb-2 dark:text-slate-400">
          {props.connection.description}
        </div>
        <div className="flex justify-between text-sm">
          <label
            htmlFor="database-name"
            className="mr-auto dark:text-slate-400"
          >
            Database Name:
          </label>
          <input
            type="database-name"
            value={databaseName}
            onChange={(e) => {
              setDatabaseName(e.target.value);
              setShowCheck(true);
            }}
            className="focus:border-slate-500 focus:hover:border-slate-500 my-auto appearance-none border border-slate-200 hover:border-slate-300 rounded mx-1 py-2 px-3 text-slate-600 leading-tight focus:outline-none focus:shadow-outline dark:bg-slate-900 dark:border-slate-700 dark:hover:border-slate-600 dark:focus:border-slate-500 dark:focus:hover:border-slate-500 transition-colors dark:text-slate-50"
          ></input>
        </div>
        <div className="flex justify-between text-sm">
          <label htmlFor="number" className="mr-auto dark:text-slate-400">
            Number of required reviews:
          </label>
          <input
            type="number"
            value={numTotalRequired}
            onChange={(e) => {
              setNumTotalRequired(parseInt(e.target.value));
              setShowCheck(true);
            }}
            className="focus:border-slate-500 focus:hover:border-slate-500 my-auto appearance-none border border-slate-200 hover:border-slate-300 rounded mx-1 py-2 px-3 text-slate-600 leading-tight focus:outline-none focus:shadow-outline dark:bg-slate-900 dark:border-slate-700 dark:hover:border-slate-600 dark:focus:border-slate-500 dark:focus:hover:border-slate-500 transition-colors dark:text-slate-50"
          ></input>
        </div>
        <button
          onClick={() => void submit()}
          className={`text-green-600 ml-2 hover:text-green-900 transition-colors ${
            showCheck ? "visible" : "invisible"
          }`}
        >
          <FontAwesomeIcon icon={solid("check")} />
        </button>
      </div>
    </div>
  );
}

function ConnectionSettings(props: {
  selectedIndex: number | undefined;
  connections: ConnectionResponse[];
  addConnectionHandler: () => void;
  editConnectionHandler: (
    connectionId: string,
    connection: PatchConnectionPayload,
  ) => Promise<void>;
}) {
  console.log("Selected Index" + props.selectedIndex);
  console.log(props.connections);
  return (
    <div className="flex flex-col max-h-[calc(100vh-theme(spacing.52))] w-full border-l dark:border-slate-700  dark:bg-slate-950">
      <div className="pl-8 text-lg">Connections</div>
      <div className="flex-grow overflow-hidden">
        <div className="pl-8 h-full flex flex-col justify-between">
          <div className="overflow-y-auto flex-grow">
            {props.connections.map((connection) => (
              <SingleConnectionSettings
                key={connection.id} // Assuming each connection has a unique 'id'
                connection={connection}
                editConnectionHandler={props.editConnectionHandler}
              />
            ))}
          </div>
          <Button className="ml-auto my-2" onClick={props.addConnectionHandler}>
            Add Connection
          </Button>
        </div>
      </div>
    </div>
  );
}

const DatabaseChooser = (props: {
  databases: DatabaseResponse[];
  setSelectedIndex: (index: number) => void;
  selectedIndex: number | undefined;
  setShowAddDatasourceModal: (show: boolean) => void;
  handleDeleteDatabase: (database: DatabaseResponse) => void;
}) => {
  return (
    <div className="border-slate-200 bg-slate-50 mx-2 px-4 dark:bg-slate-950 dark:border-none rounded-md">
      <div className="border-b-slate-300 dark:border-b-slate-700 border-b">
        <div className="text-center max-h-96 w-72 overflow-y-scroll pr-4">
          {props.databases.map((database, index) => (
            <div
              className={`flex items-center rounded-md mb-3 transition-colors px-3 cursor-pointer max-h-12 ${
                index === props.selectedIndex
                  ? "bg-slate-200 dark:bg-slate-900"
                  : "hover:bg-slate-100 dark:bg-slate-950 dark:hover:bg-slate-900"
              }`}
              onClick={() => props.setSelectedIndex(index)}
            >
              <div className="basis-1/2 my-auto text-left truncate">
                {database.displayName}
              </div>
              <div className="basis-1/2 ml-2 mr-3 text-sm self-end my-2 text-right text-slate-500 dark:text-slate-400 text-clip grow-0 overflow-hidden whitespace-nowrap">
                {database.hostname}
              </div>
              <button
                onClick={() => props.handleDeleteDatabase(database)}
                className="text-slate-500 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-200 text-xs transition-colors"
              >
                <FontAwesomeIcon icon={solid("trash")} />
              </button>
            </div>
          ))}
        </div>
      </div>
      <div>
        <div className="flex items-start">
          <Button
            className="ml-auto m-2"
            onClick={() => props.setShowAddDatasourceModal(true)}
          >
            Add Datasource
          </Button>
        </div>
      </div>
    </div>
  );
};

const DatabaseSettings = () => {
  const [selectedIndex, setSelectedIndex] = useState<number | undefined>(
    undefined,
  );
  const {
    datasources,
    createDatabase,
    loading,
    createConnection,
    deleteDatasource,
    editConnection,
  } = useDatasources();

  useEffect(() => {
    if (datasources.length > 0 && selectedIndex === undefined) {
      setSelectedIndex(0);
    }
    if (datasources.length === 0) {
      setSelectedIndex(undefined);
    }
    if (selectedIndex && selectedIndex >= datasources.length - 1) {
      setSelectedIndex(datasources.length - 1);
    }
  }, [loading]);

  const [showAddDatasourceModal, setShowAddDatasourceModal] =
    useState<boolean>(false);

  const [showAddConnectionModal, setShowAddConnectionModal] =
    useState<boolean>(false);

  const [showDeleteDatasourceModal, setShowDeleteDatasourceModal] =
    useState<boolean>(false);

  const [selectedDatasource, setSelectedDatasource] =
    useState<DatabaseResponse | null>(null);

  const handleCreateDatabase = async (database: DatabasePayload) => {
    await createDatabase(database);
    setShowAddDatasourceModal(false);
  };

  const handleCreateConnection = async (connection: ConnectionPayload) => {
    if (selectedIndex === undefined) {
      return;
    }
    await createConnection(datasources[selectedIndex].id, connection);
    setShowAddConnectionModal(false);
  };

  const deleteDatabase = (database: DatabaseResponse) => {
    setSelectedDatasource(database);
    setShowDeleteDatasourceModal(true);
  };

  return (
    <div className="h-full">
      {loading ? (
        <Spinner />
      ) : (
        <div className="">
          <div className="flex w-full dark:bg-slate-950 h-full">
            <DatabaseChooser
              databases={datasources}
              setSelectedIndex={setSelectedIndex}
              selectedIndex={selectedIndex}
              setShowAddDatasourceModal={setShowAddDatasourceModal}
              handleDeleteDatabase={deleteDatabase}
            ></DatabaseChooser>
            <ConnectionSettings
              selectedIndex={selectedIndex}
              connections={
                selectedIndex === undefined
                  ? []
                  : datasources[selectedIndex].datasourceConnections
              }
              addConnectionHandler={() => {
                setShowAddConnectionModal(true);
              }}
              editConnectionHandler={async (connectionId, connection) => {
                await editConnection(
                  datasources[selectedIndex!].id,
                  connectionId,
                  connection,
                );
              }}
            />
          </div>
          {showAddDatasourceModal && (
            <Modal setVisible={setShowAddDatasourceModal}>
              <CreateDatabaseForm handleCreateDatabase={handleCreateDatabase} />
            </Modal>
          )}
          {showAddConnectionModal && (
            <Modal setVisible={setShowAddConnectionModal}>
              <CreateConnectionForm
                handleCreateConnection={handleCreateConnection}
              />
            </Modal>
          )}
          {showDeleteDatasourceModal && (
            <Modal setVisible={setShowDeleteDatasourceModal}>
              <DeleteConfirm
                title="Delete Datasource"
                message="Are you sure you want to delete this datasource?"
                onConfirm={async () => {
                  selectedDatasource &&
                    (await deleteDatasource(selectedDatasource.id));
                  setShowDeleteDatasourceModal(false);
                }}
                onCancel={() => setShowDeleteDatasourceModal(false)}
              />
            </Modal>
          )}
        </div>
      )}
    </div>
  );
};

export default DatabaseSettings;

export { useDatasources };
