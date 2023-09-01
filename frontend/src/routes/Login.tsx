import { MouseEvent, useContext, useEffect, useState } from "react";
import Button from "../components/Button";
import GoogleButton from "react-google-button";
import { useNavigate } from "react-router-dom";
import { UserStatusContext } from "../components/UserStatusProvider";

const Login = () => {
  const [email, setEmail] = useState<string>("");
  const [password, setPassword] = useState<string>("");
  const navigate = useNavigate();
  const userContext = useContext(UserStatusContext);

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
    await userContext.refreshState();

    navigate("/requests");
  };

  const StyledInput = (props: {
    name: string;
    type: string;
    value: string;
    onChange: (event: any) => void;
  }) => {
    return (
      <input
        name={props.name}
        value={props.value}
        onChange={props.onChange}
        className="appearance-none block w-full px-3 py-2 rounded-md border 
        border-slate-300 dark:bg-slate-900 hover:border-slate-400 focus:border-indigo-600 focus:hover:border-indigo-600
        focus:outline-none focus:ring-gray-500 dark:hover:border-slate-600 dark:hover:focus:border-gray-500 dark:border-slate-700
         dark:focus:border-gray-500 sm:text-sm transition-colors"
        type={props.type}
      ></input>
    );
  };

  return (
    <div>
      <div className="max-w-sm mx-auto my-2 mt-6 dark:bg-slate-950">
        <div className="text-center">
          <img src={require("../logo.png")} className="mx-auto h-24 w-auto" />
        </div>
        <div className="text-2xl text-center mb-6">Sign in to OpsGate</div>
        <div className=" dark:bg-slate-900 p-6 rounded-md shadow-xl">
          <div className="flex flex-col">
            <label className="py-2 text-sm" htmlFor="email">
              Email
            </label>
            <StyledInput
              name="email"
              type="text"
              value={email}
              onChange={(event) => setEmail(event?.target.value)}
            ></StyledInput>
            <label className="py-2 text-sm" htmlFor="password">
              Password
            </label>
            <StyledInput
              name="password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event?.target.value)}
            ></StyledInput>
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
