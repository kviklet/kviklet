import { useEffect, useState } from "react";
import {
  Connection,
  ConnectionPayload,
  Database,
  DatabasePayload,
  addConnection,
  addDatabase,
  fetchDatabases,
} from "../../api/DatasourceApi";
import Button from "../../components/Button";
import InputField from "../../components/InputField";
import Modal from "../../components/Modal";

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
        <div className="flex justify-end mx-2 my-2">
          <Button type="submit">Add </Button>
        </div>
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
        <div className="flex justify-end mx-2 my-2">
          <Button type="submit">Add</Button>
        </div>
      </div>
    </form>
  );
}

function ConnectionSettings(props: {
  selectedIndex: number | undefined;
  connections: Connection[];
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
  databases: Database[];
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
    await addDatabase(payload);
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
    await addConnection(payload, id);
    setShowAddConnectionModal(false);
  };

  const disableModalOutsideClick = (event: React.SyntheticEvent) => {
    if (event.target === event.currentTarget) {
      setShowAddDatasourceModal(false);
      setShowAddConnectionModal(false);
    }
  };
  console.log("hellow");

  return (
    <div>
      <div className="flex w-full">
        <DatabaseChooser
          databases={databases}
          setSelectedIndex={setSelectedIndex}
          selectedIndex={selectedIndex}
          setShowAddDatasourceModal={setShowAddDatasourceModal}
        ></DatabaseChooser>
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
      </div>
      {showAddDatasourceModal ? (
        <Modal setVisible={setShowAddDatasourceModal}>
          <CreateDatabaseForm handleCreateDatabase={handleCreateDatabase} />
        </Modal>
      ) : (
        ""
      )}
      {showAddConnectionModal ? (
        <Modal setVisible={setShowAddConnectionModal}>
          <CreateConnectionForm
            handleCreateConnection={handleCreateConnection}
          />
        </Modal>
      ) : (
        ""
      )}
    </div>
  );
};

export default DatabaseSettings;
