import { MouseEvent, useEffect, useState } from "react";
import Button from "../components/Button";

const Login = () => {
  const [username, setUsername] = useState<string>("");
  const [password, setPassword] = useState<string>("");

  const login = async () => {
    console.log("login");
    const response = await fetch("http://localhost:8080/login", {
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        username: username,
        password: password,
      }),
    });
    window.location.href = "/";
  };

  return (
    <div>
      <div className="max-w-sm mx-auto my-2 mt-6">
        <div className="text-2xl text-center mb-6">Sign in to FourEyes</div>
        <div className="bg-slate-100 p-4 border rounded-md">
          <div className="flex flex-col">
            <label className="py-2" htmlFor="username">
              Username
            </label>
            <input
              name="username"
              className="appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-gray-500 focus:border-gray-500 sm:text-sm"
              type="text"
              value={username}
              onChange={(event) => setUsername(event?.target.value)}
            ></input>
            <label className="py-2" htmlFor="password">
              Password
            </label>
            <input
              name="password"
              type="password"
              className="appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-gray-500 focus:border-gray-500 sm:text-sm"
              value={password}
              onChange={(event) => setPassword(event?.target.value)}
            ></input>
            <Button className="mt-2" id="sign-in" onClick={login}>
              Sign in
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Login;
