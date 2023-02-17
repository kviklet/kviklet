import React, { useEffect, useState } from "react";
import "./App.css";

class Query {
  query: string;
  author: string;
  submittedAt: Date;

  constructor(query: string, author: string, submittedAt: Date) {
    this.query = query;
    this.author = author;
    this.submittedAt = submittedAt;
  }
}

function App() {
  const queries = [
    new Query(
      "select * from passwords;",
      "Jascha",
      new Date("2023-01-24T00:47:00")
    ),
    new Query(
      "select count(*) from users;",
      "Jascha",
      new Date("2023-01-24T00:48:00")
    ),
  ];

  return (
    <div>
      <h1 className="text-3xl font-bold text-blue-800 text-center">
        Jascha's tailwind practice ground
      </h1>
      <div className="text-center">
        <div className="inline-block w-1/2">
          {queries.map((query) => (
            <div className="text-sm text-gray-800 hover:shadow-lg hover:font-semibold my-5 px-1 text-left shadow-sm flex">
              <div className="basis-1/2 m-auto text-center">{query.query}</div>
              <div className="basis-1/4 self-end my-2 text-right border-r-2">
                {query.author}
              </div>
              <div className="basis-1/4 text-center">
                {String(query.submittedAt)}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

export default App;
