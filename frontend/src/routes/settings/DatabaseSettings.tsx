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

function CreateDatabaseForm(props: {
  handleCreateDatabase: (database: DatabasePayload) => Promise<void>;
}) {
  const [displayName, setDisplayName] = useState<string>("");
  const [datasourceType, setDatasourceType] = useState<string>("");
  const [hostname, setHostname] = useState<string>("");
  const [port, setPort] = useState<number>(0);

  const handleCreateDatabase = async (e: React.SyntheticEvent) => {
    e.preventDefault();
    await props.handleCreateDatabase({
      displayName,
      datasourceType,
      hostname,
      port,
    });
  };

  return (
    <form method="post" onSubmit={handleCreateDatabase}>
      <div className="w-2xl shadow p-3 bg-white rounded">
        <InputField
          id="displayName"
          name="Name"
          placeholder="Database Name"
          value={displayName}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            setDisplayName(e.target.value)
          }
        />
        <InputField
          id="datasourceType"
          name="Database Engine"
          placeholder="POSTGRESQL"
          value={datasourceType}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            setDatasourceType(e.target.value)
          }
        />
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
    request();
  }, []);

  const createDatabase = async (database: DatabasePayload) => {
    const newDatabase = await addDatabase(database);
    setDatasources([...datasources, newDatabase]);
  };

  const createConnection = async (
    datasourceId: string,
    connection: ConnectionPayload
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
    connection: PatchConnectionPayload
  ) => {
    const updatedConnection = await patchConnection(
      connection,
      datasourceId,
      connectionId
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
            }
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
  const [displayName, setDisplayName] = useState<string>("");
  const [username, setUsername] = useState<string>("");
  const [password, setPassword] = useState<string>("");
  const submit = async (e: React.SyntheticEvent) => {
    e.preventDefault();
    await props.handleCreateConnection({
      displayName,
      username,
      password,
      reviewConfig: {
        numTotalRequired: 1,
      },
    });
  };

  return (
    <form method="post" onSubmit={submit}>
      <div className="w-2xl shadow p-3 bg-white rounded">
        <InputField
          id="displayName"
          name="Name"
          placeholder="Database Name"
          value={displayName}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            setDisplayName(e.target.value)
          }
        />
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
    connection: PatchConnectionPayload
  ) => Promise<void>;
}) {
  const [displayName, setDisplayName] = useState<string>(
    props.connection.displayName
  );
  const [username, setUsername] = useState<string>(
    props.connection.shortUsername
  );
  const [password, setPassword] = useState<string>("");
  const [numTotalRequired, setNumTotalRequired] = useState<number>(
    props.connection.reviewConfig.numTotalRequired
  );
  const [showCheck, setShowCheck] = useState<boolean>(false);

  const submit = async () => {
    await props.editConnectionHandler(props.connection.id, {
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
          onClick={submit}
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
    connection: PatchConnectionPayload
  ) => Promise<void>;
}) {
  return (
    <div className=" border-l-2 dark:border-slate-700  dark:bg-slate-950 flex flex-col min-h-full w-full">
      <div className="pl-8 text-lg my-5">Connections</div>
      <div className="pl-8 flex flex-col h-96 justify-between">
        <div className="">
          {props.connections.map((connection) => (
            <SingleConnectionSettings
              connection={connection}
              editConnectionHandler={props.editConnectionHandler}
            />
          ))}
        </div>
        <Button className="ml-auto" onClick={props.addConnectionHandler}>
          Add Connection
        </Button>
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
        <div className="text-center max-h-96 w-72 overflow-y-scroll scrollbar-thin scrollbar-track-slate-100 scrollbar-thumb-slate-300 scrollbar-thumb-rounded">
          {props.databases.map((database, index) => (
            <div
              className={`flex items-center rounded-md mb-3 transition-colors px-3 cursor-pointer ${
                index === props.selectedIndex
                  ? "bg-slate-200 dark:bg-slate-900"
                  : "hover:bg-slate-100 dark:bg-slate-950 dark:hover:bg-slate-900"
              }`}
              onClick={() => props.setSelectedIndex(index)}
            >
              <div className="basis-1/2 my-auto text-left truncate">
                {database.displayName}
              </div>
              <div className="basis-1/2 self-end my-2 text-righttext-slate-500 dark:text-slate-400">
                {database.hostname}
              </div>
              <button
                onClick={() => props.handleDeleteDatabase(database)}
                className="text-slate-500 hover:text-slate-900 dark:text-slate-400 text-xs"
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
  const datasourceUrl = "http://localhost:8080/datasources/";
  const [selectedIndex, setSelectedIndex] = useState<number | undefined>(
    undefined
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

  const deleteDatabase = async (database: DatabaseResponse) => {
    setSelectedDatasource(database);
    setShowDeleteDatasourceModal(true);
  };

  return (
    <div className="h-full">
      {loading ? (
        <Spinner />
      ) : (
        <div>
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
                  datasources[selectedIndex!!].id,
                  connectionId,
                  connection
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
                onConfirm={() => {
                  selectedDatasource && deleteDatasource(selectedDatasource.id);
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
