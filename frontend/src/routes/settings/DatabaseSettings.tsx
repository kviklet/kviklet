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
      <div className="flex flex-col w-2xl shadow px-10 py-5 bg-slate-50 border border-slate-300 dark:border-none dark:bg-slate-950 rounded-lg">
        <h1 className="text-lg font-semibold p-2">Add a new connection</h1>
        <InputField
          id="displayName"
          name="Name"
          placeholder="Connection name"
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
          name="Database"
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
          name="Username"
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
        <div className="flex flex-row justify-between">
          <div className="w-full text-slate-400">
            <InputField
              id="hostname"
              name="Hostname"
              className=""
              placeholder="localhost"
              value={hostname}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                setHostname(e.target.value)
              }
            />
          </div>
          <div className="w-3/5 text-slate-400">
            <InputField
              id="port"
              name="Port"
              className=""
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
              dark:bg-slate-900 hover:border-slate-400 focus:border-indigo-600 focus:hover:border-indigo-600
              focus-outline-none dark:hover:border-slate-600 dark:focus:border-gray-500 dark:focus:hover:border-slate-700 dark:border-slate-700
              py-2 pl-2 pr-10 text-slate-400"
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

  // Main screen showing connection
  return (
    <div className=" my-4 mx-2 px-4 py-4 shadow-md border border-slate-200 bg-slate-50 dark:bg-slate-900 dark:border dark:border-slate-700 rounded-md transition-colors">
      <div className="flex justify-between">
        <div className="text-md font-semibold">
          {props.connection.displayName}
        </div>
        <div className="font-mono text-slate-300 text-sm">
          {props.connection.shortUsername + "..."}
        </div>
      </div>

      <div className="flex flex-col pl-2 pt-3">
        <div className="pb-3 text-slate-500 dark:text-slate-400">
          {props.connection.description}
        </div>
        <div className="flex justify-between">
          <label
            htmlFor="database-name"
            className="mr-auto dark:text-slate-400"
          >
            Database name:
          </label>
          <input
            type="database-name"
            value={databaseName}
            onChange={(e) => {
              setDatabaseName(e.target.value);
              setShowCheck(true);
            }}
            className="sm:w-36 lg:w-auto mb-2 rounded mx-1 py-1 px-3 appearance-none border border-slate-200 
              hover:border-slate-300 focus:border-slate-500 focus:hover:border-slate-500 focus:shadow-outline focus:outline-none
              text-slate-600 dark:text-slate-50 leading-tight
              dark:bg-slate-900 dark:border-slate-700 dark:hover:border-slate-600 dark:focus:border-slate-500 dark:focus:hover:border-slate-500 transition-colors"
          ></input>
        </div>
        <div className="flex justify-between">
          <label htmlFor="number" className="mr-auto dark:text-slate-400">
            Required reviews:
          </label>
          <input
            type="number"
            min="0"
            value={numTotalRequired}
            onChange={(e) => {
              setNumTotalRequired(parseInt(e.target.value));
              setShowCheck(true);
            }}
            className="w-32 sm:w-36 lg:w-auto rounded mx-1 py-1 px-3 appearance-none border border-slate-200 
            hover:border-slate-300 focus:border-slate-500 focus:hover:border-slate-500 focus:shadow-outline focus:outline-none
            text-slate-600 dark:text-slate-50 leading-tight
            dark:bg-slate-900 dark:border-slate-700 dark:hover:border-slate-600 dark:focus:border-slate-500 dark:focus:hover:border-slate-500 transition-colors"
          ></input>
        </div>

        {/* Accept button */}
        <div className="flex justify-end">
          <button
            onClick={() => void submit()}
            className={`dark:bg-slate-800 mt-3 mr-1 px-5 rounded-md text-white-600 hover:text-sky-500 dark:hover:text-sky-400
              shadow-sm border border-slate-300 dark:border-slate-700 hover:border-slate-300 dark:hover:border-slate-600 transition-colors ${
                showCheck ? "visible" : "invisible"
              }`}
          >
            <FontAwesomeIcon icon={solid("check")} />
          </button>
        </div>
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
        <div className="pl-5 h-full flex flex-col justify-between">
          <div className="overflow-y-auto flex-grow">
            {props.connections.map((connection) => (
              <SingleConnectionSettings
                key={connection.id} // Assuming each connection has a unique 'id'
                connection={connection}
                editConnectionHandler={props.editConnectionHandler}
              />
            ))}
          </div>
          <Button
            className="ml-auto mx-2 my-1"
            onClick={props.addConnectionHandler}
          >
            Add connection
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
