import { MouseEvent, useEffect, useState } from "react";
import Button from "../components/Button";
import GoogleButton from "react-google-button";
import { useNavigate } from "react-router-dom";

const Login = () => {
  const [email, setEmail] = useState<string>("");
  const [password, setPassword] = useState<string>("");
  const navigate = useNavigate();

  const login = async () => {
    console.log("login");
    const response = await fetch("http://localhost:8080/login", {
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        email: email,
        password: password,
      }),
    });

    navigate("/requests");
  };

  return (
    <div>
      <div className="max-w-sm mx-auto my-2 mt-6">
        <div className="text-center">
          <img src={require("../logo.png")} className="mx-auto h-24 w-auto" />
        </div>
        <div className="text-2xl text-center mb-6">Sign in to OpsGate</div>
        <div className="bg-slate-100 p-4 border rounded-md">
          <div className="flex flex-col">
            <label className="py-2" htmlFor="email">
              Email
            </label>
            <input
              name="email"
              className="appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-gray-500 focus:border-gray-500 sm:text-sm"
              type="text"
              value={email}
              onChange={(event) => setEmail(event?.target.value)}
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
            <a
              href="http://localhost:8080/oauth2/authorization/google"
              className="w-full block mt-8"
            >
              <GoogleButton type="light" className="m-auto"></GoogleButton>
            </a>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Login;
