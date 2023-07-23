import { useEffect, useState } from "react";
import {
  ConnectionPayload,
  ConnectionResponse,
  DatabasePayload,
  DatabaseResponse,
  addConnection,
  addDatabase,
  fetchDatabases,
} from "../../api/DatasourceApi";
import Button from "../../components/Button";
import InputField from "../../components/InputField";
import Modal from "../../components/Modal";
import Spinner from "../../components/Spinner";

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

  return { datasources, createDatabase, loading, createConnection };
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

function ConnectionSettings(props: {
  selectedIndex: number | undefined;
  connections: ConnectionResponse[];
  addConnectionHandler: () => void;
}) {
  return (
    <div className=" border-l-2 flex flex-col min-h-full w-full">
      <div className="pl-8 text-lg font-bold my-5">Connections</div>
      <div className="pl-8 flex flex-col h-96 justify-between">
        <div className="">
          {props.connections.map((connection) => (
            <div className="text-slate-700 border-b mb-7 text-lg">
              <div className="flex justify-between">
                <div className="font-medium">{connection.displayName}</div>
                <div className="font-mono text-slate-300 text-sm">
                  {connection.shortUsername + "..."}
                </div>
              </div>
              <div className="m-2">
                <div className="text-slate-500 text-sm mb-2">
                  {connection.description}
                </div>
                <div className="flex justify-between text-sm">
                  <label htmlFor="number" className="mr-auto">
                    Number of required reviews:
                  </label>
                  <input
                    type="number"
                    value="2"
                    className="focus:border-blue-600 my-auto appearance-none border rounded mx-1 py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
                  ></input>
                </div>
              </div>
            </div>
          ))}
        </div>
        <button
          className="rounded-md ml-auto mr-5 text-slate-700 p-2.5 border border-slate-500"
          onClick={props.addConnectionHandler}
        >
          Add Connection
        </button>
      </div>
    </div>
  );
}

const DatabaseChooser = (props: {
  databases: DatabaseResponse[];
  setSelectedIndex: (index: number) => void;
  selectedIndex: number | undefined;
  setShowAddDatasourceModal: (show: boolean) => void;
}) => {
  return (
    <div className="m-2 p-1">
      <div className="border-b-slate-300 border-b py-2">
        <div className="text-center max-h-96 w-72  overflow-y-scroll scrollbar-thin scrollbar-track-slate-100 scrollbar-thumb-slate-300 scrollbar-thumb-rounded">
          {props.databases.map((database, index) => (
            //flex items-center rounded-md p-1.5 bg-indigo-600 text-white
            <div
              className={`flex items-center rounded-md my-3 mx-2 p-1.5 cursor-pointer hover:bg-sky-500 hover:text-white ${
                index === props.selectedIndex ? "bg-sky-500 text-white" : ""
              }`}
              onClick={() => props.setSelectedIndex(index)}
            >
              <div className="basis-1/2 my-auto text-left truncate">
                {database.displayName}
              </div>
              <div
                className={`basis-1/2 self-end my-2 text-right ${
                  index === props.selectedIndex
                    ? "text-white"
                    : "text-slate-500"
                }`}
              >
                {database.hostname}
              </div>
            </div>
          ))}
        </div>
      </div>
      <div>
        <div className="flex items-start">
          <button
            className="rounded-md ml-auto m-2 text-slate-700 p-2.5 border border-slate-500"
            onClick={() => props.setShowAddDatasourceModal(true)}
          >
            Add Datasource
          </button>
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
  const { datasources, createDatabase, loading, createConnection } =
    useDatasources();

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

  return (
    <div>
      {loading ? (
        <Spinner />
      ) : (
        <div>
          <div className="flex w-full">
            <DatabaseChooser
              databases={datasources}
              setSelectedIndex={setSelectedIndex}
              selectedIndex={selectedIndex}
              setShowAddDatasourceModal={setShowAddDatasourceModal}
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
        </div>
      )}
    </div>
  );
};

export default DatabaseSettings;
export { useDatasources };
