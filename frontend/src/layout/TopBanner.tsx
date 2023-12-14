import { useContext } from "react";
import {
  ThemeContext,
  ThemeStatusContext,
} from "../components/ThemeStatusProvider";
import logo from "../logo.png";
import { JSX } from "react/jsx-runtime";

const navigation = [
  { name: "New", href: "/new" },
  { name: "Requests", href: "/requests" },
  { name: "Settings", href: "/settings" },
];

/* Github icon */
const GitHubIcon = (
  props: JSX.IntrinsicAttributes & SVGProps<SVGSVGElement>
) => (
  <svg fill="currentColor" viewBox="0 0 24 24" {...props}>
    <path
      fillRule="evenodd"
      d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" // rest of the path details
    />
  </svg>
);

function TopBanner() {
  const themeContext = useContext<ThemeContext>(ThemeStatusContext);

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

  return (
    <div>
      <header className="sticky w-full z-50 backdrop-blur-sm border-b border-slate-900/10 dark:border-b-slate-700 inset-x- top-0">
        
        {/* Navigation Bar */}
        <nav className="flex items-center justify-center p-0.5 px-2 sm:px-6" aria-label="Global">
          
          <div className="flex items-center justify-between w-full">
            
            {/* Logo and name */}
            <div className="flex lg:flex-1">
              <a href="#" className="-m-1.5 p-1.5 flex items-center">
                <img className="p-1 h-11 w-auto" src={logo} alt="" />
                <span className="ml-1 text-slate-700 dark:text-slate-50 font-semibold text-lg">Kviklet</span>
              </a>
            </div>
           
            
            <div className="flex justify-end items-center gap-x-2 sm:gap-x-4">
              
              {/* Navigation Links */}
              <div className="flex items-center justify-between pr-3 sm:pr-4 border-r dark:border-slate-600 border-slate-200">
                <div className="flex gap-x-4 sm:gap-x-9 lg:gap-x-9">
                  {navigation.map((item) => (
                    <a
                      key={item.name}
                      href={item.href}
                      className="font-semibold leading-6 text-slate-700 dark:text-slate-50 hover:text-sky-500 dark:hover:text-sky-400"
                      >
                      {item.name}
                    </a>
                  ))}
                </div>
              </div>

              {/* DarkMode Button */}
              <button onClick={switchTheme}>
                <span className="dark:hidden">
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    className="w-6 h-6"
                  >
                    <path
                      d="M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z"
                      className="stroke-slate-400 dark:stroke-slate-500"
                    ></path>
                    <path
                      d="M12 4v1M17.66 6.344l-.828.828M20.005 12.004h-1M17.66 17.664l-.828-.828M12 20.01V19M6.34 17.664l.835-.836M3.995 12.004h1.01M6 6l.835.836"
                      className="stroke-slate-400 dark:stroke-slate-500"
                    ></path>
                  </svg>
                </span>
                
                <span className="hidden dark:inline">
                  <svg viewBox="0 0 24 24" fill="none" className="w-6 h-6">
                    <path
                      fillRule="evenodd"
                      clipRule="evenodd"
                      d="M17.715 15.15A6.5 6.5 0 0 1 9 6.035C6.106 6.922 4 9.645 4 12.867c0 3.94 3.153 7.136 7.042 7.136 3.101 0 5.734-2.032 6.673-4.853Z"
                      className="fill-transparent"
                    ></path>
                    <path
                      d="m17.715 15.15.95.316a1 1 0 0 0-1.445-1.185l.495.869ZM9 6.035l.846.534a1 1 0 0 0-1.14-1.49L9 6.035Zm8.221 8.246a5.47 5.47 0 0 1-2.72.718v2a7.47 7.47 0 0 0 3.71-.98l-.99-1.738Zm-2.72.718A5.5 5.5 0 0 1 9 9.5H7a7.5 7.5 0 0 0 7.5 7.5v-2ZM9 9.5c0-1.079.31-2.082.845-2.93L8.153 5.5A7.47 7.47 0 0 0 7 9.5h2Zm-4 3.368C5 10.089 6.815 7.75 9.292 6.99L8.706 5.08C5.397 6.094 3 9.201 3 12.867h2Zm6.042 6.136C7.718 19.003 5 16.268 5 12.867H3c0 4.48 3.588 8.136 8.042 8.136v-2Zm5.725-4.17c-.81 2.433-3.074 4.17-5.725 4.17v2c3.552 0 6.553-2.327 7.622-5.537l-1.897-.632Z"
                      className="fill-slate-400 dark:fill-slate-500"
                    ></path>
                    <path
                      fillRule="evenodd"
                      clipRule="evenodd"
                      d="M17 3a1 1 0 0 1 1 1 2 2 0 0 0 2 2 1 1 0 1 1 0 2 2 2 0 0 0-2 2 1 1 0 1 1-2 0 2 2 0 0 0-2-2 1 1 0 1 1 0-2 2 2 0 0 0 2-2 1 1 0 0 1 1-1Z"
                      className="fill-slate-400 dark:fill-slate-500"
                    ></path>
                  </svg>
                </span>
              </button>
              
              {/* Github and about */}
              <a
                href="https://github.com/kviklet/kviklet"
                className="flex font-semibold leading-6 text-white items-center hover:text-sky-500 dark:hover:text-sky-400 invert dark:invert-0"
                target="_blank"
                rel="noopener noreferrer"
                >
                <GitHubIcon className="h-6 w-6" />
                <span aria-hidden="true"></span>
              </a>

              <a href="https://Kviklet.dev" 
                target="_blank" 
                rel="noopener noreferrer"
                className="hover:text-sky-500 dark:hover:text-sky-400">
                  about
              </a>
              
            </div>
          </div>
        </nav>
      </header>
    </div>
  );
}

export default TopBanner;