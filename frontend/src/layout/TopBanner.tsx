import { useContext } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  ThemeContext,
  ThemeStatusContext,
} from "../components/ThemeStatusProvider";
import image from "../logo.png";
import { Cog6ToothIcon } from "@heroicons/react/20/solid";
import { Fragment } from "react";
import {
  Popover,
  PopoverButton,
  PopoverPanel,
  Transition,
} from "@headlessui/react";
import { logout } from "../api/LoginApi";
import { UserStatusContext } from "../components/UserStatusProvider";

function TopBanner() {
  const themeContext = useContext<ThemeContext>(ThemeStatusContext);

  const userContext = useContext(UserStatusContext);

  const switchTheme = () => {
    if (themeContext.currentTheme === "light") {
      themeContext.setTheme("dark");
      localStorage.theme = "dark";
      document.documentElement.classList.add("dark");
    } else {
      themeContext.setTheme("light");
      localStorage.theme = "light";
      document.documentElement.classList.remove("dark");
    }
  };

  const navigate = useNavigate();

  const logoutHandler = async () => {
    await logout();
    navigate("/login");
  };

  const loggedIn = userContext.userStatus !== undefined;

  return (
    <div className="supports-backdrop-blur:bg-white/95 sticky top-0 z-40 mx-auto h-16 w-full flex-none border-b border-slate-900/10 backdrop-blur transition-colors duration-500 dark:border-b-slate-700">
      <div className="mx-4 px-8 py-4">
        <div className="relative flex items-center">
          <Link to="/">
            <div className="flex">
              <img src={image} className="h-8 invert dark:invert-0" />
              <h1 className="ml-2 text-xl font-bold text-slate-700 dark:text-slate-50">
                Kviklet
              </h1>
            </div>
          </Link>
          <div className="relative ml-auto flex items-center">
            <nav className="text-sm font-semibold leading-6 text-slate-700 dark:text-slate-200">
              <ul className="flex space-x-8">
                <li>
                  <Link
                    to={"/new"}
                    className="hover:text-sky-500 dark:hover:text-sky-400"
                    data-testid="new-link"
                  >
                    New
                  </Link>
                </li>
                <li>
                  <Link
                    to={"/requests"}
                    className="hover:text-sky-500 dark:hover:text-sky-400"
                    data-testid="requests-link"
                  >
                    Requests
                  </Link>
                </li>
              </ul>
            </nav>
            <div className="ml-6 flex items-center border-l border-slate-200 pl-6 dark:border-slate-800">
              <Popover className="relative h-6">
                <PopoverButton
                  className="mr-4 h-6 w-6 text-slate-400 transition-colors hover:text-slate-800 dark:text-slate-500 dark:hover:text-slate-200"
                  data-testid="settings-dropdown"
                >
                  <span className="sr-only">Settings Drop Down</span>
                  <Cog6ToothIcon className="" />
                </PopoverButton>

                <Transition
                  as={Fragment}
                  enter="transition ease-out duration-200"
                  enterFrom="opacity-0 translate-y-1"
                  enterTo="opacity-100 translate-y-0"
                  leave="transition ease-in duration-150"
                  leaveFrom="opacity-100 translate-y-0"
                  leaveTo="opacity-0 translate-y-1"
                >
                  <PopoverPanel className="absolute left-1/2 z-10 mt-5 flex w-screen max-w-min -translate-x-1/2 px-4">
                    <div className="dark:border-px w-40 shrink rounded-xl bg-white p-4 text-sm font-semibold leading-6 shadow-lg ring-1 ring-slate-900/5 dark:border dark:border-slate-700 dark:bg-slate-900">
                      <PopoverButton as={Link} to="/settings">
                        <p className="p-2 text-slate-900 hover:text-sky-500 dark:text-slate-50 dark:hover:text-sky-400">
                          Settings
                        </p>
                      </PopoverButton>
                      <PopoverButton as={Link} to="/auditlog">
                        <p className="p-2 text-slate-900 hover:text-sky-500 dark:text-slate-50 dark:hover:text-sky-400">
                          Auditlog
                        </p>
                      </PopoverButton>
                      {loggedIn && (
                        <PopoverButton onClick={() => void logoutHandler()}>
                          <p className="p-2 text-slate-900 hover:text-sky-500 dark:text-slate-50 dark:hover:text-sky-400">
                            Logout
                          </p>
                        </PopoverButton>
                      )}
                    </div>
                  </PopoverPanel>
                </Transition>
              </Popover>
              <button onClick={switchTheme}>
                <span className="dark:hidden">
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    className="group h-6 w-6 "
                  >
                    <path
                      d="M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z"
                      className="stroke-slate-400 group-hover:stroke-slate-800 dark:stroke-slate-500"
                    ></path>
                    <path
                      d="M12 4v1M17.66 6.344l-.828.828M20.005 12.004h-1M17.66 17.664l-.828-.828M12 20.01V19M6.34 17.664l.835-.836M3.995 12.004h1.01M6 6l.835.836"
                      className="stroke-slate-400 group-hover:stroke-slate-800 dark:stroke-slate-500"
                    ></path>
                  </svg>
                </span>
                <span className="hidden dark:inline">
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    className="group h-6 w-6"
                  >
                    <path
                      fillRule="evenodd"
                      clipRule="evenodd"
                      d="M17.715 15.15A6.5 6.5 0 0 1 9 6.035C6.106 6.922 4 9.645 4 12.867c0 3.94 3.153 7.136 7.042 7.136 3.101 0 5.734-2.032 6.673-4.853Z"
                      className="fill-transparent"
                    ></path>
                    <path
                      d="m17.715 15.15.95.316a1 1 0 0 0-1.445-1.185l.495.869ZM9 6.035l.846.534a1 1 0 0 0-1.14-1.49L9 6.035Zm8.221 8.246a5.47 5.47 0 0 1-2.72.718v2a7.47 7.47 0 0 0 3.71-.98l-.99-1.738Zm-2.72.718A5.5 5.5 0 0 1 9 9.5H7a7.5 7.5 0 0 0 7.5 7.5v-2ZM9 9.5c0-1.079.31-2.082.845-2.93L8.153 5.5A7.47 7.47 0 0 0 7 9.5h2Zm-4 3.368C5 10.089 6.815 7.75 9.292 6.99L8.706 5.08C5.397 6.094 3 9.201 3 12.867h2Zm6.042 6.136C7.718 19.003 5 16.268 5 12.867H3c0 4.48 3.588 8.136 8.042 8.136v-2Zm5.725-4.17c-.81 2.433-3.074 4.17-5.725 4.17v2c3.552 0 6.553-2.327 7.622-5.537l-1.897-.632Z"
                      className="fill-slate-400 group-hover:fill-slate-200 dark:fill-slate-500"
                    ></path>
                    <path
                      fillRule="evenodd"
                      clipRule="evenodd"
                      d="M17 3a1 1 0 0 1 1 1 2 2 0 0 0 2 2 1 1 0 1 1 0 2 2 2 0 0 0-2 2 1 1 0 1 1-2 0 2 2 0 0 0-2-2 1 1 0 1 1 0-2 2 2 0 0 0 2-2 1 1 0 0 1 1-1Z"
                      className="fill-slate-400 group-hover:fill-slate-200 dark:fill-slate-500"
                    ></path>
                  </svg>
                </span>
              </button>
              <a href="https://github.com/kviklet/kviklet">
                <svg
                  viewBox="0 0 16 16"
                  className="ml-4 mr-6 h-5 w-5"
                  fill="currentColor"
                  aria-hidden="true"
                >
                  <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"></path>
                </svg>
              </a>
              <a href="https://kviklet.dev">about</a>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default TopBanner;
