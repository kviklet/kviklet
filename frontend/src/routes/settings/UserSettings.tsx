import { solid } from "@fortawesome/fontawesome-svg-core/import.macro";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import React from "react";
import Button from "../../components/Button";

const capitalizeFirstLetter = ([first, ...rest]: string) =>
  first.toUpperCase() + rest.join("");

const ColorfulLabel = (props: {
  text: string;
  onDelete?: (event: React.MouseEvent<HTMLButtonElement, MouseEvent>) => void;
  onClick?: (event: React.MouseEvent<HTMLDivElement, MouseEvent>) => void;
  color?: string;
}) => {
  // hash the label text to get a color inside the tailwindcss color palette
  // https://tailwindcss.com/docs/customizing-colors#color-palette-reference
  const hash = (s: string) => {
    return s.split("").reduce(function (a, b) {
      a = (a << 5) - a + b.charCodeAt(0);
      return a & a;
    }, 0);
  };
  const colors = [
    "bg-blue-500",
    "bg-green-500",
    "bg-yellow-500",
    "bg-red-500",
    "bg-indigo-500",
    "bg-purple-500",
    "bg-pink-500",
    "bg-blue-400",
    "bg-green-400",
    "bg-yellow-400",
  ];

  const color = props.color || colors[hash(props.text) % 10];

  return (
    <div
      onClick={props.onClick}
      className={`${color} ${
        props.onClick && "cursor-pointer"
      } text-white text-sm rounded-full px-2 py-1 m-1`}
    >
      {capitalizeFirstLetter(props.text)}

      {props.onDelete && (
        <button onClick={props.onDelete} className="ml-2">
          <FontAwesomeIcon icon={solid("times")} />
        </button>
      )}
    </div>
  );
};

const UserSettings = () => {
  const listOfusers = [
    {
      fullName: "Jascha Beste",
      email: "jascha@opsgate.io",
      groups: ["admin", "user"],
    },
    {
      fullName: "Test User",
      email: "test@example.com",
      groups: ["user"],
    },
    {
      fullName: "Test User 2",
      email: "test@example.comm",
      groups: [],
    },
  ];

  const listOfGroups = [
    {
      name: "admin",
      description: "Admin group",
      users: ["Jascha Beste"],
      permissions: {
        connection: "test Connection",
        actions: [
          "submitWriteRequest",
          "submitReadRequest",
          "viewRequests",
          "downloadResult",
          "ByPassReviewConfig",
        ],
      },
    },
    {
      name: "user",
      description: "User group",
      users: ["Jascha Beste"],
      permissions: {
        connection: "test Connection",
        actions: [
          "submitWriteRequest",
          "submitReadRequest",
          "viewRequests",
          "downloadResult",
        ],
      },
    },
  ];

  return (
    <div>
      <div className="flex flex-col justify-between w-2/3 mx-auto">
        <div className="flex flex-col h-60">
          {listOfusers.map((user) => {
            return (
              <div className="flex flex-row">
                <div className="flex flex-row justify-between w-full shadow-sm p-2">
                  <div className="flex flex-row w-1/3">
                    <div className="font-bold">{user.fullName}</div>
                  </div>
                  <div className="flex flex-row text-slate-400 w-1/3">
                    <div>{user.email}</div>
                  </div>
                  <div className="flex flex-row w-1/3 flex-wrap justify-end">
                    {user.groups.map((group) => {
                      return (
                        <ColorfulLabel
                          onDelete={() => {
                            console.log("Remove Group");
                          }}
                          text={group}
                        />
                      );
                    })}
                    <ColorfulLabel
                      text="Add Group"
                      onClick={() => {
                        console.log("Add Group");
                      }}
                      color="bg-white text-slate-700 border border-slate-400"
                    />
                  </div>
                </div>
              </div>
            );
          })}
        </div>
        <div className="flex">
          <Button className="ml-auto my-2">Add User</Button>
        </div>
      </div>
    </div>
  );
};

export default UserSettings;
