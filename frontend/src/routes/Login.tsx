import { ChangeEvent, FormEvent, useContext, useState } from "react";
import Button from "../components/Button";
import GoogleButton from "react-google-button";
import { useNavigate } from "react-router-dom";
import { UserStatusContext } from "../components/UserStatusProvider";
import image from "../logo.png";
import baseUrl from "../api/base";
import useConfig from "../hooks/config";
import Spinner from "../components/Spinner";
import { isApiErrorResponse } from "../api/Errors";
import useNotification from "../hooks/useNotification";
import { attemptLogin } from "../api/LoginApi";

const StyledInput = (props: {
  name: string;
  type: string;
  value: string;
  onChange: (event: ChangeEvent<HTMLInputElement>) => void;
  dataTestId?: string;
}) => {
  return (
    <input
      id={props.name}
      name={props.name}
      value={props.value}
      onChange={props.onChange}
      data-testid={props.dataTestId}
      className="block w-full appearance-none rounded-md border border-slate-300 px-3 
        py-2 transition-colors focus:border-indigo-600 focus:outline-none hover:border-slate-400
        focus:hover:border-indigo-600 dark:border-slate-700 dark:bg-slate-900 dark:focus:border-gray-500
         dark:hover:border-slate-600 dark:hover:focus:border-gray-500 sm:text-sm"
      type={props.type}
    ></input>
  );
};

const Login = () => {
  const [email, setEmail] = useState<string>("");
  const [password, setPassword] = useState<string>("");
  const navigate = useNavigate();
  const userContext = useContext(UserStatusContext);

  const { config, loading } = useConfig();

  const { addNotification } = useNotification();

  const login = async (event: FormEvent) => {
    event.preventDefault();
    const response = await attemptLogin(email, password);
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Failed to login",
        text: response.message,
        type: "error",
      });
    } else {
      await userContext.refreshState();

      navigate("/requests");
    }
  };

  const oAuthButton = () => {
    if (config?.oauthProvider) {
      if (config.oauthProvider === "google") {
        return (
          <a
            href={`${baseUrl}/oauth2/authorization/google`}
            className="mt-8 block w-full"
          >
            <GoogleButton type="light" className="m-auto"></GoogleButton>
          </a>
        );
      }
      if (config.oauthProvider === "keycloak") {
        return (
          <a
            href={`${baseUrl}/oauth2/authorization/keycloak`}
            className="mt-8 block w-full"
          >
            <Button className="mx-auto w-full">Login with Keycloak</Button>
          </a>
        );
      } else {
        return (
          <a
            href={`${baseUrl}/oauth2/authorization/${config.oauthProvider}`}
            className="mt-8 block w-full"
          >
            <Button className="mx-auto w-full">
              Login with {config.oauthProvider}
            </Button>
          </a>
        );
      }
    }
  };

  return (
    <div>
      {(loading && <Spinner></Spinner>) || (
        <div className="mx-auto my-2 mt-6 max-w-sm dark:bg-slate-950">
          <div className="text-center">
            <img
              src={image}
              className="mx-auto h-24 w-auto invert dark:invert-0"
            />
          </div>
          <div className="mb-6 text-center text-2xl">Sign in to Kviklet</div>
          <div className=" rounded-md p-6 shadow-xl dark:bg-slate-900">
            <form onSubmit={(e) => void login(e)}>
              <div className="flex flex-col">
                <label className="py-2 text-sm" htmlFor="email">
                  {(config?.ldapEnabled && "LDAP login") || "Email"}
                </label>
                <StyledInput
                  name="email"
                  type="text"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  dataTestId="email-input"
                ></StyledInput>
                <label className="py-2 text-sm" htmlFor="password">
                  Password
                </label>
                <StyledInput
                  name="password"
                  type="password"
                  value={password}
                  onChange={(event) => setPassword(event?.target.value)}
                  dataTestId="password-input"
                ></StyledInput>
                <Button
                  className="mt-2 w-full"
                  id="sign-in"
                  type="submit"
                  dataTestId="login-button"
                >
                  Sign in
                </Button>
              </div>
            </form>

            {oAuthButton()}
          </div>
        </div>
      )}
    </div>
  );
};

export default Login;
