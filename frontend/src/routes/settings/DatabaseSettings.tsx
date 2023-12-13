import { useEffect, useState } from "react";
import {
  ConnectionPayload,
  ConnectionResponse,
  DatabaseType,
  PatchConnectionPayload,
  addConnection,
  getConnections,
  patchConnection,
} from "../../api/DatasourceApi";
import Button from "../../components/Button";
import InputField from "../../components/InputField";
import Modal from "../../components/Modal";
import Spinner from "../../components/Spinner";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { solid } from "@fortawesome/fontawesome-svg-core/import.macro";
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

const useConnections = () => {
  const [connections, setConnections] = useState<ConnectionResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  useEffect(() => {
    async function request() {
      const response = await getConnections();
      setConnections(response);
      setLoading(false);
    }
    void request();
  }, []);

  const createConnection = async (connection: ConnectionPayload) => {
    const newConnection = await addConnection(connection);
    const newConnections = [...connections, newConnection];
    setConnections(newConnections);
  };

  const editConnection = async (
    connectionId: string,
    connection: PatchConnectionPayload,
  ) => {
    const updatedConnection = await patchConnection(connection, connectionId);
    const newConnections = connections.map((connection) => {
      if (connection.id === updatedConnection.id) {
        return updatedConnection;
      }
      return connection;
    });
    setConnections(newConnections);
  };

  return {
    loading,
    connections,
    createConnection,
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
  const [type, setType] = useState<DatabaseType>(DatabaseType.POSTGRES);
  const [hostname, setHostname] = useState<string>("localhost");
  const [port, setPort] = useState<number>(5432);

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
        <div className="flex justify-between">
          <div className="w-1/2">
            <InputField
              id="hostname"
              name="Hostname"
              placeholder="localhost"
              value={hostname}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                setHostname(e.target.value)
              }
            />
          </div>
          <div className="w-1/2">
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
          </div>
        </div>
        <div className="flex justify-between">
          <div className="w-1/2">
            <label
              htmlFor="type"
              className="block text-sm font-medium leading-6 text-slate-900 dark:text-slate-50"
            >
              Database Type
            </label>
            <select
              id="type"
              name="type"
              className="mt-2 block w-full dark:bg-slate-900 rounded-md border-0 py-1.5 pl-3 pr-10 text-slate-900 ring-1 ring-inset ring-slate-300 focus:ring-2 focus:ring-indigo-600 sm:text-sm sm:leading-6 dark:text-slate-50"
              defaultValue="POSTGRES"
              onChange={(e) => setType(e.target.value as DatabaseType)}
            >
              {Object.values(DatabaseType).map((type) => (
                <option>{type}</option>
              ))}
            </select>
          </div>
        </div>
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
  connections: ConnectionResponse[];
  addConnectionHandler: () => void;
  editConnectionHandler: (
    connectionId: string,
    connection: PatchConnectionPayload,
  ) => Promise<void>;
}) {
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

const DatabaseSettings = () => {
  const { loading, connections, createConnection, editConnection } =
    useConnections();

  const [showAddConnectionModal, setShowAddConnectionModal] =
    useState<boolean>(false);

  const handleCreateConnection = async (connection: ConnectionPayload) => {
    await createConnection(connection);
    setShowAddConnectionModal(false);
  };

  return (
    <div className="h-full">
      {loading ? (
        <Spinner />
      ) : (
        <div className="">
          <div className="flex w-full dark:bg-slate-950 h-full">
            <ConnectionSettings
              connections={connections}
              addConnectionHandler={() => {
                setShowAddConnectionModal(true);
              }}
              editConnectionHandler={async (connectionId, connection) => {
                await editConnection(connectionId, connection);
              }}
            />
          </div>
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

export { useConnections };
