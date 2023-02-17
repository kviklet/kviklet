import React, { useEffect, useState } from "react";
import "./App.css";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { solid } from "@fortawesome/fontawesome-svg-core/import.macro";

class Database {
  id: string;
  name: string;
  uri: string;
  username: string;

  constructor(id: string, name: string, uri: string, username: string) {
    this.id = id;
    this.name = name;
    this.uri = uri;
    this.username = username;
  }
}

function Connections() {
  const [databases, setDatabases] = useState<Database[]>([]);

  useEffect(() => {
    const url = "http://localhost:8080/connection/";

    const fetchData = async () => {
      try {
        const response = await fetch(url);
        const json = await response.json();
        console.log(json);
        setDatabases(json.databases);
      } catch (error) {
        console.log("error", error);
      }
    };

    fetchData();
  }, []);

  const handleCreateDatabase = (e: React.SyntheticEvent) => {
    e.preventDefault();
    console.log(e);
    const form = e.target as HTMLFormElement;
    const formData = new FormData(form);
    const json = Object.fromEntries(formData.entries());
    console.log(json);
  };

  return (
    <div>
      <div className="max-w-5xl mx-auto">
        <h2 className="text-2xl font-bold m-5 pl-1.5">Databases</h2>
        <div className="text-center">
          <div className="inline">
            {databases.map((database) => (
              //flex items-center rounded-md p-1.5 bg-indigo-600 text-white
              <div className="flex items-center rounded-md m-5 p-1.5 hover:bg-sky-500 hover:text-white ">
                <div className="basis-1/2 my-auto text-left">
                  {database.name}
                </div>
                <div className="basis-1/4 self-end my-2 px-5 text-right">
                  {database.uri}
                </div>
                <div className="hover:text-sky-500 hover:bg-white p-1.5 rounded-md active:bg-red-600 ">
                  <FontAwesomeIcon icon={solid("trash")}></FontAwesomeIcon>
                </div>
              </div>
            ))}
            <form method="post" onSubmit={handleCreateDatabase}>
              <div className="flex items-center rounded-md m-5 p-1.5 hover:text-white ">
                <input
                  type="text"
                  className="basis-1/5 my-auto shadow appearance-none border rounded w-full mx-1 py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
                  placeholder="Database Name"
                  name="name"
                  id="name"
                />
                <input
                  type="text"
                  className="basis-1/5 my-auto shadow appearance-none border rounded w-full mx-1 py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
                  placeholder="postgresql://localhost:5432/postgres"
                  name="url"
                  id="url"
                />
                <input
                  type="text"
                  className="basis-1/5 my-auto shadow appearance-none border rounded w-full mx-1 py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
                  placeholder="username"
                  name="username"
                  id="username"
                />
                <input
                  type="password"
                  className="basis-1/5 my-auto shadow appearance-none border rounded w-full mx-1 py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
                  placeholder="password"
                  name="password"
                  id="password"
                />
                <button
                  className="text-white bg-sky-500 border rounded w-full basis-1/5 py-2 active:text-sky-500 active:bg-white"
                  type="submit"
                >
                  Add
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  );
}

export default Connections;
